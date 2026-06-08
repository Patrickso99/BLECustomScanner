package com.preichert.blecustomscanner.repository

import com.preichert.blecustomscanner.ble.BleDevice
import kotlinx.coroutines.flow.Flow

interface BleDeviceRepository {
    fun getPairedDevices(): Flow<List<BleDevice>>
    suspend fun saveDevice(device: BleDevice)
    suspend fun saveDevices(devices: List<BleDevice>)
    suspend fun syncDevices(devices: List<BleDevice>)
}
