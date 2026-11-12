package com.hmtracker

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Kuning: sisa 20 jam atau kurang (peringatan jelang servis). */
const val WARNING_WINDOW_HOURS = 20.0
/** Merah: sisa 10 jam atau kurang, unit harus berhenti dan diservis. */
const val RED_WINDOW_HOURS = 10.0
const val BIG_JUMP_HOURS = 100.0
private const val DAY_MS = 86_400_000.0
private val LOCALE_ID: Locale = Locale.forLanguageTag("id-ID")

enum class Status(val label: String) {
    GREEN("Lancar"),
    YELLOW("Peringatan"),
    RED("Wajib servis")
}

data class Summary(
    val startHm: Double,
    val lastHm: Double,
    val limitHm: Double,
    val used: Double,
    val remaining: Double,
    val status: Status,
    val hoursPerDay: Double?,
    val estimateMillis: Long?
)

fun fmtHm(v: Double): String = String.format(LOCALE_ID, "%.1f", v)
fun fmtDate(ms: Long): String = SimpleDateFormat("dd MMM yyyy", LOCALE_ID).format(Date(ms))
fun fmtDateTime(ms: Long): String = SimpleDateFormat("dd MMM yyyy HH:mm", LOCALE_ID).format(Date(ms))
fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm", LOCALE_ID).format(Date(ms))
fun fmtCoord(lat: Double, lon: Double): String =
    String.format(LOCALE_ID, "%.5f, %.5f", lat, lon)

private const val AMBER = "#FAC775"
private const val WHITE = "#FFFFFF"

fun summarize(unit: UnitMachine): Summary? {
    val sorted = unit.sorted()
    if (sorted.isEmpty()) return null

    val start = sorted.lastOrNull { it.isService } ?: sorted.first()
    val last = sorted.last()
    val limit = unit.serviceLimit

    val used = (last.hm - start.hm).coerceAtLeast(0.0)
    val remaining = limit - used
    val status = when {
        used >= limit - RED_WINDOW_HOURS -> Status.RED
        used >= limit - WARNING_WINDOW_HOURS -> Status.YELLOW
        else -> Status.GREEN
    }

    val elapsedDays = (last.timeMillis - start.timeMillis) / DAY_MS
    val perDay = if (elapsedDays >= 1.0 && used > 0.0) used / elapsedDays else null
    val estimate = perDay?.let {
        last.timeMillis + (remaining.coerceAtLeast(0.0) / it * DAY_MS).toLong()
    }

    return Summary(
        startHm = start.hm,
        lastHm = last.hm,
        limitHm = start.hm + limit,
        used = used,
        remaining = remaining,
        status = status,
        hoursPerDay = perDay,
        estimateMillis = estimate
    )
}

fun blockingError(hm: Double, unit: UnitMachine, isService: Boolean): String? {
    if (isService) return null
    val last = unit.sorted().lastOrNull() ?: return null
    return if (hm < last.hm) {
        "HM lebih kecil dari pembacaan terakhir (${fmtHm(last.hm)}). Periksa angkanya."
    } else null
}

fun softWarning(hm: Double, unit: UnitMachine, isService: Boolean): String? {
    if (isService) return null
    val last = unit.sorted().lastOrNull() ?: return null
    val jump = hm - last.hm
    return if (jump > BIG_JUMP_HOURS) {
        "Selisih ${fmtHm(jump)} jam dari pembacaan terakhir cukup besar. Pastikan angkanya benar."
    } else null
}

/**
 * Isi watermark foto. Urutan baris: tanggal dan jam, nama pengguna, jabatan/alat, unit, operator,
 * HM sekarang, HM terpakai, HM awal servis, target HM wajib servis dengan persentase, sisa, dan GPS.
 */
fun watermarkLines(
    unit: UnitMachine,
    summary: Summary,
    reading: Reading,
    profile: Profile? = null
): List<List<Seg>> {
    val pct = if (unit.serviceLimit > 0) (summary.used / unit.serviceLimit * 100).coerceAtLeast(0.0) else 0.0
    val lines = mutableListOf<List<Seg>>()

    lines += listOf(Seg("${fmtDate(reading.timeMillis)}  ${fmtTime(reading.timeMillis)}", AMBER))
    profile?.name?.takeIf { it.isNotBlank() }?.let { lines += listOf(Seg("Nama: $it", WHITE)) }
    profile?.toolType?.takeIf { it.isNotBlank() }?.let { lines += listOf(Seg("Jabatan/alat: $it", WHITE)) }
    lines += listOf(Seg("Unit: ${unit.name}", WHITE))
    if (reading.operator.isNotBlank()) {
        lines += listOf(Seg("Operator: ${reading.operator}", WHITE))
    }
    lines += listOf(Seg("HM sekarang: ${fmtHm(reading.hm)} jam${if (reading.isService) " (SERVIS)" else ""}", WHITE))
    lines += listOf(Seg("HM terpakai: ${fmtHm(summary.used)} jam", WHITE))
    lines += listOf(Seg("HM awal servis: ${fmtHm(summary.startHm)} jam", WHITE))
    lines += listOf(Seg("Target HM wajib servis: ${fmtHm(summary.limitHm)} jam (${pct.roundToInt()}%)", WHITE))
    lines += listOf(Seg("Sisa: ${fmtHm(summary.remaining)} jam | ${summary.status.label}", WHITE))
    if (unit.siteName.isNotBlank()) {
        lines += listOf(Seg("Tempat: ${unit.siteName}", WHITE))
    }
    if (reading.latitude != null && reading.longitude != null) {
        lines += listOf(Seg("GPS: ${fmtCoord(reading.latitude, reading.longitude)}", WHITE))
    }
    return lines
}
