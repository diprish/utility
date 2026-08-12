package com.diprish.utilitymeter.backup

import android.content.Context
import com.diprish.utilitymeter.data.Meter
import com.diprish.utilitymeter.data.MeterReading
import com.diprish.utilitymeter.data.MeterRepository
import com.diprish.utilitymeter.data.MeterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreSummary(val found: Boolean, val meters: Int = 0, val readings: Int = 0)

/**
 * Serialises the app's data (meters + readings) plus the reading photos into a
 * single zip and uploads it to Google Drive, and restores from that zip. The
 * data goes in `backup.json`; photos go under `photos/<filename>` and are
 * re-linked on restore.
 */
class BackupManager(
    private val context: Context,
    private val repository: MeterRepository,
) {

    private val prefs = context.getSharedPreferences("utility_backup", Context.MODE_PRIVATE)
    private val photosDir get() = File(context.filesDir, "meter_photos")

    fun lastBackupAt(): Long = prefs.getLong(KEY_LAST_BACKUP, 0L)
    fun isConnected(): Boolean = prefs.getBoolean(KEY_CONNECTED, false)
    fun isAutoEnabled(): Boolean = prefs.getBoolean(KEY_AUTO, false)
    fun setAutoEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO, enabled).apply()

    /** Build the backup and upload it, overwriting the previous one. */
    suspend fun backup(token: String): Int = withContext(Dispatchers.IO) {
        val readings = repository.allReadings()
        val zip = buildZip(repository.allMeters(), readings)
        DriveClient(token).upload(FILE_NAME, zip)
        prefs.edit()
            .putLong(KEY_LAST_BACKUP, System.currentTimeMillis())
            .putBoolean(KEY_CONNECTED, true)
            .apply()
        readings.size
    }

    /** Download the latest backup and replace all local data with it. */
    suspend fun restore(token: String): RestoreSummary = withContext(Dispatchers.IO) {
        val client = DriveClient(token)
        val file = client.findBackup(FILE_NAME) ?: return@withContext RestoreSummary(found = false)
        val zip = client.download(file.id)
        val (meters, readings) = parseZip(zip)
        repository.replaceAll(meters, readings)
        prefs.edit().putBoolean(KEY_CONNECTED, true).apply()
        RestoreSummary(found = true, meters = meters.size, readings = readings.size)
    }

    private fun buildZip(meters: List<Meter>, readings: List<MeterReading>): ByteArray {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val metersJson = JSONArray()
        for (m in meters) {
            metersJson.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("type", m.type.name)
                    .put("unit", m.unit)
                    .put("location", m.location)
                    .put("createdAt", m.createdAt)
                    .put("position", m.position)
            )
        }
        root.put("meters", metersJson)

        val readingsJson = JSONArray()
        for (r in readings) {
            readingsJson.put(
                JSONObject()
                    .put("id", r.id)
                    .put("meterId", r.meterId)
                    .put("value", r.value)
                    .put("timestamp", r.timestamp)
                    .put("photo", r.photoPath?.let { File(it).name } ?: JSONObject.NULL)
                    .put("note", r.note)
                    .put("photoTakenAt", r.photoTakenAt ?: JSONObject.NULL)
            )
        }
        root.put("readings", readingsJson)

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(root.toString().toByteArray())
            zip.closeEntry()

            val seen = HashSet<String>()
            for (r in readings) {
                val path = r.photoPath ?: continue
                val file = File(path)
                if (!file.exists() || !seen.add(file.name)) continue
                zip.putNextEntry(ZipEntry("photos/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun parseZip(bytes: ByteArray): Pair<List<Meter>, List<MeterReading>> {
        photosDir.mkdirs()
        var json: String? = null
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "backup.json") {
                    json = zip.readBytes().decodeToString()
                } else if (name.startsWith("photos/")) {
                    val fileName = name.substringAfter("photos/")
                    if (fileName.isNotBlank()) {
                        File(photosDir, fileName).outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val root = JSONObject(json ?: error("backup.json missing from archive"))

        val meters = root.getJSONArray("meters").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Meter(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    type = MeterType.fromName(o.getString("type")),
                    unit = o.getString("unit"),
                    location = o.optString("location", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    position = o.optLong("position", 0L),
                )
            }
        }

        val readings = root.getJSONArray("readings").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val photo = if (o.isNull("photo")) null else o.getString("photo")
                MeterReading(
                    id = o.getLong("id"),
                    meterId = o.getLong("meterId"),
                    value = o.getDouble("value"),
                    timestamp = o.getLong("timestamp"),
                    photoPath = photo?.let { File(photosDir, it).absolutePath },
                    photoTakenAt = if (o.isNull("photoTakenAt")) null else o.getLong("photoTakenAt"),
                    note = o.optString("note", ""),
                )
            }
        }
        return meters to readings
    }

    companion object {
        const val FILE_NAME = "utility-meter-backup.zip"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_CONNECTED = "connected"
        private const val KEY_AUTO = "auto_enabled"
    }
}
