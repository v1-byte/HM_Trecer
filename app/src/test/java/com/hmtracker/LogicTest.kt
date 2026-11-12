package com.hmtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    private val t0 = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun reading(hoursAfterStart: Int, hm: Double, service: Boolean = false) =
        Reading(timeMillis = t0 + hoursAfterStart * day, hm = hm, operator = "", photoPath = null, isService = service)

    private fun unit(vararg readings: Reading, limit: Double = 200.0) =
        UnitMachine(id = "u1", name = "Excavator 01", siteName = "Site A", serviceLimit = limit, readings = readings.toList())

    @Test
    fun tanpaPembacaanTidakAdaRingkasan() {
        assertNull(summarize(unit()))
    }

    @Test
    fun pemakaianDihitungDariTitikServisTerbaru() {
        val u = unit(reading(0, 984.5, service = true), reading(3, 988.6))
        val s = summarize(u)!!
        assertEquals(4.1, s.used, 0.0001)
        assertEquals(195.9, s.remaining, 0.0001)
        assertEquals(1184.5, s.limitHm, 0.0001)
        assertEquals(Status.GREEN, s.status)
    }

    @Test
    fun kuningSaatSisaDuaPuluhJamAtauKurang() {
        val u = unit(reading(0, 1000.0, service = true), reading(5, 1181.0))
        assertEquals(Status.YELLOW, summarize(u)!!.status) // sisa 19 jam
    }

    @Test
    fun merahSaatSisaSepuluhJamAtauKurang() {
        // Contoh dari pengguna: target 200, pemakaian 190 harus merah
        val u = unit(reading(0, 1000.0, service = true), reading(5, 1190.0))
        val s = summarize(u)!!
        assertEquals(10.0, s.remaining, 0.0001)
        assertEquals(Status.RED, s.status)
    }

    @Test
    fun hmLebihKecilDariPembacaanTerakhirDitolak() {
        val u = unit(reading(0, 1000.0), reading(1, 1005.0))
        assertTrue(blockingError(1002.0, u, isService = false) != null)
        assertNull(blockingError(1002.0, u, isService = true))
    }

    @Test
    fun watermarkMenampilkanTargetDanPersentase() {
        val u = unit(reading(0, 984.5, service = true), reading(5, 1174.5))
        val s = summarize(u)!!
        val lines = watermarkLines(u, s, u.sorted().last(), Profile("Budi", "Operator")).flatten().map { it.text }
        assertTrue(lines.contains("Next Maintenance: 1184,5 jam (95%)"))
        assertTrue(lines.contains("SMR: 984,5 jam"))
        assertTrue(lines.contains("Sisa: 10,0 jam | Wajib servis"))
        assertTrue(lines.contains("Nama: Budi"))
    }
}
