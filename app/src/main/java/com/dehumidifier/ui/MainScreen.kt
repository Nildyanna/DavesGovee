package com.dehumidifier.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dehumidifier.data.ConnectionStatus
import com.dehumidifier.data.GoveeDevice
import com.dehumidifier.viewmodel.UiState

@Composable
fun MainScreen(
    state: UiState,
    onSelectDevice: (GoveeDevice) -> Unit,
    onSaveLocation: (lat: Double, lon: Double) -> Unit,
    onSaveVpdSettings: (targetVpd: Double, band: Double) -> Unit,
    onToggleAutomation: (Boolean) -> Unit,
    onDispatch: () -> Unit,
    onLogout: () -> Unit,
    onCheckConnection: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var targetVpdText by remember(state.targetVpd) { mutableStateOf("%.2f".format(state.targetVpd)) }
    var vpdBandText by remember(state.vpdBand) { mutableStateOf("%.2f".format(state.vpdBand)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Dehumidifier Automation", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onLogout) { Text("Logout") }
        }

        Spacer(Modifier.height(8.dp))
        ConnectionStatusRow(state.connectionStatus, onCheckConnection)

        state.updateAvailable?.let { release ->
            Spacer(Modifier.height(8.dp))
            UpdateBanner(
                tagName = release.tagName,
                isDownloading = state.isDownloadingUpdate,
                progress = state.updateProgress,
                onDownload = onDownloadUpdate,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Select Device", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (state.devices.isEmpty()) {
            Text("No devices found.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(state.devices) { device ->
                    DeviceCard(
                        device = device,
                        selected = device.device == state.selectedDeviceId,
                        onClick = { onSelectDevice(device) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Weather Location", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the latitude/longitude near your dehumidifier for accurate local humidity.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Longitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val lat = latText.toDoubleOrNull()
                val lon = lonText.toDoubleOrNull()
                if (lat != null && lon != null) onSaveLocation(lat, lon)
            },
            enabled = latText.toDoubleOrNull() != null && lonText.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save Location") }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("VPD Target", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Target vapor pressure deficit in kPa. Fan runs harder when VPD is below target. " +
            "Dead-band prevents constant cycling — fan stays Low within ±band of target.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = targetVpdText,
                onValueChange = { targetVpdText = it },
                label = { Text("Target VPD (kPa)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = vpdBandText,
                onValueChange = { vpdBandText = it },
                label = { Text("Dead-band (kPa)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val t = targetVpdText.toDoubleOrNull()
                val b = vpdBandText.toDoubleOrNull()
                if (t != null && b != null && t > 0 && b >= 0) onSaveVpdSettings(t, b)
            },
            enabled = targetVpdText.toDoubleOrNull()?.let { it > 0 } == true &&
                      vpdBandText.toDoubleOrNull()?.let { it >= 0 } == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save VPD Settings") }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Auto-adjust every hour", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Computes outdoor VPD and sets fan speed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.automationEnabled,
                onCheckedChange = onToggleAutomation,
                enabled = state.selectedDeviceId != null,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onDispatch,
            enabled = state.selectedDeviceId != null && !state.isDispatching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isDispatching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(18.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (state.isDispatching) "Running…" else "Run Now")
        }

        state.lastStatus?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UpdateBanner(
    tagName: String,
    isDownloading: Boolean,
    progress: Int,
    onDownload: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Update available ($tagName)", style = MaterialTheme.typography.bodyMedium)
                if (isDownloading) {
                    Text("Downloading… $progress%", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = onDownload) { Text("Update") }
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus, onRetry: () -> Unit) {
    val (label, color) = when (status) {
        ConnectionStatus.UNKNOWN  -> "Not checked" to Color.Gray
        ConnectionStatus.CHECKING -> "Checking…"   to Color.Gray
        ConnectionStatus.ONLINE   -> "Govee: Online"  to Color(0xFF2E7D32)
        ConnectionStatus.OFFLINE  -> "Govee: Offline" to Color(0xFFC62828)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status == ConnectionStatus.CHECKING) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        if (status != ConnectionStatus.CHECKING) {
            TextButton(onClick = onRetry) { Text("Check", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DeviceCard(device: GoveeDevice, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(device.deviceName, style = MaterialTheme.typography.bodyLarge)
            Text("Model: ${device.model}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
