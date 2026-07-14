package com.dehumidifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dehumidifier.data.GoveeRepository
import com.dehumidifier.data.PreferencesRepository
import com.dehumidifier.data.activeTargetVpd
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
        val nightVpd = prefs.nightVpd.first()

        return try {
            val govee = GoveeRepository()
            val currentVpd = govee.getVpd(apiKey, sensorId, sensorModel).getOrThrow()
            val speed = vpdToFanSpeed(currentVpd, activeTargetVpd(targetVpd, nightVpd), vpdBand)

            govee.setFanSpeed(apiKey, deviceId, model, speed).getOrThrow()
            Result.success()
        } catch (e: Exception) {
            // Retry transient failures a couple of times, then give up rather than retrying
            // forever — an unbounded retry keeps a manually-triggered "Run Now" spinning with
            // no feedback, since a retrying job never reaches a "finished" WorkInfo state.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
