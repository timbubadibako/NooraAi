package com.example.nooraai.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.nooraai.R
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.core.app.ActivityCompat

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val CHANNEL_ID = "prayer_alarm_channel"
        const val NOTIF_ID_BASE = 1000
        private const val TAG = "PrayerAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Waktu Sholat"
        val id = (name.hashCode() and 0xffff)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Prayer alarms", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }

        // On Android 13+ we must have POST_NOTIFICATIONS permission to post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "Missing POST_NOTIFICATIONS permission - skipping notification")
                return
            }
        }

        // Try to use app drawable; fallback to android built-in icon if not found
        val smallIconRes = try {
            // if your drawable exists this will return its id; otherwise 0
            context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
        } catch (t: Throwable) {
            0
        }.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Waktu Sholat: $name")
            .setContentText("Sudah waktunya sholat $name")
            .setSmallIcon(smallIconRes)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // correct method name + constant
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID_BASE + id, notif)
    }
}