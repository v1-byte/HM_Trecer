package com.hmtracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReviewDialog(
    pending: Pending,
    unit: UnitMachine,
    onConfirm: (Double, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val ocr = pending.ocr
    var hmText by remember(ocr) {
        mutableStateOf(ocr?.hmCandidates?.firstOrNull()?.let { fmtHm(it) } ?: "")
    }
    var opText by remember { mutableStateOf("") }
    var service by remember(pending.service) { mutableStateOf(pending.service) }

    val hm = hmText.replace(',', '.').toDoubleOrNull()
    val error = hm?.let { blockingError(it, unit, service) }
    val warning = hm?.let { softWarning(it, unit, service) }
    val canSave = pending.ocrDone && hm != null && error == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (service) "Cek titik awal SERVIS" else "Cek angka HM",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(unit.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF1565C0))

                if (!pending.ocrDone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text("Sedang membaca angka dari foto…\nTunggu sebentar.")
                    }
                }

                pending.bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Foto meter",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                if (pending.ocrDone) {
                    val cands = ocr?.hmCandidates.orEmpty()
                    if (cands.isNotEmpty()) {
                        Text("Angka terdeteksi (ketuk untuk pilih):", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cands.take(4).forEach { c ->
                                OutlinedButton(onClick = { hmText = fmtHm(c) }) {
                                    Text(fmtHm(c), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = hmText,
                        onValueChange = { hmText = it },
                        label = { Text("Angka HM (jam)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = opText,
                        onValueChange = { opText = it },
                        label = { Text("Nama operator (opsional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = service, onCheckedChange = { service = it })
                        Text("Ini titik awal servis")
                    }

                    error?.let {
                        Text(it, color = Color(0xFFC62828), fontWeight = FontWeight.Medium)
                    }
                    warning?.let {
                        Text(it, color = Color(0xFFEF6C00))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { hm?.let { onConfirm(it, opText.trim(), service) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) { Text("SIMPAN", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(reading: Reading, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus catatan ini?") },
        text = {
            Text("HM ${fmtHm(reading.hm)} jam\n${fmtDateTime(reading.timeMillis)}")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) { Text("Hapus") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitPicker(
    units: List<UnitMachine>,
    activeId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val active = units.find { it.id == activeId } ?: units.firstOrNull()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = active?.name ?: "Pilih unit",
            onValueChange = {},
            readOnly = true,
            label = { Text("Unit yang aktif") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u.name, fontWeight = if (u.id == activeId) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onSelect(u.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: AppState,
    onSaveUnit: (UnitMachine) -> Unit,
    onAddUnit: (String, String, Double, Double) -> Unit,
    onDeleteUnit: (String) -> Unit,
    onExportCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit
) {
    val active = state.active()
    var name by remember(active?.id) { mutableStateOf(active?.name ?: "") }
    var site by remember(active?.id) { mutableStateOf(active?.siteName ?: "") }
    val startHm = active?.let { u -> (u.sorted().lastOrNull { it.isService } ?: u.sorted().firstOrNull())?.hm } ?: 0.0
    var target by remember(active?.id) { mutableStateOf(fmtHm(active?.serviceLimit ?: 200.0)) }
    var showAdd by remember { mutableStateOf(state.units.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    var newSite by remember { mutableStateOf("") }
    var newLimit by remember { mutableStateOf("200") }
    var newSmr by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (active != null) {
            Text("Unit aktif", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama unit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = site,
                onValueChange = { site = it },
                label = { Text("Lokasi / tempat kerja") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "SMR: ${fmtHm(startHm)} jam. Next Maintenance = SMR + interval servis.",
                fontSize = 13.sp,
                color = Color(0xFF6F7075)
            )
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Interval servis (jam)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSaveUnit(
                        active.copy(
                            name = name.trim().ifEmpty { active.name },
                            siteName = site.trim(),
                            serviceLimit = target.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0) ?: active.serviceLimit
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) { Text("Simpan perubahan", fontWeight = FontWeight.Bold) }

            Text("Next Maintenance = SMR + interval servis. Foto HM harian menambah pembacaan sampai batas ini.", fontSize = 13.sp, color = Color(0xFF546E7A))
            Text("Kesiapan unit", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(when (summarize(active)?.status) { Status.GREEN -> "🟢 Ready"; Status.YELLOW -> "🟡 Persiapan servis"; Status.RED -> "🔴 STOP — parkir unit, servis berkala"; null -> "Belum siap — isi SMR awal" }, fontWeight = FontWeight.Bold)
            Text("Arti warna status", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("Hijau = aman (sisa lebih dari ${WARNING_WINDOW_HOURS.toInt()} jam).", color = Color(0xFF2E7D32), fontSize = 14.sp)
            Text("Kuning = peringatan jelang servis (sisa ${WARNING_WINDOW_HOURS.toInt()} jam atau kurang).", color = Color(0xFFEF6C00), fontSize = 14.sp)
            Text("Merah = unit stop, jangan bergerak sebelum servis (sisa ${RED_WINDOW_HOURS.toInt()} jam atau kurang).", color = Color(0xFFC62828), fontSize = 14.sp)

            if (state.units.size > 1) {
                OutlinedButton(
                    onClick = { onDeleteUnit(active.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Hapus unit ini", color = Color(0xFFC62828)) }
            }
        }

        HorizontalDivider()

        Text("Unit lain", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) { Text("+ Tambah unit baru", fontWeight = FontWeight.Bold) }

        HorizontalDivider()

        Text("Data & cadangan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        OutlinedButton(
            onClick = onExportCsv,
            enabled = state.units.any { it.readings.isNotEmpty() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export laporan CSV") }
        OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
            Text("Backup data (JSON)")
        }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text("Restore dari backup")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Kembali ke beranda", fontSize = 16.sp)
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (state.units.isNotEmpty()) showAdd = false },
            title = { Text("Tambah unit / alat", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Isi sekali saja. Data tersimpan dan dapat diedit kapan pun.")
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("ID / nama unit (wajib)") },
                        placeholder = { Text("Contoh: Excavator 01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSite,
                        onValueChange = { newSite = it },
                        label = { Text("Lokasi (opsional)") },
                        placeholder = { Text("Contoh: Site A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSmr,
                        onValueChange = { newSmr = it },
                        label = { Text("HM/KM awal — SMR (wajib)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Unggah/ambil foto meter untuk membantu pencatatan dan verifikasi pembacaan. Pastikan angka awal benar.", fontSize = 12.sp, color = Color(0xFF546E7A))
                    OutlinedTextField(
                        value = newLimit,
                        onValueChange = { newLimit = it },
                        label = { Text("Interval servis (jam HM / KM)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newName.trim().isNotEmpty() && newSmr.replace(',', '.').toDoubleOrNull() != null && (newLimit.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0,
                    onClick = {
                        onAddUnit(
                            newName.trim(),
                            newSite.trim(),
                            newSmr.replace(',', '.').toDoubleOrNull() ?: 0.0,
                            newLimit.replace(',', '.').toDoubleOrNull() ?: 200.0
                        )
                        showAdd = false
                        newName = ""
                        newSite = ""
                        newLimit = "200"
                        newSmr = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("TAMBAH", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                if (state.units.isNotEmpty()) {
                    TextButton(onClick = { showAdd = false }) { Text("Batal") }
                }
            }
        )
    }
}

/** Step number circle */
@Composable
fun StepBadge(number: String, color: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun UpdateDialog(
    update: AppUpdate,
    progress: Int?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("Update tersedia", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HM Tracker versi ${update.version} sudah tersedia.")
                Text("Perubahan terbaru:", fontWeight = FontWeight.SemiBold)
                Text(update.notes.take(600).ifBlank { "Peningkatan stabilitas dan fitur aplikasi." }, fontSize = 13.sp)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Mengunduh APK… $progress%", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, enabled = progress == null) {
                Text(if (progress == null) "Update sekarang" else "Mengunduh…")
            }
        },
        dismissButton = {
            if (progress == null) TextButton(onClick = onDismiss) { Text("Nanti") }
        }
    )
}
