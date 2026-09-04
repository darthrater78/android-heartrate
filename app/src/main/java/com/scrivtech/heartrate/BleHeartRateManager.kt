package com.scrivtech.heartrate

import android.annotation.SuppressLint
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
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ConnectionState {
    IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, FAILED
}

@SuppressLint("MissingPermission")
class BleHeartRateManager(context: Context) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager.adapter
    private val appContext = context.applicationContext

    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val connectionTimeout = Runnable {
        if (_state.value == ConnectionState.CONNECTING) {
            _errorMessage.value = "Connection timed out. The device may not support heart rate over BLE."
            _state.value = ConnectionState.FAILED
            gatt?.close()
            gatt = null
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
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

    fun connect(device: BluetoothDevice) {
        stopScan()
        _state.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        _connectedDeviceName.value = device.name ?: device.address

        val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
        val transport = if (isBonded) BluetoothDevice.TRANSPORT_AUTO else BluetoothDevice.TRANSPORT_LE
        gatt = device.connectGatt(appContext, isBonded, gattCallback, transport)

        handler.postDelayed(connectionTimeout, 15_000)
    }

    fun disconnect() {
        handler.removeCallbacks(connectionTimeout)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.IDLE
        _heartRate.value = null
        _connectedDeviceName.value = null
        _errorMessage.value = null
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
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.removeCallbacks(connectionTimeout)
            android.util.Log.d("HeartRateMirror", "onConnectionStateChange status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                _errorMessage.value = "Connection failed (status $status). Try starting a workout first."
                _state.value = ConnectionState.FAILED
                gatt.close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    android.util.Log.d("HeartRateMirror", "Connected, discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = ConnectionState.DISCONNECTED
                    _heartRate.value = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _errorMessage.value = "Service discovery failed"
                _state.value = ConnectionState.FAILED
                gatt.close()
                return
            }

            val serviceUuids = gatt.services.map { it.uuid.toString() }
            android.util.Log.d("HeartRateMirror", "Discovered services: $serviceUuids")

            val hrService = gatt.getService(HR_SERVICE_UUID)
            if (hrService == null) {
                _errorMessage.value = "No Heart Rate service found. " +
                    "Start a workout on the watch and try again."
                _state.value = ConnectionState.FAILED
                gatt.close()
                return
            }

            val hrChar = hrService.getCharacteristic(HR_MEASUREMENT_UUID)
            if (hrChar == null) {
                _errorMessage.value = "Heart Rate service found but no measurement characteristic"
                _state.value = ConnectionState.FAILED
                gatt.close()
                return
            }

            gatt.setCharacteristicNotification(hrChar, true)

            val descriptor = hrChar.getDescriptor(CCCD_UUID) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            _state.value = ConnectionState.CONNECTED
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                _heartRate.value = parseHeartRate(value)
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                characteristic.value?.let { _heartRate.value = parseHeartRate(it) }
            }
        }
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

    companion object {
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
