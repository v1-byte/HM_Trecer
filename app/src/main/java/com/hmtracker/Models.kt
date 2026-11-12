package com.hmtracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Satu catatan HM. operator boleh kosong. isService = true berarti titik awal servis. */
data class Reading(
    val timeMillis: Long,
    val hm: Double,
    val operator: String,
    val photoPath: String?,
    val isService: Boolean
)

data class AppState(
    val siteName: String = "",
    val serviceLimit: Double = 200.0,
    val readings: List<Reading> = emptyList()
) {
    fun sorted(): List<Reading> = readings.sortedBy { it.timeMillis }
}

fun AppState.toJson(): String {
    val reads = JSONArray()
    readings.forEach { r ->
        reads.put(
            JSONObject()
                .put("time", r.timeMillis)
                .put("hm", r.hm)
                .put("operator", r.operator)
                .put("photo", r.photoPath ?: JSONObject.NULL)
                .put("service", r.isService)
        )
    }
    return JSONObject()
        .put("siteName", siteName)
        .put("serviceLimit", serviceLimit)
        .put("readings", reads)
        .toString(2)
}

fun parseState(json: String?): AppState {
    if (json.isNullOrBlank()) return AppState()
    return runCatching {
        val root = JSONObject(json)
        val readsArr = root.optJSONArray("readings") ?: JSONArray()
        val reads = (0 until readsArr.length()).map { i ->
            val r = readsArr.getJSONObject(i)
            Reading(
                timeMillis = r.getLong("time"),
                hm = r.getDouble("hm"),
                operator = r.optString("operator", ""),
                photoPath = if (r.isNull("photo")) null else r.getString("photo"),
                isService = r.optBoolean("service", false)
            )
        }
        AppState(
            siteName = root.optString("siteName", ""),
            serviceLimit = root.optDouble("serviceLimit", 200.0),
            readings = reads
        )
    }.getOrDefault(AppState())
}

/** Penyimpanan lokal di HP (SharedPreferences). */
object Storage {
    private const val PREFS = "hmtracker"
    private const val KEY = "state"

    fun load(context: Context): AppState {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return parseState(json)
    }

    fun save(context: Context, state: AppState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, state.toJson())
            .apply()
    }
}

/** Export & backup helpers. */
object Export {
    private val LOCALE_ID = Locale.forLanguageTag("id-ID")
    private val stampFmt = SimpleDateFormat("yyyyMMdd_HHmm", LOCALE_ID)

    fun csv(state: AppState): String {
        val sb = StringBuilder()
        sb.appendLine("waktu,hm,operator,titik_servis,tempat,batas_servis")
        val site = state.siteName.replace(",", " ")
        state.sorted().forEach { r ->
            val waktu = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", LOCALE_ID).format(Date(r.timeMillis))
            val op = r.operator.replace(",", " ")
            sb.appendLine("$waktu,${r.hm},$op,${if (r.isService) "ya" else "tidak"},$site,${state.serviceLimit}")
        }
        return sb.toString()
    }

    fun writeCsv(context: Context, state: AppState): File {
        val name = "HMTracker_${stampFmt.format(Date())}.csv"
        val file = File(context.cacheDir, name)
        file.writeText(csv(state), Charsets.UTF_8)
        return file
    }

    fun writeBackup(context: Context, state: AppState): File {
        val name = "HMTracker_backup_${stampFmt.format(Date())}.json"
        val file = File(context.cacheDir, name)
        file.writeText(state.toJson(), Charsets.UTF_8)
        return file
    }
}
