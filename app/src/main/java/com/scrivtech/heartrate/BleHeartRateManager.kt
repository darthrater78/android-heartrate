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
import android.bluetooth.BluetoothStatusCodes
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
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.scrivtech.heartrate.data.HrReading
import java.util.UUID

enum class ConnectionState {
    IDLE, SCANNING, CONNECTING, RECONNECTING, CONNECTED, DISCONNECTED, FAILED
}

/** Where the live client is in the off-then-on CCCD sequence; see handleServicesDiscovered. */
private enum class CccdStep { NONE, RESETTING, ENABLING, ENABLED }

@SuppressLint("MissingPermission")
class BleHeartRateManager(context: Context) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager.adapter
    private val appContext = context.applicationContext

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The live GATT client. Every callback is checked against it: one callback object serves
     * every client this class opens, so a late event from a client already being torn down
     * would otherwise land on the connection that replaced it. Volatile because the
     * notification path reads it on a binder thread.
     */
    @Volatile private var gatt: BluetoothGatt? = null

    /**
     * A client on its way out: notifications off, then disconnect, then close. Kept apart
     * from [gatt] so its callbacks can finish the shutdown without touching the new session.
     */
    private var closingGatt: BluetoothGatt? = null

    /** Whether [gatt] currently has a link up — only then is a graceful shutdown possible. */
    private var linkUp = false
    private var cccdStep = CccdStep.NONE
    private var hrCccd: BluetoothGattDescriptor? = null

    /** Timestamps on the [SystemClock.elapsedRealtime] clock; 0 means "not yet". */
    private var connectStartedAt = 0L
    private var notificationsEnabledAt = 0L
    @Volatile private var lastReadingAt = 0L

    private var targetDevice: BluetoothDevice? = null
    private var pendingConnect: Runnable? = null

    /**
     * Connection lifecycle counters. GATT callbacks arrive on a binder thread, so every
     * lifecycle handler hops onto [handler] before touching these — the volatile marking
     * covers the reads that happen before that hop.
     */
    @Volatile private var connectAttempt = 0
    @Volatile private var discoveryAttempt = 0
    /** Backed by [reconnectAttemptState] so the live screen can show "n of 3". */
    private var reconnectAttempt: Int
        get() = _reconnectAttempt.value
        set(value) { _reconnectAttempt.value = value }
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

    private val closeTimeout = Runnable {
        android.util.Log.d(TAG, "Graceful close timed out, closing anyway")
        finishClosing()
    }

    /**
     * Watches for readings to stop while CONNECTED. The link reporting itself as up is not
     * proof the watch is sending: a watch that stops sharing, or a CCCD write the watch
     * accepted without starting its stream, leaves a connection that is up and silent, and
     * the app used to sit on it until force-closed.
     */
    private val readingWatchdog = object : Runnable {
        override fun run() {
            if (_state.value != ConnectionState.CONNECTED) return
            val now = SystemClock.elapsedRealtime()
            val last = lastReadingAt
            if (last == 0L) {
                val waited = now - notificationsEnabledAt
                if (waited >= FIRST_READING_TIMEOUT_MS) {
                    android.util.Log.d(TAG, "No reading $waited ms after notifications were enabled")
                    retryOrFail(
                        "Connected, but the watch is not sending heart rate. Make sure heart " +
                            "rate sharing is on at the watch, then connect again."
                    )
                    return
                }
            } else if (now - last >= READING_SILENCE_TIMEOUT_MS) {
                android.util.Log.d(TAG, "No reading for ${now - last} ms, treating it as a drop")
                handleUnexpectedDisconnect()
                return
            }
            handler.postDelayed(this, WATCHDOG_TICK_MS)
        }
    }

    private val _state = MutableStateFlow(ConnectionState.IDLE)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _reconnectAttempt = MutableStateFlow(0)
    val reconnectAttemptState: StateFlow<Int> = _reconnectAttempt.asStateFlow()

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

        startNewSession()
        openGatt(SCAN_SETTLE_DELAY_MS)
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        targetDevice = null
        pendingConnect = null
        // Finish any earlier shutdown first: clearing the handler below drops its timeout.
        finishClosing()
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
            // A graceful close normally finishes well inside the settle delay; if it has not,
            // cut it short rather than hold two clients on the same device.
            finishClosing()
            lastReadingAt = 0L
            notificationsEnabledAt = 0L
            connectStartedAt = SystemClock.elapsedRealtime()
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
     *
     * While the link is up the shutdown is [graceful]: notifications are switched off at the
     * watch before disconnecting. Skipping that was the reconnect hang. The stack holds an
     * idle link open for a few seconds after the last client closes, a reconnect inside that
     * window reuses it, and the watch — still holding "notify on" from the old client — saw
     * the new client's enable as no change and never started streaming. The shutdown is
     * bounded by [closeTimeout], so a watch that never answers cannot leak the client.
     */
    private fun closeGatt(graceful: Boolean = true) {
        handler.removeCallbacks(readingWatchdog)
        val client = gatt ?: return
        gatt = null
        val descriptor = hrCccd
        val notifying = cccdStep != CccdStep.NONE
        val wasUp = linkUp
        hrCccd = null
        cccdStep = CccdStep.NONE
        linkUp = false

        if (!graceful || !wasUp) {
            runCatching { client.disconnect() }
            runCatching { client.close() }
            return
        }

        finishClosing()
        closingGatt = client
        handler.postDelayed(closeTimeout, CLOSE_TIMEOUT_MS)
        val disabling = notifying && descriptor != null && runCatching {
            client.setCharacteristicNotification(descriptor.characteristic, false)
            writeCccd(client, descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        }.getOrDefault(false)
        // Without a disable in flight there is nothing to wait for before disconnecting;
        // with one, the descriptor-write callback disconnects.
        if (!disabling) runCatching { client.disconnect() }
    }

    /** Ends the graceful shutdown started by [closeGatt], whichever step it reached. */
    private fun finishClosing() {
        val client = closingGatt ?: return
        closingGatt = null
        handler.removeCallbacks(closeTimeout)
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }

    /** Retries the connection with backoff, or gives up and surfaces [reason]. */
    private fun retryOrFail(reason: String) {
        handler.removeCallbacks(connectionTimeout)
        closeGatt()

        if (reconnectAttempt > 0) {
            // Each reconnect attempt is a single connect: a failure moves straight on to the
            // next attempt, so "3 attempts" means three, not three rounds of three.
            handleUnexpectedDisconnect()
            return
        }

        if (connectAttempt < MAX_CONNECT_ATTEMPTS) {
            connectAttempt++
            android.util.Log.d(TAG, "Retrying connection, attempt $connectAttempt")
            _state.value = ConnectionState.CONNECTING
            openGatt(RETRY_BACKOFF_MS * connectAttempt)
            return
        }

        _errorMessage.value = reason
        _state.value = ConnectionState.FAILED
    }

    /**
     * Handles a drop the user did not ask for, or a failed reconnect attempt.
     *
     * The client is closed and each attempt is one fresh connect. The live screen and the
     * session stay up through RECONNECTING, and a success carries on in the same session.
     * After [MAX_RECONNECT_ATTEMPTS] it is a hard drop: DISCONNECTED sends the user back to
     * the scan screen, which saves the session.
     */
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
            val name = _connectedDeviceName.value ?: "the device"
            android.util.Log.d(TAG, "Giving up after $MAX_RECONNECT_ATTEMPTS reconnect attempts")
            reconnectAttempt = 0
            _errorMessage.value = "Lost connection to $name and could not reconnect after " +
                "$MAX_RECONNECT_ATTEMPTS attempts. Tap it to reconnect."
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
            handler.post { handleDescriptorWrite(gatt, status) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                publishHeartRate(gatt, parseHeartRate(value))
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                characteristic.value?.let { publishHeartRate(gatt, parseHeartRate(it)) }
            }
        }
    }

    private fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (gatt === closingGatt) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) finishClosing()
            return
        }
        if (gatt !== this.gatt) {
            android.util.Log.d(TAG, "Ignoring state change from a stale client")
            return
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            linkUp = false
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
            linkUp = true
            // The timeout stays armed until notifications are confirmed — discovery and the
            // descriptor write can both stall after the link itself is up.
            android.util.Log.d(TAG, "Connected, discovering services...")

            // Discovery is deliberately delayed rather than called straight from this
            // callback. Asking the stack for services the instant the link comes up is a
            // known Android BLE race that returns an empty or stale service list, and this
            // app spent three releases losing it:
            //
            //   debug build      slowest (debuggable, JIT)   always worked
            //   v1.4.1/v1.5.0    fast (release, logs kept)   intermittent
            //   v1.4.0           fastest (logs stripped)     always hung
            //
            // Minification looked guilty for a long time, but it was only ever a proxy for
            // how quickly execution reached this line. The Log.d call above was acting as
            // an accidental delay; stripping it in v1.4.0 removed the last of the margin.
            // This makes the wait explicit so correctness no longer depends on how fast
            // the build happens to run.
            //
            // The identity guard matches the rediscovery path below: a teardown inside the
            // delay window nulls or replaces `this.gatt`, and discovering on a closed
            // client would throw.
            handler.postDelayed({
                if (this.gatt === gatt) gatt.discoverServices()
            }, SERVICE_DISCOVERY_DELAY_MS)
        }
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (gatt !== this.gatt) return
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
        hrCccd = descriptor

        // CONNECTED is deferred to onDescriptorWrite: until the descriptor write lands the
        // watch is not actually pushing measurements, and reporting success here produced a
        // connected screen that never showed a number.
        //
        // Delayed for the same reason as discovery above. setCharacteristicNotification only
        // sets a local flag; the descriptor write is the operation that actually reaches the
        // peer, and issuing it in the same breath is the second instance of the pattern that
        // made this app's connection depend on execution speed. The wait is shorter because
        // the link is already established by this point.
        //
        // The write is "off" first, then "on" (handleDescriptorWrite chains the second). The
        // watch starts streaming on the change to "on", and a reused link can still hold "on"
        // from an earlier client, so writing "on" alone may change nothing — see closeGatt.
        handler.postDelayed({
            if (this.gatt !== gatt) return@postDelayed
            cccdStep = CccdStep.RESETTING
            if (!writeCccd(gatt, descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                retryOrFail("Could not enable heart rate notifications.")
            }
        }, CCCD_WRITE_DELAY_MS)
    }

    private fun handleDescriptorWrite(gatt: BluetoothGatt, status: Int) {
        if (gatt === closingGatt) {
            // Notifications are off at the watch (or the write failed — either way the
            // shutdown moves on). The disconnect callback, or the timeout, closes the client.
            runCatching { gatt.disconnect() }
            return
        }
        if (gatt !== this.gatt) return

        if (status != BluetoothGatt.GATT_SUCCESS) {
            retryOrFail("Could not enable heart rate notifications (status $status).")
            return
        }

        when (cccdStep) {
            CccdStep.RESETTING -> {
                cccdStep = CccdStep.ENABLING
                val descriptor = hrCccd
                if (descriptor == null ||
                    !writeCccd(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                ) {
                    retryOrFail("Could not enable heart rate notifications.")
                }
            }
            CccdStep.ENABLING -> {
                cccdStep = CccdStep.ENABLED
                handleNotificationsEnabled()
            }
            else -> Unit
        }
    }

    private fun writeCccd(
        client: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        client.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        @Suppress("DEPRECATION")
        descriptor.value = value
        @Suppress("DEPRECATION")
        client.writeDescriptor(descriptor)
    }

    private fun handleNotificationsEnabled() {
        handler.removeCallbacks(connectionTimeout)
        _errorMessage.value = null
        _state.value = ConnectionState.CONNECTED
        notificationsEnabledAt = SystemClock.elapsedRealtime()
        android.util.Log.d(TAG, "Notifications enabled, waiting for the first reading")
        // The retry budgets are reset by the first reading, not here: a connection that
        // enables notifications and then stays silent has not succeeded, and resetting on it
        // would let the watchdog retry forever.
        handler.removeCallbacks(readingWatchdog)
        handler.postDelayed(readingWatchdog, WATCHDOG_TICK_MS)
    }

    private fun onFirstReading(client: BluetoothGatt) {
        if (client !== gatt) return
        connectAttempt = 0
        reconnectAttempt = 0
        val elapsed = SystemClock.elapsedRealtime() - connectStartedAt
        android.util.Log.d(TAG, "First reading $elapsed ms after connectGatt, streaming")
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

    private fun startNewSession() {
        synchronized(readingsLock) { readings.clear() }
        _sessionReadings.value = emptyList()
        currentSessionStartTime = System.currentTimeMillis()
    }

    /** Runs on a binder thread; lifecycle work is posted to [handler]. */
    private fun publishHeartRate(client: BluetoothGatt, bpm: Int) {
        // A client being shut down can deliver a last notification or two.
        if (client !== gatt) return
        // Characteristic payloads are external input: parseHeartRate returns 0 for a short
        // packet, and a malformed uint16 can decode to five digits. Either would corrupt the
        // session's min/max and the graph's scale, so drop anything outside a plausible range.
        if (bpm !in MIN_PLAUSIBLE_BPM..MAX_PLAUSIBLE_BPM) return
        val first = lastReadingAt == 0L
        lastReadingAt = SystemClock.elapsedRealtime()
        if (first) handler.post { onFirstReading(client) }
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
        // The radio is gone, so there is no watch left to switch notifications off at.
        finishClosing()
        closeGatt(graceful = false)
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

        // Settle times that keep the GATT sequence off the stack's races. Both are paid once
        // per connection and sit well inside CONNECT_TIMEOUT_MS, so the cost is under a
        // second of extra connect time in exchange for the sequence no longer depending on
        // how fast the build executes. 600ms matches DISCOVERY_RETRY_DELAY_MS, which has
        // been reliable on the rediscovery path.
        private const val SERVICE_DISCOVERY_DELAY_MS = 600L
        private const val CCCD_WRITE_DELAY_MS = 200L

        // A graceful close is two round trips (CCCD write, disconnect), normally well under
        // 300ms; the timeout only matters for a watch that has stopped answering.
        private const val CLOSE_TIMEOUT_MS = 1_000L

        // The watch sends about one reading a second. The first can lag while its sensor
        // starts, so it gets longer than the gap allowed mid-session.
        private const val FIRST_READING_TIMEOUT_MS = 8_000L
        private const val READING_SILENCE_TIMEOUT_MS = 10_000L
        private const val WATCHDOG_TICK_MS = 1_000L

        // First connects retry up to MAX_CONNECT_ATTEMPTS times (status 133 is routine).
        // Reconnects after a drop get MAX_RECONNECT_ATTEMPTS single tries with 2/4/6s backoff,
        // each bounded by CONNECT_TIMEOUT_MS — about a minute at worst before giving up.
        private const val MAX_CONNECT_ATTEMPTS = 3
        const val MAX_RECONNECT_ATTEMPTS = 3
        private const val MAX_DISCOVERY_ATTEMPTS = 2

        private const val MIN_PLAUSIBLE_BPM = 1
        private const val MAX_PLAUSIBLE_BPM = 250

        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
