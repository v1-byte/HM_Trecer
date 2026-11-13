package com.hmtracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Satu catatan HM. */
data class Reading(
    val timeMillis: Long,
    val hm: Double,
    val operator: String,
    val photoPath: String?,
    val isService: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/** Satu unit/alat. */
data class UnitMachine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val siteName: String = "",
    val serviceLimit: Double = 200.0,
    val readings: List<Reading> = emptyList()
) {
    fun sorted(): List<Reading> = readings.sortedBy { it.timeMillis }
}

data class AppState(
    val units: List<UnitMachine> = emptyList(),
    val activeUnitId: String? = null
) {
    fun active(): UnitMachine? = units.find { it.id == activeUnitId } ?: units.firstOrNull()

    fun withActive(transform: (UnitMachine) -> UnitMachine): AppState {
        val cur = active() ?: return this
        val updated = transform(cur)
        return copy(units = units.map { if (it.id == updated.id) updated else it })
    }
}

fun UnitMachine.toJson(): JSONObject {
    val reads = JSONArray()
    readings.forEach { r ->
        reads.put(
            JSONObject()
                .put("time", r.timeMillis)
                .put("hm", r.hm)
                .put("operator", r.operator)
                .put("photo", r.photoPath ?: JSONObject.NULL)
                .put("service", r.isService)
                .put("lat", r.latitude ?: JSONObject.NULL)
                .put("lon", r.longitude ?: JSONObject.NULL)
        )
    }
    return JSONObject()
        .put("id", id)
        .put("name", name)
        .put("siteName", siteName)
        .put("serviceLimit", serviceLimit)
        .put("readings", reads)
}

fun parseUnit(obj: JSONObject): UnitMachine {
    val readsArr = obj.optJSONArray("readings") ?: JSONArray()
    val reads = (0 until readsArr.length()).map { i ->
        val r = readsArr.getJSONObject(i)
        Reading(
            timeMillis = r.getLong("time"),
            hm = r.getDouble("hm"),
            operator = r.optString("operator", ""),
            photoPath = if (r.isNull("photo")) null else r.getString("photo"),
            isService = r.optBoolean("service", false),
            latitude = if (r.isNull("lat")) null else r.optDouble("lat"),
            longitude = if (r.isNull("lon")) null else r.optDouble("lon")
        )
    }
    return UnitMachine(
        id = obj.optString("id", UUID.randomUUID().toString()),
        name = obj.optString("name", "Unit"),
        siteName = obj.optString("siteName", ""),
        serviceLimit = obj.optDouble("serviceLimit", 200.0),
        readings = reads
    )
}

fun AppState.toJson(): String {
    val arr = JSONArray()
    units.forEach { arr.put(it.toJson()) }
    return JSONObject()
        .put("version", 2)
        .put("units", arr)
        .put("activeUnitId", activeUnitId ?: JSONObject.NULL)
        .toString(2)
}

fun parseState(json: String?): AppState {
    if (json.isNullOrBlank()) return AppState()
    return runCatching {
        val root = JSONObject(json)

        // Migrasi format lama (v1 single unit)
        if (!root.has("units") && root.has("readings")) {
            val old = UnitMachine(
                name = "Unit 1",
                siteName = root.optString("siteName", ""),
                serviceLimit = root.optDouble("serviceLimit", 200.0),
                readings = run {
                    val readsArr = root.optJSONArray("readings") ?: JSONArray()
                    (0 until readsArr.length()).map { i ->
                        val r = readsArr.getJSONObject(i)
                        Reading(
                            timeMillis = r.getLong("time"),
                            hm = r.getDouble("hm"),
                            operator = r.optString("operator", ""),
                            photoPath = if (r.isNull("photo")) null else r.getString("photo"),
                            isService = r.optBoolean("service", false)
                        )
                    }
                }
            )
            return AppState(units = listOf(old), activeUnitId = old.id)
        }

        val unitsArr = root.optJSONArray("units") ?: JSONArray()
        val units = (0 until unitsArr.length()).map { parseUnit(unitsArr.getJSONObject(it)) }
        val activeId = if (root.isNull("activeUnitId")) units.firstOrNull()?.id else root.optString("activeUnitId")
        AppState(units = units, activeUnitId = activeId)
    }.getOrDefault(AppState())
}

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

object Export {
    private val LOCALE_ID = Locale.forLanguageTag("id-ID")
    private val stampFmt = SimpleDateFormat("yyyyMMdd_HHmm", LOCALE_ID)

    fun csv(state: AppState): String {
        val sb = StringBuilder()
        sb.appendLine("unit,tempat,waktu,hm,operator,titik_servis,latitude,longitude,batas_servis")
        state.units.forEach { u ->
            val unitName = u.name.replace(",", " ")
            val site = u.siteName.replace(",", " ")
            u.sorted().forEach { r ->
                val waktu = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", LOCALE_ID).format(Date(r.timeMillis))
                val op = r.operator.replace(",", " ")
                val lat = r.latitude?.toString() ?: ""
                val lon = r.longitude?.toString() ?: ""
                sb.appendLine("$unitName,$site,$waktu,${r.hm},$op,${if (r.isService) "ya" else "tidak"},$lat,$lon,${u.serviceLimit}")
            }
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
