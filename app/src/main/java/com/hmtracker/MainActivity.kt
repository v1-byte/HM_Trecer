package com.hmtracker
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** Foto atau input yang sedang ditinjau sebelum disimpan. */
data class Pending(
    val bitmap: Bitmap?,
    val service: Boolean,
    val ocr: OcrResult? = null,
    val ocrDone: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(Storage.load(context)) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var showSettings by remember { mutableStateOf(state.siteName.isBlank()) }
    var message by remember { mutableStateOf<String?>(null) }
    var captureService by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Reading?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val captureFile = remember { File(context.cacheDir, "capture.jpg") }
    val captureUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
    }

    fun commit(newState: AppState) {
        state = newState
        Storage.save(context, newState)
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

    fun startReview(bitmap: Bitmap?, service: Boolean) {
        if (bitmap == null) {
            pending = Pending(null, service, null, true)
            return
        }
        pending = Pending(bitmap, service)
        MeterOcr.read(
            bitmap = bitmap,
            lastHm = state.sorted().lastOrNull()?.hm,
            onResult = { res -> pending = pending?.copy(ocr = res, ocrDone = true) },
            onError = { err ->
                pending = pending?.copy(ocrDone = true)
                message = "Baca otomatis gagal ($err). Isi HM secara manual."
            }
        )
    }

    fun decodeAndReview(source: ImageDecoder.Source, service: Boolean) {
        runCatching { Watermark.decode(source) }
            .onSuccess { startReview(it, service) }
            .onFailure { message = "Foto tidak bisa dibuka: ${it.message}" }
    }

    fun saveReading(p: Pending, hm: Double, operator: String, service: Boolean) {
        val now = System.currentTimeMillis()
        val bmp = p.bitmap
        val photoFile = if (bmp != null) File(File(context.filesDir, "photos"), "$now.jpg") else null

        val draft = Reading(now, hm, operator, photoFile?.absolutePath, service)
        val newState = state.copy(readings = state.readings + draft)
        val summary = summarize(newState) ?: return

        if (bmp != null && photoFile != null) {
            Watermark.save(Watermark.stamp(bmp, watermarkLines(newState, summary, draft)), photoFile)
        }
        commit(newState)
        pending = null
        message = if (service) {
            "Titik awal servis dicatat: ${fmtHm(hm)} jam"
        } else {
            "Tersimpan. Pemakaian ${fmtHm(summary.used)} jam, sisa ${fmtHm(summary.remaining)} jam"
        }
    }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            decodeAndReview(ImageDecoder.createSource(captureFile), captureService)
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            decodeAndReview(ImageDecoder.createSource(context.contentResolver, uri), false)
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
                if (restored.readings.isEmpty() && restored.siteName.isBlank()) {
                    message = "File backup tidak valid atau kosong."
                } else {
                    commit(restored)
                    message = "Restore berhasil. ${restored.readings.size} catatan dimuat."
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (showSettings) {
                SettingsScreen(
                    state = state,
                    onSave = { newState ->
                        commit(newState)
                        showSettings = false
                        message = "Pengaturan disimpan"
                    },
                    onExportCsv = {
                        runCatching {
                            val file = Export.writeCsv(context, state)
                            shareFile(file, "text/csv", "Export HM Tracker")
                        }.onFailure { message = "Export gagal: ${it.message}" }
                    },
                    onBackup = {
                        runCatching {
                            val file = Export.writeBackup(context, state)
                            shareFile(file, "application/json", "Backup HM Tracker")
                        }.onFailure { message = "Backup gagal: ${it.message}" }
                    },
                    onRestore = { restorePicker.launch("application/json") },
                    onBack = {
                        if (state.siteName.isBlank()) {
                            message = "Isi Tempat dulu sebelum kembali."
                        } else {
                            showSettings = false
                        }
                    }
                )
            } else {
                HomeScreen(
                    state = state,
                    onFoto = {
                        captureService = false
                        takePhoto.launch(captureUri)
                    },
                    onServiceFoto = {
                        captureService = true
                        takePhoto.launch(captureUri)
                    },
                    onGaleri = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onManual = { startReview(null, false) },
                    onSettings = { showSettings = true },
                    onDeleteRequest = { toDelete = it }
                )
            }
        }
    }

    pending?.let { p ->
        ReviewDialog(
            pending = p,
            state = state,
            onConfirm = { hm, op, service -> saveReading(p, hm, op, service) },
            onDismiss = { pending = null }
        )
    }

    toDelete?.let { r ->
        DeleteConfirmDialog(
            reading = r,
            onConfirm = {
                val newReadings = state.readings.filterNot {
                    it.timeMillis == r.timeMillis && it.hm == r.hm
                }
                // Hapus file foto jika ada
                r.photoPath?.let { path -> runCatching { File(path).delete() } }
                commit(state.copy(readings = newReadings))
                toDelete = null
                message = "Catatan dihapus"
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
fun HomeScreen(
    state: AppState,
    onFoto: () -> Unit,
    onServiceFoto: () -> Unit,
    onGaleri: () -> Unit,
    onManual: () -> Unit,
    onSettings: () -> Unit,
    onDeleteRequest: (Reading) -> Unit
) {
    val summary = summarize(state)
    val history = state.sorted().asReversed()

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
                Column {
                    Text("HM Tracker", style = MaterialTheme.typography.headlineSmall)
                    if (state.siteName.isNotBlank()) {
                        Text(
                            state.siteName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onSettings) { Text("Pengaturan") }
            }
        }
        item { StatusCard(summary, state.serviceLimit) }
        item { Text("Catat HM", style = MaterialTheme.typography.titleMedium) }
        item {
            Button(onClick = onFoto, modifier = Modifier.fillMaxWidth()) {
                Text("Foto HM")
            }
        }
        item {
            OutlinedButton(onClick = onServiceFoto, modifier = Modifier.fillMaxWidth()) {
                Text("Foto HM Servis (titik awal)")
            }
        }
        item {
            OutlinedButton(onClick = onGaleri, modifier = Modifier.fillMaxWidth()) {
                Text("Pilih foto dari galeri")
            }
        }
        item {
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Text("Input manual")
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
            item { Text("Belum ada catatan.") }
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
                Text("Belum ada data. Tekan \"Foto HM Servis\" untuk memulai.")
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
                    Text(
                        "Rata-rata ${fmtHm(it)} jam per hari",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                summary.estimateMillis?.let {
                    Text(
                        "Perkiraan wajib servis ${fmtDate(it)}",
                        style = MaterialTheme.typography.bodySmall
                    )
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
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
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
