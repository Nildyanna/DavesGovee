package com.dehumidifier.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dehumidifier.data.GithubRelease
import com.dehumidifier.data.GoveeDevice
import com.dehumidifier.data.GoveeRepository
import com.dehumidifier.data.PreferencesRepository
import com.dehumidifier.data.UpdateCheck
import com.dehumidifier.data.UpdateRepository
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
    val isUpdating: Boolean = false,
    val updateStatus: String? = null,
    val availableUpdate: GithubRelease? = null,
)

class MainViewModel(
    private val context: Context,
    private val govee: GoveeRepository = GoveeRepository(),
    private val prefs: PreferencesRepository = PreferencesRepository(context),
    private val updates: UpdateRepository = UpdateRepository(),
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
        // Restore the previously selected device so controls aren't disabled after a restart.
        viewModelScope.launch {
            prefs.deviceId.collect { deviceId ->
                _state.update { it.copy(selectedDeviceId = deviceId) }
            }
        }
        // Reflect the real automation state from WorkManager rather than assuming OFF on launch.
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(AUTOMATION_WORK_NAME)
                .collect { infos ->
                    val enabled = infos.any { !it.state.isFinished }
                    _state.update { it.copy(automationEnabled = enabled) }
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
                AUTOMATION_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } else {
            wm.cancelUniqueWork(AUTOMATION_WORK_NAME)
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

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true, updateStatus = null, availableUpdate = null) }
            updates.check(context)
                .onSuccess { result ->
                    when (result) {
                        is UpdateCheck.Available -> _state.update {
                            it.copy(
                                isUpdating = false,
                                updateStatus = "Update ${result.versionName} available.",
                                availableUpdate = result.release,
                            )
                        }
                        is UpdateCheck.UpToDate -> _state.update {
                            it.copy(isUpdating = false, updateStatus = "You're on the latest version.")
                        }
                        is UpdateCheck.NoArtifact -> _state.update {
                            it.copy(
                                isUpdating = false,
                                updateStatus = "Version ${result.versionName} exists but has no APK to install.",
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isUpdating = false, updateStatus = "Update check failed: ${e.message}") }
                }
        }
    }

    fun downloadAndInstallUpdate() {
        val release = _state.value.availableUpdate ?: return
        if (!updates.canInstall(context)) {
            _state.update { it.copy(updateStatus = "Allow installs from this app, then tap update again.") }
            context.startActivity(
                updates.installPermissionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true, updateStatus = "Downloading update…") }
            updates.download(context, release)
                .onSuccess { apk ->
                    _state.update { it.copy(isUpdating = false, updateStatus = "Starting installer…") }
                    updates.installApk(context, apk)
                }
                .onFailure { e ->
                    _state.update { it.copy(isUpdating = false, updateStatus = "Download failed: ${e.message}") }
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

    companion object {
        private const val AUTOMATION_WORK_NAME = "dehumidifier_automation"
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            MainViewModel(context.applicationContext) as T
    }
}
