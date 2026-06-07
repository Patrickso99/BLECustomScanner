package com.preichert.blecustomscanner.ble

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic contract the Compose UI talks to. Each platform provides an
 * `actual` implementation (Android: BluetoothLeScanner + GATT, iOS: CoreBluetooth)
 * and the host (MainActivity / MainViewController) injects it into [com.preichert.blecustomscanner.App].
 *
 * All state is exposed as [StateFlow] so the UI can collect it reactively and so
 * the same screens drive both platforms unchanged.
 */
interface BleController {

    /** Current adapter state — powered off, unauthorized, ready, ... */
    val bluetoothState: StateFlow<BluetoothState>

    /** Whether the required runtime permissions have been granted. */
    val permissionGranted: StateFlow<Boolean>

    /** True while a BLE scan is running. */
    val isScanning: StateFlow<Boolean>

    /** Devices discovered during the active/last scan, most recently seen first. */
    val scannedDevices: StateFlow<List<BleDevice>>

    /** Devices the OS reports as paired/bonded (Android) or connected/known (iOS). */
    val pairedDevices: StateFlow<List<BleDevice>>

    /** Live snapshot of the currently selected peripheral connection. */
    val connection: StateFlow<DeviceConnection>

    /** Ask the OS for the runtime permissions needed to scan/connect. */
    fun requestPermissions()

    /** Prompt the user to switch Bluetooth on (no-op where unsupported). */
    fun requestEnableBluetooth()

    /** Start scanning for nearby BLE peripherals. Safe to call repeatedly. */
    fun startScan()

    /** Stop an in-progress scan. */
    fun stopScan()

    /** Re-query the OS paired/known device list. */
    fun refreshPairedDevices()

    /** Connect to [device] and discover all of its services/characteristics. */
    fun connect(device: BleDevice)

    /** Tear down the active connection. */
    fun disconnect()

    /** Release adapter resources. Called when the UI is permanently gone. */
    fun dispose()
}