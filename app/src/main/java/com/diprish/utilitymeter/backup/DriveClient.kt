package com.diprish.utilitymeter.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** A backup file found in Drive. */
data class DriveFile(val id: String, val modifiedTime: String?)

/**
 * Minimal Google Drive v3 REST client for a single backup file. Uses direct
 * HTTPS calls with a Bearer token rather than the heavyweight Drive client
 * library. All calls are blocking and must run off the main thread.
 */
class DriveClient(private val accessToken: String) {

    private val http = OkHttpClient()

    /** Find the app's backup file by name, or null if it doesn't exist yet. */
    fun findBackup(name: String): DriveFile? {
        val query = URLEncoder.encode("name = '$name' and trashed = false", "UTF-8")
        val url = "$DRIVE_V3/files?spaces=drive&fields=files(id,modifiedTime)&q=$query"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Drive list failed (${response.code}): $body")
            val files = JSONObject(body).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            val file = files.getJSONObject(0)
            return DriveFile(file.getString("id"), file.optString("modifiedTime", null))
        }
    }

    /** Create the backup file (multipart: metadata + content). Returns its id. */
    fun create(name: String, content: ByteArray): String {
        val metadata = JSONObject().put("name", name).put("mimeType", ZIP_MIME).toString()
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(content.toRequestBody(ZIP_MIME.toMediaType()))
            .build()
        val request = Request.Builder()
            .url("$UPLOAD_V3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val resp = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Drive create failed (${response.code}): $resp")
            return JSONObject(resp).getString("id")
        }
    }

    /** Overwrite the content of an existing file, keeping its name/id. */
    fun update(fileId: String, content: ByteArray) {
        val request = Request.Builder()
            .url("$UPLOAD_V3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $accessToken")
            .patch(content.toRequestBody(ZIP_MIME.toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive update failed (${response.code}): ${response.body?.string()}")
            }
        }
    }

    /** Create or overwrite the named backup file. Returns the file id. */
    fun upload(name: String, content: ByteArray): String {
        val existing = findBackup(name)
        return if (existing != null) {
            update(existing.id, content)
            existing.id
        } else {
            create(name, content)
        }
    }

    /** Download a file's raw bytes. */
    fun download(fileId: String): ByteArray {
        val request = Request.Builder()
            .url("$DRIVE_V3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive download failed (${response.code}): ${response.body?.string()}")
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    companion object {
        private const val DRIVE_V3 = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_V3 = "https://www.googleapis.com/upload/drive/v3"
        private const val ZIP_MIME = "application/zip"
    }
}
