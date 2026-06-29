package com.dehumidifier.data

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path

data class GithubRelease(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val name: String?,
    @Json(name = "prerelease") val prerelease: Boolean = false,
    @Json(name = "html_url") val htmlUrl: String?,
    @Json(name = "assets") val assets: List<GithubAsset> = emptyList(),
) {
    /** First APK attached to the release, if any. */
    val apkAsset: GithubAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

data class GithubAsset(
    @Json(name = "name") val name: String,
    @Json(name = "browser_download_url") val downloadUrl: String,
    @Json(name = "size") val size: Long = 0,
)

interface UpdateApiService {
    /** Latest non-prerelease release for the repo. */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GithubRelease
}
