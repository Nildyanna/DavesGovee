package com.dehumidifier.data

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// Govee's Open Platform API (openapi.api.govee.com/router/api/v1/...). Unlike the legacy
// developer-api.govee.com/v1 endpoints (lights only, flat request bodies), this is the API
// surface that actually supports appliances like dehumidifiers, via a requestId/payload
// envelope and a device-declared "capabilities" model.

// ── Lenient numeric decoding ───────────────────────────────────────────────────
// A capability's state.value / option.value can be a number, boolean, or string depending
// on the instance (e.g. "online" is a bool, "humidity" is a number). We only ever read
// numeric values, so this adapter coerces what it can and returns null otherwise, instead
// of throwing and breaking deserialization of the whole capabilities array.

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Lenient

class LenientDoubleAdapter {
    @FromJson
    @Lenient
    fun fromJson(reader: JsonReader): Double? = when (reader.peek()) {
        JsonReader.Token.NUMBER -> reader.nextDouble()
        JsonReader.Token.STRING -> reader.nextString().toDoubleOrNull()
        JsonReader.Token.BOOLEAN -> { reader.nextBoolean(); null }
        JsonReader.Token.NULL -> reader.nextNull()
        else -> { reader.skipValue(); null }
    }

    @ToJson
    fun toJson(writer: JsonWriter, @Lenient value: Double?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}

// ── Devices (GET /user/devices) ─────────────────────────────────────────────────

data class DeviceListResponse(
    val code: Int,
    val message: String? = null,
    val data: List<GoveeDevice> = emptyList(),
)

data class GoveeDevice(
    val device: String,
    /** Govee calls this "sku"; kept as `model` to match the rest of the app's naming. */
    val sku: String,
    val deviceName: String,
    val type: String? = null,
    val capabilities: List<GoveeCapability> = emptyList(),
) {
    val model: String get() = sku
}

data class GoveeCapability(
    val type: String,
    val instance: String,
    val parameters: CapabilityParameters? = null,
    /** Present only in /device/state responses. */
    val state: CapabilityState? = null,
)

data class CapabilityParameters(
    val dataType: String? = null,
    val options: List<CapabilityOption>? = null,
    val fields: List<CapabilityField>? = null,
)

data class CapabilityField(
    val fieldName: String,
    val dataType: String? = null,
    val options: List<CapabilityOption>? = null,
)

data class CapabilityOption(
    val name: String,
    @Lenient val value: Double? = null,
)

data class CapabilityState(
    @Lenient val value: Double? = null,
)

// ── Device state (POST /device/state) ─────────────────────────────────────────

data class DeviceStateRequest(
    val requestId: String,
    val payload: DeviceStatePayload,
)

data class DeviceStatePayload(
    val sku: String,
    val device: String,
)

data class DeviceStateResponse(
    val requestId: String? = null,
    val code: Int,
    val msg: String? = null,
    val payload: DeviceStateData? = null,
)

data class DeviceStateData(
    val sku: String? = null,
    val device: String? = null,
    val capabilities: List<GoveeCapability> = emptyList(),
)

/** Matches by instance name; Govee sensors use "sensorHumidity"/"humidity" and "sensorTemperature"/"temperature". */
fun List<GoveeCapability>.propertyValue(vararg instanceNames: String): Double? =
    firstNotNullOfOrNull { cap -> cap.state?.value.takeIf { cap.instance in instanceNames } }

// ── Control (POST /device/control) ────────────────────────────────────────────

data class ControlRequest(
    val requestId: String,
    val payload: ControlPayload,
)

data class ControlPayload(
    val sku: String,
    val device: String,
    val capability: ControlCapability,
)

data class ControlCapability(
    val type: String,
    val instance: String,
    val value: WorkModeValue,
)

/** Gear-based appliances (fans, dehumidifiers) take {workMode, modeValue} as their work_mode value. */
data class WorkModeValue(
    val workMode: Int,
    val modeValue: Int,
)

data class ControlResponse(
    val requestId: String? = null,
    val code: Int,
    val msg: String? = null,
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface GoveeApiService {

    @GET("router/api/v1/user/devices")
    suspend fun getDevices(@Header("Govee-API-Key") apiKey: String): DeviceListResponse

    @POST("router/api/v1/device/state")
    suspend fun getDeviceState(
        @Header("Govee-API-Key") apiKey: String,
        @Body request: DeviceStateRequest,
    ): DeviceStateResponse

    @POST("router/api/v1/device/control")
    suspend fun control(
        @Header("Govee-API-Key") apiKey: String,
        @Body request: ControlRequest,
    ): ControlResponse
}
