package com.dehumidifier.data

import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.HttpException

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

data class DeviceStatus(
    val online: Boolean?,
    val temperatureCelsius: Double?,
    val humidity: Double?,
    val vpd: Double?,
    val alerts: List<String>,
) {
    val hasFault: Boolean get() = online == false || alerts.isNotEmpty()
}

private val FAULT_KEYWORDS = listOf("error", "fault", "full", "warn", "alarm")

/** Any capability whose instance name looks fault-related and currently reads true/nonzero. */
private fun detectAlerts(capabilities: List<GoveeCapability>): List<String> =
    capabilities.mapNotNull { cap ->
        val nameLower = cap.instance.lowercase()
        if (FAULT_KEYWORDS.none { nameLower.contains(it) }) return@mapNotNull null
        cap.instance.takeIf { cap.state?.value.asBoolean == true }
    }

/** Resolved work_mode control values for a specific device's LOW/MEDIUM/HIGH fan speeds. */
data class FanSpeedMapping(
    val workMode: Int,
    val low: Int,
    val medium: Int,
    val high: Int,
) {
    fun valueFor(speed: FanSpeed): WorkModeValue = WorkModeValue(
        workMode = workMode,
        modeValue = when (speed) {
            FanSpeed.LOW -> low
            FanSpeed.MEDIUM -> medium
            FanSpeed.HIGH -> high
        },
    )
}

/**
 * Extracts the work_mode control values from a device's own declared capabilities (fetched
 * once, e.g. when the device is selected from the list) so later control calls don't need to
 * re-fetch the device list just to look this up. Returns null if the device doesn't declare a
 * work_mode capability, in which case callers should fall back to a live lookup or a default.
 */
fun resolveFanSpeedMapping(capabilities: List<GoveeCapability>): FanSpeedMapping? {
    val fields = capabilities
        .firstOrNull { it.type == WORK_MODE_TYPE || it.instance == WORK_MODE_INSTANCE }
        ?.parameters?.fields ?: return null
    val modeId = fields.firstOrNull { it.fieldName == "workMode" }
        ?.options?.firstOrNull()?.value?.toInt() ?: return null
    val gearOptions = fields.firstOrNull { it.fieldName == "modeValue" }?.options
    return FanSpeedMapping(
        workMode = modeId,
        low = gearOptions?.getOrNull(FanSpeed.LOW.ordinal)?.value?.toInt() ?: FanSpeed.LOW.goveeValue,
        medium = gearOptions?.getOrNull(FanSpeed.MEDIUM.ordinal)?.value?.toInt() ?: FanSpeed.MEDIUM.goveeValue,
        high = gearOptions?.getOrNull(FanSpeed.HIGH.ordinal)?.value?.toInt() ?: FanSpeed.HIGH.goveeValue,
    )
}

class GoveeRepository {

    private val api = NetworkModule.goveeApi

    /**
     * Retrofit throws HttpException for any non-2xx status *before* the body is deserialized —
     * so a real HTTP 401/429/etc. never reaches the response models' own error fields. This
     * unwraps HttpException into a message with the actual status and Govee's raw error body,
     * instead of Retrofit's generic (and mostly useless) "HTTP 401 Unauthorized".
     */
    private suspend fun <T> unwrapHttpErrors(block: suspend () -> T): T =
        try {
            block()
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()?.take(300)
            val hint = when (e.code()) {
                401 -> " — the API key was rejected. It may be invalid, expired, or temporarily suspended after heavy use."
                429 -> " — rate limited. Wait a bit before trying again."
                else -> ""
            }
            error("Govee HTTP ${e.code()}$hint${body?.let { ": $it" } ?: ""}")
        }

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
        val response = unwrapHttpErrors { api.getDevices(apiKey) }
        if (response.code != 200) {
            error("Govee API error (${response.code}): ${response.errorMessage ?: "no message"}")
        }
        response.data
    }

    /**
     * @param alerts human-readable names of any currently-active capability whose instance name
     *   suggests a fault (contains "error"/"fault"/"full"/"warn"/"alarm" and reads true/nonzero).
     *   Govee's exact capability name for e.g. a water-tank-full condition on the H7151 isn't
     *   confirmed from this environment (no live device access), so this is deliberately
     *   generic rather than hardcoded to a guessed name — it will catch whatever Govee actually
     *   calls it, at the cost of possibly also flagging unrelated boolean capabilities that
     *   happen to match a keyword.
     */
    suspend fun getDeviceStatus(apiKey: String, deviceId: String, model: String): Result<DeviceStatus> =
        runCatching {
            val response = unwrapHttpErrors {
                api.getDeviceState(
                    apiKey = apiKey,
                    request = DeviceStateRequest(
                        requestId = UUID.randomUUID().toString(),
                        payload = DeviceStatePayload(sku = model, device = deviceId),
                    ),
                )
            }
            val caps = response.payload?.capabilities
                ?: error("No state data (${response.code}): ${response.msg}")
            // Confirmed empirically: this sensor's "sensorTemperature"/"temperature" capability
            // reports Fahrenheit, not Celsius — feeding that raw value straight into the
            // (Celsius-only) VPD formula produced wildly inflated results (e.g. ~26 kPa instead
            // of ~1.2), since VPD is exponential in temperature.
            val rawTemp = caps.propertyValue("sensorTemperature", "temperature")
            val temp = rawTemp?.let { (it - 32) * 5.0 / 9.0 }
            val rh = caps.propertyValue("sensorHumidity", "humidity")
            val online = caps.firstOrNull { it.instance.equals("online", ignoreCase = true) }
                ?.state?.value.asBoolean
            DeviceStatus(
                online = online,
                temperatureCelsius = temp,
                humidity = rh,
                vpd = if (temp != null && rh != null) computeVpd(temp, rh.toInt()) else null,
                alerts = detectAlerts(caps),
            )
        }

    suspend fun getVpd(apiKey: String, deviceId: String, model: String): Result<Double> =
        getDeviceStatus(apiKey, deviceId, model).mapCatching {
            it.vpd ?: error("No temperature/humidity in sensor response")
        }

    /**
     * @param mapping the device's cached work_mode values (see [resolveFanSpeedMapping]),
     *   normally resolved once when the device was selected and passed in to avoid an extra
     *   Govee round trip on every control call. Only re-fetched here (one more API call) if
     *   no cached mapping is available — e.g. a manually-entered device whose capabilities
     *   were never fetched.
     */
    suspend fun setFanSpeed(
        apiKey: String,
        deviceId: String,
        model: String,
        speed: FanSpeed,
        mapping: FanSpeedMapping?,
    ): Result<Unit> = runCatching {
        val resolvedMapping = mapping ?: listDevices(apiKey).getOrNull()
            ?.firstOrNull { it.device == deviceId && it.sku == model }
            ?.let { resolveFanSpeedMapping(it.capabilities) }
        val value = resolvedMapping?.valueFor(speed)
            ?: WorkModeValue(workMode = 1, modeValue = speed.goveeValue)
        val response = unwrapHttpErrors {
            api.control(
                apiKey = apiKey,
                request = ControlRequest(
                    requestId = UUID.randomUUID().toString(),
                    payload = ControlPayload(
                        sku = model,
                        device = deviceId,
                        capability = ControlCapability(
                            type = WORK_MODE_TYPE,
                            instance = WORK_MODE_INSTANCE,
                            value = value,
                        ),
                    ),
                ),
            )
        }
        if (response.code != 200) error("Control failed (${response.code}): ${response.msg}")
    }
}
