@file:OptIn(ExperimentalForeignApi::class)

package com.preichert.blecustomscanner.ble

import com.preichert.blecustomscanner.repository.BleDeviceRepository
import co.touchlab.kermit.Logger
import com.preichert.blecustomscanner.logger.withTag
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
import platform.CoreBluetooth.CBAdvertisementDataIsConnectable
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBDescriptor
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS [BleController] backed by CoreBluetooth.
 *
 * Kotlin/Native does not allow a single class to mix a Kotlin interface with
 * Objective-C supertypes, so the CoreBluetooth delegate lives in a separate
 * inner [NSObject] ([Delegate]) that forwards callbacks back into this class.
 *
 * Note: creating the [CBCentralManager] is what triggers the system Bluetooth
 * permission prompt, so no explicit "request permission" call is needed — make
 * sure `NSBluetoothAlwaysUsageDescription` is present in Info.plist.
 */
class IosBleController(
    private val repository: BleDeviceRepository,
) : BleController {

    private val logger = Logger.withTag(this::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _bluetoothState = MutableStateFlow(BluetoothState.Unknown)
    override val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    private val _permissionGranted = MutableStateFlow(true)
    override val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    override val pairedDevices: StateFlow<List<BleDevice>> = repository.getPairedDevices()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connection = MutableStateFlow(DeviceConnection())
    override val connection: StateFlow<DeviceConnection> = _connection.asStateFlow()

    /** CBPeripheral objects must be retained for the lifetime of their use. */
    private val peripherals = mutableMapOf<String, CBPeripheral>()
    private val discovered = LinkedHashMap<String, BleDevice>()
    private var active: CBPeripheral? = null

    /** Common services used to query the OS for already-connected peripherals. */
    private val knownServiceUuids: List<CBUUID> =
        listOf("1800", "1801", "180A", "180F", "180D", "1812", "181A", "1810").map(CBUUID::UUIDWithString)

    private val delegate = Delegate()
    private val central = CBCentralManager(delegate = delegate, queue = null)

    private val notificationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = null,
        usingBlock = {
            onStateChanged(central.state)
        }
    )

    // region BleController --------------------------------------------------------

    override fun requestPermissions() {
        // CoreBluetooth prompts automatically on first use; if the user denied it
        // (either now or previously in Settings), the only recourse is the Settings app.
        if (_bluetoothState.value == BluetoothState.Unauthorized || !_permissionGranted.value) {
            openSettings()
        }
    }

    override fun requestEnableBluetooth() = openSettings()

    override fun startScan() {
        if (central.state != CBManagerStatePoweredOn) {
            logger.w("Start scan failed: BT not powered on (state=${central.state})")
            return
        }
        if (_isScanning.value) {
            logger.d("Start scan called while already scanning")
            return
        }
        logger.i("Starting BLE scan")
        discovered.clear()
        _scannedDevices.value = emptyList()
        central.scanForPeripheralsWithServices(null, null)
        _isScanning.value = true
    }

    override fun stopScan() {
        if (!_isScanning.value) return
        logger.i("Stopping BLE scan")
        central.stopScan()
        _isScanning.value = false
    }

    @Suppress("UNCHECKED_CAST")
    override fun refreshPairedDevices() {
        if (central.state != CBManagerStatePoweredOn) return
        val connected = central.retrieveConnectedPeripheralsWithServices(knownServiceUuids) as List<CBPeripheral>
        val devices = connected.map { cbPeripheral ->
            val id = cbPeripheral.identifier.UUIDString
            peripherals[id] = cbPeripheral
            BleDevice(id = id, name = cbPeripheral.name, bonded = true)
        }
        scope.launch {
            repository.syncDevices(devices)
        }
    }

    override fun connect(device: BleDevice) {
        logger.i("Connecting to device: ${device.name} (${device.id})")
        stopScan()
        disconnect()
        val peripheral = peripherals[device.id] ?: run {
            _connection.value = DeviceConnection(device = device, error = "Peripheral no longer available")
            logger.e("Connect failed: Peripheral no longer available")
            return
        }
        active = peripheral
        peripheral.delegate = delegate
        _connection.value = DeviceConnection(device = device, state = ConnectionState.Connecting)
        central.connectPeripheral(peripheral, null)
    }

    override fun disconnect() {
        logger.i("Disconnecting Peripheral")
        active?.let(central::cancelPeripheralConnection)
        active = null
    }

    override fun dispose() {
        NSNotificationCenter.defaultCenter.removeObserver(notificationObserver)
        stopScan()
        disconnect()
    }

    // endregion

    // region shared helpers (called from the delegate) ----------------------------

    private fun onStateChanged(state: platform.CoreBluetooth.CBManagerState) {
        _bluetoothState.value = when (state) {
            CBManagerStatePoweredOn -> BluetoothState.Ready
            CBManagerStatePoweredOff -> BluetoothState.PoweredOff
            CBManagerStateResetting -> BluetoothState.Resetting
            CBManagerStateUnsupported -> BluetoothState.Unsupported
            CBManagerStateUnauthorized -> BluetoothState.Unauthorized
            else -> BluetoothState.Unknown
        }
        logger.d("centralManagerDidUpdateState: ${_bluetoothState.value}")
        _permissionGranted.value = state != CBManagerStateUnauthorized
        if (state == CBManagerStatePoweredOn) refreshPairedDevices()
    }

    private fun onDiscovered(peripheral: CBPeripheral, advertisementData: Map<Any?, *>, rssi: Int) {
        val id = peripheral.identifier.UUIDString
        peripherals[id] = peripheral

        val manufacturer = (advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData)
            ?.toByteArray()?.toHex()
        val connectable = (advertisementData[CBAdvertisementDataIsConnectable] as? NSNumber)?.boolValue ?: true
        val advName = advertisementData[CBAdvertisementDataLocalNameKey] as? String

        discovered[id] = BleDevice(
            id = id,
            name = peripheral.name ?: advName,
            rssi = rssi,
            isConnectable = connectable,
            manufacturerData = manufacturer,
        )
        _scannedDevices.value = discovered.values.filterNot { it.name.isNullOrBlank() }.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
    }

    @Suppress("UNCHECKED_CAST")
    private fun snapshot(peripheral: CBPeripheral): List<GattService> {
        val services = peripheral.services as? List<CBService> ?: return emptyList()
        return services.map { svc ->
            val uuid = svc.UUID.UUIDString
            GattService(
                uuid = uuid,
                name = GattNames.lookup(uuid),
                characteristics = (svc.characteristics as? List<CBCharacteristic> ?: emptyList()).map { ch ->
                    val cUuid = ch.UUID.UUIDString
                    GattCharacteristic(
                        uuid = cUuid,
                        name = GattNames.lookup(cUuid),
                        properties = ch.properties.toProperties(),
                        value = ch.value?.toReadable(),
                        descriptors = (ch.descriptors as? List<CBDescriptor> ?: emptyList()).map { d ->
                            val dUuid = d.UUID.UUIDString
                            GattDescriptor(
                                uuid = dUuid,
                                name = GattNames.lookup(dUuid),
                                value = d.value?.toString(),
                            )
                        },
                    )
                },
            )
        }
    }

    private fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    // endregion

    /** Bridges CoreBluetooth callbacks (Objective-C) back into the controller. */
    private inner class Delegate :
        NSObject(),
        CBCentralManagerDelegateProtocol,
        CBPeripheralDelegateProtocol {

        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            onStateChanged(central.state)
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            onDiscovered(didDiscoverPeripheral, advertisementData, RSSI.intValue)
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            logger.i("Connected to peripheral: ${didConnectPeripheral.name}")
            _connection.update { it.copy(state = ConnectionState.DiscoveringServices, error = null) }
            didConnectPeripheral.delegate = this
            didConnectPeripheral.discoverServices(null)
            didConnectPeripheral.readRSSI()
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            logger.e("Failed to connect: ${error?.localizedDescription}")
            _connection.update {
                it.copy(state = ConnectionState.Failed, error = error?.localizedDescription ?: "Failed to connect")
            }
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            val failed = error != null
            logger.i("Disconnected from peripheral (failed=$failed, error=${error?.localizedDescription})")
            _connection.update {
                it.copy(
                    state = if (failed) ConnectionState.Failed else ConnectionState.Disconnected,
                    error = error?.localizedDescription ?: it.error,
                )
            }
            if (active === didDisconnectPeripheral) active = null
        }

        @Suppress("UNCHECKED_CAST")
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            if (didDiscoverServices != null) {
                _connection.update { it.copy(error = didDiscoverServices.localizedDescription) }
                return
            }
            val services = peripheral.services as? List<CBService> ?: emptyList()
            services.forEach { peripheral.discoverCharacteristics(null, forService = it) }
            _connection.update { it.copy(state = ConnectionState.Ready, services = snapshot(peripheral)) }
        }

        @Suppress("UNCHECKED_CAST")
        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?,
        ) {
            val characteristics =
                didDiscoverCharacteristicsForService.characteristics as? List<CBCharacteristic> ?: emptyList()
            characteristics.forEach { ch ->
                if (ch.properties and CBCharacteristicPropertyRead != 0uL) {
                    peripheral.readValueForCharacteristic(ch)
                }
                peripheral.discoverDescriptorsForCharacteristic(characteristic = ch)
            }
            _connection.update { it.copy(services = snapshot(peripheral)) }
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            _connection.update { it.copy(services = snapshot(peripheral)) }
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverDescriptorsForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            _connection.update { it.copy(services = snapshot(peripheral)) }
        }

        override fun peripheral(peripheral: CBPeripheral, didReadRSSI: NSNumber, error: NSError?) {
            _connection.update { it.copy(rssi = didReadRSSI.intValue) }
        }
    }
}

private fun ULong.toProperties(): CharacteristicProperties = CharacteristicProperties(
    read = this and CBCharacteristicPropertyRead != 0uL,
    write = this and CBCharacteristicPropertyWrite != 0uL,
    writeNoResponse = this and CBCharacteristicPropertyWriteWithoutResponse != 0uL,
    notify = this and CBCharacteristicPropertyNotify != 0uL,
    indicate = this and CBCharacteristicPropertyIndicate != 0uL,
)

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, this.length) }
    return result
}

private fun ByteArray.toHex(): String =
    joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Renders bytes as hex plus a printable-ASCII preview when meaningful. */
private fun NSData.toReadable(): String {
    val bytes = toByteArray()
    if (bytes.isEmpty()) return "(empty)"
    val hex = bytes.toHex()
    val ascii = bytes.map { it.toInt() and 0xFF }
        .takeIf { all -> all.all { it in 0x20..0x7E } }
        ?.map { it.toChar() }
        ?.joinToString("")
    return if (ascii != null) "$hex  \"$ascii\"" else hex
}
