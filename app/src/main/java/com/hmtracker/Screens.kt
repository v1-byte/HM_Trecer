package com.hmtracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
        title = { Text(if (service) "Cek titik awal servis" else "Cek hasil baca HM") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(unit.name, style = MaterialTheme.typography.labelLarge)
                if (!pending.ocrDone) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Membaca angka dari foto…")
                    }
                }
                pending.bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                if (pending.ocrDone) {
                    val cands = ocr?.hmCandidates.orEmpty()
                    if (cands.size > 1) {
                        Text("Kandidat HM dari foto (ketuk untuk pilih):")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            cands.forEach { c ->
                                TextButton(onClick = { hmText = fmtHm(c) }) { Text(fmtHm(c)) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = hmText,
                        onValueChange = { hmText = it },
                        label = { Text("HM meter (jam)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = opText,
                        onValueChange = { opText = it },
                        label = { Text("Operator (boleh kosong)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = service, onCheckedChange = { service = it })
                        Text("Ini titik awal servis")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    warning?.let { Text(it, color = Color(0xFFEF6C00)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { hm?.let { onConfirm(it, opText.trim(), service) } }) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun DeleteConfirmDialog(reading: Reading, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus catatan?") },
        text = {
            Text(
                "HM ${fmtHm(reading.hm)} jam · ${fmtDateTime(reading.timeMillis)}" +
                    if (reading.isService) " (titik servis)" else ""
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Hapus", color = MaterialTheme.colorScheme.error)
            }
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
            label = { Text("Unit / Alat") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u.name) },
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
    onAddUnit: (String, String, Double) -> Unit,
    onDeleteUnit: (String) -> Unit,
    onExportCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit
) {
    val active = state.active()
    var name by remember(active?.id) { mutableStateOf(active?.name ?: "") }
    var site by remember(active?.id) { mutableStateOf(active?.siteName ?: "") }
    var limit by remember(active?.id) { mutableStateOf(fmtHm(active?.serviceLimit ?: 200.0)) }
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newSite by remember { mutableStateOf("") }
    var newLimit by remember { mutableStateOf("200") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)

        if (active != null) {
            Text("Unit aktif", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama unit / alat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = site,
                onValueChange = { site = it },
                label = { Text("Tempat / lokasi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it },
                label = { Text("Batas servis (jam)") },
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
                            serviceLimit = limit.replace(',', '.').toDoubleOrNull() ?: active.serviceLimit
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan unit ini") }

            if (state.units.size > 1) {
                OutlinedButton(
                    onClick = { onDeleteUnit(active.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Hapus unit ini", color = MaterialTheme.colorScheme.error) }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Kelola unit", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Tambah unit / alat")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Export CSV untuk laporan. Backup JSON untuk cadangan. Restore menimpa semua data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onExportCsv,
            enabled = state.units.any { it.readings.isNotEmpty() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export CSV") }
        OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("Backup (JSON)") }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Restore dari file…") }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Kembali") }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Tambah unit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama unit (contoh: Excavator 01)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSite,
                        onValueChange = { newSite = it },
                        label = { Text("Tempat / lokasi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newLimit,
                        onValueChange = { newLimit = it },
                        label = { Text("Batas servis (jam)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.trim().isNotEmpty(),
                    onClick = {
                        onAddUnit(
                            newName.trim(),
                            newSite.trim(),
                            newLimit.replace(',', '.').toDoubleOrNull() ?: 200.0
                        )
                        showAdd = false
                        newName = ""
                        newSite = ""
                        newLimit = "200"
                    }
                ) { Text("Tambah") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Batal") } }
        )
    }
}
