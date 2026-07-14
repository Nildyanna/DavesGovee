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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import java.util.Locale

/**
 * Parses locale-invariantly: [String.toDoubleOrNull] only accepts a period, but a comma-decimal
 * device locale (most of Europe/Latin America) makes the decimal keyboard emit a comma, which
 * silently fails to parse and disables the button with no visible error.
 */
private fun String.toDoubleLenient(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun MainScreen(
    state: UiState,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (GoveeDevice) -> Unit,
    onSelectSensor: (GoveeDevice) -> Unit,
    onSaveManualDevice: (deviceId: String, model: String) -> Unit,
    onSaveManualSensor: (deviceId: String, model: String) -> Unit,
    onSaveVpdSettings: (targetVpd: Double, band: Double, nightVpd: Double) -> Unit,
    onToggleAutomation: (Boolean) -> Unit,
    onDispatch: () -> Unit,
    onLogout: () -> Unit,
    onCheckConnection: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    var targetVpdText by remember(state.targetVpd) { mutableStateOf(String.format(Locale.US, "%.2f", state.targetVpd)) }
    var vpdBandText by remember(state.vpdBand) { mutableStateOf(String.format(Locale.US, "%.2f", state.vpdBand)) }
    var nightVpdText by remember(state.nightVpd) { mutableStateOf(String.format(Locale.US, "%.2f", state.nightVpd)) }
    var vpdSaveConfirmed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        Text("Dehumidifier", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (state.devices.isNotEmpty()) {
            Column {
                state.devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        selected = device.device == state.selectedDeviceId,
                        onClick = { onSelectDevice(device) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading devices…", style = MaterialTheme.typography.bodySmall)
                } else {
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onRefreshDevices) { Text("Refresh") }
                }
            }
            Spacer(Modifier.height(8.dp))
            ManualDeviceEntry(
                label = "Dehumidifier",
                savedId = state.selectedDeviceId,
                savedModel = state.selectedDeviceModel,
                onSave = onSaveManualDevice,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Hygrometer (sensor)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (state.devices.isNotEmpty()) {
            Column {
                state.devices.forEach { device ->
                    DeviceCard(
                        device = device,
                        selected = device.device == state.selectedSensorId,
                        onClick = { onSelectSensor(device) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            ManualDeviceEntry(
                label = "Hygrometer",
                savedId = state.selectedSensorId,
                savedModel = state.selectedSensorModel,
                onSave = onSaveManualSensor,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("VPD Target", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Target vapor pressure deficit in kPa. Fan runs harder when VPD is below target. " +
            "Dead-band prevents constant cycling — fan stays Low within ±band of target. " +
            "Night VPD applies instead of the day target from 9pm–9am local time, sharing the same dead-band.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = targetVpdText,
                onValueChange = { targetVpdText = it; vpdSaveConfirmed = false },
                label = { Text("Day VPD (kPa)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = nightVpdText,
                onValueChange = { nightVpdText = it; vpdSaveConfirmed = false },
                label = { Text("Night VPD (kPa)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vpdBandText,
            onValueChange = { vpdBandText = it; vpdSaveConfirmed = false },
            label = { Text("Dead-band (kPa)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val t = targetVpdText.toDoubleLenient()
                val b = vpdBandText.toDoubleLenient()
                val n = nightVpdText.toDoubleLenient()
                if (t != null && b != null && n != null && t > 0 && b >= 0 && n > 0) {
                    onSaveVpdSettings(t, b, n)
                    vpdSaveConfirmed = true
                }
            },
            enabled = targetVpdText.toDoubleLenient()?.let { it > 0 } == true &&
                      vpdBandText.toDoubleLenient()?.let { it >= 0 } == true &&
                      nightVpdText.toDoubleLenient()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save VPD Settings") }
        if (vpdSaveConfirmed) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Settings saved.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }

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
                    "Reads hygrometer VPD every hour and sets fan speed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.automationEnabled,
                onCheckedChange = onToggleAutomation,
                enabled = state.selectedDeviceId != null && state.selectedSensorId != null,
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
internal fun UpdateBanner(
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
private fun ManualDeviceEntry(
    label: String,
    savedId: String?,
    savedModel: String?,
    onSave: (deviceId: String, model: String) -> Unit,
) {
    var deviceId by remember(savedId) { mutableStateOf(savedId ?: "") }
    var model by remember(savedModel) { mutableStateOf(savedModel ?: "") }
    val saved = deviceId == savedId && model == savedModel && savedId != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (saved) {
            Text("$label set: $savedId (${savedModel})", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("$label Device ID") },
            placeholder = { Text("e.g. 34:20:03:15:82:ae") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("$label Model") },
            placeholder = { Text("e.g. H7151") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(deviceId.trim(), model.trim()) },
            enabled = deviceId.isNotBlank() && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save $label") }
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
