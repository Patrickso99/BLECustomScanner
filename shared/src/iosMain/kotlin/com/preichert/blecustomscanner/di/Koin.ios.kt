package com.preichert.blecustomscanner.di

import com.preichert.blecustomscanner.ble.BleController
import com.preichert.blecustomscanner.ble.IosBleController
import com.preichert.blecustomscanner.db.AppDatabase
import com.preichert.blecustomscanner.db.getDatabase
import com.preichert.blecustomscanner.db.getDatabaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module = module {
    single<AppDatabase> {
        getDatabase(getDatabaseBuilder())
    }
    single { get<AppDatabase>().bleDeviceDao() }
}

actual val bleModule: Module = module {
    single<BleController> { IosBleController(get()) }
}
