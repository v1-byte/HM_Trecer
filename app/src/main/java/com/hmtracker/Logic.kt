package com.hmtracker

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val WARNING_WINDOW_HOURS = 20.0
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
private const val TEAL = "#9FE1CB"
private const val BLUE = "#85B7EB"
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
        used >= limit -> Status.RED
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

fun watermarkLines(unit: UnitMachine, summary: Summary, reading: Reading): List<List<Seg>> {
    val statusColor = when (summary.status) {
        Status.GREEN -> "#C0DD97"
        Status.YELLOW -> "#FAC775"
        Status.RED -> "#F09595"
    }
    val lines = mutableListOf<List<Seg>>()
    lines += listOf(
        Seg(fmtDate(reading.timeMillis), AMBER, framed = true),
        Seg(fmtTime(reading.timeMillis), AMBER, framed = true)
    )
    lines += listOf(Seg(unit.name, TEAL, framed = true))
    if (reading.operator.isNotBlank()) {
        lines += listOf(Seg("Operator: ${reading.operator}", TEAL, framed = true))
    }
    lines += listOf(Seg("HM: ${fmtHm(reading.hm)} jam${if (reading.isService) " (SERVIS)" else ""}", WHITE))
    lines += listOf(Seg("Pemakaian: ${fmtHm(summary.used)} jam | Sisa: ${fmtHm(summary.remaining)} jam", BLUE))
    lines += listOf(Seg("Status: ${summary.status.label} | Wajib servis di HM ${fmtHm(summary.limitHm)}", statusColor))
    if (unit.siteName.isNotBlank()) {
        lines += listOf(Seg("Tempat: ${unit.siteName}", WHITE))
    }
    if (reading.latitude != null && reading.longitude != null) {
        lines += listOf(Seg("GPS: ${fmtCoord(reading.latitude, reading.longitude)}", WHITE))
    }
    return lines
}
