package com.preichert.blecustomscanner.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.preichert.blecustomscanner.ble.BleDevice

@Entity(tableName = "ble_devices")
data class BleDeviceEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val isConnectable: Boolean,
    val manufacturerData: String?,
    val bonded: Boolean
)

fun BleDeviceEntity.toBleDevice() = BleDevice(
    id = id,
    name = name,
    isConnectable = isConnectable,
    manufacturerData = manufacturerData,
    bonded = bonded
)

fun BleDevice.toEntity() = BleDeviceEntity(
    id = id,
    name = name,
    isConnectable = isConnectable,
    manufacturerData = manufacturerData,
    bonded = bonded
)
