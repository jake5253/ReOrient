package com.github.jake5253.rotationrepair;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * RotationService runs as a foreground service to globally override screen rotation.
 * It uses a transparent system overlay to influence the orientation of the screen
 * based on the device's physical orientation, correcting for inverted hardware sensors.
 */
public class RotationService extends Service {
    private static final String TAG = "RotationService";

    // Notification & Intent Constants
    private static final String CHANNEL_ID = "RotationServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_STOP_SERVICE = "com.github.jake5253.rotationrepair.action.STOP_SERVICE";

    // Preference Constants
    private static final String PREFS_NAME = "RotationRepairPrefs";
    private static final String PREF_SERVICE_ENABLED = "isServiceEnabled";
    private static final String PREF_HYSTERESIS_DEGREES = "hysteresisDegrees";
    private static final String PREF_INVERT_PORTRAIT = "invertPortrait";
    private static final String PREF_INVERT_LANDSCAPE = "invertLandscape";
    private static final String PREF_PREVENT_UPSIDE_DOWN = "preventUpsideDownPortrait";
    private static final int DEFAULT_HYSTERESIS_DEGREES = 10;

    // Window Management
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    // State & Listeners
    private OrientationEventListener orientationListener;
    private int currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private boolean isSystemAutoRotateEnabled = true;
    private ContentObserver autoRotateObserver;

    // Settings
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private int hysteresisDegrees = DEFAULT_HYSTERESIS_DEGREES;
    private boolean invertPortrait = true;
    private boolean invertLandscape = true;
    private boolean preventUpsideDownPortrait = false;

    // Global state for Activity UI
    public static volatile boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;

        // 1. Establish Foreground Status immediately for stability
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification());

        // 2. Initialize Overlay components
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new View(this);

        params = new WindowManager.LayoutParams(
                0, 0,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            Log.e(TAG, "Critical error adding overlay view: " + e.getMessage());
            stopSelf();
            return;
        }

        // 3. Load user preferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadPreferences();
        registerPreferenceObserver();

        // 4. Handle System Auto-Rotate state
        isSystemAutoRotateEnabled = isAutoRotateEnabled();
        registerAutoRotateObserver();

        // 5. Setup Sensor Listener
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;

                // Optimization: If system auto-rotate is disabled,
                // we don't want to fight the user's manual lock.
                if (!isSystemAutoRotateEnabled) {
                    if (currentOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                        applyOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                    return;
                }

                int mappedOrientation = calculateMappedOrientation(orientation);

                // Use hysteresis logic to prevent "flickering" near the 45-degree boundaries
                if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ||
                        shouldLeaveCurrentOrientation(orientation, currentOrientation)) {
                    applyOrientation(mappedOrientation);
                }
            }
        };

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();
        } else {
            Log.e(TAG, "Hardware does not support orientation detection.");
            stopSelf();
        }
    }

    private int calculateMappedOrientation(int orientation) {
        int mapped = getMapped(orientation);

        // Apply "Prevent Upside Down" logic if enabled
        if (preventUpsideDownPortrait) {
            int upsideDown = invertPortrait ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            int normalPortrait = invertPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            if (mapped == upsideDown) {
                return normalPortrait;
            }
        }

        return mapped;
    }

    private int getMapped(int orientation) {
        int mapped;
        // Standard Mapping (with inversions applied)
        if (orientation >= 315 || orientation < 45) {
            mapped = invertPortrait ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if (orientation < 135) {
            mapped = invertLandscape ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        } else if (orientation < 225) {
            mapped = invertPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        } else {
            mapped = invertLandscape ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        return mapped;
    }

    private boolean shouldLeaveCurrentOrientation(int orientation, int current) {
        // Defines the "dead zone" (hysteresis) around the current orientation
        // to prevent accidental rotations when the phone is slightly tilted.
        switch (current) {
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT: // Top
                return orientation > (45 + hysteresisDegrees) && orientation < (315 - hysteresisDegrees);
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE: // Right
                return orientation < (45 - hysteresisDegrees) || orientation > (135 + hysteresisDegrees);
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT: // Bottom
                return orientation < (135 - hysteresisDegrees) || orientation > (225 + hysteresisDegrees);
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE: // Left
                return orientation < (225 - hysteresisDegrees) || orientation > (315 + hysteresisDegrees);
            default:
                return true;
        }
    }

    private void applyOrientation(int newOrientation) {
        if (newOrientation == currentOrientation) return;

        currentOrientation = newOrientation;
        params.screenOrientation = currentOrientation;
        try {
            windowManager.updateViewLayout(overlayView, params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update layout: " + e.getMessage());
        }
    }

    private void loadPreferences() {
        hysteresisDegrees = prefs.getInt(PREF_HYSTERESIS_DEGREES, DEFAULT_HYSTERESIS_DEGREES);
        invertPortrait = prefs.getBoolean(PREF_INVERT_PORTRAIT, true);
        invertLandscape = prefs.getBoolean(PREF_INVERT_LANDSCAPE, true);
        preventUpsideDownPortrait = prefs.getBoolean(PREF_PREVENT_UPSIDE_DOWN, false);
    }

    private void registerPreferenceObserver() {
        prefListener = (sharedPreferences, key) -> loadPreferences();
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    private boolean isAutoRotateEnabled() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
    }

    private void registerAutoRotateObserver() {
        autoRotateObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                isSystemAutoRotateEnabled = isAutoRotateEnabled();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false, autoRotateObserver);
    }

    private void createNotificationChannel() {
        // Using IMPORTANCE_LOW to keep the notification subtle on older devices like PH-1
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rotation Repair Service",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Maintains global orientation correction.");
        channel.setShowBadge(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        Intent stopIntent = new Intent(this, RotationService.class).setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Rotation Repair Active")
                .setContentText("Correction is running in the background")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent);

        // Clicking the notification opens the main UI
        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(activityPendingIntent);

        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            // User clicked 'Stop' from the notification
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_SERVICE_ENABLED, false)
                    .apply();
            stopSelf();
            return START_NOT_STICKY;
        }

        // START_STICKY ensures the OS attempts to restart us if killed for memory
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;

        if (prefs != null && prefListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        if (autoRotateObserver != null) {
            getContentResolver().unregisterContentObserver(autoRotateObserver);
        }
        if (orientationListener != null) {
            orientationListener.disable();
        }
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay view: " + e.getMessage());
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
