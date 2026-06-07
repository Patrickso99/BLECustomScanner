package com.preichert.blecustomscanner.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BleDeviceDao {
    @Query("SELECT * FROM ble_devices")
    fun getAll(): Flow<List<BleDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: BleDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<BleDeviceEntity>)

    @Delete
    suspend fun delete(device: BleDeviceEntity)

    @Query("DELETE FROM ble_devices WHERE id NOT IN (:ids)")
    suspend fun deleteExcept(ids: List<String>)
}
