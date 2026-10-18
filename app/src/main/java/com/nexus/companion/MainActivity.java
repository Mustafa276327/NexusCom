package com.nexus.companion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nexus.companion.nexus.NexusHttpService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private TextView statusText;
    private Button btnStartServer;
    private Button btnCheckPermissions;
    private Button btnOpenSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        btnStartServer = findViewById(R.id.btn_start_server);
        btnCheckPermissions = findViewById(R.id.btn_check_permissions);
        btnOpenSettings = findViewById(R.id.btn_open_settings);

        btnStartServer.setOnClickListener(v -> startNexusService());
        btnCheckPermissions.setOnClickListener(v -> checkAndRequestPermissions());
        btnOpenSettings.setOnClickListener(v -> openAppSettings());

        checkAndRequestPermissions();
    }

    private void startNexusService() {
        Intent serviceIntent = new Intent(this, NexusHttpService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        statusText.setText("Nexus HTTP Sunucusu: ÇALIŞIYOR (Port 8090)");
        statusText.setTextColor(0xFF00AA00);
        Toast.makeText(this, "Nexus Companion sunucu başlatıldı", Toast.LENGTH_SHORT).show();
    }

    private void checkAndRequestPermissions() {
        List<String> neededPermissions = new ArrayList<>();
        neededPermissions.add(Manifest.permission.RECORD_AUDIO);
        neededPermissions.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
        neededPermissions.add(Manifest.permission.CALL_PHONE);
        neededPermissions.add(Manifest.permission.SEND_SMS);
        neededPermissions.add(Manifest.permission.RECEIVE_SMS);
        neededPermissions.add(Manifest.permission.READ_SMS);
        neededPermissions.add(Manifest.permission.READ_PHONE_STATE);
        neededPermissions.add(Manifest.permission.QUERY_ALL_PACKAGES);
        neededPermissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);
        neededPermissions.add(Manifest.permission.WAKE_LOCK);
        neededPermissions.add(Manifest.permission.FOREGROUND_SERVICE);

        List<String> missing = new ArrayList<>();
        for (String perm : neededPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm);
            }
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            statusText.setText("İzinler isteniyor...");
        } else {
            statusText.setText("Tüm izinler verildi ✅");
            startNexusService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                statusText.setText("Tüm izinler verildi ✅");
                startNexusService();
            } else {
                statusText.setText("Bazı izinler reddedildi ⚠️");
            }
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
}
