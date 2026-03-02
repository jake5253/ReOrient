package com.github.jake5253.reorient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class RotationService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private OrientationEventListener orientationListener;
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    private static final String CHANNEL_ID = "RotationServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int HYSTERESIS = 10; // 10 degrees threshold to prevent flickering
    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new View(this);

        params = new WindowManager.LayoutParams(
                0, 0,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            Log.e("RotationService", "Error adding view: " + e.getMessage());
            stopSelf();
            return;
        }

        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;

                int newOrientation = currentOrientation;

                if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    newOrientation = calculateMappedOrientation(orientation);
                } else if (shouldLeaveCurrentOrientation(orientation, currentOrientation)) {
                    newOrientation = calculateMappedOrientation(orientation);
                }

                if (newOrientation != currentOrientation) {
                    currentOrientation = newOrientation;
                    params.screenOrientation = currentOrientation;
                    try {
                        windowManager.updateViewLayout(overlayView, params);
                    } catch (Exception e) {
                        Log.e("RotationService", "Error updating view: " + e.getMessage());
                    }
                }
            }
        };

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        }
    }

    /**
     * Maps hardware sensor orientation to the desired screen orientation,
     * accounting for the 180-degree inversion on Essential PH-1.
     */
    private int calculateMappedOrientation(int orientation) {
        // 0 degrees (Up) -> We force Reverse Portrait to correct the flip
        if (orientation >= 315 || orientation < 45) {
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        } else if (orientation < 135) { // No need for >= 45
            return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else if (orientation < 225) { // No need for >= 135
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else { // No need for any check here, it's the only range left (225-314)
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        }
    }

    /**
     * Checks if the current orientation should be changed, applying a hysteresis threshold.
     */
    private boolean shouldLeaveCurrentOrientation(int orientation, int current) {
        switch (current) {
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT: // Physical UP
                // Stay if between 305 and 55. Leave if > 55 AND < 305.
                return orientation > (45 + HYSTERESIS) && orientation < (315 - HYSTERESIS);

            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE: // Physical RIGHT
                // Stay if between 35 and 145. Leave if < 35 OR > 145.
                return orientation < (45 - HYSTERESIS) || orientation > (135 + HYSTERESIS);

            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT: // Physical DOWN
                // Stay if between 125 and 235. Leave if < 125 OR > 235.
                return orientation < (135 - HYSTERESIS) || orientation > (225 + HYSTERESIS);

            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE: // Physical LEFT
                // Stay if between 215 and 325. Leave if < 215 OR > 325.
                return orientation < (225 - HYSTERESIS) || orientation > (315 + HYSTERESIS);

            default:
                return true;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Orientation Fixer Service",
                    NotificationManager.IMPORTANCE_MIN
            );
            serviceChannel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Orientation Fixer Active")
                .setContentText("Listening for rotation changes...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (orientationListener != null) {
            orientationListener.disable();
        }
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e("RotationService", "Error removing view: " + e.getMessage());
            }
        }
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}