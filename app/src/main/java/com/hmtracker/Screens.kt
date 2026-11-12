package com.hmtracker

import android.graphics.Bitmap
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

/** Dialog untuk mengecek dan mengedit hasil baca sebelum data disimpan. */
@Composable
fun ReviewDialog(
    pending: Pending,
    state: AppState,
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
    val error = hm?.let { blockingError(it, state, service) }
    val warning = hm?.let { softWarning(it, state, service) }
    val canSave = pending.ocrDone && hm != null && error == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (service) "Cek titik awal servis" else "Cek hasil baca HM") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
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
            TextButton(
                enabled = canSave,
                onClick = { hm?.let { onConfirm(it, opText.trim(), service) } }
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/** Dialog konfirmasi hapus catatan. */
@Composable
fun DeleteConfirmDialog(
    reading: Reading,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

/** Layar pengaturan + export / backup. */
@Composable
fun SettingsScreen(
    state: AppState,
    onSave: (AppState) -> Unit,
    onExportCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit
) {
    var site by remember { mutableStateOf(state.siteName) }
    var limit by remember { mutableStateOf(fmtHm(state.serviceLimit)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = site,
            onValueChange = { site = it },
            label = { Text("Tempat (tampil di watermark)") },
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
                onSave(
                    state.copy(
                        siteName = site.trim().ifEmpty { "-" },
                        serviceLimit = limit.replace(',', '.').toDoubleOrNull() ?: state.serviceLimit
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Simpan pengaturan") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Data", style = MaterialTheme.typography.titleMedium)
        Text(
            "Export CSV untuk laporan. Backup JSON untuk cadangan. Restore menimpa data saat ini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = onExportCsv,
            enabled = state.readings.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export CSV") }

        OutlinedButton(
            onClick = onBackup,
            enabled = state.readings.isNotEmpty() || state.siteName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Backup (JSON)") }

        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Restore dari file…") }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Kembali") }
    }
}
