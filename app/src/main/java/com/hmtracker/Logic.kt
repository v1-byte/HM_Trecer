package com.hmtracker

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val WARNING_WINDOW_HOURS = 20.0   // status kuning saat sisa <= 20 jam
const val BIG_JUMP_HOURS = 100.0        // lompatan HM di atas ini diberi peringatan
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

private const val AMBER = "#FAC775"
private const val TEAL = "#9FE1CB"
private const val BLUE = "#85B7EB"
private const val WHITE = "#FFFFFF"

fun summarize(state: AppState): Summary? {
    val sorted = state.sorted()
    if (sorted.isEmpty()) return null

    // Titik awal = servis terakhir. Kalau belum pernah servis, pakai catatan pertama.
    val start = sorted.lastOrNull { it.isService } ?: sorted.first()
    val last = sorted.last()
    val limit = state.serviceLimit

    val used = (last.hm - start.hm).coerceAtLeast(0.0)
    val remaining = limit - used
    val status = when {
        used >= limit -> Status.RED
        used >= limit - WARNING_WINDOW_HOURS -> Status.YELLOW
        else -> Status.GREEN
    }

    // Perkiraan tanggal hanya dihitung setelah data minimal 1 hari supaya tidak meleset.
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

/** Pesan error yang membuat data tidak boleh disimpan. */
fun blockingError(hm: Double, state: AppState, isService: Boolean): String? {
    if (isService) return null
    val last = state.sorted().lastOrNull() ?: return null
    return if (hm < last.hm) {
        "HM lebih kecil dari pembacaan terakhir (${fmtHm(last.hm)}). Periksa angkanya."
    } else {
        null
    }
}

/** Peringatan yang tidak menghalangi penyimpanan. */
fun softWarning(hm: Double, state: AppState, isService: Boolean): String? {
    if (isService) return null
    val last = state.sorted().lastOrNull() ?: return null
    val jump = hm - last.hm
    return if (jump > BIG_JUMP_HOURS) {
        "Selisih ${fmtHm(jump)} jam dari pembacaan terakhir cukup besar. Pastikan angkanya benar."
    } else {
        null
    }
}

/**
 * Baris watermark foto. Tanggal, jam, dan operator diberi bingkai.
 * Operator hanya muncul jika diisi. Warna status mengikuti status Lancar, Peringatan, atau Wajib servis.
 */
fun watermarkLines(state: AppState, summary: Summary, reading: Reading): List<List<Seg>> {
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
    if (reading.operator.isNotBlank()) {
        lines += listOf(Seg("Operator: ${reading.operator}", TEAL, framed = true))
    }
    lines += listOf(Seg("HM: ${fmtHm(reading.hm)} jam${if (reading.isService) " (SERVIS)" else ""}", WHITE))
    lines += listOf(Seg("Pemakaian: ${fmtHm(summary.used)} jam | Sisa: ${fmtHm(summary.remaining)} jam", BLUE))
    lines += listOf(Seg("Status: ${summary.status.label} | Wajib servis di HM ${fmtHm(summary.limitHm)}", statusColor))
    lines += listOf(Seg("Tempat: ${state.siteName}", WHITE))
    return lines
}
