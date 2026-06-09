package com.preichert.blecustomscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import blecustomscanner.shared.generated.resources.Res
import blecustomscanner.shared.generated.resources.back
import blecustomscanner.shared.generated.resources.characteristic
import blecustomscanner.shared.generated.resources.characteristics
import blecustomscanner.shared.generated.resources.dbm
import blecustomscanner.shared.generated.resources.identifier
import blecustomscanner.shared.generated.resources.manufacturer_data
import blecustomscanner.shared.generated.resources.mtu
import blecustomscanner.shared.generated.resources.no_services_found
import blecustomscanner.shared.generated.resources.properties
import blecustomscanner.shared.generated.resources.rssi
import blecustomscanner.shared.generated.resources.service
import blecustomscanner.shared.generated.resources.services
import blecustomscanner.shared.generated.resources.status
import blecustomscanner.shared.generated.resources.unknown_device
import blecustomscanner.shared.generated.resources.value
import com.preichert.blecustomscanner.ble.BleDevice
import com.preichert.blecustomscanner.ble.ConnectionState
import com.preichert.blecustomscanner.ble.GattCharacteristic
import com.preichert.blecustomscanner.ble.GattNames
import com.preichert.blecustomscanner.ble.GattService
import com.preichert.blecustomscanner.ui.theme.BleScannerTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeviceDetailRoot(
    onBack: () -> Unit,
    viewModel: DeviceDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DeviceDetailEvent.NavigateBack -> onBack()
        }
    }

    DeviceDetailScreen(
        state = state,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    state: DeviceDetailState,
    onBack: () -> Unit,
) {
    val device = state.device ?: state.connection.device
    val connection = state.connection

    val busy = connection.state == ConnectionState.Connecting ||
        connection.state == ConnectionState.Connected ||
        connection.state == ConnectionState.DiscoveringServices

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.displayName ?: stringResource(Res.string.unknown_device)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(Res.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            LazyColumn(Modifier.fillMaxSize()) {
                if (device != null) {
                    item {
                        ConnectionHeader(
                            device = device,
                            state = connection.state,
                            rssi = connection.rssi,
                            mtu = connection.mtu,
                            serviceCount = connection.services.size,
                            characteristicCount = connection.characteristicCount,
                            error = connection.error,
                        )
                    }
                }

                if (connection.services.isEmpty() && !busy) {
                    item {
                        Text(
                            stringResource(Res.string.no_services_found),
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(connection.services, key = GattService::uuid) { service ->
                    ServiceCard(service)
                }
            }
        }
    }
}

@Composable
private fun ConnectionHeader(
    device: BleDevice,
    state: ConnectionState,
    rssi: Int?,
    mtu: Int?,
    serviceCount: Int,
    characteristicCount: Int,
    error: String?,
) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoRow(stringResource(Res.string.status), state.name)
            InfoRow(stringResource(Res.string.identifier), device.id)
            if (rssi != null) InfoRow(stringResource(Res.string.rssi), stringResource(Res.string.dbm, rssi))
            if (mtu != null) InfoRow(stringResource(Res.string.mtu), "$mtu bytes")
            InfoRow(stringResource(Res.string.services), serviceCount.toString())
            InfoRow(stringResource(Res.string.characteristics), characteristicCount.toString())
            if (device.manufacturerData != null) {
                InfoRow(stringResource(Res.string.manufacturer_data), device.manufacturerData)
            }
            if (error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        error,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ServiceCard(service: GattService) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                service.name ?: GattNames.lookup(service.uuid) ?: stringResource(Res.string.service),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                service.uuid,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            service.characteristics.forEach { ch ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                CharacteristicRow(ch)
            }
        }
    }
}

@Composable
private fun CharacteristicRow(ch: GattCharacteristic) {
    Column {
        Text(
            ch.name ?: GattNames.lookup(ch.uuid) ?: stringResource(Res.string.characteristic),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            ch.uuid,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(Res.string.properties, ch.properties.label()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (ch.value != null) {
            Text(
                stringResource(Res.string.value, ch.value),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        ch.descriptors.forEach { d ->
            Text(
                "• ${d.name ?: GattNames.lookup(d.uuid) ?: d.uuid}" + (d.value?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
fun DeviceDetailScreenPreview() {
    BleScannerTheme(darkTheme = false) {
        DeviceDetailScreen(
            state = DeviceDetailState(
                device = BleDevice(id = "00:11:22:33:44:55", name = "Test Device", rssi = -55),
                connection = com.preichert.blecustomscanner.ble.DeviceConnection(
                    device = BleDevice(id = "00:11:22:33:44:55", name = "Test Device"),
                    state = ConnectionState.Connected,
                    services = listOf(
                        GattService(
                            uuid = "180D",
                            name = "Heart Rate",
                            characteristics = listOf(
                                GattCharacteristic(
                                    uuid = "2A37",
                                    name = "Heart Rate Measurement",
                                    properties = com.preichert.blecustomscanner.ble.CharacteristicProperties(notify = true),
                                    value = "75 bpm"
                                )
                            )
                        )
                    )
                )
            ),
            onBack = {}
        )
    }
}

@Preview
@Composable
fun DeviceDetailScreenDarkPreview() {
    BleScannerTheme(darkTheme = true) {
        DeviceDetailScreen(
            state = DeviceDetailState(
                device = BleDevice(id = "00:11:22:33:44:55", name = "Test Device", rssi = -55),
                connection = com.preichert.blecustomscanner.ble.DeviceConnection(
                    device = BleDevice(id = "00:11:22:33:44:55", name = "Test Device"),
                    state = ConnectionState.Connected,
                    services = listOf(
                        GattService(
                            uuid = "180D",
                            name = "Heart Rate",
                            characteristics = listOf(
                                GattCharacteristic(
                                    uuid = "2A37",
                                    name = "Heart Rate Measurement",
                                    properties = com.preichert.blecustomscanner.ble.CharacteristicProperties(notify = true),
                                    value = "75 bpm"
                                )
                            )
                        )
                    )
                )
            ),
            onBack = {}
        )
    }
}
