package com.dehumidifier.data

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

data class WeatherResponse(
    @Json(name = "current") val current: CurrentWeather,
)

data class CurrentWeather(
    @Json(name = "relative_humidity_2m") val relativeHumidity: Int,
    @Json(name = "temperature_2m") val temperature: Double,
)

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getCurrent(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "relative_humidity_2m,temperature_2m",
        @Query("forecast_days") forecastDays: Int = 1,
    ): WeatherResponse
}
