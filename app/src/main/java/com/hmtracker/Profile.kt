package com.hmtracker

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Data pengguna yang diisi saat aplikasi dibuka. Dipakai di watermark foto. */
data class Profile(val name: String, val toolType: String)

object ProfileStore {
    private const val PREFS = "hmtracker_profile"

    fun load(context: Context): Profile? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString("name", null) ?: return null
        return Profile(name, prefs.getString("toolType", "") ?: "")
    }

    fun save(context: Context, profile: Profile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", profile.name)
            .putString("toolType", profile.toolType)
            .apply()
    }
}

/** Popup input saat pengguna masuk. Nama wajib diisi; tombol Selesai menutup popup. */
@Composable
fun ProfileDialog(initial: Profile?, onDone: (Profile) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var toolType by remember { mutableStateOf(initial?.toolType ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Selamat datang", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nama pengguna") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = toolType,
                    onValueChange = { toolType = it },
                    label = { Text("Jabatan / jenis alat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = Color(0xFFC62828), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) error = "Nama pengguna wajib diisi."
                else onDone(Profile(name.trim(), toolType.trim()))
            }) { Text("Selesai!!", fontWeight = FontWeight.Bold) }
        }
    )
}
