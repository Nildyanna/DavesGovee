package com.dehumidifier.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

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

class GoveeRepository {

    private val api = NetworkModule.goveeApi

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.okHttp
                .newCall(Request.Builder().url("https://developer-api.govee.com/").head().build())
                .execute()
            response.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listDevices(apiKey: String): Result<List<GoveeDevice>> = runCatching {
        val response = api.getDevices(apiKey)
        val devices = response.data?.devices
        if (devices == null) error("No devices (${response.status}): ${response.message}")
        if (devices.isEmpty()) error("API returned 0 devices. Check that devices are added to your Govee account.")
        devices
    }

    suspend fun getVpd(apiKey: String, deviceId: String, model: String): Result<Double> =
        runCatching {
            val response = api.getDeviceState(apiKey, deviceId, model)
            val props = response.data?.properties ?: error("No state data: ${response.message}")
            props.vpd()
                ?: run {
                    val temp = props.temperatureCelsius() ?: error("No VPD or temperature in sensor response")
                    val rh = props.humidity() ?: error("No VPD or humidity in sensor response")
                    computeVpd(temp, rh)
                }
        }

    suspend fun setFanSpeed(
        apiKey: String,
        deviceId: String,
        model: String,
        speed: FanSpeed,
    ): Result<Unit> = runCatching {
        val response = api.control(
            apiKey = apiKey,
            request = ControlRequest(
                device = deviceId,
                model = model,
                cmd = DeviceCmd(name = "workMode", value = speed.goveeValue),
            ),
        )
        if (response.status != 200) error("Control failed: ${response.message}")
    }
}
