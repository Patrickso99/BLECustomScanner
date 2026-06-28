package com.preichert.blecustomscanner.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.preichert.blecustomscanner.repository.BleDeviceRepository
import co.touchlab.kermit.Logger
import com.preichert.blecustomscanner.logger.withTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.util.isNotEmpty

/**
 * Android [BleController] backed by [android.bluetooth.le.BluetoothLeScanner] and
 * [BluetoothGatt].
 */
class AndroidBleController(
    context: Context,
    private val repository: BleDeviceRepository,
) : BleController {

    private val logger = Logger.withTag(this::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appContext: Context = context.applicationContext
    private val manager: BluetoothManager? = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = manager?.adapter

    private val _bluetoothState = MutableStateFlow(BluetoothState.Unknown)
    override val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    override val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private var arePermissionsAlreadyAsked = false

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    override val pairedDevices: StateFlow<List<BleDevice>> = repository.getPairedDevices()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connection = MutableStateFlow(DeviceConnection())
    override val connection: StateFlow<DeviceConnection> = _connection.asStateFlow()

    /** Discovered devices kept by address so repeat advertisements just update RSSI. */
    private val discovered = LinkedHashMap<String, BleDevice>()
    private var gatt: BluetoothGatt? = null

    // Sequential GATT read queue — Android allows only one outstanding operation.
    private val readQueue = ArrayDeque<() -> Unit>()
    private var readInFlight = false

    // region launchers & receivers ------------------------------------------------

    private var activity: ComponentActivity? = null
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var enableBtLauncher: ActivityResultLauncher<Intent>? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null

    fun bind(activity: ComponentActivity) {
        this.activity = activity
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            _permissionGranted.value = result.values.all { it } && hasPermissions()
            refreshAdapterState()
            refreshPairedDevices()
        }

        enableBtLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { refreshAdapterState() }

        lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                _permissionGranted.value = hasPermissions()
                refreshAdapterState()
                if (_permissionGranted.value) refreshPairedDevices()
            }
        }
        activity.lifecycle.addObserver(lifecycleObserver!!)

        _permissionGranted.value = hasPermissions()
        refreshAdapterState()
        if (_permissionGranted.value) refreshPairedDevices()
    }

    fun unbind() {
        lifecycleObserver?.let { activity?.lifecycle?.removeObserver(it) }
        lifecycleObserver = null
        activity = null
        permissionLauncher = null
        enableBtLauncher = null
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) refreshAdapterState()
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                stateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            appContext.registerReceiver(
                stateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            )
        }
        _permissionGranted.value = hasPermissions()
        refreshAdapterState()
    }

    // endregion

    // region permissions & adapter state ------------------------------------------

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasPermissions(): Boolean = requiredPermissions().all {
        appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshAdapterState() {
        val currentActivity = activity
        val permissionsBlocked = !hasPermissions() && arePermissionsAlreadyAsked && requiredPermissions().all { permission ->
            currentActivity?.shouldShowRequestPermissionRationale(permission) == false
        }

        _bluetoothState.value = when {
            permissionsBlocked -> BluetoothState.Unauthorized
            adapter == null -> BluetoothState.Unsupported
            !adapter.isEnabled -> BluetoothState.PoweredOff
            else -> BluetoothState.Ready
        }
    }

    override fun requestPermissions() {
        logger.d("Requesting permissions")
        if (hasPermissions()) {
            _permissionGranted.value = true
            arePermissionsAlreadyAsked = true
            refreshAdapterState()
            refreshPairedDevices()
        } else {
            val currentActivity = activity
            val showRationale = currentActivity != null && requiredPermissions().any {
                currentActivity.shouldShowRequestPermissionRationale(it)
            }
            if (!showRationale && arePermissionsAlreadyAsked) {
                openSettings()
            } else {
                arePermissionsAlreadyAsked = true
                permissionLauncher?.launch(requiredPermissions())
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    override fun requestEnableBluetooth() {
        if (adapter == null) return
        if (!adapter.isEnabled) {
            enableBtLauncher?.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    // endregion

    // region scanning -------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::addResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _connection.update { it.copy(error = "Scan failed (code $errorCode)") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val manufacturer = record?.manufacturerSpecificData
        val manufacturerHex = if (manufacturer != null && manufacturer.isNotEmpty()) {
            manufacturer.valueAt(0)?.toHex()
        } else null

        val ble = BleDevice(
            id = device.address,
            name = record?.deviceName ?: safeName(device),
            rssi = result.rssi,
            isConnectable = result.isConnectable,
            manufacturerData = manufacturerHex,
            bonded = try {
                device.bondState == BluetoothDevice.BOND_BONDED
            } catch (_: SecurityException) {
                false
            },
        )
        synchronized(discovered) {
            discovered[device.address] = ble
            _scannedDevices.value = discovered.values.filterNot { it.name.isNullOrBlank() }.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            logger.w("Start scan failed: adapter or scanner null")
            return
        }
        if (!hasPermissions() || _bluetoothState.value != BluetoothState.Ready) {
            logger.w("Start scan failed: permissions or BT state (state=${_bluetoothState.value})")
            return
        }
        if (_isScanning.value) {
            logger.d("Start scan called while already scanning")
            return
        }
        logger.i("Starting BLE scan")
        synchronized(discovered) {
            discovered.clear()
            _scannedDevices.value = emptyList()
        }
        scanner.startScan(scanCallback)
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        if (!_isScanning.value) return
        logger.i("Stopping BLE scan")
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    override fun refreshPairedDevices() {
        if (!hasPermissions()) return
        val bonded = adapter?.bondedDevices ?: emptySet()
        val devices = bonded.map {
            BleDevice(id = it.address, name = safeName(it), bonded = true)
        }
        scope.launch {
            repository.syncDevices(devices)
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String? =
        try { device.name } catch (_: SecurityException) { null }

    // endregion

    // region connection & GATT discovery ------------------------------------------

    @SuppressLint("MissingPermission")
    override fun connect(device: BleDevice) {
        logger.i("Connecting to device: ${device.name} (${device.id})")
        if (!hasPermissions()) {
            _connection.value = DeviceConnection(device = device, error = "Missing Bluetooth permission")
            logger.e("Connect failed: Missing Bluetooth permission")
            return
        }
        stopScan()
        disconnect()
        val remote = adapter?.getRemoteDevice(device.id) ?: run {
            _connection.value = DeviceConnection(device = device, error = "Device unavailable")
            logger.e("Connect failed: Device unavailable")
            return
        }
        _connection.value = DeviceConnection(device = device, state = ConnectionState.Connecting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            val settings = BluetoothGattConnectionSettings.Builder()
                .setAutoConnectEnabled(false)
                .setTransport(BluetoothDevice.TRANSPORT_LE)
                .build()
            gatt = remote.connectGatt(settings, appContext.mainExecutor, gattCallback)
        } else {
            @Suppress("DEPRECATION")
            gatt = remote.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        logger.i("Disconnecting GATT")
        readQueue.clear()
        readInFlight = false
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
    }

    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            logger.d("onConnectionStateChange: status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    logger.i("Connected to GATT")
                    _connection.update { it.copy(state = ConnectionState.Connected, error = null) }
                    if (!g.requestMtu(517)) {
                        logger.d("Request MTU failed, starting service discovery")
                        _connection.update { it.copy(state = ConnectionState.DiscoveringServices) }
                        g.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val failed = status != BluetoothGatt.GATT_SUCCESS
                    logger.i("Disconnected from GATT (failed=$failed, status=$status)")
                    _connection.update {
                        it.copy(
                            state = if (failed) ConnectionState.Failed else ConnectionState.Disconnected,
                            error = if (failed) "Disconnected (status $status)" else it.error,
                        )
                    }
                    g.close()
                    if (gatt === g) gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            logger.d("onMtuChanged: mtu=$mtu, status=$status")
            _connection.update { it.copy(mtu = mtu, state = ConnectionState.DiscoveringServices) }
            g.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            logger.d("onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logger.e("Service discovery failed ($status)")
                _connection.update { it.copy(state = ConnectionState.Failed, error = "Service discovery failed ($status)") }
                return
            }
            _connection.update { it.copy(state = ConnectionState.Ready, services = g.services.map(::mapService)) }
            g.readRemoteRssi()
            // Queue a read for every readable characteristic.
            g.services.forEach { svc ->
                svc.characteristics.forEach { ch ->
                    if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                        logger.v("Queuing read for characteristic: ${ch.uuid}")
                        readQueue.add { g.readCharacteristic(ch) }
                    }
                }
            }
            pumpQueue()
        }

        @Deprecated("Uses the pre-API-33 read callback for backwards compatibility")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value?.toReadable()
                updateCharacteristicValue(characteristic.service.uuid.toString(), characteristic.uuid.toString(), value)
            }
            readInFlight = false
            pumpQueue()
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connection.update { it.copy(rssi = rssi) }
            }
        }
    }

    private fun pumpQueue() {
        if (readInFlight) return
        val op = readQueue.removeFirstOrNull() ?: return
        readInFlight = true
        op()
    }

    private fun updateCharacteristicValue(serviceUuid: String, charUuid: String, value: String?) {
        _connection.update { conn ->
            conn.copy(
                services = conn.services.map { svc ->
                    if (svc.uuid != serviceUuid) svc
                    else svc.copy(
                        characteristics = svc.characteristics.map { ch ->
                            if (ch.uuid == charUuid) ch.copy(value = value) else ch
                        },
                    )
                },
            )
        }
    }

    private fun mapService(service: BluetoothGattService): GattService = GattService(
        uuid = service.uuid.toString(),
        name = GattNames.lookup(service.uuid.toString()),
        isPrimary = service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY,
        characteristics = service.characteristics.map { ch ->
            GattCharacteristic(
                uuid = ch.uuid.toString(),
                name = GattNames.lookup(ch.uuid.toString()),
                properties = ch.properties.toProperties(),
                descriptors = ch.descriptors.map { d ->
                    GattDescriptor(uuid = d.uuid.toString(), name = GattNames.lookup(d.uuid.toString()))
                },
            )
        },
    )

    override fun dispose() {
        stopScan()
        disconnect()
        runCatching { appContext.unregisterReceiver(stateReceiver) }
    }

    // endregion
}

private fun Int.toProperties(): CharacteristicProperties = CharacteristicProperties(
    read = this and BluetoothGattCharacteristic.PROPERTY_READ != 0,
    write = this and BluetoothGattCharacteristic.PROPERTY_WRITE != 0,
    writeNoResponse = this and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0,
    notify = this and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0,
    indicate = this and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0,
)

private fun ByteArray.toHex(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Renders bytes as hex plus a printable-ASCII preview when meaningful. */
private fun ByteArray.toReadable(): String {
    if (isEmpty()) return "(empty)"
    val hex = toHex()
    val ascii = map { it.toInt() and 0xFF }
        .takeIf { bytes -> bytes.all { it in 0x20..0x7E } }
        ?.map { it.toChar() }
        ?.joinToString("")
    return if (ascii != null) "$hex  \"$ascii\"" else hex
}
