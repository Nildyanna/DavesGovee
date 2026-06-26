package com.dehumidifier.data

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
    val client: String = "15ad88905d96c956",
)

data class LoginResponse(
    val status: Int,
    val message: String,
    val data: LoginData?,
)

data class LoginData(
    val token: String?,
    @Json(name = "accountId") val accountId: String? = null,
    val client: String? = null,
    val email: String? = null,
    val nickname: String? = null,
)

// ── Devices ───────────────────────────────────────────────────────────────────

data class DeviceListResponse(
    val status: Int,
    val message: String,
    val data: DeviceListData?,
)

data class DeviceListData(val devices: List<GoveeDevice>)

data class GoveeDevice(
    val device: String,
    val model: String,
    val deviceName: String,
    val controllable: Boolean,
    val retrievable: Boolean,
)

// ── Device state (sensors) ────────────────────────────────────────────────────

data class DeviceStateResponse(
    val status: Int,
    val message: String,
    val data: DeviceStateData?,
)

data class DeviceStateData(
    val device: String,
    val model: String,
    val properties: List<DeviceProperty>,
)

// Govee sends one property per list entry, e.g. [{"humidity":65},{"temperature":23.5}]
data class DeviceProperty(
    val online: Boolean? = null,
    val humidity: Int? = null,
    val temperature: Double? = null,
)

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
    val status: Int,
    val message: String,
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface GoveeApiService {

    @POST("account/rest/account/v1/login")
    suspend fun login(
        @Header("appVersion") appVersion: String = "6.5.02",
        @Header("clientType") clientType: String = "1",
        @Header("iotVersion") iotVersion: String = "0",
        @Header("timestamp") timestamp: String = System.currentTimeMillis().toString(),
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Body request: LoginRequest,
    ): LoginResponse

    @GET("device/rest/devices/v1/list")
    suspend fun getDevices(@Header("Authorization") token: String): DeviceListResponse

    @GET("device/rest/devices/v1/state")
    suspend fun getDeviceState(
        @Header("Authorization") token: String,
        @Query("device") deviceId: String,
        @Query("model") model: String,
    ): DeviceStateResponse

    @PUT("device/rest/devices/v1/control")
    suspend fun control(
        @Header("Authorization") token: String,
        @Body request: ControlRequest,
    ): ControlResponse
}
