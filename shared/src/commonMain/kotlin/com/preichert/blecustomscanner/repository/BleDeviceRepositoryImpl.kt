package com.preichert.blecustomscanner.repository

import com.preichert.blecustomscanner.ble.BleDevice
import com.preichert.blecustomscanner.db.BleDeviceDao
import com.preichert.blecustomscanner.db.BleDeviceEntity
import com.preichert.blecustomscanner.db.toBleDevice
import com.preichert.blecustomscanner.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BleDeviceRepositoryImpl(
    private val dao: BleDeviceDao
) : BleDeviceRepository {
    override fun getPairedDevices(): Flow<List<BleDevice>> {
        return dao.getAll().map { entities ->
            entities.map(BleDeviceEntity::toBleDevice)
        }
    }

    override suspend fun saveDevice(device: BleDevice) {
        dao.insert(device.toEntity())
    }

    override suspend fun saveDevices(devices: List<BleDevice>) {
        dao.insertAll(devices.map(BleDevice::toEntity))
    }

    override suspend fun syncDevices(devices: List<BleDevice>) {
        dao.insertAll(devices.map(BleDevice::toEntity))
        dao.deleteExcept(devices.map(BleDevice::id))
    }
}
