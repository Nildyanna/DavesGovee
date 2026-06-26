package com.dehumidifier.data

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query

// ── Devices ───────────────────────────────────────────────────────────────────

data class DeviceListResponse(
    @Json(name = "code") val status: Int,
    val message: String,
    val data: DeviceListData?,
)

data class DeviceListData(val devices: List<GoveeDevice>)

data class GoveeDevice(
    val device: String,
    val model: String,
    val deviceName: String,
    val controllable: Boolean? = null,
    val retrievable: Boolean? = null,
    val supportCmds: List<String>? = null,
)

// ── Device state (sensors) ────────────────────────────────────────────────────

data class DeviceStateResponse(
    @Json(name = "code") val status: Int,
    val message: String,
    val data: DeviceStateData?,
)

data class DeviceStateData(
    val device: String,
    val model: String,
    val properties: List<DeviceProperty>,
)

// Govee sends one property per list entry, e.g. [{"humidity":65},{"temperature":23.5},{"vpd":0.82}]
data class DeviceProperty(
    val online: Boolean? = null,
    val humidity: Int? = null,
    val temperature: Double? = null,
    val vpd: Double? = null,
)

fun List<DeviceProperty>.vpd(): Double? = firstNotNullOfOrNull { it.vpd }
fun List<DeviceProperty>.humidity(): Int? = firstNotNullOfOrNull { it.humidity }
fun List<DeviceProperty>.temperatureCelsius(): Double? = firstNotNullOfOrNull { it.temperature }

// ── Control ───────────────────────────────────────────────────────────────────

data class ControlRequest(
    val device: String,
    val model: String,
    val cmd: DeviceCmd,
)

data class DeviceCmd(
    val name: String,
    val value: Int,
)

data class ControlResponse(
    @Json(name = "code") val status: Int,
    val message: String,
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface GoveeApiService {

    @GET("v1/devices")
    suspend fun getDevices(@Header("Govee-API-Key") apiKey: String): DeviceListResponse

    @GET("v1/devices/state")
    suspend fun getDeviceState(
        @Header("Govee-API-Key") apiKey: String,
        @Query("device") deviceId: String,
        @Query("model") model: String,
    ): DeviceStateResponse

    @PUT("v1/devices/control")
    suspend fun control(
        @Header("Govee-API-Key") apiKey: String,
        @Body request: ControlRequest,
    ): ControlResponse
}
