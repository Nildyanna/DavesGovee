package com.dehumidifier.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class PreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("govee_api_key")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_DEVICE_MODEL = stringPreferencesKey("device_model")
        private val KEY_TARGET_VPD = doublePreferencesKey("target_vpd")
        private val KEY_VPD_BAND = doublePreferencesKey("vpd_band")
        private val KEY_SENSOR_DEVICE_ID = stringPreferencesKey("sensor_device_id")
        private val KEY_SENSOR_MODEL = stringPreferencesKey("sensor_model")
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { it[KEY_API_KEY] }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val deviceModel: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_MODEL] }
    val targetVpd: Flow<Double> = context.dataStore.data.map { it[KEY_TARGET_VPD] ?: 0.8 }
    val vpdBand: Flow<Double> = context.dataStore.data.map { it[KEY_VPD_BAND] ?: 0.1 }
    val sensorDeviceId: Flow<String?> = context.dataStore.data.map { it[KEY_SENSOR_DEVICE_ID] }
    val sensorModel: Flow<String?> = context.dataStore.data.map { it[KEY_SENSOR_MODEL] }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun saveDevice(id: String, model: String) {
        context.dataStore.edit {
            it[KEY_DEVICE_ID] = id
            it[KEY_DEVICE_MODEL] = model
        }
    }

    suspend fun saveVpdSettings(targetVpd: Double, band: Double) {
        context.dataStore.edit {
            it[KEY_TARGET_VPD] = targetVpd
            it[KEY_VPD_BAND] = band
        }
    }

    suspend fun saveSensor(deviceId: String, model: String) {
        context.dataStore.edit {
            it[KEY_SENSOR_DEVICE_ID] = deviceId
            it[KEY_SENSOR_MODEL] = model
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
