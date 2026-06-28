package com.preichert.blecustomscanner

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {

    return ComposeUIViewController {
        App()
    }
}
