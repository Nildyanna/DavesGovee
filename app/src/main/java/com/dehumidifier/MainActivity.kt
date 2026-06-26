package com.dehumidifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.dehumidifier.ui.LoginScreen
import com.dehumidifier.ui.MainScreen
import com.dehumidifier.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels { MainViewModel.Factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by vm.state.collectAsState()
                    val apiKey by vm.savedApiKey.collectAsState()

                    if (!state.isLoggedIn) {
                        LoginScreen(
                            isLoading = state.isLoading,
                            error = state.error,
                            onLogin = vm::login,
                            updateAvailable = state.updateAvailable,
                            isCheckingUpdate = state.isCheckingUpdate,
                            isDownloadingUpdate = state.isDownloadingUpdate,
                            updateProgress = state.updateProgress,
                            updateCheckResult = state.updateCheckResult,
                            onCheckUpdate = vm::checkForUpdate,
                            onDownloadUpdate = { vm.downloadUpdate(this@MainActivity) },
                        )
                    } else {
                        if (state.devices.isEmpty() && apiKey != null) {
                            vm.loadDevices(apiKey!!)
                        }
                        MainScreen(
                            state = state,
                            onSelectDevice = vm::selectDevice,
                            onSelectSensor = vm::selectSensor,
                            onSaveManualDevice = vm::saveManualDevice,
                            onSaveManualSensor = vm::saveManualSensor,
                            onSaveVpdSettings = vm::saveVpdSettings,
                            onToggleAutomation = vm::setAutomation,
                            onDispatch = vm::dispatch,
                            onLogout = vm::logout,
                            onCheckConnection = vm::checkConnection,
                            onDownloadUpdate = { vm.downloadUpdate(this@MainActivity) },
                        )
                    }
                }
            }
        }
    }
}
