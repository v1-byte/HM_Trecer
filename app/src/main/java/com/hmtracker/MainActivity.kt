package com.hmtracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
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
        setContent { MaterialTheme { App() } }
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
    var profile by remember { mutableStateOf(ProfileStore.load(context)) }
    var showProfile by remember { mutableStateOf(true) }

    val captureFile = remember { File(context.cacheDir, "capture.jpg") }
    val liveCam = remember { LiveCameraState() }
    var torch by remember { mutableStateOf(false) }
    var hd by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var showSettingsDialog by remember { mutableStateOf(false) }

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

    var cameraGranted by remember { mutableStateOf(hasCameraPermission()) }

    fun saveReading(p: Pending, hm: Double, operator: String, service: Boolean) {
        val unit = state.active() ?: return
        val now = System.currentTimeMillis()
        val bmp = p.bitmap
        val photoFile = if (bmp != null) {
            File(File(context.filesDir, "photos/${unit.id}"), "$now.jpg")
        } else null

        val draft = Reading(
            timeMillis = now, hm = hm, operator = operator,
            photoPath = photoFile?.absolutePath, isService = service,
            latitude = p.gps?.latitude, longitude = p.gps?.longitude
        )
        val newUnit = unit.copy(readings = unit.readings + draft)
        val newState = state.copy(units = state.units.map { if (it.id == unit.id) newUnit else it })
        val summary = summarize(newUnit) ?: return

        if (bmp != null && photoFile != null) {
            Watermark.save(Watermark.stamp(bmp, watermarkLines(newUnit, summary, draft, profile)), photoFile)
        }
        commit(newState)
        pending = null
        message = if (service) "Titik awal servis tersimpan otomatis: ${fmtHm(hm)} jam"
        else "Tersimpan otomatis. Sisa ${fmtHm(summary.remaining)} jam"
    }

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
            onResult = { res ->
                val updated = pending?.copy(ocr = res, ocrDone = true)
                pending = updated
                if (res.hmCandidates.isEmpty()) {
                    message = "Angka HM belum terbaca. Foto ulang dengan meter lebih dekat."
                }
            },
            onError = { err ->
                pending = pending?.copy(ocrDone = true)
                message = "Baca otomatis gagal. Foto ulang dengan meter lebih jelas."
            }
        )
    }

    fun decodeAndReview(source: ImageDecoder.Source, service: Boolean) {
        val gps = LocationHelper.lastKnown(context)
        runCatching { Watermark.decode(source) }
            .onSuccess { startReview(it, service, gps) }
            .onFailure { message = "Foto tidak bisa dibuka." }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) pendingCameraAction?.invoke()
        else {
            val act = context as? Activity
            // Ditolak permanen (pilih "Jangan tanya lagi"): arahkan ke pengaturan aplikasi
            val permanent = act != null && !ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.CAMERA)
            if (permanent) showSettingsDialog = true
            else message = "Izin kamera ditolak. Aktifkan lagi lewat tombol Pengaturan HP."
        }
        pendingCameraAction = null
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { cameraGranted = hasCameraPermission() }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        multiPermissionLauncher.launch(perms.toTypedArray())
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) decodeAndReview(ImageDecoder.createSource(context.contentResolver, uri), false)
    }

    fun launchCamera(service: Boolean) {
        captureService = service
        val action = {
            captureLivePhoto(
                context, liveCam, captureFile,
                onSaved = { decodeAndReview(ImageDecoder.createSource(captureFile), service) },
                onFailed = { message = it }
            )
        }
        if (hasCameraPermission()) action()
        else {
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
                val restored = parseState(stream.bufferedReader().readText())
                if (restored.units.isEmpty()) message = "File backup kosong / tidak valid."
                else {
                    commit(restored)
                    message = "Restore OK: ${restored.units.size} unit"
                    showSettings = false
                }
            }
        }.onFailure { message = "Restore gagal." }
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
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
                            val units = if (state.units.any { it.id == updated.id })
                                state.units.map { if (it.id == updated.id) updated else it }
                            else state.units + updated
                            commit(state.copy(units = units, activeUnitId = updated.id))
                            message = "Tersimpan"
                        },
                        onAddUnit = { name, site, limit ->
                            val u = UnitMachine(UUID.randomUUID().toString(), name, site, limit)
                            commit(state.copy(units = state.units + u, activeUnitId = u.id))
                            message = "Unit \"$name\" siap dipakai"
                            showSettings = false
                        },
                        onDeleteUnit = { id ->
                            val left = state.units.filter { it.id != id }
                            commit(state.copy(units = left, activeUnitId = left.firstOrNull()?.id))
                            message = "Unit dihapus"
                        },
                        onExportCsv = {
                            runCatching {
                                shareFile(Export.writeCsv(context, state), "text/csv", "Export HM")
                            }
                        },
                        onBackup = {
                            runCatching {
                                shareFile(Export.writeBackup(context, state), "application/json", "Backup HM")
                            }
                        },
                        onRestore = { restorePicker.launch("application/json") },
                        onBack = {
                            if (state.units.isEmpty()) message = "Tambah unit dulu."
                            else showSettings = false
                        }
                    )
                }
                else -> {
                    val unit = state.active()
                    if (unit == null) showSettings = true
                    else HomeScreen(
                        state = state, unit = unit,
                        onSelectUnit = { commit(state.copy(activeUnitId = it)) },
                        onFoto = { launchCamera(false) },
                        onServiceFoto = { launchCamera(true) },
                        onGaleri = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onManual = { startReview(null, false, LocationHelper.lastKnown(context)) },
                        onSettings = { showSettings = true },
                        onDeleteRequest = { toDelete = it },
                        cameraOn = cameraGranted,
                        liveCam = liveCam,
                        torch = torch,
                        onTorch = { torch = it },
                        hd = hd,
                        onHd = { hd = it },
                        zoom = zoom,
                        onZoom = { zoom = it }
                    )
                }
            }
        }
    }

    pending?.let { p ->
        val unit = state.active() ?: return@let
        ReviewDialog(p, unit, { hm, op, svc -> saveReading(p, hm, op, svc) }, { pending = null })
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Izin kamera diperlukan", fontWeight = FontWeight.Bold) },
            text = { Text("Izin kamera ditolak permanen. Buka pengaturan aplikasi untuk mengaktifkannya.") },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text("Buka pengaturan") }
            },
            dismissButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Nanti") } }
        )
    }

    if (showProfile) {
        ProfileDialog(initial = profile) { p ->
            ProfileStore.save(context, p)
            profile = p
            showProfile = false
        }
    }

    toDelete?.let { r ->
        DeleteConfirmDialog(r,
            onConfirm = {
                val unit = state.active() ?: return@DeleteConfirmDialog
                val nr = unit.readings.filterNot { it.timeMillis == r.timeMillis && it.hm == r.hm }
                r.photoPath?.let { runCatching { File(it).delete() } }
                commit(state.withActive { it.copy(readings = nr) })
                toDelete = null
                message = "Catatan dihapus"
            },
            onDismiss = { toDelete = null }
        )
    }
}

