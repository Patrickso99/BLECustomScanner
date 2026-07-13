package com.preichert.blecustomscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blecustomscanner.shared.generated.resources.Res
import blecustomscanner.shared.generated.resources.bt_not_ready
import blecustomscanner.shared.generated.resources.no_devices_found
import blecustomscanner.shared.generated.resources.no_devices_yet
import blecustomscanner.shared.generated.resources.no_paired_devices
import blecustomscanner.shared.generated.resources.scan
import blecustomscanner.shared.generated.resources.scanning
import blecustomscanner.shared.generated.resources.stop
import blecustomscanner.shared.generated.resources.tab_found
import blecustomscanner.shared.generated.resources.tab_paired
import com.preichert.blecustomscanner.BuildKonfig
import com.preichert.blecustomscanner.ble.BleDevice
import com.preichert.blecustomscanner.ble.BluetoothState
import com.preichert.blecustomscanner.ui.theme.BleScannerTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun ScannerRoot(
    onNavigateToDevice: (BleDevice) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: ScannerViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ScannerEvent.NavigateToDevice -> onNavigateToDevice(event.device)
        }
    }

    ScannerScreen(
        state = state,
        onAction = viewModel::onAction,
        isDarkMode = isDarkMode,
        onToggleTheme = onToggleTheme,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    state: ScannerState,
    onAction: (ScannerAction) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
) {
    val tab = state.selectedTab
    val isReady = state.arePermissionGranted && state.bluetoothState == BluetoothState.Ready
    val pagerState = rememberPagerState(initialPage = tab.ordinal) { ScannerTab.entries.size }

    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.ordinal) {
            pagerState.animateScrollToPage(tab.ordinal)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onAction(ScannerAction.SelectTab(ScannerTab.entries[page]))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(BuildKonfig.APP_NAME) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Text(
                            text = if (isDarkMode) "☀️" else "🌙",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (tab == ScannerTab.Found && isReady) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (state.isScanning) {
                            onAction(ScannerAction.StopScan)
                        } else {
                            onAction(ScannerAction.StartScan)
                        }
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(if (state.isScanning) stringResource(Res.string.stop) else stringResource(Res.string.scan))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StatusBanner(
                bluetoothState = state.bluetoothState,
                arePermissionGranted = state.arePermissionGranted,
                onRequestPermission = { onAction(ScannerAction.RequestPermission) },
                onEnableBluetooth = { onAction(ScannerAction.EnableBluetooth) },
            )

            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                ScannerTab.entries.forEach { entry ->
                    Tab(
                        selected = pagerState.currentPage == entry.ordinal,
                        onClick = { onAction(ScannerAction.SelectTab(entry)) },
                        text = {
                            val count = when (entry) {
                                ScannerTab.Found -> state.scannedDevices.size
                                ScannerTab.Paired -> state.pairedDevices.size
                            }
                            val title = when (entry) {
                                ScannerTab.Found -> stringResource(Res.string.tab_found)
                                ScannerTab.Paired -> stringResource(Res.string.tab_paired)
                            }
                            Text("$title ($count)")
                        },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().weight(1f),
            ) { page ->
                val currentTab = ScannerTab.entries[page]
                val devices = if (currentTab == ScannerTab.Found) state.scannedDevices else state.pairedDevices

                if (devices.isEmpty()) {
                    EmptyState(
                        text = when {
                            !isReady -> stringResource(Res.string.bt_not_ready)
                            currentTab == ScannerTab.Found && state.isScanning -> stringResource(Res.string.scanning)
                            currentTab == ScannerTab.Found && !state.hasStartedScanning -> stringResource(Res.string.no_devices_yet)
                            currentTab == ScannerTab.Found -> stringResource(Res.string.no_devices_found)
                            else -> stringResource(Res.string.no_paired_devices)
                        },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(devices, key = BleDevice::id) { device ->
                            DeviceRow(
                                device = device,
                                onClick = { onAction(ScannerAction.NavigateToDevice(device)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun ScannerScreenPreview() {
    BleScannerTheme(isDarkMode = false) {
        ScannerScreen(
            state = ScannerState(
                bluetoothState = BluetoothState.Ready,
                arePermissionGranted = true,
                isScanning = true,
                scannedDevices = listOf(
                    BleDevice(id = "00:11:22:33:44:51", name = "Device 1", rssi = -60),
                    BleDevice(id = "00:11:22:33:44:52", name = "Device 2", rssi = -80),
                )
            ),
            onAction = {},
            isDarkMode = false,
            onToggleTheme = {},
        )
    }
}

@Preview
@Composable
fun ScannerScreenDarkPreview() {
    BleScannerTheme(isDarkMode = true) {
        ScannerScreen(
            state = ScannerState(
                bluetoothState = BluetoothState.Ready,
                arePermissionGranted = true,
                isScanning = true,
                scannedDevices = listOf(
                    BleDevice(id = "00:11:22:33:44:51", name = "Device 1", rssi = -60),
                    BleDevice(id = "00:11:22:33:44:52", name = "Device 2", rssi = -80),
                )
            ),
            onAction = {},
            isDarkMode = true,
            onToggleTheme = {},
        )
    }
}

@Preview
@Composable
fun ScannerScreenEmptyPreview() {
    BleScannerTheme(isDarkMode = false) {
        ScannerScreen(
            state = ScannerState(
                bluetoothState = BluetoothState.PoweredOff,
                arePermissionGranted = true,
            ),
            onAction = {},
            isDarkMode = false,
            onToggleTheme = {},
        )
    }
}

@Preview
@Composable
fun ScannerScreenEmptyDarkPreview() {
    BleScannerTheme(isDarkMode = true) {
        ScannerScreen(
            state = ScannerState(
                bluetoothState = BluetoothState.PoweredOff,
                arePermissionGranted = true,
            ),
            onAction = {},
            isDarkMode = true,
            onToggleTheme = {},
        )
    }
}
