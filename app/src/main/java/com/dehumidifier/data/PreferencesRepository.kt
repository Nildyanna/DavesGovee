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
        private val KEY_TOKEN = stringPreferencesKey("govee_token")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_DEVICE_MODEL = stringPreferencesKey("device_model")
        private val KEY_LATITUDE = doublePreferencesKey("latitude")
        private val KEY_LONGITUDE = doublePreferencesKey("longitude")
        private val KEY_TARGET_VPD = doublePreferencesKey("target_vpd")
        private val KEY_VPD_BAND = doublePreferencesKey("vpd_band")
        private val KEY_SENSOR_DEVICE_ID = stringPreferencesKey("sensor_device_id")
        private val KEY_SENSOR_MODEL = stringPreferencesKey("sensor_model")
        private val KEY_CLIENT_ID = stringPreferencesKey("govee_client_id")
    }

    val token: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val deviceModel: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_MODEL] }
    val latitude: Flow<Double?> = context.dataStore.data.map { it[KEY_LATITUDE] }
    val longitude: Flow<Double?> = context.dataStore.data.map { it[KEY_LONGITUDE] }
    val targetVpd: Flow<Double> = context.dataStore.data.map { it[KEY_TARGET_VPD] ?: 0.8 }
    val vpdBand: Flow<Double> = context.dataStore.data.map { it[KEY_VPD_BAND] ?: 0.1 }
    val sensorDeviceId: Flow<String?> = context.dataStore.data.map { it[KEY_SENSOR_DEVICE_ID] }
    val sensorModel: Flow<String?> = context.dataStore.data.map { it[KEY_SENSOR_MODEL] }
    // Stable per-installation UUID used as Govee client identifier
    val clientId: Flow<String> = context.dataStore.data.map {
        it[KEY_CLIENT_ID] ?: java.util.UUID.randomUUID().toString().also { id ->
            context.dataStore.edit { prefs -> prefs[KEY_CLIENT_ID] = id }
        }
    }

    suspend fun saveAuth(token: String) {
        context.dataStore.edit { it[KEY_TOKEN] = token }
    }

    suspend fun saveDevice(id: String, model: String) {
        context.dataStore.edit {
            it[KEY_DEVICE_ID] = id
            it[KEY_DEVICE_MODEL] = model
        }
    }

    suspend fun saveLocation(lat: Double, lon: Double) {
        context.dataStore.edit {
            it[KEY_LATITUDE] = lat
            it[KEY_LONGITUDE] = lon
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
