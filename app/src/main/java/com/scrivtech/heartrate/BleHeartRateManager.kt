package com.scrivtech.heartrate

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.scrivtech.heartrate.data.HrReading
import java.util.UUID

enum class ConnectionState {
    IDLE, SCANNING, CONNECTING, RECONNECTING, CONNECTED, DISCONNECTED, FAILED
}

@SuppressLint("MissingPermission")
class BleHeartRateManager(context: Context) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager.adapter
    private val appContext = context.applicationContext

    private val handler = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null
    private var pendingConnect: Runnable? = null

    /**
     * Connection lifecycle counters. GATT callbacks arrive on a binder thread, so every
     * lifecycle handler hops onto [handler] before touching these — the volatile marking
     * covers the reads that happen before that hop.
     */
    @Volatile private var connectAttempt = 0
    @Volatile private var discoveryAttempt = 0
    @Volatile private var reconnectAttempt = 0
    @Volatile private var userInitiatedDisconnect = false

    private val readingsLock = Any()
    private val readings = ArrayList<HrReading>()

    private val connectionTimeout = Runnable {
        val state = _state.value
        if (state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING) {
            android.util.Log.d(TAG, "Connection attempt timed out")
            retryOrFail("Connection timed out. Make sure heart rate sharing is on at the watch.")
        }
    }

    private val _state = MutableStateFlow(ConnectionState.IDLE)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sessionReadings = MutableStateFlow<List<HrReading>>(emptyList())
    val sessionReadings: StateFlow<List<HrReading>> = _sessionReadings.asStateFlow()

    var currentSessionStartTime: Long = 0L
        private set

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // ---------------------------------------------------------------- scanning

    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        _errorMessage.value = null
        _state.value = ConnectionState.SCANNING

        val bonded = bluetoothAdapter.bondedDevices
            ?.filter { !it.name.isNullOrBlank() }
            ?.toList()
            ?: emptyList()
        _devices.value = bonded

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_state.value == ConnectionState.SCANNING) {
            _state.value = ConnectionState.IDLE
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name.isNullOrBlank()) return
            val current = _devices.value
            if (current.none { it.address == device.address }) {
                _devices.value = current + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            android.util.Log.w(TAG, "Scan failed with error $errorCode")
            handler.post {
                _errorMessage.value = "Bluetooth scan could not start (error $errorCode). " +
                    "Toggle Bluetooth off and on, then try again."
                _state.value = ConnectionState.FAILED
            }
        }
    }

    // -------------------------------------------------------------- connecting

    fun connect(device: BluetoothDevice) {
        stopScan()

        targetDevice = device
        connectAttempt = 0
        reconnectAttempt = 0
        userInitiatedDisconnect = false

        _errorMessage.value = null
        _connectedDeviceName.value = device.name ?: device.address
        _connectedDeviceAddress.value = device.address
        _state.value = ConnectionState.CONNECTING

        synchronized(readingsLock) { readings.clear() }
        _sessionReadings.value = emptyList()
        currentSessionStartTime = System.currentTimeMillis()

        openGatt(SCAN_SETTLE_DELAY_MS)
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        targetDevice = null
        pendingConnect = null
        handler.removeCallbacksAndMessages(null)
        closeGatt()
        _state.value = ConnectionState.IDLE
        _heartRate.value = null
        _errorMessage.value = null
    }

    /**
     * Opens a fresh GATT client after [delayMs].
     *
     * The delay matters on the first attempt: the BLE scanner does not stop the instant
     * [stopScan] returns, and opening a GATT client while it is still tearing down is a
     * well-known source of status 133. On retries the same delay doubles as backoff.
     */
    private fun openGatt(delayMs: Long) {
        val device = targetDevice ?: return
        if (!isBluetoothEnabled) {
            failConnection("Bluetooth is off. Turn it on and try again.")
            return
        }
        closeGatt()
        discoveryAttempt = 0
        handler.removeCallbacks(connectionTimeout)
        // Drop any connect that is still queued. Letting two of them run would open a second
        // client over the first without closing it — the very leak this class guards against.
        pendingConnect?.let { handler.removeCallbacks(it) }

        val task = Runnable {
            pendingConnect = null
            val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
            val transport =
                if (isBonded) BluetoothDevice.TRANSPORT_AUTO else BluetoothDevice.TRANSPORT_LE
            // autoConnect = false: the background connection path can take minutes to
            // resolve and would make the timeout below meaningless. Retries are explicit.
            gatt = device.connectGatt(appContext, false, gattCallback, transport)
            handler.postDelayed(connectionTimeout, CONNECT_TIMEOUT_MS)
        }
        pendingConnect = task
        handler.postDelayed(task, delayMs)
    }

    /**
     * Releases the GATT client back to the Bluetooth stack.
     *
     * Android hands out a limited pool of GATT client interfaces per app and [close] is
     * the only thing that returns one. Leaking them makes every later [connectGatt] fail
     * with status 133 until the process is killed — which is why this must run on every
     * terminal path, not just the happy one.
     */
    private fun closeGatt() {
        gatt?.let { client ->
            runCatching { client.disconnect() }
            runCatching { client.close() }
        }
        gatt = null
    }

    /** Retries the connection with backoff, or gives up and surfaces [reason]. */
    private fun retryOrFail(reason: String) {
        handler.removeCallbacks(connectionTimeout)
        closeGatt()

        if (connectAttempt < MAX_CONNECT_ATTEMPTS) {
            connectAttempt++
            android.util.Log.d(TAG, "Retrying connection, attempt $connectAttempt")
            _state.value =
                if (reconnectAttempt > 0) ConnectionState.RECONNECTING else ConnectionState.CONNECTING
            openGatt(RETRY_BACKOFF_MS * connectAttempt)
            return
        }

        if (reconnectAttempt > 0) {
            // Recovering a live session: hand back to the reconnect ladder, which carries its
            // own budget, rather than ending the session on one exhausted round of retries.
            handleUnexpectedDisconnect()
            return
        }

        _errorMessage.value = reason
        _state.value = ConnectionState.FAILED
    }

    /** Handles a drop that the user did not ask for, reconnecting without ending the session. */
    private fun handleUnexpectedDisconnect() {
        handler.removeCallbacks(connectionTimeout)
        closeGatt()
        _heartRate.value = null

        if (userInitiatedDisconnect || targetDevice == null) {
            _state.value = ConnectionState.DISCONNECTED
            return
        }

        if (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempt++
            connectAttempt = 0
            android.util.Log.d(TAG, "Connection dropped, reconnect attempt $reconnectAttempt")
            _errorMessage.value = null
            _state.value = ConnectionState.RECONNECTING
            openGatt(RECONNECT_BACKOFF_MS * reconnectAttempt)
        } else {
            _errorMessage.value = "Lost connection to the device and could not reconnect."
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    // ---------------------------------------------------------- gatt callbacks

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            handler.post { handleConnectionStateChange(gatt, status, newState) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post { handleServicesDiscovered(gatt, status) }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid != CCCD_UUID) return
            handler.post { handleNotificationsEnabled(status) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                publishHeartRate(parseHeartRate(value))
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                characteristic.value?.let { publishHeartRate(parseHeartRate(it)) }
            }
        }
    }

    private fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (_state.value == ConnectionState.CONNECTED) {
                // A live session dropped — link timeout, peer terminated, out of range. The
                // status code does not matter here: recover on the reconnect ladder rather
                // than treating it as a connect that failed.
                handleUnexpectedDisconnect()
            } else {
                retryOrFail("Connection failed (status $status). Try starting a workout first.")
            }
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            // Status 133 is the stack's generic "try again" rather than a real fault, so
            // treat every non-success the same way: tear down cleanly and retry.
            retryOrFail("Connection failed (status $status). Try starting a workout first.")
            return
        }

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            // The timeout stays armed until notifications are confirmed — discovery and the
            // descriptor write can both stall after the link itself is up.
            android.util.Log.d(TAG, "Connected, discovering services...")
            gatt.discoverServices()
        }
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            retryOrFail("Service discovery failed (status $status).")
            return
        }

        android.util.Log.d(TAG, "Discovered services: ${gatt.services.map { it.uuid }}")

        val hrService = gatt.getService(HR_SERVICE_UUID)
        if (hrService == null) {
            // Android caches a device's service list. If the app connected before heart rate
            // sharing was switched on at the watch, that cache has no 0x180D entry and will
            // keep reporting none until it is dropped — restarting the app looked like the
            // only cure. Drop it and rediscover before giving up.
            if (discoveryAttempt < MAX_DISCOVERY_ATTEMPTS) {
                discoveryAttempt++
                android.util.Log.d(TAG, "No HR service; refreshing cache (try $discoveryAttempt)")
                refreshDeviceCache(gatt)
                handler.postDelayed({
                    // Only if this is still the live client — a teardown inside the delay
                    // would otherwise have us rediscover on a closed one.
                    if (this.gatt === gatt) gatt.discoverServices()
                }, DISCOVERY_RETRY_DELAY_MS)
                return
            }
            failConnection(
                "No Heart Rate service found. Turn on heart rate sharing (or start a " +
                    "workout) on the watch, then connect again."
            )
            return
        }

        val hrChar = hrService.getCharacteristic(HR_MEASUREMENT_UUID)
        if (hrChar == null) {
            failConnection("Heart Rate service found but no measurement characteristic.")
            return
        }

        gatt.setCharacteristicNotification(hrChar, true)

        val descriptor = hrChar.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            failConnection("Heart Rate characteristic does not support notifications.")
            return
        }

        // CONNECTED is deferred to onDescriptorWrite: until the descriptor write lands the
        // watch is not actually pushing measurements, and reporting success here produced a
        // connected screen that never showed a number.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleNotificationsEnabled(status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            retryOrFail("Could not enable heart rate notifications (status $status).")
            return
        }
        handler.removeCallbacks(connectionTimeout)
        connectAttempt = 0
        reconnectAttempt = 0
        _errorMessage.value = null
        _state.value = ConnectionState.CONNECTED
        android.util.Log.d(TAG, "Notifications enabled, streaming heart rate")
    }

    private fun failConnection(reason: String) {
        handler.removeCallbacks(connectionTimeout)
        closeGatt()
        _errorMessage.value = reason
        _state.value = ConnectionState.FAILED
    }

    /**
     * Drops Android's cached GATT service list for this device via the hidden
     * `BluetoothGatt.refresh()`. Reflection against non-SDK members is restricted on modern
     * Android, so this is best effort — when it is unavailable the caller still rediscovers,
     * which picks up the new service on devices that send a Service Changed indication.
     */
    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean = runCatching {
        gatt.javaClass.getMethod("refresh").invoke(gatt) as? Boolean ?: false
    }.getOrElse {
        android.util.Log.d(TAG, "refresh() unavailable: ${it.javaClass.simpleName}")
        false
    }

    // ------------------------------------------------------------- measurements

    private fun publishHeartRate(bpm: Int) {
        // Characteristic payloads are external input: parseHeartRate returns 0 for a short
        // packet, and a malformed uint16 can decode to five digits. Either would corrupt the
        // session's min/max and the graph's scale, so drop anything outside a plausible range.
        if (bpm !in MIN_PLAUSIBLE_BPM..MAX_PLAUSIBLE_BPM) return
        _heartRate.value = bpm

        val reading = HrReading(
            timestampMs = System.currentTimeMillis() - currentSessionStartTime,
            bpm = bpm
        )
        val snapshot = synchronized(readingsLock) {
            readings.add(reading)
            ArrayList(readings)
        }
        _sessionReadings.value = snapshot
    }

    private fun parseHeartRate(data: ByteArray): Int {
        if (data.size < 2) return 0
        val isUint16 = (data[0].toInt() and 0x01) != 0
        return if (isUint16 && data.size >= 3) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            data[1].toInt() and 0xFF
        }
    }

    // ------------------------------------------------------- adapter lifecycle

    /**
     * Watches for the user switching Bluetooth off. Without this the manager keeps handles
     * the stack has already torn down, and the reconnect ladder burns its whole budget
     * retrying against a radio that is not there.
     */
    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val adapterState =
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (adapterState == BluetoothAdapter.STATE_TURNING_OFF ||
                adapterState == BluetoothAdapter.STATE_OFF
            ) {
                handler.post { handleBluetoothOff() }
            }
        }
    }

    private fun handleBluetoothOff() {
        android.util.Log.d(TAG, "Bluetooth turned off, tearing down")
        pendingConnect = null
        handler.removeCallbacksAndMessages(null)
        closeGatt()
        _heartRate.value = null
        _devices.value = emptyList()
        // Clearing the target stops the reconnect ladder: there is nothing to reconnect to
        // until the radio comes back, and the user has to pick a device again anyway.
        targetDevice = null

        if (_state.value != ConnectionState.IDLE) {
            _errorMessage.value = "Bluetooth was turned off."
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    /** Tears down for good. Call from the owning ViewModel's onCleared. */
    fun release() {
        runCatching { appContext.unregisterReceiver(adapterStateReceiver) }
        disconnect()
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(adapterStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(adapterStateReceiver, filter)
        }
    }

    companion object {
        private const val TAG = "HeartRateMirror"

        private const val SCAN_SETTLE_DELAY_MS = 500L
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val RETRY_BACKOFF_MS = 1_000L
        private const val RECONNECT_BACKOFF_MS = 2_000L
        private const val DISCOVERY_RETRY_DELAY_MS = 600L

        // A reconnect round spends up to MAX_CONNECT_ATTEMPTS tries, so the ladders multiply:
        // 3 x 3 attempts with growing backoff is roughly 40s of recovery before giving up.
        private const val MAX_CONNECT_ATTEMPTS = 3
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val MAX_DISCOVERY_ATTEMPTS = 2

        private const val MIN_PLAUSIBLE_BPM = 1
        private const val MAX_PLAUSIBLE_BPM = 250

        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
