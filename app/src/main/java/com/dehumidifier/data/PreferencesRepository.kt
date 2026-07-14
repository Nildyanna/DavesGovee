package com.dehumidifier.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("govee_api_key")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_DEVICE_MODEL = stringPreferencesKey("device_model")
        private val KEY_TARGET_VPD = doublePreferencesKey("target_vpd")
        private val KEY_VPD_BAND = doublePreferencesKey("vpd_band")
        private val KEY_NIGHT_VPD = doublePreferencesKey("night_target_vpd")
        private val KEY_SENSOR_DEVICE_ID = stringPreferencesKey("sensor_device_id")
        private val KEY_SENSOR_MODEL = stringPreferencesKey("sensor_model")
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { it[KEY_API_KEY] }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val deviceModel: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_MODEL] }
    val targetVpd: Flow<Double> = context.dataStore.data.map { it[KEY_TARGET_VPD] ?: 0.8 }
    val vpdBand: Flow<Double> = context.dataStore.data.map { it[KEY_VPD_BAND] ?: 0.1 }
    /** Night-cycle target (9pm–9am local, see isNightTime); defaults to the day target until customized. */
    val nightVpd: Flow<Double> = context.dataStore.data.map { it[KEY_NIGHT_VPD] ?: it[KEY_TARGET_VPD] ?: 0.8 }
    val sensorDeviceId: Flow<String?> = context.dataStore.data.map { it[KEY_SENSOR_DEVICE_ID] }
    val sensorModel: Flow<String?> = context.dataStore.data.map { it[KEY_SENSOR_MODEL] }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
        backupSnapshot()
    }

    suspend fun saveDevice(id: String, model: String) {
        context.dataStore.edit {
            it[KEY_DEVICE_ID] = id
            it[KEY_DEVICE_MODEL] = model
        }
        backupSnapshot()
    }

    suspend fun saveVpdSettings(targetVpd: Double, band: Double, nightVpd: Double) {
        context.dataStore.edit {
            it[KEY_TARGET_VPD] = targetVpd
            it[KEY_VPD_BAND] = band
            it[KEY_NIGHT_VPD] = nightVpd
        }
        backupSnapshot()
    }

    suspend fun saveSensor(deviceId: String, model: String) {
        context.dataStore.edit {
            it[KEY_SENSOR_DEVICE_ID] = deviceId
            it[KEY_SENSOR_MODEL] = model
        }
        backupSnapshot()
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    /** Writes the current settings to the uninstall-surviving backup file (see BackupRepository). */
    private suspend fun backupSnapshot() {
        BackupRepository.write(
            context,
            BackupData(
                apiKey = apiKey.first(),
                deviceId = deviceId.first(),
                deviceModel = deviceModel.first(),
                sensorDeviceId = sensorDeviceId.first(),
                sensorModel = sensorModel.first(),
                targetVpd = targetVpd.first(),
                vpdBand = vpdBand.first(),
                nightVpd = nightVpd.first(),
            ),
        )
    }

    /** True if nothing has been configured yet — the signal to check for a restorable backup. */
    suspend fun isEmpty(): Boolean = apiKey.first() == null

    /** Restores all settings from a previously exported backup (see BackupRepository.read). */
    suspend fun restoreFromBackup(data: BackupData) {
        context.dataStore.edit { prefs ->
            data.apiKey?.let { prefs[KEY_API_KEY] = it }
            data.deviceId?.let { prefs[KEY_DEVICE_ID] = it }
            data.deviceModel?.let { prefs[KEY_DEVICE_MODEL] = it }
            data.sensorDeviceId?.let { prefs[KEY_SENSOR_DEVICE_ID] = it }
            data.sensorModel?.let { prefs[KEY_SENSOR_MODEL] = it }
            prefs[KEY_TARGET_VPD] = data.targetVpd
            prefs[KEY_VPD_BAND] = data.vpdBand
            prefs[KEY_NIGHT_VPD] = data.nightVpd
        }
    }
}
