package com.dehumidifier.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Snapshot of everything needed to reconnect without re-entering data. */
data class BackupData(
    val apiKey: String?,
    val deviceId: String?,
    val deviceModel: String?,
    val sensorDeviceId: String?,
    val sensorModel: String?,
    val targetVpd: Double,
    val vpdBand: Double,
)

/**
 * Exports/imports [BackupData] as JSON in the shared Downloads/DavesGovee folder via
 * MediaStore, so it survives an uninstall (unlike the app's private DataStore, which Android
 * deletes when the app is uninstalled). Trade-off: the API key sits in a plaintext file in
 * shared storage, readable by other apps with storage access or the user's file manager —
 * accepted deliberately so a reinstall doesn't require re-entering everything.
 *
 * Only active on API 29+ (Scoped Storage's MediaStore APIs); pre-29 this silently no-ops,
 * since writing to public storage there needs the WRITE_EXTERNAL_STORAGE runtime permission,
 * which isn't wired up here.
 *
 * Reading back after a real uninstall relies on MediaStore recognizing this app (same package
 * name + signing key) as the file's owner — the documented mechanism for an app reading files
 * it created without READ_EXTERNAL_STORAGE. Not verified on a live device.
 */
object BackupRepository {

    private const val DISPLAY_NAME = "davesgovee_backup.json"
    private const val RELATIVE_PATH = "Download/DavesGovee/"

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(BackupData::class.java)

    suspend fun write(context: Context, data: BackupData) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        try {
            val resolver = context.contentResolver
            val json = adapter.toJson(data)
            val uri = findUri(context) ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, DISPLAY_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
                },
            ) ?: return@withContext
            resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
        } catch (_: Exception) {
            // Best-effort — never let a backup failure interrupt saving settings.
        }
    }

    /** Deletes the backup file, e.g. on explicit logout, so a stale key can't silently restore. */
    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        try {
            findUri(context)?.let { context.contentResolver.delete(it, null, null) }
        } catch (_: Exception) {
            // Best-effort.
        }
    }

    suspend fun read(context: Context): BackupData? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        try {
            val uri = findUri(context) ?: return@withContext null
            context.contentResolver.openInputStream(uri)?.use { input ->
                adapter.fromJson(input.readBytes().toString(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findUri(context: Context): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val args = arrayOf(DISPLAY_NAME, RELATIVE_PATH)
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
        return null
    }
}
