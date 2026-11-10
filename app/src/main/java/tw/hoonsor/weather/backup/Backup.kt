package tw.hoonsor.weather.backup

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONObject
import tw.hoonsor.weather.BuildConfig
import tw.hoonsor.weather.Graph
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 備份四件套（F21）：分享、存到下載資料夾、從檔案匯入（合併／取代）、匯入前自動備份可復原。
 * 內容只有一般設定與常用地點；授權碼不備份（換手機後在設定頁重填）。
 */
object Backup {
    const val SCHEMA = 1

    private fun json(): JSONObject = JSONObject().put("schema", SCHEMA).put("app", "tw.hoonsor.weather").put("version", BuildConfig.VERSION_NAME)
        .put("exportedAt", LocalDateTime.now().toString()).put("settings", Graph.settings.export())

    fun fileName() = "Weather-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.zip"

    /** 在快取資料夾產生 zip（分享用） */
    fun export(context: Context): File {
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, fileName())
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("backup.json")); z.write(json().toString(2).toByteArray(Charsets.UTF_8)); z.closeEntry()
        }
        return f
    }

    fun shareIntent(context: Context, f: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        return Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).let { Intent.createChooser(it, "分享天氣備份").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .also { it.clipData = android.content.ClipData.newRawUri("backup", uri) }
    }

    /** 存到「下載」資料夾（MediaStore，不需要儲存權限），回傳檔名 */
    fun saveToDownloads(context: Context): String {
        val f = export(context)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("沒辦法寫入下載資料夾")
        context.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
        return f.name
    }

    /** 寫到使用者用「儲存到檔案」選的位置（SAF） */
    fun writeTo(context: Context, uri: Uri) {
        val f = export(context)
        context.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
    }

    private fun undoFile(context: Context) = File(context.filesDir, "pre-import.json")
    fun hasUndo(context: Context) = undoFile(context).exists()

    /** 匯入：先把目前設定存起來（可復原），再合併或取代 */
    fun import(context: Context, uri: Uri, merge: Boolean): String {
        val text = context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input).use { z ->
                var e = z.nextEntry
                var found: String? = null
                while (e != null) { if (e.name == "backup.json") { found = z.readBytes().toString(Charsets.UTF_8); break }; e = z.nextEntry }
                found
            }
        } ?: error("這個檔案裡沒有 backup.json，不是天氣 APP 的備份")
        val o = JSONObject(text)
        if (o.optString("app") != "tw.hoonsor.weather") error("這不是天氣 APP 的備份")
        if (o.optInt("schema") > SCHEMA) error("備份檔版本比 APP 新，請先更新 APP")
        undoFile(context).writeText(Graph.settings.export().toString())
        Graph.settings.import(o.getJSONObject("settings"), merge)
        return o.optString("exportedAt")
    }

    fun undoImport(context: Context): Boolean {
        val f = undoFile(context)
        if (!f.exists()) return false
        Graph.settings.import(JSONObject(f.readText()), merge = false)
        f.delete()
        return true
    }
}
