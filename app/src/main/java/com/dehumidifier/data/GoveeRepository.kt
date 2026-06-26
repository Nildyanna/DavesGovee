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
 * Above target+band the VPD is too high (air is too dry for the plants/space),
 * but for a dehumidifier the concern is the opposite: current VPD below target
 * means the air is too humid, so we run harder.
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
                .newCall(Request.Builder().url("https://app2.govee.com/").head().build())
                .execute()
            response.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun login(email: String, password: String, clientId: String): Result<String> = runCatching {
        val response = api.login(
            appVersion = "6.5.02",
            clientType = "1",
            iotVersion = "0",
            timestamp = System.currentTimeMillis().toString(),
            userAgent = "okhttp/3.12.0",
            request = LoginRequest(email = email, password = password, client = clientId),
        )
        val token = response.data?.token
        if (token.isNullOrBlank()) {
            if (response.status == 454) {
                error("Govee sent a verification email to your account. Check your inbox, click the link, then try logging in again.")
            }
            error("Login failed (${response.status}): ${response.message}")
        }
        token
    }

    suspend fun listDevices(token: String): Result<List<GoveeDevice>> = runCatching {
        val response = api.getDevices("Bearer $token")
        response.data?.devices ?: error("No devices: ${response.message}")
    }

    suspend fun getVpd(token: String, deviceId: String, model: String): Result<Double> =
        runCatching {
            val response = api.getDeviceState("Bearer $token", deviceId, model)
            val props = response.data?.properties ?: error("No state data: ${response.message}")
            // Use device-reported VPD if available; otherwise compute from temp + RH
            props.vpd()
                ?: run {
                    val temp = props.temperatureCelsius() ?: error("No VPD or temperature in sensor response")
                    val rh = props.humidity() ?: error("No VPD or humidity in sensor response")
                    computeVpd(temp, rh)
                }
        }

    suspend fun setFanSpeed(
        token: String,
        deviceId: String,
        model: String,
        speed: FanSpeed,
    ): Result<Unit> = runCatching {
        val response = api.control(
            token = "Bearer $token",
            request = ControlRequest(
                device = deviceId,
                model = model,
                cmd = DeviceCmd(name = "workMode", value = speed.goveeValue),
            ),
        )
        if (response.status != 200) error("Control failed: ${response.message}")
    }
}
