package com.dehumidifier.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

// Maps outdoor relative humidity % → dehumidifier fan speed (Low / Medium / High)
// Adjust thresholds to taste.
enum class FanSpeed(val goveeValue: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
}

fun humidityToFanSpeed(humidity: Int): FanSpeed = when {
    humidity < 55 -> FanSpeed.LOW
    humidity < 70 -> FanSpeed.MEDIUM
    else -> FanSpeed.HIGH
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

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val response = api.login(
            appVersion = "6.5.02",
            clientType = "1",
            iotVersion = "0",
            timestamp = System.currentTimeMillis().toString(),
            userAgent = "okhttp/3.12.0",
            request = LoginRequest(email = email, password = password),
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
