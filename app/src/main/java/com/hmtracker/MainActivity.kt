package com.hmtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

data class Pending(
    val bitmap: Bitmap?,
    val service: Boolean,
    val ocr: OcrResult? = null,
    val ocrDone: Boolean = false,
    val gps: GpsFix? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notify.ensureChannel(this)
        setContent {
            MaterialTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Notify.checkAndNotify(this, Storage.load(this))
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(Storage.load(context)) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var captureService by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Reading?>(null) }
    var pendingCameraAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val captureFile = remember { File(context.cacheDir, "capture.jpg") }
    val captureUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
    }

    fun commit(newState: AppState) {
        state = newState
        Storage.save(context, newState)
        Notify.checkAndNotify(context, newState)
    }

    fun shareFile(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun startReview(bitmap: Bitmap?, service: Boolean, gps: GpsFix?) {
        if (bitmap == null) {
            pending = Pending(null, service, null, true, gps)
            return
        }
        pending = Pending(bitmap, service, gps = gps)
        val unit = state.active()
        MeterOcr.read(
            bitmap = bitmap,
            lastHm = unit?.sorted()?.lastOrNull()?.hm,
            onResult = { res -> pending = pending?.copy(ocr = res, ocrDone = true) },
            onError = { err ->
                pending = pending?.copy(ocrDone = true)
                message = "Baca otomatis gagal ($err). Isi HM secara manual."
            }
        )
    }

    fun decodeAndReview(source: ImageDecoder.Source, service: Boolean) {
        val gps = LocationHelper.lastKnown(context)
        runCatching { Watermark.decode(source) }
            .onSuccess { startReview(it, service, gps) }
            .onFailure { message = "Foto tidak bisa dibuka: ${it.message}" }
    }

    fun saveReading(p: Pending, hm: Double, operator: String, service: Boolean) {
        val unit = state.active() ?: return
        val now = System.currentTimeMillis()
        val bmp = p.bitmap
        val photoFile = if (bmp != null) {
            File(File(context.filesDir, "photos/${unit.id}"), "$now.jpg")
        } else null

        val draft = Reading(
            timeMillis = now,
            hm = hm,
            operator = operator,
            photoPath = photoFile?.absolutePath,
            isService = service,
            latitude = p.gps?.latitude,
            longitude = p.gps?.longitude
        )
        val newUnit = unit.copy(readings = unit.readings + draft)
        val newState = state.copy(units = state.units.map { if (it.id == unit.id) newUnit else it })
        val summary = summarize(newUnit) ?: return

        if (bmp != null && photoFile != null) {
            Watermark.save(Watermark.stamp(bmp, watermarkLines(newUnit, summary, draft)), photoFile)
        }
        commit(newState)
        pending = null
        message = if (service) {
            "Titik awal servis dicatat: ${fmtHm(hm)} jam"
        } else {
            "Tersimpan. Pemakaian ${fmtHm(summary.used)} jam, sisa ${fmtHm(summary.remaining)} jam"
        }
    }

    // --- Permission launchers ---
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraAction?.invoke()
        } else {
            message = "Izin kamera ditolak. Aktifkan di Pengaturan HP → Aplikasi → HM Tracker → Izin."
        }
        pendingCameraAction = null
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* lokasi & notifikasi opsional */ }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        multiPermissionLauncher.launch(perms.toTypedArray())
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok && captureFile.exists() && captureFile.length() > 0) {
            decodeAndReview(ImageDecoder.createSource(captureFile), captureService)
        } else if (ok) {
            message = "Foto kosong. Coba lagi."
        } else {
            message = "Kamera dibatalkan."
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            decodeAndReview(ImageDecoder.createSource(context.contentResolver, uri), false)
        }
    }

    fun launchCamera(service: Boolean) {
        captureService = service
        val action = {
            // Pastikan file kosong sebelum ambil foto baru
            captureFile.delete()
            takePhoto.launch(captureUri)
        }
        if (hasCameraPermission()) {
            action()
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                val restored = parseState(text)
                if (restored.units.isEmpty()) {
                    message = "File backup tidak valid atau kosong."
                } else {
                    commit(restored)
                    message = "Restore berhasil. ${restored.units.size} unit, ${restored.units.sumOf { it.readings.size }} catatan."
                    showSettings = false
                }
            } ?: run { message = "Tidak bisa membaca file." }
        }.onFailure { message = "Restore gagal: ${it.message}" }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    // --- UI ---
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                // Belum ada unit sama sekali
                state.units.isEmpty() && !showSettings -> {
                    OnboardingScreen(
                        onAddUnit = { showSettings = true },
                        onRestore = { restorePicker.launch("application/json") }
                    )
                }
                showSettings -> {
                    SettingsScreen(
                        state = state,
                        onSaveUnit = { updated ->
                            val units = if (state.units.any { it.id == updated.id }) {
                                state.units.map { if (it.id == updated.id) updated else it }
                            } else state.units + updated
                            commit(state.copy(units = units, activeUnitId = updated.id))
                            message = "Unit disimpan"
                            if (state.units.isEmpty()) showSettings = false
                        },
                        onAddUnit = { name, site, limit ->
                            val u = UnitMachine(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                siteName = site,
                                serviceLimit = limit
                            )
                            commit(state.copy(units = state.units + u, activeUnitId = u.id))
                            message = "Unit \"$name\" ditambahkan"
                            showSettings = false
                        },
                        onDeleteUnit = { id ->
                            val remaining = state.units.filter { it.id != id }
                            commit(
                                state.copy(
                                    units = remaining,
                                    activeUnitId = remaining.firstOrNull()?.id
                                )
                            )
                            message = "Unit dihapus"
                            if (remaining.isEmpty()) showSettings = false
                        },
                        onExportCsv = {
                            runCatching {
                                shareFile(Export.writeCsv(context, state), "text/csv", "Export HM Tracker")
                            }.onFailure { message = "Export gagal: ${it.message}" }
                        },
                        onBackup = {
                            runCatching {
                                shareFile(Export.writeBackup(context, state), "application/json", "Backup HM Tracker")
                            }.onFailure { message = "Backup gagal: ${it.message}" }
                        },
                        onRestore = { restorePicker.launch("application/json") },
                        onBack = {
                            if (state.units.isEmpty()) {
                                message = "Tambah minimal 1 unit dulu."
                            } else {
                                showSettings = false
                            }
                        }
                    )
                }
                else -> {
                    val unit = state.active()
                    if (unit == null) {
                        showSettings = true
                    } else {
                        HomeScreen(
                            state = state,
                            unit = unit,
                            onSelectUnit = { id -> commit(state.copy(activeUnitId = id)) },
                            onFoto = { launchCamera(service = false) },
                            onServiceFoto = { launchCamera(service = true) },
                            onGaleri = {
                                pickPhoto.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onManual = {
                                startReview(null, false, LocationHelper.lastKnown(context))
                            },
                            onSettings = { showSettings = true },
                            onDeleteRequest = { toDelete = it }
                        )
                    }
                }
            }
        }
    }

    pending?.let { p ->
        val unit = state.active() ?: return@let
        ReviewDialog(
            pending = p,
            unit = unit,
            onConfirm = { hm, op, service -> saveReading(p, hm, op, service) },
            onDismiss = { pending = null }
        )
    }

    toDelete?.let { r ->
        DeleteConfirmDialog(
            reading = r,
            onConfirm = {
                val unit = state.active() ?: return@DeleteConfirmDialog
                val newReadings = unit.readings.filterNot {
                    it.timeMillis == r.timeMillis && it.hm == r.hm
                }
                r.photoPath?.let { path -> runCatching { File(path).delete() } }
                commit(state.withActive { it.copy(readings = newReadings) })
                toDelete = null
                message = "Catatan dihapus"
            },
            onDismiss = { toDelete = null }
        )
    }
}

