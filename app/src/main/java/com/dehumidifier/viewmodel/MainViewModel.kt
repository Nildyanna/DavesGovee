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
import com.dehumidifier.data.BackupRepository
import com.dehumidifier.data.ConnectionStatus
import com.dehumidifier.data.GoveeDevice
import com.dehumidifier.data.GoveeRepository
import com.dehumidifier.data.PreferencesRepository
import com.dehumidifier.data.ReleaseInfo
import com.dehumidifier.data.UpdateChecker
import com.dehumidifier.data.resolveFanSpeedMapping
import com.dehumidifier.worker.AutomationWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

data class UiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val devicesFetched: Boolean = false,
    val isDispatching: Boolean = false,
    val error: String? = null,
    val updateCheckResult: String? = null,
    val devices: List<GoveeDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val selectedDeviceModel: String? = null,
    val selectedSensorId: String? = null,
    val selectedSensorModel: String? = null,
    val automationEnabled: Boolean = false,
    val lastStatus: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
    val updateAvailable: ReleaseInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateProgress: Int = 0,
    val targetVpd: Double = 0.8,
    val vpdBand: Double = 0.1,
    /** Applies 9pm–9am local instead of [targetVpd]; shares [vpdBand]. See activeTargetVpd. */
    val nightVpd: Double = 0.8,
)

class MainViewModel(
    private val context: Context,
    private val govee: GoveeRepository = GoveeRepository(),
    private val prefs: PreferencesRepository = PreferencesRepository(context),
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val savedApiKey = prefs.apiKey.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val savedDeviceId = prefs.deviceId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Fresh install with nothing saved yet — check for an uninstall-surviving backup
        // (see BackupRepository) before anything else so the rest of init picks it up.
        viewModelScope.launch {
            if (prefs.isEmpty()) {
                BackupRepository.read(context)?.let { backup -> prefs.restoreFromBackup(backup) }
            }
        }
        viewModelScope.launch {
            prefs.apiKey.collect { key ->
                _state.update { it.copy(isLoggedIn = key != null) }
            }
        }
        viewModelScope.launch {
            prefs.targetVpd.collect { v -> _state.update { it.copy(targetVpd = v) } }
        }
        viewModelScope.launch {
            prefs.vpdBand.collect { b -> _state.update { it.copy(vpdBand = b) } }
        }
        viewModelScope.launch {
            prefs.nightVpd.collect { v -> _state.update { it.copy(nightVpd = v) } }
        }
        viewModelScope.launch {
            prefs.deviceId.collect { id -> _state.update { it.copy(selectedDeviceId = id) } }
        }
        viewModelScope.launch {
            prefs.deviceModel.collect { m -> _state.update { it.copy(selectedDeviceModel = m) } }
        }
        viewModelScope.launch {
            prefs.sensorDeviceId.collect { id -> _state.update { it.copy(selectedSensorId = id) } }
        }
        viewModelScope.launch {
            prefs.sensorModel.collect { m -> _state.update { it.copy(selectedSensorModel = m) } }
        }
        // Reflect the real automation state from WorkManager rather than assuming OFF on launch.
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(AUTOMATION_WORK_NAME)
                .collect { infos ->
                    _state.update { it.copy(automationEnabled = infos.any { info -> !info.state.isFinished }) }
                }
        }
        checkConnection()
        checkForUpdate()
    }

    fun saveVpdSettings(targetVpd: Double, band: Double, nightVpd: Double) {
        viewModelScope.launch { prefs.saveVpdSettings(targetVpd, band, nightVpd) }
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

    fun login(apiKey: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            govee.listDevices(apiKey)
                .onSuccess { devices ->
                    prefs.saveApiKey(apiKey)
                    _state.update { it.copy(isLoading = false, devices = devices, devicesFetched = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun loadDevices(apiKey: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            govee.listDevices(apiKey)
                .onSuccess { devices ->
                    _state.update { it.copy(isLoading = false, devices = devices, devicesFetched = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun selectDevice(device: GoveeDevice) {
        viewModelScope.launch {
            prefs.saveDevice(device.device, device.model)
            // Resolve gear values from the capabilities already fetched for the device list, so
            // "Run Now" doesn't need to re-fetch them (an extra Govee round trip) on every call.
            resolveFanSpeedMapping(device.capabilities)?.let { prefs.saveFanSpeedMapping(it) }
            _state.update { it.copy(selectedDeviceId = device.device, selectedDeviceModel = device.model) }
        }
    }

    fun selectSensor(device: GoveeDevice) {
        viewModelScope.launch {
            prefs.saveSensor(device.device, device.model)
            _state.update { it.copy(selectedSensorId = device.device, selectedSensorModel = device.model) }
        }
    }

    fun saveManualDevice(deviceId: String, model: String) {
        viewModelScope.launch {
            prefs.saveDevice(deviceId, model)
            _state.update { it.copy(selectedDeviceId = deviceId, selectedDeviceModel = model) }
        }
    }

    fun saveManualSensor(deviceId: String, model: String) {
        viewModelScope.launch {
            prefs.saveSensor(deviceId, model)
            _state.update { it.copy(selectedSensorId = deviceId, selectedSensorModel = model) }
        }
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
            val apiKey = savedApiKey.value
            val deviceId = savedDeviceId.value
            if (apiKey == null || deviceId == null) {
                _state.update { it.copy(isDispatching = false, error = "Device not configured.") }
                return@launch
            }
            val request = OneTimeWorkRequestBuilder<AutomationWorker>().build()
            val wm = WorkManager.getInstance(context)
            wm.enqueue(request)
            // Bounded wait — a retrying job never reaches a "finished" WorkInfo state, so
            // without a timeout a stuck run leaves the spinner running with no feedback.
            val finished = withTimeoutOrNull(45_000) {
                wm.getWorkInfoByIdFlow(request.id).first { it != null && it.state.isFinished }
            }
            val status = when {
                finished == null -> "Timed out. Check your connection and try again."
                finished.state == WorkInfo.State.SUCCEEDED -> "Done — fan speed updated."
                else -> "Run failed. Check API key and device selection."
            }
            _state.update { it.copy(isDispatching = false, lastStatus = status) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            setAutomation(false)
            prefs.clear()
            BackupRepository.delete(context)
            _state.update { UiState() }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingUpdate = true, updateCheckResult = null) }
            val release = UpdateChecker.checkForUpdate()
            _state.update {
                it.copy(
                    isCheckingUpdate = false,
                    updateAvailable = release,
                    updateCheckResult = if (release == null) "Up to date (build ${com.dehumidifier.BuildConfig.VERSION_CODE})" else null,
                )
            }
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

    companion object {
        private const val AUTOMATION_WORK_NAME = "dehumidifier_automation"
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            MainViewModel(context.applicationContext) as T
    }
}
