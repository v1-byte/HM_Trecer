package com.hmtracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
        .toString()
}

fun parseState(json: String?): AppState {
    if (json.isNullOrBlank()) return AppState()
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

    return AppState(
        siteName = root.optString("siteName", ""),
        serviceLimit = root.optDouble("serviceLimit", 200.0),
        readings = reads
    )
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
