package com.preichert.blecustomscanner.di

import com.preichert.blecustomscanner.ble.BleController
import com.preichert.blecustomscanner.repository.BleDeviceRepository
import com.preichert.blecustomscanner.repository.BleDeviceRepositoryImpl
import com.preichert.blecustomscanner.ui.DeviceDetailViewModel
import com.preichert.blecustomscanner.ui.ScannerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(
            databaseModule,
            repositoryModule,
            bleModule,
            viewModelModule,
        )
    }

expect val databaseModule: Module

val repositoryModule = module {
    singleOf(::BleDeviceRepositoryImpl) { bind<BleDeviceRepository>() }
}

expect val bleModule: Module

val viewModelModule = module {
    viewModel { (controller: BleController) ->
        ScannerViewModel(controller)
    }
    viewModel { (controller: BleController, deviceId: String) ->
        DeviceDetailViewModel(controller, deviceId)
    }
}
