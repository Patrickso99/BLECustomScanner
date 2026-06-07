package com.preichert.blecustomscanner.di

import com.preichert.blecustomscanner.ble.AndroidBleController
import com.preichert.blecustomscanner.ble.BleController
import com.preichert.blecustomscanner.db.AppDatabase
import com.preichert.blecustomscanner.db.getDatabase
import com.preichert.blecustomscanner.db.getDatabaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module = module {
    single<AppDatabase> {
        getDatabase(getDatabaseBuilder(androidContext()))
    }
    single { get<AppDatabase>().bleDeviceDao() }
}

actual val bleModule: Module = module {
    // Note: AndroidBleController is a singleton that survives configuration changes.
    // It must be bound to the current Activity in onCreate to handle ActivityResult launchers.
    single<BleController> {
        AndroidBleController(androidContext(), get())
    }
}
