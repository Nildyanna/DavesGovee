package com.dehumidifier.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dehumidifier.data.ConnectionStatus
import com.dehumidifier.data.GoveeDevice
import com.dehumidifier.data.GoveeRepository
import com.dehumidifier.data.PreferencesRepository
import com.dehumidifier.data.ReleaseInfo
import com.dehumidifier.data.UpdateChecker
import com.dehumidifier.worker.AutomationWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class UiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isDispatching: Boolean = false,
    val error: String? = null,
    val devices: List<GoveeDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val automationEnabled: Boolean = false,
    val lastStatus: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
    val updateAvailable: ReleaseInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateProgress: Int = 0,
)

class MainViewModel(
    private val context: Context,
    private val govee: GoveeRepository = GoveeRepository(),
    private val prefs: PreferencesRepository = PreferencesRepository(context),
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val savedToken = prefs.token.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val savedDeviceId = prefs.deviceId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            prefs.token.collect { token ->
                _state.update { it.copy(isLoggedIn = token != null) }
            }
        }
        checkConnection()
        checkForUpdate()
    }

    fun checkConnection() {
        viewModelScope.launch {
            _state.update { it.copy(connectionStatus = ConnectionStatus.CHECKING) }
            val online = govee.checkConnection()
            _state.update {
                it.copy(connectionStatus = if (online) ConnectionStatus.ONLINE else ConnectionStatus.OFFLINE)
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            govee.login(email, password)
                .onSuccess { token ->
                    prefs.saveAuth(token)
                    loadDevices(token)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun loadDevices(token: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            govee.listDevices(token)
                .onSuccess { devices ->
                    _state.update { it.copy(isLoading = false, devices = devices) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun selectDevice(device: GoveeDevice) {
        viewModelScope.launch {
            prefs.saveDevice(device.device, device.model)
            _state.update { it.copy(selectedDeviceId = device.device) }
        }
    }

    fun saveLocation(lat: Double, lon: Double) {
        viewModelScope.launch { prefs.saveLocation(lat, lon) }
    }

    fun setAutomation(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<AutomationWorker>(1, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(
                "dehumidifier_automation",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } else {
            wm.cancelUniqueWork("dehumidifier_automation")
        }
        _state.update { it.copy(automationEnabled = enabled) }
    }

    fun dispatch() {
        viewModelScope.launch {
            _state.update { it.copy(isDispatching = true, error = null, lastStatus = null) }
            val token = savedToken.value
            val deviceId = savedDeviceId.value
            if (token == null || deviceId == null) {
                _state.update { it.copy(isDispatching = false, error = "Device not configured.") }
                return@launch
            }
            val request = OneTimeWorkRequestBuilder<com.dehumidifier.worker.AutomationWorker>().build()
            val wm = WorkManager.getInstance(context)
            wm.enqueue(request)
            wm.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info != null && info.state.isFinished) {
                    val status = if (info.state == WorkInfo.State.SUCCEEDED)
                        "Done — fan speed updated."
                    else
                        "Run failed. Check credentials and location."
                    _state.update { it.copy(isDispatching = false, lastStatus = status) }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            setAutomation(false)
            prefs.clear()
            _state.update { UiState() }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val release = UpdateChecker.checkForUpdate()
            _state.update { it.copy(updateAvailable = release) }
        }
    }

    fun downloadUpdate(context: Context) {
        val info = _state.value.updateAvailable ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDownloadingUpdate = true, updateProgress = 0) }
            UpdateChecker.downloadAndInstall(context, info) { progress ->
                _state.update { it.copy(updateProgress = progress) }
            }
            _state.update { it.copy(isDownloadingUpdate = false) }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            MainViewModel(context.applicationContext) as T
    }
}
