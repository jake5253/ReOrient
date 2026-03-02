package com.github.jake5253.reorient;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private Button btnToggle;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (Settings.canDrawOverlays(this)) {
                            startRotationService();
                            requestIgnoreBatteryOptimizations();
                        } else {
                            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnToggle = findViewById(R.id.btn_toggle);
        btnToggle.setOnClickListener(v -> {
            if (isServiceRunning()) {
                stopRotationService();
            } else {
                checkOverlayAndStartService();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void checkOverlayAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            startRotationService();
        }
    }

    private void startRotationService() {
        // SAVE THE STATE so BootReceiver can see it
        getSharedPreferences("ReOrientPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("isServiceEnabled", true)
                .apply();

        Intent serviceIntent = new Intent(this, RotationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateUI();
    }

    private void stopRotationService() {
        // This triggers the onDestroy() logic above
        Intent serviceIntent = new Intent(this, RotationService.class);
        stopService(serviceIntent);

        // Reset our "Should Start on Boot" flag
        getSharedPreferences("ReOrientPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("isServiceEnabled", false)
                .apply();

        updateUI();
    }

    private void updateUI() {
        if (isServiceRunning()) {
            btnToggle.setText(R.string.stop_service);
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.LTGRAY));
            btnToggle.setTextColor(Color.BLACK); // Make text readable on grey
        } else {
            btnToggle.setText(R.string.start_service);
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#3C04FF00")));
            btnToggle.setTextColor(Color.BLACK);
        }
    }

    private boolean isServiceRunning() {
        return getSharedPreferences("ReOrientPrefs", MODE_PRIVATE)
                .getBoolean("isServiceEnabled", false);
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            // Send user to the system settings list
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Please find ReOrient and set to 'Don't Optimize'", Toast.LENGTH_LONG).show();
        }
    }
}