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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dehumidifier.data.GoveeDevice
import com.dehumidifier.viewmodel.UiState

@Composable
fun MainScreen(
    state: UiState,
    onSelectDevice: (GoveeDevice) -> Unit,
    onSaveLocation: (lat: Double, lon: Double) -> Unit,
    onToggleAutomation: (Boolean) -> Unit,
    onDispatch: () -> Unit,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onLogout: () -> Unit,
) {
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Auto-adjust every hour", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Checks outdoor humidity and sets fan speed automatically.",
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

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("App Updates", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCheckUpdate,
                enabled = !state.isUpdating,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Check for Updates")
            }
            if (state.availableUpdate != null) {
                Button(
                    onClick = onInstallUpdate,
                    enabled = !state.isUpdating,
                    modifier = Modifier.weight(1f),
                ) { Text("Download & Install") }
            }
        }
        state.updateStatus?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
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
