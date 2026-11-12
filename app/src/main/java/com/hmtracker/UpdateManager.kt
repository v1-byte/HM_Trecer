package com.hmtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Informasi release APK terbaru dari repositori resmi HM Tracker. */
data class AppUpdate(
    val version: String,
    val downloadUrl: String,
    val notes: String
)

object UpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/v1-byte/HM_Trecer/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    fun check(context: Context, onResult: (AppUpdate?) -> Unit) {
        Thread {
            val result = runCatching {
                val localVersion = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName.orEmpty()
                val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                connection.inputStream.bufferedReader().use { reader ->
                    val json = JSONObject(reader.readText())
                    val tag = json.optString("tag_name", "").removePrefix("v")
                    val apkUrl = json.optJSONArray("assets")?.let { assets ->
                        (0 until assets.length())
                            .map { assets.getJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk") }
                            ?.optString("browser_download_url")
                    }.orEmpty()
                    if (tag.isBlank() || apkUrl.isBlank() || !isNewer(tag, localVersion)) {
                        null
                    } else {
                        AppUpdate(tag, apkUrl, json.optString("body", "Update tersedia."))
                    }
                }
            }.getOrNull()
            main.post { onResult(result) }
        }.start()
    }

    fun download(context: Context, update: AppUpdate, onProgress: (Int) -> Unit, onDone: (File) -> Unit, onError: () -> Unit) {
        Thread {
            runCatching {
                val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/vnd.android.package-archive")
                }
                val total = connection.contentLengthLong
                val file = File(context.cacheDir, "HMTracker-${update.version}.apk")
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var readTotal = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            readTotal += read
                            if (total > 0) main.post { onProgress((readTotal * 100 / total).toInt()) }
                        }
                    }
                }
                main.post { onDone(file) }
            }.onFailure {
                main.post { onError() }
            }
        }.start()
    }

    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(value: String) = value.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val r = parts(remote).let { it + List((3 - it.size).coerceAtLeast(0)) { 0 } }
        val l = parts(local).let { it + List((3 - it.size).coerceAtLeast(0)) { 0 } }
        return (0..2).firstOrNull { r[it] != l[it] }?.let { r[it] > l[it] } ?: false
    }
}