/* ========== ONBOARDING ========== */
@Composable
fun OnboardingScreen(onAddUnit: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "HM TRACKER",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF1A237E)
        )
        Text(
            "Catat jam meter alat berat",
            fontSize = 16.sp,
            color = Color(0xFF546E7A)
        )
        Spacer(Modifier.height(32.dp))

        // Step 1
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                StepBadge("1", Color(0xFF2E7D32))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tambah unit / alat", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Contoh: Excavator 01, Dozer 02, Dump Truck 03",
                        color = Color(0xFF607D8B),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onAddUnit,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("MULAI — TAMBAH UNIT", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Step 2 preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                StepBadge("2", Color(0xFFF57C00))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Foto meter HM (titik awal)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Setelah unit ada, tekan tombol oranye lalu foto angka di meter jam.",
                        color = Color(0xFF5D4037),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                StepBadge("3", Color(0xFF1565C0))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Catat rutin setiap hari", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Tekan \"Foto HM\" untuk pembacaan berikutnya. Status servis otomatis terhitung.",
                        color = Color(0xFF37474F),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Sudah punya backup? Restore di sini")
        }
    }
}

/* ========== HOME ========== */
@Composable
fun LegacyHomeScreen(
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
    val hasService = unit.readings.any { it.isService }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FB))
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(10.dp)) }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("HM Tracker", fontWeight = FontWeight.Black, fontSize = 25.sp, color = Color(0xFF20203A))
                    Text("Bukti kerja dan servis alat", fontSize = 12.sp, color = Color(0xFF77778B))
                }
                TextButton(onClick = onSettings) {
                    Text("Pengaturan", fontSize = 14.sp, color = Color(0xFF6549A8))
                }
            }
        }

        if (state.units.size > 1) {
            item { UnitPicker(state.units, unit.id, onSelectUnit) }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEAF8)),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("UNIT AKTIF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF766D92))
                            Text(unit.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF26243D))
                            if (unit.siteName.isNotBlank()) Text(unit.siteName, fontSize = 12.sp, color = Color(0xFF77778B))
                        }
                        Text("GPS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF6549A8), modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Text("Ambil foto meter dengan waktu dan lokasi otomatis", fontSize = 13.sp, color = Color(0xFF5D5871))
                }
            }
        }

        item { StatusCard(summary, unit.serviceLimit) }

        item {
            if (!hasService) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StepBadge("!", Color(0xFFE65100))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Belum ada titik awal servis",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            "Foto dulu angka di meter jam alat, lalu simpan sebagai titik awal.",
                            fontSize = 13.sp,
                            color = Color(0xFF5D4037)
                        )
                        Button(
                            onClick = onServiceFoto,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6549A8)),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            Text("Foto HM servis pertama", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onFoto,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6549A8)),
                    shape = RoundedCornerShape(29.dp)
                ) {
                    Text("Ambil foto HM sekarang", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }

        item {
            Text("Tambahkan bukti kerja", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF20203A))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onGaleri,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6549A8))
                ) { Text("Dari galeri", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = onManual,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6549A8))
                ) { Text("Input manual", fontSize = 13.sp) }
            }
        }
        if (hasService) {
            item {
                OutlinedButton(
                    onClick = onServiceFoto,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Reset titik awal servis (foto baru)", fontSize = 13.sp) }
            }
        }

        // History
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bukti kerja terbaru", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF20203A))
                if (history.isNotEmpty()) {
                    Text("Tahan untuk hapus", fontSize = 11.sp, color = Color(0xFF9999AA))
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    "Belum ada catatan.",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(history, key = { "${it.timeMillis}_${it.hm}" }) { r ->
            HistoryRow(r) { onDeleteRequest(r) }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/* ========== CAMERA-FIRST HOME ========== */
@OptIn(ExperimentalFoundationApi::class)
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
    onDeleteRequest: (Reading) -> Unit,
    cameraOn: Boolean,
    liveCam: LiveCameraState,
    torch: Boolean,
    onTorch: (Boolean) -> Unit,
    hd: Boolean,
    onHd: (Boolean) -> Unit,
    zoom: Float,
    onZoom: (Float) -> Unit
) {
    val summary = summarize(unit)
    val history = unit.sorted().asReversed()
    val last = history.firstOrNull()
    val statusColor = when (summary?.status) {
        Status.RED -> Color(0xFFFFB4AB)
        Status.YELLOW -> Color(0xFFFFDFA1)
        else -> Color(0xFFB9F6C8)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFFDFBFF))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("☰", fontSize = 27.sp, color = Color(0xFF202124))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(unit.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF202124))
                Text(unit.siteName.ifBlank { "HM Tracker" }, fontSize = 11.sp, color = Color(0xFF6F7075))
            }
            TextButton(onClick = onSettings) { Text("⚙", fontSize = 23.sp, color = Color(0xFF202124)) }
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF303238))
        ) {
            if (cameraOn) {
                LiveCameraPreview(state = liveCam, hd = hd, torch = torch, zoom = zoom)
            } else {
                Text("Izin kamera belum diberikan", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onFoto) { Text("Deteksi HM", color = Color.White, fontSize = 13.sp) }
                    TextButton(onClick = onSettings) { Text("⚙ Pengaturan", color = Color.White, fontSize = 13.sp) }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onTorch(!torch) }) { Text(if (torch) "Flash on" else "Flash off", color = Color.White, fontSize = 13.sp) }
                    TextButton(onClick = { onHd(!hd) }) { Text(if (hd) "HD on" else "HD off", color = Color.White, fontSize = 13.sp) }
                    TextButton(onClick = { onZoom((zoom + 0.5f).coerceAtMost(liveCam.zoomMax)) }) { Text("Perbesar", color = Color.White, fontSize = 13.sp) }
                    TextButton(onClick = { onZoom((zoom - 0.5f).coerceAtLeast(liveCam.zoomMin)) }) { Text("Perkecil", color = Color.White, fontSize = 13.sp) }
                    Text("${"%.1f".format(zoom)}x", color = Color.White, fontSize = 13.sp)
                }

                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PREVIEW FOTO", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(fmtTime(System.currentTimeMillis()), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light)
                    Text(fmtDate(System.currentTimeMillis()), color = Color.White, fontSize = 16.sp)
                    Text("HM ${summary?.let { fmtHm(it.lastHm) } ?: "—"} jam  •  ${unit.siteName.ifBlank { "Lokasi belum diisi" }}", color = Color.White, fontSize = 13.sp)
                    Text("GPS aktif  •  watermark otomatis  •  ${summary?.status?.label ?: "Siap mencatat"}", color = statusColor, fontSize = 12.sp)
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▣", color = Color.White, fontSize = 25.sp)
                        Text("Preview", color = Color.White, fontSize = 10.sp)
                    }
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(Color.White).combinedClickable(onClick = onFoto, onLongClick = onServiceFoto),
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(62.dp).clip(CircleShape).background(Color.White).background(Color.Transparent)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▤", color = Color.White, fontSize = 25.sp)
                        Text("Files", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 54.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("VIDEO", color = Color(0xFF8B8C91), fontSize = 13.sp)
            Text("PHOTO", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF202124))
            Text("ATTENDANCE", color = Color(0xFF8B8C91), fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onGaleri, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp)) { Text("Files / Galeri") }
            OutlinedButton(onClick = onServiceFoto, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp)) { Text("Foto Servis") }
        }

        Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EE)), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(0.dp)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HM & SERVIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF367345))
                    Text(summary?.let { "SMR ${fmtHm(it.startHm)}  •  terpakai ${fmtHm(it.used)} jam  •  sisa ${fmtHm(it.remaining)} jam" } ?: "Foto HM servis untuk mulai menghitung", fontSize = 13.sp, color = Color(0xFF245E33))
                }
                Text(summary?.status?.label ?: "Belum ada data", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF367345))
            }
        }
    }
}

