package com.dehumidifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dehumidifier.data.GoveeRepository
import com.dehumidifier.data.NetworkModule
import com.dehumidifier.data.PreferencesRepository
import com.dehumidifier.data.humidityToFanSpeed
import kotlinx.coroutines.flow.first

class AutomationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesRepository(applicationContext)

        val token = prefs.token.first() ?: return Result.failure()
        val deviceId = prefs.deviceId.first() ?: return Result.failure()
        val model = prefs.deviceModel.first() ?: return Result.failure()
        val lat = prefs.latitude.first() ?: return Result.failure()
        val lon = prefs.longitude.first() ?: return Result.failure()

        return try {
            val weather = NetworkModule.weatherApi.getCurrent(lat, lon)
            val humidity = weather.current.relativeHumidity
            val speed = humidityToFanSpeed(humidity)

            GoveeRepository().setFanSpeed(token, deviceId, model, speed).getOrThrow()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
