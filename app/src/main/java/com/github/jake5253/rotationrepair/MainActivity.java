package com.github.jake5253.rotationrepair;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    private Button btnToggle;
    private TextView hysteresisValueText;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "RotationRepairPrefs";
    private static final String PREF_HYSTERESIS_DEGREES = "hysteresisDegrees";
    private static final String PREF_INVERT_PORTRAIT = "invertPortrait";
    private static final String PREF_INVERT_LANDSCAPE = "invertLandscape";
    private static final String PREF_PREVENT_UPSIDE_DOWN = "preventUpsideDownPortrait";
    private static final int DEFAULT_HYSTERESIS_DEGREES = 10;
    private static final int MAX_HYSTERESIS_DEGREES = 45;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (Settings.canDrawOverlays(this)) {
                            startRotationService();
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

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        btnToggle = findViewById(R.id.btn_toggle);
        SeekBar hysteresisSeekBar = findViewById(R.id.seek_hysteresis);
        hysteresisValueText = findViewById(R.id.txt_hysteresis_value);
        SwitchMaterial invertPortraitSwitch = findViewById(R.id.switch_invert_portrait);
        SwitchMaterial invertLandscapeSwitch = findViewById(R.id.switch_invert_landscape);
        SwitchMaterial preventUpsideDownSwitch = findViewById(R.id.switch_prevent_upside_down);

        invertPortraitSwitch.setChecked(prefs.getBoolean(PREF_INVERT_PORTRAIT, true));
        invertLandscapeSwitch.setChecked(prefs.getBoolean(PREF_INVERT_LANDSCAPE, true));
        preventUpsideDownSwitch.setChecked(prefs.getBoolean(PREF_PREVENT_UPSIDE_DOWN, false));

        invertPortraitSwitch.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean(PREF_INVERT_PORTRAIT, checked).apply());
        invertLandscapeSwitch.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean(PREF_INVERT_LANDSCAPE, checked).apply());
        preventUpsideDownSwitch.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean(PREF_PREVENT_UPSIDE_DOWN, checked).apply());

        int initialHysteresis = prefs.getInt(PREF_HYSTERESIS_DEGREES, DEFAULT_HYSTERESIS_DEGREES);
        hysteresisSeekBar.setMax(MAX_HYSTERESIS_DEGREES);
        hysteresisSeekBar.setProgress(initialHysteresis);
        updateHysteresisLabel(initialHysteresis);

        hysteresisSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateHysteresisLabel(progress);
                if (fromUser) prefs.edit().putInt(PREF_HYSTERESIS_DEGREES, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnToggle.setOnClickListener(v -> {
            if (RotationService.isRunning) stopRotationService();
            else checkOverlayAndStartService();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void checkOverlayAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            try { overlayPermissionLauncher.launch(intent); }
            catch (Exception e) { overlayPermissionLauncher.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
        } else {
            startRotationService();
        }
    }

    private void startRotationService() {
        prefs.edit().putBoolean("isServiceEnabled", true).apply();
        Intent intent = new Intent(this, RotationService.class);
        startForegroundService(intent);
        updateUI();
    }

    private void stopRotationService() {
        stopService(new Intent(this, RotationService.class));
        prefs.edit().putBoolean("isServiceEnabled", false).apply();
        updateUI();
    }

    private void updateUI() {
        boolean running = RotationService.isRunning;
        btnToggle.setText(running ? R.string.stop_service : R.string.start_service);
        btnToggle.setBackgroundTintList(ColorStateList.valueOf(running ? Color.LTGRAY : Color.parseColor("#4CAF50")));
    }

    private void updateHysteresisLabel(int value) {
        hysteresisValueText.setText(getString(R.string.hysteresis_value, value));
    }
}