@Composable
fun StatusCard(summary: Summary?, limit: Double) {
    val (bg, fg, bar, label) = when (summary?.status) {
        Status.GREEN -> listOf(Color(0xFFE8F5E9), Color(0xFF1B5E20), Color(0xFF43A047), "LANCAR")
        Status.YELLOW -> listOf(Color(0xFFFFF8E1), Color(0xFFE65100), Color(0xFFFB8C00), "PERINGATAN")
        Status.RED -> listOf(Color(0xFFFFEBEE), Color(0xFFB71C1C), Color(0xFFE53935), "WAJIB SERVIS")
        null -> listOf(Color(0xFFECEFF1), Color(0xFF37474F), Color(0xFF90A4AE), "BELUM ADA DATA")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg as Color),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("STATUS SERVIS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = (fg as Color).copy(alpha = 0.75f))
                Text(label as String, fontWeight = FontWeight.Black, fontSize = 13.sp, color = fg as Color, modifier = Modifier.background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
            if (summary == null) {
                Text("Foto HM Servis dulu untuk mulai hitung.", color = fg as Color, fontSize = 13.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text("Pemakaian", fontSize = 12.sp, color = (fg as Color).copy(alpha = 0.7f))
                        Text(
                            "${fmtHm(summary.used)} jam",
                            fontWeight = FontWeight.Black,
                            fontSize = 30.sp,
                            color = fg as Color
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Sisa", fontSize = 12.sp, color = (fg as Color).copy(alpha = 0.7f))
                        Text(
                            "${fmtHm(summary.remaining)} jam",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = fg as Color
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { (summary.used / limit).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = bar as Color,
                    trackColor = Color.White
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("HM ${fmtHm(summary.startHm)} → ${fmtHm(summary.lastHm)}", fontSize = 12.sp, color = (fg as Color).copy(alpha = 0.8f))
                    Text("Batas ${fmtHm(limit)} jam", fontSize = 12.sp, color = (fg as Color).copy(alpha = 0.8f))
                }
                summary.hoursPerDay?.let { Text("Rata-rata ${fmtHm(it)} jam per hari", fontSize = 12.sp, color = (fg as Color).copy(alpha = 0.8f)) }
                summary.estimateMillis?.let {
                    Text("Perkiraan servis: ${fmtDate(it)}", fontSize = 12.sp, color = (fg as Color).copy(alpha = 0.8f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(r: Reading, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(fmtDateTime(r.timeMillis), fontSize = 12.sp, color = Color(0xFF90A4AE))
                Text(
                    "HM ${fmtHm(r.hm)} jam",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (r.operator.isNotBlank()) {
                    Text(r.operator, fontSize = 12.sp, color = Color(0xFF546E7A))
                }
                if (r.latitude != null && r.longitude != null) {
                    Text("GPS ${fmtCoord(r.latitude, r.longitude)}", fontSize = 11.sp, color = Color(0xFFB0BEC5))
                }
            }
            if (r.isService) {
                Text(
                    "SERVIS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF0D47A1),
                    modifier = Modifier
                        .background(Color(0xFFE3F2FD), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
