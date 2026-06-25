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
    }

    val token: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val deviceModel: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_MODEL] }
    val latitude: Flow<Double?> = context.dataStore.data.map { it[KEY_LATITUDE] }
    val longitude: Flow<Double?> = context.dataStore.data.map { it[KEY_LONGITUDE] }

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

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
