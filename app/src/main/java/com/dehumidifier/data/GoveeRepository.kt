package com.dehumidifier.data

import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private val NIGHT_START = LocalTime.of(21, 0)
private val NIGHT_END = LocalTime.of(9, 0)

/** True between 9pm and 9am local time (wraps past midnight). */
fun isNightTime(now: LocalTime = LocalTime.now()): Boolean =
    now >= NIGHT_START || now < NIGHT_END

/** Picks the day or night VPD target depending on the current local time. */
fun activeTargetVpd(dayTarget: Double, nightTarget: Double, now: LocalTime = LocalTime.now()): Double =
    if (isNightTime(now)) nightTarget else dayTarget

enum class FanSpeed(val goveeValue: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
}

/**
 * VPD = SVP * (1 - RH/100), where SVP = 0.6108 * exp(17.27*T / (T+237.3)) kPa
 * Returns kPa.
 */
fun computeVpd(tempCelsius: Double, relativeHumidity: Int): Double {
    val svp = 0.6108 * Math.exp(17.27 * tempCelsius / (tempCelsius + 237.3))
    return svp * (1.0 - relativeHumidity / 100.0)
}

/**
 * Maps current VPD to fan speed relative to the target VPD.
 * [band] is a dead-band around the target — within ±band the fan stays Low.
 *
 * Below target - band  → HIGH (very humid, run hard)
 * Below target         → MEDIUM (somewhat humid)
 * Within ±band         → LOW (close enough, don't cycle)
 * Above target + band  → LOW (air already drier than target, rest)
 */
fun vpdToFanSpeed(currentVpd: Double, targetVpd: Double, band: Double): FanSpeed {
    val diff = targetVpd - currentVpd  // positive = air is more humid than target
    return when {
        diff > band + 0.3 -> FanSpeed.HIGH
        diff > band       -> FanSpeed.MEDIUM
        else              -> FanSpeed.LOW
    }
}

enum class ConnectionStatus { UNKNOWN, CHECKING, ONLINE, OFFLINE }

private const val WORK_MODE_TYPE = "devices.capabilities.work_mode"
private const val WORK_MODE_INSTANCE = "workMode"

class GoveeRepository {

    private val api = NetworkModule.goveeApi

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.okHttp
                .newCall(Request.Builder().url("https://openapi.api.govee.com/").head().build())
                .execute()
            response.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listDevices(apiKey: String): Result<List<GoveeDevice>> = runCatching {
        val response = api.getDevices(apiKey)
        if (response.code != 200) error("Govee API error (${response.code}): ${response.message}")
        response.data
    }

    suspend fun getVpd(apiKey: String, deviceId: String, model: String): Result<Double> =
        runCatching {
            val response = api.getDeviceState(
                apiKey = apiKey,
                request = DeviceStateRequest(
                    requestId = UUID.randomUUID().toString(),
                    payload = DeviceStatePayload(sku = model, device = deviceId),
                ),
            )
            val caps = response.payload?.capabilities
                ?: error("No state data (${response.code}): ${response.msg}")
            val temp = caps.propertyValue("sensorTemperature", "temperature")
                ?: error("No temperature in sensor response")
            val rh = caps.propertyValue("sensorHumidity", "humidity")
                ?: error("No humidity in sensor response")
            computeVpd(temp, rh.toInt())
        }

    /**
     * Builds the work_mode control value for [speed]. Prefers the device's own declared
     * workMode/gear options (from its `devices.capabilities.work_mode` capability) so the
     * correct mode id and gear numbering is used regardless of SKU; falls back to the
     * documented default (workMode=1 "Gear Mode", modeValue=1/2/3) if the device's
     * capabilities aren't available or don't declare a work_mode capability.
     */
    private fun resolveWorkModeValue(device: GoveeDevice?, speed: FanSpeed): WorkModeValue {
        val fields = device?.capabilities
            ?.firstOrNull { it.type == WORK_MODE_TYPE || it.instance == WORK_MODE_INSTANCE }
            ?.parameters?.fields
        val modeId = fields?.firstOrNull { it.fieldName == "workMode" }
            ?.options?.firstOrNull()?.value?.toInt() ?: 1
        val gearOptions = fields?.firstOrNull { it.fieldName == "modeValue" }?.options
        val gearValue = gearOptions
            ?.getOrNull(speed.ordinal)?.value?.toInt()
            ?: speed.goveeValue
        return WorkModeValue(workMode = modeId, modeValue = gearValue)
    }

    suspend fun setFanSpeed(
        apiKey: String,
        deviceId: String,
        model: String,
        speed: FanSpeed,
    ): Result<Unit> = runCatching {
        val device = listDevices(apiKey).getOrNull()
            ?.firstOrNull { it.device == deviceId && it.sku == model }
        val response = api.control(
            apiKey = apiKey,
            request = ControlRequest(
                requestId = UUID.randomUUID().toString(),
                payload = ControlPayload(
                    sku = model,
                    device = deviceId,
                    capability = ControlCapability(
                        type = WORK_MODE_TYPE,
                        instance = WORK_MODE_INSTANCE,
                        value = resolveWorkModeValue(device, speed),
                    ),
                ),
            ),
        )
        if (response.code != 200) error("Control failed (${response.code}): ${response.msg}")
    }
}
