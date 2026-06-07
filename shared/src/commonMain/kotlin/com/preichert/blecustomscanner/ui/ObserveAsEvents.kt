package com.preichert.blecustomscanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    key1: Any? = null,
    onEvent: (T) -> Unit,
) {
    LaunchedEffect(key1, flow) {
        flow.collect(onEvent)
    }
}
