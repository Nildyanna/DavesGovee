package com.dehumidifier.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dehumidifier.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

data class ReleaseInfo(val tagName: String, val apkUrl: String, val buildNumber: Int)

object UpdateChecker {

    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val token = BuildConfig.GITHUB_TOKEN
            if (token.isBlank()) return@withContext null

            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            val body = NetworkModule.okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string() ?: return@withContext null
            }

            val tagName = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(body)
                ?.groupValues?.get(1) ?: return@withContext null
            val buildNumber = tagName.removePrefix("v").toIntOrNull() ?: return@withContext null

            if (buildNumber <= BuildConfig.VERSION_CODE) return@withContext null

            val apkUrl = Regex(""""browser_download_url"\s*:\s*"([^"]+\.apk)"""").find(body)
                ?.groupValues?.get(1) ?: return@withContext null

            ReleaseInfo(tagName, apkUrl, buildNumber)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadAndInstall(context: Context, info: ReleaseInfo, onProgress: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val token = BuildConfig.GITHUB_TOKEN
            val req = Request.Builder()
                .url(info.apkUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/octet-stream")
                .build()

            NetworkModule.okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                val body = resp.body ?: return@withContext
                val total = body.contentLength()
                val file = File(context.cacheDir, "update.apk")
                var downloaded = 0L

                file.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) onProgress((downloaded * 100 / total).toInt())
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.provider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
}
