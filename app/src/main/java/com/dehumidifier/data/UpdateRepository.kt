package com.dehumidifier.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/** Outcome of an update check. */
sealed interface UpdateCheck {
    data class Available(val versionName: String, val release: GithubRelease) : UpdateCheck
    object UpToDate : UpdateCheck
    data class NoArtifact(val versionName: String) : UpdateCheck
}

class UpdateRepository(
    private val owner: String = "Nildyanna",
    private val repo: String = "DavesGovee",
    private val api: UpdateApiService = NetworkModule.updateApi,
) {

    /** Compares the latest GitHub release against the installed version. */
    suspend fun check(context: Context): Result<UpdateCheck> = withContext(Dispatchers.IO) {
        runCatching {
            val release = api.latestRelease(owner, repo)
            val latest = normalizeVersion(release.tagName)
            val current = normalizeVersion(installedVersionName(context))
            when {
                compareVersions(latest, current) <= 0 -> UpdateCheck.UpToDate
                release.apkAsset == null -> UpdateCheck.NoArtifact(release.tagName)
                else -> UpdateCheck.Available(release.tagName, release)
            }
        }
    }

    /** Downloads the release APK into app-private cache and returns the file. */
    suspend fun download(context: Context, release: GithubRelease): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val asset = release.apkAsset ?: error("Release has no APK asset.")
                val request = Request.Builder().url(asset.downloadUrl).build()
                NetworkModule.downloadClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("Download failed: HTTP ${resp.code}")
                    val body = resp.body ?: error("Empty download response.")
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val file = File(dir, "update.apk")
                    file.outputStream().use { out -> body.byteStream().copyTo(out) }
                    file
                }
            }
        }

    /** Whether the OS will allow this app to install packages (Android 8+ gates this per-app). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Intent that sends the user to grant "install unknown apps" for this app. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    /** Launches the system package installer for the downloaded APK. */
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun installedVersionName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0"
        }

    companion object {
        /** Strips a leading "v" and keeps only digit/dot characters, e.g. "v1.2.0" -> "1.2.0". */
        internal fun normalizeVersion(raw: String): String =
            raw.trim().removePrefix("v").removePrefix("V").takeWhile { it.isDigit() || it == '.' }

        /** Compares dotted numeric versions. Returns >0 if a is newer than b. */
        internal fun compareVersions(a: String, b: String): Int {
            val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
            val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val diff = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
