package com.dehumidifier.data

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

class GoveeRepository {

    private val api = NetworkModule.goveeApi

    suspend fun login(email: String, password: String): Result<String> = runCatching {
        val response = api.login(LoginRequest(email = email, password = password))
        response.data?.token ?: error("Login failed: ${response.message}")
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
