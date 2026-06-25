package com.dehumidifier.data

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

// ── Auth ──────────────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
    val client: String = "android-dehumidifier-app",
)

data class LoginResponse(
    val status: Int,
    val message: String,
    val data: LoginData?,
)

data class LoginData(
    val token: String,
    @Json(name = "accountId") val accountId: String,
    val client: String,
    val email: String,
    val nickname: String,
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
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("device/rest/devices/v1/list")
    suspend fun getDevices(@Header("Authorization") token: String): DeviceListResponse

    @PUT("device/rest/devices/v1/control")
    suspend fun control(
        @Header("Authorization") token: String,
        @Body request: ControlRequest,
    ): ControlResponse
}
