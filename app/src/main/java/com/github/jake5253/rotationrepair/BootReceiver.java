package com.github.jake5253.rotationrepair;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences("RotationRepairPrefs", Context.MODE_PRIVATE);
            boolean isServiceEnabled = prefs.getBoolean("isServiceEnabled", false);

            // Only attempt to start if the user enabled it AND we have overlay permission
            if (isServiceEnabled && Settings.canDrawOverlays(context)) {
                Intent serviceIntent = new Intent(context, RotationService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