/** Layar pertama kali: belum ada unit. */
@Composable
fun OnboardingScreen(
    onAddUnit: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("HM Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Catat jam meter alat berat, hitung sisa servis, dan simpan foto ber-watermark.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Langkah 1", fontWeight = FontWeight.Bold)
                Text("Tambah unit / alat (contoh: Excavator 01)")
                Button(onClick = onAddUnit, modifier = Modifier.fillMaxWidth()) {
                    Text("Tambah unit sekarang")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Langkah 2 (setelah unit ada)", fontWeight = FontWeight.Bold)
                Text("1. Tekan \"Foto HM Servis\" → ambil foto meter")
                Text("2. Cek angka HM, simpan sebagai titik awal")
                Text("3. Pembacaan berikutnya pakai \"Foto HM\"")
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text("Sudah punya backup? Restore")
        }
    }
}

@Composable
fun HomeScreen(
    state: AppState,
    unit: UnitMachine,
    onSelectUnit: (String) -> Unit,
    onFoto: () -> Unit,
    onServiceFoto: () -> Unit,
    onGaleri: () -> Unit,
    onManual: () -> Unit,
    onSettings: () -> Unit,
    onDeleteRequest: (Reading) -> Unit
) {
    val summary = summarize(unit)
    val history = unit.sorted().asReversed()
    val hasServicePoint = unit.readings.any { it.isService }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HM Tracker", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onSettings) { Text("Pengaturan") }
            }
        }

        if (state.units.size > 1) {
            item {
                UnitPicker(units = state.units, activeId = unit.id, onSelect = onSelectUnit)
            }
        } else {
            item {
                Text(
                    unit.name + if (unit.siteName.isNotBlank()) " · ${unit.siteName}" else "",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        item { StatusCard(summary, unit.serviceLimit) }

        // Panduan jika belum ada titik servis
        if (!hasServicePoint) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Mulai di sini", fontWeight = FontWeight.Bold)
                        Text("Belum ada titik awal servis. Tekan tombol oranye di bawah, foto meter HM, lalu simpan.")
                    }
                }
            }
        }

        item { Text("Catat HM", style = MaterialTheme.typography.titleMedium) }

        // Tombol utama: kalau belum ada service point, highlight Foto Servis
        if (!hasServicePoint) {
            item {
                Button(
                    onClick = onServiceFoto,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE65100)
                    )
                ) {
                    Text("① Foto HM Servis (titik awal)")
                }
            }
            item {
                OutlinedButton(onClick = onFoto, modifier = Modifier.fillMaxWidth()) {
                    Text("Foto HM (setelah titik awal)")
                }
            }
        } else {
            item {
                Button(onClick = onFoto, modifier = Modifier.fillMaxWidth()) {
                    Text("Foto HM")
                }
            }
            item {
                OutlinedButton(onClick = onServiceFoto, modifier = Modifier.fillMaxWidth()) {
                    Text("Foto HM Servis (reset titik awal)")
                }
            }
        }

        item {
            OutlinedButton(onClick = onGaleri, modifier = Modifier.fillMaxWidth()) {
                Text("Pilih foto dari galeri")
            }
        }
        item {
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Text("Input manual (tanpa foto)")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Riwayat", style = MaterialTheme.typography.titleMedium)
                if (history.isNotEmpty()) {
                    Text(
                        "Tahan untuk hapus",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (history.isEmpty()) {
            item {
                Text(
                    "Belum ada catatan. Mulai dengan Foto HM Servis.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(history, key = { "${it.timeMillis}_${it.hm}" }) { r ->
            HistoryRow(r, onLongClick = { onDeleteRequest(r) })
        }
    }
}

@Composable
fun StatusCard(summary: Summary?, limit: Double) {
    val (bg, fg, bar) = when (summary?.status) {
        Status.GREEN -> Triple(Color(0xFFEAF3DE), Color(0xFF27500A), Color(0xFF639922))
        Status.YELLOW -> Triple(Color(0xFFFAEEDA), Color(0xFF633806), Color(0xFFBA7517))
        Status.RED -> Triple(Color(0xFFFCEBEB), Color(0xFF791F1F), Color(0xFFE24B4A))
        null -> Triple(Color(0xFFE0E0E0), Color(0xFF222222), Color(0xFF888888))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (summary == null) {
                Text("Belum ada data. Foto HM Servis dulu untuk memulai.")
            } else {
                Text("Status: ${summary.status.label}", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("${fmtHm(summary.used)} jam", style = MaterialTheme.typography.headlineMedium)
                    Text("dari ${fmtHm(limit)} jam", style = MaterialTheme.typography.bodySmall)
                }
                Text("pemakaian sejak servis", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { (summary.used / limit).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = bar,
                    trackColor = Color.White
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Metric("HM servis", "${fmtHm(summary.startHm)} jam", Modifier.weight(1f))
                        Metric("HM terakhir", "${fmtHm(summary.lastHm)} jam", Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Metric("Sisa", "${fmtHm(summary.remaining)} jam", Modifier.weight(1f))
                        Metric("Wajib servis di HM", "${fmtHm(summary.limitHm)} jam", Modifier.weight(1f))
                    }
                }
                summary.hoursPerDay?.let {
                    Text("Rata-rata ${fmtHm(it)} jam per hari", style = MaterialTheme.typography.bodySmall)
                }
                summary.estimateMillis?.let {
                    Text("Perkiraan wajib servis ${fmtDate(it)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(r: Reading, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(fmtDateTime(r.timeMillis), style = MaterialTheme.typography.bodySmall)
                val op = if (r.operator.isBlank()) "" else "  ·  ${r.operator}"
                Text("HM ${fmtHm(r.hm)} jam$op", style = MaterialTheme.typography.bodyLarge)
                if (r.latitude != null && r.longitude != null) {
                    Text(
                        "GPS ${fmtCoord(r.latitude, r.longitude)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (r.isService) {
                Text(
                    "SERVIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF0C447C),
                    modifier = Modifier
                        .background(Color(0xFFE6F1FB), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
