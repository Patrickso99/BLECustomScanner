package com.preichert.blecustomscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import blecustomscanner.shared.generated.resources.Res
import blecustomscanner.shared.generated.resources.bt_denied
import blecustomscanner.shared.generated.resources.bt_initializing
import blecustomscanner.shared.generated.resources.bt_permission_required
import blecustomscanner.shared.generated.resources.bt_powered_off
import blecustomscanner.shared.generated.resources.bt_resetting
import blecustomscanner.shared.generated.resources.dbm
import blecustomscanner.shared.generated.resources.grant_permission
import blecustomscanner.shared.generated.resources.open_settings
import blecustomscanner.shared.generated.resources.paired
import blecustomscanner.shared.generated.resources.turn_on
import com.preichert.blecustomscanner.ble.BleDevice
import com.preichert.blecustomscanner.ble.BluetoothState
import com.preichert.blecustomscanner.ui.theme.BleScannerTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Colored banner explaining why scanning is unavailable, plus an optional
 * call-to-action (grant permission / turn Bluetooth on).
 */
@Composable
fun StatusBanner(
    bluetoothState: BluetoothState,
    arePermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Info(val message: String, val action: String?, val onAction: () -> Unit)

    val info: Info? = when {
        bluetoothState == BluetoothState.Unauthorized -> Info(
            stringResource(Res.string.bt_denied),
            stringResource(Res.string.open_settings),
            onRequestPermission,
        )
        !arePermissionGranted -> Info(
            stringResource(Res.string.bt_permission_required),
            stringResource(Res.string.grant_permission),
            onRequestPermission,
        )
        bluetoothState == BluetoothState.PoweredOff -> Info(
            stringResource(Res.string.bt_powered_off),
            stringResource(Res.string.turn_on),
            onEnableBluetooth,
        )
        bluetoothState == BluetoothState.Resetting -> Info(
            stringResource(Res.string.bt_resetting),
            null,
        ) {}

        bluetoothState == BluetoothState.Unknown -> Info(
            stringResource(Res.string.bt_initializing),
            null,
        ) {}

        else -> null
    }

    if (info != null) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    info.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.action != null) {
                    Button(
                        onClick = info.onAction,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(info.action)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun StatusBannerPreview() {
    BleScannerTheme(isDarkMode = false) {
        StatusBanner(
            bluetoothState = BluetoothState.PoweredOff,
            arePermissionGranted = true,
            onRequestPermission = {},
            onEnableBluetooth = {},
        )
    }
}

@Preview
@Composable
fun StatusBannerDarkPreview() {
    BleScannerTheme(isDarkMode = true) {
        StatusBanner(
            bluetoothState = BluetoothState.PoweredOff,
            arePermissionGranted = true,
            onRequestPermission = {},
            onEnableBluetooth = {},
        )
    }
}

/** Centered placeholder shown when a list has no items. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
fun EmptyStatePreview() {
    BleScannerTheme(isDarkMode = false) {
        EmptyState(text = "No devices found")
    }
}

@Preview
@Composable
fun EmptyStateDarkPreview() {
    BleScannerTheme(isDarkMode = true) {
        EmptyState(text = "No devices found")
    }
}

/** A single tappable row representing a discovered or paired [BleDevice]. */
@Composable
fun DeviceRow(
    device: BleDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SignalDot(device.rssi)
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    device.id,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (device.bonded) {
                    Text(
                        stringResource(Res.string.paired),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (device.rssi != null) {
                Text(
                    stringResource(Res.string.dbm, device.rssi),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
fun DeviceRowPreview() {
    BleScannerTheme(isDarkMode = false) {
        DeviceRow(
            device = BleDevice(
                id = "00:11:22:33:44:55",
                name = "Test Device",
                rssi = -55,
                bonded = true,
            ),
            onClick = {},
        )
    }
}

@Preview
@Composable
fun DeviceRowDarkPreview() {
    BleScannerTheme(isDarkMode = true) {
        DeviceRow(
            device = BleDevice(
                id = "00:11:22:33:44:55",
                name = "Test Device",
                rssi = -55,
                bonded = true,
            ),
            onClick = {},
        )
    }
}

/** Small colored dot used to summarize RSSI strength at a glance. */
@Composable
private fun SignalDot(rssi: Int?) {
    val color = when {
        rssi == null -> MaterialTheme.colorScheme.outline
        rssi >= -60 -> Color(0xFF2E7D32)
        rssi >= -75 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }
    Box(
        Modifier.size(12.dp).clip(CircleShape),
    ) {
        Surface(color = color, modifier = Modifier.fillMaxSize()) {}
    }
}