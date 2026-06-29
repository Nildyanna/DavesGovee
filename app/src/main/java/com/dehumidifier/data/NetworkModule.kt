package com.dehumidifier.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    /** Shared client for downloading release artifacts (no body logging — APKs are large/binary). */
    val downloadClient: OkHttpClient = OkHttpClient.Builder().build()

    val goveeApi: GoveeApiService = Retrofit.Builder()
        .baseUrl("https://app2.govee.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoveeApiService::class.java)

    val weatherApi: WeatherApiService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WeatherApiService::class.java)

    val updateApi: UpdateApiService = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(UpdateApiService::class.java)
}
