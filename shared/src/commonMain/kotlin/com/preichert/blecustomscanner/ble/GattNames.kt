package com.preichert.blecustomscanner.ble

/**
 * Best-effort human-readable names for the most common Bluetooth SIG assigned
 * 16-bit UUIDs. Used to label services/characteristics in the detail screen so
 * the user sees "Battery Level" instead of a bare 128-bit UUID.
 */
object GattNames {

    private val known: Map<String, String> = mapOf(
        // Services
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "1802" to "Immediate Alert",
        "1803" to "Link Loss",
        "1804" to "Tx Power",
        "180a" to "Device Information",
        "180d" to "Heart Rate",
        "180f" to "Battery Service",
        "1810" to "Blood Pressure",
        "1812" to "Human Interface Device",
        "1816" to "Cycling Speed and Cadence",
        "181a" to "Environmental Sensing",
        "181b" to "Body Composition",
        "181c" to "User Data",
        "181d" to "Weight Scale",
        // Characteristics
        "2a00" to "Device Name",
        "2a01" to "Appearance",
        "2a04" to "Peripheral Preferred Connection Parameters",
        "2a05" to "Service Changed",
        "2a06" to "Alert Level",
        "2a07" to "Tx Power Level",
        "2a19" to "Battery Level",
        "2a23" to "System ID",
        "2a24" to "Model Number String",
        "2a25" to "Serial Number String",
        "2a26" to "Firmware Revision String",
        "2a27" to "Hardware Revision String",
        "2a28" to "Software Revision String",
        "2a29" to "Manufacturer Name String",
        "2a2a" to "IEEE 11073-20601 Regulatory Cert.",
        "2a37" to "Heart Rate Measurement",
        "2a38" to "Body Sensor Location",
        "2a50" to "PnP ID",
        "2a6e" to "Temperature",
        "2a6f" to "Humidity",
        // Descriptors
        "2900" to "Characteristic Extended Properties",
        "2901" to "Characteristic User Description",
        "2902" to "Client Characteristic Configuration",
        "2903" to "Server Characteristic Configuration",
        "2904" to "Characteristic Presentation Format",
    )

    /**
     * Returns a friendly name for [uuid] if known, else null. Accepts both full
     * 128-bit UUIDs (extracting the 16-bit short form from the standard base) and
     * already-shortened 16-bit values, case-insensitively.
     */
    fun lookup(uuid: String): String? {
        val normalized = uuid.lowercase()
        known[normalized]?.let { return it }
        // 0000XXXX-0000-1000-8000-00805f9b34fb -> XXXX
        if (normalized.length == 36 && normalized.endsWith("-0000-1000-8000-00805f9b34fb")) {
            val short = normalized.substring(4, 8)
            return known[short]
        }
        return null
    }
}