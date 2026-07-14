package com.dehumidifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.dehumidifier.data.GoveeRepository
import com.dehumidifier.data.PreferencesRepository
import com.dehumidifier.data.activeTargetVpd
import com.dehumidifier.data.vpdToFanSpeed
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        val fanSpeedMapping = prefs.fanSpeedMapping.first()

        return try {
            val govee = GoveeRepository()

            // Fetch the sensor's VPD and the dehumidifier's own status concurrently — doing
            // this sequentially would add a full extra round trip on top of the control call,
            // risking the same "Run Now" timeout already fixed once in this app.
            val (sensorResult, deviceResult) = coroutineScope {
                val sensor = async { govee.getDeviceStatus(apiKey, sensorId, sensorModel) }
                val device = async { govee.getDeviceStatus(apiKey, deviceId, model) }
                sensor.await() to device.await()
            }

            val sensorStatus = sensorResult.getOrThrow()
            val currentVpd = sensorStatus.vpd ?: error("No temperature/humidity in sensor response")

            val output = Data.Builder().putDouble("vpd", currentVpd)
            sensorStatus.temperatureCelsius?.let { output.putDouble("temp", it) }
            sensorStatus.humidity?.let { output.putDouble("humidity", it) }

            // Only skip on a *confirmed* fault — if the status check itself failed (e.g. a
            // transient error on that one call), proceed with the command rather than blocking
            // automation on a check we couldn't actually complete.
            val fault = deviceResult.getOrNull()?.takeIf { it.hasFault }
            if (fault != null) {
                val reason = if (fault.online == false) "Dehumidifier offline"
                    else "Dehumidifier alert: ${fault.alerts.joinToString()}"
                output.putBoolean("skipped", true).putString("skippedReason", reason)
                return Result.success(output.build())
            }

            val speed = vpdToFanSpeed(currentVpd, activeTargetVpd(targetVpd, nightVpd), vpdBand)
            govee.setFanSpeed(apiKey, deviceId, model, speed, fanSpeedMapping).getOrThrow()

            output.putBoolean("skipped", false).putString("speed", speed.name)
            Result.success(output.build())
        } catch (e: Exception) {
            // Retry transient failures a couple of times, then give up rather than retrying
            // forever — an unbounded retry keeps a manually-triggered "Run Now" spinning with
            // no feedback, since a retrying job never reaches a "finished" WorkInfo state.
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
