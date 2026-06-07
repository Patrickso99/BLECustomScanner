package com.preichert.blecustomscanner

import android.app.Application
import com.preichert.blecustomscanner.di.initKoin
import org.koin.android.ext.koin.androidContext

class BleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@BleApp)
        }
    }
}
