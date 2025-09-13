package com.meshtastic.android.meshserviceexample.ble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.meshtastic.android.meshserviceexample.R;

/**
 * A simple foreground service for handling long-running BLE operations.
 * Note: This is a basic implementation and is not currently used by MainActivity,
 * but it is provided for project completeness.
 */
public class BleForegroundService extends Service {
    private static final String TAG = "BleForegroundService";
    private static final String CHANNEL_ID = "BleForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Meshtastic HR Bridge")
                .setContentText("BLE service is running in the background.")
                .setSmallIcon(R.mipmap.ic_launcher) // Make sure you have this icon
                .build();
        startForeground(NOTIFICATION_ID, notification);

        // Perform background tasks here.
        // For this example, we just start the service and keep it running.

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "BLE Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
