package com.preichert.blecustomscanner.ble

/**
 * High-level state of the platform Bluetooth adapter. Drives the error/empty
 * banners shown in the UI (BLE off, unsupported, unauthorized, ...).
 */
enum class BluetoothState {
    /** State not yet known (adapter still initializing). */
    Unknown,

    /** The device has no BLE hardware / CoreBluetooth reports unsupported. */
    Unsupported,

    /** The user denied the Bluetooth permission. */
    Unauthorized,

    /** Bluetooth is supported and authorized but currently switched off. */
    PoweredOff,

    /** Adapter is resetting (transient). */
    Resetting,

    /** Everything is ready: powered on, authorized, usable. */
    Ready,
}

/**
 * A BLE peripheral, either freshly discovered during a scan or retrieved from
 * the list of paired/known devices.
 *
 * @property id stable identifier used to reconnect — MAC address on Android, the CoreBluetooth peripheral UUID on iOS.
 * @property name advertised/GAP name, null when the peripheral is anonymous.
 * @property rssi last seen signal strength in dBm (null for paired entries).
 * @property isConnectable whether the advertisement flagged the device as connectable.
 * @property manufacturerData raw manufacturer-specific advertising payload, hex encoded.
 * @property bonded true when the OS considers the device paired/bonded.
 */
data class BleDevice(
    val id: String,
    val name: String?,
    val rssi: Int? = null,
    val isConnectable: Boolean = true,
    val manufacturerData: String? = null,
    val bonded: Boolean = false,
) {
    val displayName: String
        get() = name?.takeIf(String::isNotBlank) ?: "Unknown device"
}

/** Lifecycle of a GATT connection to a single peripheral. */
enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    DiscoveringServices,
    Ready,
    Disconnecting,
    Failed,
}

/** A descriptor attached to a GATT characteristic. */
data class GattDescriptor(
    val uuid: String,
    val name: String? = null,
    val value: String? = null,
)

/** Standard GATT characteristic property flags, as advertised by the peripheral. */
data class CharacteristicProperties(
    val read: Boolean = false,
    val write: Boolean = false,
    val writeNoResponse: Boolean = false,
    val notify: Boolean = false,
    val indicate: Boolean = false,
) {
    /** Human-readable, comma separated list — e.g. "READ, NOTIFY". */
    fun label(): String = buildList {
        if (read) add("READ")
        if (write) add("WRITE")
        if (writeNoResponse) add("WRITE_NR")
        if (notify) add("NOTIFY")
        if (indicate) add("INDICATE")
    }.joinToString(", ").ifEmpty { "—" }
}

/** A single GATT characteristic with its (best-effort) read value. */
data class GattCharacteristic(
    val uuid: String,
    val name: String? = null,
    val properties: CharacteristicProperties = CharacteristicProperties(),
    val value: String? = null,
    val descriptors: List<GattDescriptor> = emptyList(),
)

/** A GATT service and the characteristics discovered under it. */
data class GattService(
    val uuid: String,
    val name: String? = null,
    val isPrimary: Boolean = true,
    val characteristics: List<GattCharacteristic> = emptyList(),
)

/**
 * Everything the UI knows about the currently selected peripheral connection.
 * Emitted as an immutable snapshot so Compose can diff it cheaply.
 */
data class DeviceConnection(
    val device: BleDevice? = null,
    val state: ConnectionState = ConnectionState.Disconnected,
    val services: List<GattService> = emptyList(),
    val rssi: Int? = null,
    val mtu: Int? = null,
    /** Non-null when the connection failed or a GATT operation errored. */
    val error: String? = null,
) {
    val characteristicCount: Int
        get() = services.sumOf { gattService -> gattService.characteristics.size }
}