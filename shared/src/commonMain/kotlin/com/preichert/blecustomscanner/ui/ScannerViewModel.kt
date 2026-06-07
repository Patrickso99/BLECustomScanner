package com.preichert.blecustomscanner.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.preichert.blecustomscanner.ble.BleController
import com.preichert.blecustomscanner.ble.BleDevice
import com.preichert.blecustomscanner.ble.BluetoothState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScannerTab(val title: String) {
    Found("Found"),
    Paired("Paired"),
}

@Immutable
data class ScannerState(
    val bluetoothState: BluetoothState = BluetoothState.Unknown,
    val arePermissionGranted: Boolean = false,
    val isScanning: Boolean = false,
    val scannedDevices: List<BleDevice> = emptyList(),
    val pairedDevices: List<BleDevice> = emptyList(),
    val selectedTab: ScannerTab = ScannerTab.Found,
)

sealed interface ScannerAction {
    data object StartScan : ScannerAction
    data object StopScan : ScannerAction
    data object RequestPermission : ScannerAction
    data object EnableBluetooth : ScannerAction
    data class SelectTab(val tab: ScannerTab) : ScannerAction
    data class NavigateToDevice(val device: BleDevice) : ScannerAction
}

sealed interface ScannerEvent {
    data class NavigateToDevice(val device: BleDevice) : ScannerEvent
}

class ScannerViewModel(
    private val controller: BleController
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerState())
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val _events = Channel<ScannerEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                controller.bluetoothState,
                controller.permissionGranted,
                controller.isScanning,
                controller.scannedDevices,
                controller.pairedDevices,
            ) { bluetoothState, arePermissionGranted, isScanning, scannedDevices, pairedDevices ->
                _state.value.copy(
                    bluetoothState = bluetoothState,
                    arePermissionGranted = arePermissionGranted,
                    isScanning = isScanning,
                    scannedDevices = scannedDevices,
                    pairedDevices = pairedDevices,
                )
            }.collect { value -> 
                _state.value = value
            }
        }
        viewModelScope.launch {
            combine(
                controller.bluetoothState,
                controller.permissionGranted
            ) { bluetoothState, arePermissionGranted ->
                bluetoothState == BluetoothState.Ready && arePermissionGranted
            }
                .distinctUntilChanged()
                .collect { isReady -> 
                    if (isReady) controller.refreshPairedDevices()
                }
        }
    }

    fun onAction(action: ScannerAction) {
        when (action) {
            ScannerAction.StartScan -> controller.startScan()
            ScannerAction.StopScan -> controller.stopScan()
            ScannerAction.RequestPermission -> controller.requestPermissions()
            ScannerAction.EnableBluetooth -> controller.requestEnableBluetooth()
            is ScannerAction.SelectTab -> _state.update { it.copy(selectedTab = action.tab) }
            is ScannerAction.NavigateToDevice -> viewModelScope.launch {
                _events.send(ScannerEvent.NavigateToDevice(action.device))
            }
        }
    }
}
