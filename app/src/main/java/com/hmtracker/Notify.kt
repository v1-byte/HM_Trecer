package com.hmtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notify {
    private const val CHANNEL_ID = "hm_service"
    private const val CHANNEL_NAME = "Peringatan servis"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Pemberitahuan saat unit mendekati atau melewati batas servis"
        }
        nm.createNotificationChannel(ch)
    }

    fun checkAndNotify(context: Context, state: AppState) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        state.units.forEachIndexed { index, unit ->
            val summary = summarize(unit) ?: return@forEachIndexed
            when (summary.status) {
                Status.YELLOW -> show(
                    context,
                    id = 1000 + index,
                    title = "Peringatan: ${unit.name}",
                    body = "Sisa ${fmtHm(summary.remaining)} jam. Segera rencanakan servis."
                )
                Status.RED -> show(
                    context,
                    id = 2000 + index,
                    title = "Wajib servis: ${unit.name}",
                    body = "Sisa ${fmtHm(summary.remaining)} jam. Unit harus berhenti dan diservis sekarang."
                )
                Status.GREEN -> {
                    // Batalkan notifikasi lama jika status sudah hijau lagi
                    NotificationManagerCompat.from(context).cancel(1000 + index)
                    NotificationManagerCompat.from(context).cancel(2000 + index)
                }
            }
        }
    }

    private fun show(context: Context, id: Int, title: String, body: String) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notif)
        }
    }
}
