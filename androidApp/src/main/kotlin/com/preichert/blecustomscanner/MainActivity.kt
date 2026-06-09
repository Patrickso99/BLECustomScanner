package com.preichert.blecustomscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.preichert.blecustomscanner.ble.AndroidBleController
import com.preichert.blecustomscanner.ble.BleController
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val bleController: BleController by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bind the activity to the controller to handle permissions and Bluetooth enabling
        (bleController as? AndroidBleController)?.bind(this)

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        (bleController as? AndroidBleController)?.unbind()
        
        // Dispose only if the activity is finishing (not on config changes) to maintain active scans or connections.
        if (isFinishing) {
            bleController.dispose()
        }
        super.onDestroy()
    }
}
