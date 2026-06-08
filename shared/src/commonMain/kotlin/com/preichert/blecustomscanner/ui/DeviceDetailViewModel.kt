package com.preichert.blecustomscanner.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.preichert.blecustomscanner.ble.BleController
import com.preichert.blecustomscanner.ble.BleDevice
import com.preichert.blecustomscanner.ble.DeviceConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DeviceDetailState(
    val device: BleDevice? = null,
    val connection: DeviceConnection = DeviceConnection(),
)

sealed interface DeviceDetailEvent {
    data object NavigateBack : DeviceDetailEvent
}

class DeviceDetailViewModel(
    private val controller: BleController,
    private val deviceId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceDetailState())
    val state: StateFlow<DeviceDetailState> = _state.asStateFlow()

    private val _events = Channel<DeviceDetailEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            controller.connection.collect { conn ->
                _state.update { it.copy(connection = conn) }
            }
        }
        viewModelScope.launch {
            combine(controller.scannedDevices, controller.pairedDevices) { scanned, paired ->
                (scanned + paired).find { it.id == deviceId }
            }.collect { device ->
                _state.update { it.copy(device = device) }
            }
        }
        viewModelScope.launch {
            val device = combine(controller.scannedDevices, controller.pairedDevices) { scanned, paired ->
                (scanned + paired).find { it.id == deviceId }
            }.first { it != null }!!
            controller.connect(device)
        }
    }

    override fun onCleared() {
        super.onCleared()
        controller.disconnect()
    }
}
