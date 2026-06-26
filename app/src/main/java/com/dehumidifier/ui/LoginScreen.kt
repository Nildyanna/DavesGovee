package com.dehumidifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dehumidifier.data.ReleaseInfo

@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onLogin: (apiKey: String) -> Unit,
    updateAvailable: ReleaseInfo? = null,
    isCheckingUpdate: Boolean = false,
    isDownloadingUpdate: Boolean = false,
    updateProgress: Int = 0,
    updateCheckResult: String? = null,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
) {
    var apiKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (updateAvailable != null) {
            UpdateBanner(
                tagName = updateAvailable.tagName,
                isDownloading = isDownloadingUpdate,
                progress = updateProgress,
                onDownload = onDownloadUpdate,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Checking for updates…", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    Text(
                        updateCheckResult ?: "Check for updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    OutlinedButton(onClick = onCheckUpdate) { Text("Check", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Text("Dehumidifier Automation", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Enter your Govee API key", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Get it in the Govee app: Me → Settings → About Us → Apply for API Key",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Govee API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onLogin(apiKey.trim()) },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }
        }
    }
}
