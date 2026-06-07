package com.preichert.blecustomscanner.ui

import kotlinx.serialization.Serializable

@Serializable
data object ScannerGraph

@Serializable
data object ScannerRoute

@Serializable
data class DeviceDetailRoute(val deviceId: String)
