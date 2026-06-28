package com.preichert.blecustomscanner.ui.theme

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformThemeEffect(isDarkMode: Boolean) {
    // iOS handles status bar color based on the info.plist and UIViewController
    // By default, it follows the system appearance.
}
