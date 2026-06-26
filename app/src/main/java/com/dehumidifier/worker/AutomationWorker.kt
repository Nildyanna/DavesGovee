package com.dehumidifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dehumidifier.data.GoveeRepository
import com.dehumidifier.data.PreferencesRepository
import com.dehumidifier.data.vpdToFanSpeed
import kotlinx.coroutines.flow.first

class AutomationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesRepository(applicationContext)

        val apiKey = prefs.apiKey.first() ?: return Result.failure()
        val deviceId = prefs.deviceId.first() ?: return Result.failure()
        val model = prefs.deviceModel.first() ?: return Result.failure()
        val sensorId = prefs.sensorDeviceId.first() ?: return Result.failure()
        val sensorModel = prefs.sensorModel.first() ?: return Result.failure()
        val targetVpd = prefs.targetVpd.first()
        val vpdBand = prefs.vpdBand.first()

        return try {
            val govee = GoveeRepository()
            val currentVpd = govee.getVpd(apiKey, sensorId, sensorModel).getOrThrow()
            val speed = vpdToFanSpeed(currentVpd, targetVpd, vpdBand)

            govee.setFanSpeed(apiKey, deviceId, model, speed).getOrThrow()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
