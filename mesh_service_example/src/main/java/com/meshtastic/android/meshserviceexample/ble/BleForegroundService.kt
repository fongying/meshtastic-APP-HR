package com.meshtastic.android.meshserviceexample.ble

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meshtastic.android.meshserviceexample.R

class BleForegroundService : Service() {

    companion object {
        const val ACTION_START = "ble.service.START"
        const val ACTION_STOP  = "ble.service.STOP"
        private const val CH_ID = "ble_conn"
        private const val NOTI_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, BleForegroundService::class.java).apply { action = ACTION_START }
            context.startForegroundService(i)
        }
        fun stop(context: Context) {
            val i = Intent(context, BleForegroundService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val noti = NotificationCompat.Builder(this, CH_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("BLE connected (foreground)")
                    .setSmallIcon(R.mipmap.ic_launcher)   // ← 用 app icon，避免資源缺失
                    .setOngoing(true)
                    .build()
                startForeground(NOTI_ID, noti)

                // TODO: 如要在背景維持連線/自動重連，可把 HrPlxBleClient 放這裡
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // TODO: 釋放 BLE 資源（client.disconnect()）
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "BLE connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
