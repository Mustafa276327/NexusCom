package com.nexus.companion;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.nexus.companion.nexus.NexusDeviceAdmin;
import com.nexus.companion.nexus.NexusHttpService;

import java.util.ArrayList;
import java.util.List;

/**
 * NEXUS Companion — Kurulum Sihirbazı'lı ana ekran.
 *
 * Sihirbaz adımları:
 *  1. Temel izinler (mikrofon, telefon, SMS, arama kaydı, rehber, konum, bildirim)
 *  2. Ekran üstü gösterim izni (SYSTEM_ALERT_WINDOW — özel ekran)
 *  3. Pil optimizasyonu muafiyeti (aksi halde Android sunucuyu öldürebilir)
 *  4. Cihaz yöneticisi (ekran kilidi politikası)
 *  5. Tamamlandı → ana ekran (sunucu başlat/durdur)
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_RUNTIME = 1001;
    private static final int REQ_ADMIN = 1002;
    private static final String PREFS = "nexus_pref";
    private static final String KEY_WIZARD_DONE = "wizard_done";

    // Sihirbaz adımları
    private static final int STEP_BASE = 0;      // runtime izinler
    private static final int STEP_OVERLAY = 1;   // SYSTEM_ALERT_WINDOW
    private static final int STEP_BATTERY = 2;   // pil optimizasyonu muafiyeti
    private static final int STEP_ADMIN = 3;     // cihaz yöneticisi
    private static final int STEP_DONE = 4;      // bitti
    private static final int STEP_COUNT = 4;     // kullanıcıya gösterilen adım sayısı

    private LinearLayout wizardPanel, mainPanel;
    private TextView wizardIlerleme, wizardAd, wizardAciklama, statusText, izinOzet;
    private Button wizardBtnVer, wizardBtnAtla;
    private Button btnStartServer, btnCheckPermissions, btnOpenSettings, btnWizard;

    private int step = STEP_BASE;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        wizardPanel = findViewById(R.id.wizard_panel);
        mainPanel = findViewById(R.id.main_panel);
        wizardIlerleme = findViewById(R.id.wizard_ilerleme);
        wizardAd = findViewById(R.id.wizard_ad);
        wizardAciklama = findViewById(R.id.wizard_aciklama);
        wizardBtnVer = findViewById(R.id.wizard_btn_ver);
        wizardBtnAtla = findViewById(R.id.wizard_btn_atla);
        statusText = findViewById(R.id.status_text);
        izinOzet = findViewById(R.id.izin_ozet);
        btnStartServer = findViewById(R.id.btn_start_server);
        btnCheckPermissions = findViewById(R.id.btn_check_permissions);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        btnWizard = findViewById(R.id.btn_wizard);

        wizardBtnVer.setOnClickListener(v -> wizardIzinVer());
        wizardBtnAtla.setOnClickListener(v -> sonrakiAdim());

        btnStartServer.setOnClickListener(v -> startNexusService());
        btnCheckPermissions.setOnClickListener(v -> izinOzetiGoster());
        btnOpenSettings.setOnClickListener(v -> openAppSettings());
        btnWizard.setOnClickListener(v -> {
            step = STEP_BASE;
            wizardPanel.setVisibility(android.view.View.VISIBLE);
            mainPanel.setVisibility(android.view.View.GONE);
            wizardGoster();
        });

        if (prefs.getBoolean(KEY_WIZARD_DONE, false)) {
            anaEkraniGoster();
        } else {
            wizardGoster();
        }
    }

    // ─────────────── SİHİRBAZ ───────────────

    private void wizardGoster() {
        wizardPanel.setVisibility(android.view.View.VISIBLE);
        mainPanel.setVisibility(android.view.View.GONE);

        if (step >= STEP_DONE) {
            prefs.edit().putBoolean(KEY_WIZARD_DONE, true).apply();
            Toast.makeText(this, "Kurulum tamamlandı 🎉", Toast.LENGTH_SHORT).show();
            anaEkraniGoster();
            return;
        }

        wizardIlerleme.setText("Adım " + (step + 1) + " / " + STEP_COUNT);
        switch (step) {
            case STEP_BASE:
                wizardAd.setText("Temel İzinler");
                wizardAciklama.setText("NEXUS'un sesli komutlar, arama, SMS, arama kayıtları, rehber, konum ve bildirimlerle çalışabilmesi için bu izinler gerekli.");
                wizardBtnVer.setText("İzinleri Ver");
                break;
            case STEP_OVERLAY:
                wizardAd.setText("Ekran Üzerinde Gösterim");
                wizardAciklama.setText("NEXUS'un ekranın üstünde bilgi gösterebilmesi için 'Diğer uygulamaların üzerine çiz' iznini açın. Ayarlarda bu uygulamayı seçip açın ve geri dönün.");
                wizardBtnVer.setText("Ayarları Aç");
                break;
            case STEP_BATTERY:
                wizardAd.setText("Pil Optimizasyonu Muafiyeti");
                wizardAciklama.setText("Android, arka plandaki uygulamaları öldürebilir. Sunucunun sürekli çalışması için pil optimizasyonundan muaf tutun.");
                wizardBtnVer.setText("Muafiyet İste");
                break;
            case STEP_ADMIN:
                wizardAd.setText("Cihaz Yöneticisi");
                wizardAciklama.setText("Ekran kilidi gibi cihaz kontrol komutları için uygulamanın cihaz yöneticisi olarak etkinleştirilmesi gerekir.");
                wizardBtnVer.setText("Etkinleştir");
                break;
        }
    }

    private void wizardIzinVer() {
        switch (step) {
            case STEP_BASE: {
                List<String> izinler = new ArrayList<>();
                String[] aday = {
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                };
                for (String p : aday) {
                    if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                        izinler.add(p);
                    }
                }
                if (Build.VERSION.SDK_INT >= 33
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                    izinler.add(Manifest.permission.POST_NOTIFICATIONS);
                }

                if (izinler.isEmpty()) {
                    Toast.makeText(this, "Temel izinler zaten verilmiş ✓", Toast.LENGTH_SHORT).show();
                    sonrakiAdim();
                } else {
                    ActivityCompat.requestPermissions(this, izinler.toArray(new String[0]), REQ_RUNTIME);
                }
                break;
            }
            case STEP_OVERLAY: {
                if (Settings.canDrawOverlays(this)) {
                    sonrakiAdim();
                } else {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    try {
                        startActivityForResult(intent, REQ_ADMIN);
                    } catch (Exception e) {
                        Toast.makeText(this, "Ayarlar açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
                break;
            }
            case STEP_BATTERY: {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    sonrakiAdim();
                } else {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQ_ADMIN);
                    } catch (Exception e) {
                        // Bazı ROM'larda intent çalışmaz — genel ayar ekranına düş
                        try {
                            startActivityForResult(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), REQ_ADMIN);
                        } catch (Exception e2) {
                            sonrakiAdim();
                        }
                    }
                }
                break;
            }
            case STEP_ADMIN: {
                DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                ComponentName admin = new ComponentName(this, NexusDeviceAdmin.class);
                if (dpm != null && dpm.isAdminActive(admin)) {
                    sonrakiAdim();
                } else {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "NEXUS'un ekran kilidi ve cihaz kontrol komutları için gereklidir.");
                    try {
                        startActivityForResult(intent, REQ_ADMIN);
                    } catch (Exception e) {
                        Toast.makeText(this, "Cihaz yöneticisi ekranı açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
                break;
            }
        }
    }

    private void sonrakiAdim() {
        step++;
        wizardGoster();
    }

    // ─────────────── ANA EKRAN ───────────────

    private void anaEkraniGoster() {
        wizardPanel.setVisibility(android.view.View.GONE);
        mainPanel.setVisibility(android.view.View.VISIBLE);
        izinOzetiGoster();
    }

    private void startNexusService() {
        Intent serviceIntent = new Intent(this, NexusHttpService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        statusText.setText("Nexus HTTP Sunucusu: ÇALIŞIYOR (Port 8090)");
        statusText.setTextColor(0xFF00AA00);
        Toast.makeText(this, "Nexus Companion sunucu başlatıldı", Toast.LENGTH_SHORT).show();
    }

    private void izinOzetiGoster() {
        String[] kontrol = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ACCESS_FINE_LOCATION,
        };
        int verilen = 0;
        for (String p : kontrol) {
            if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) verilen++;
        }
        StringBuilder ekstra = new StringBuilder();
        if (Settings.canDrawOverlays(this)) ekstra.append(" • ekran üstü");
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) ekstra.append(" • pil muaf");
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isAdminActive(new ComponentName(this, NexusDeviceAdmin.class))) ekstra.append(" • cihaz yöneticisi");

        izinOzet.setText("İzinler: " + verilen + "/" + kontrol.length + " temel" + ekstra);
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    // ─────────────── SONUÇLAR ───────────────

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RUNTIME) {
            int verilen = 0;
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_GRANTED) verilen++;
            }
            if (verilen == grantResults.length) {
                Toast.makeText(this, "Tüm temel izinler verildi ✓", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, verilen + "/" + grantResults.length + " izin verildi — atlananlar sonra tekrar istenebilir", Toast.LENGTH_LONG).show();
            }
            sonrakiAdim();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Özel izin ekranlarından (overlay/pil/admin) dönünce durumu yeniden kontrol et
        if (wizardPanel.getVisibility() == android.view.View.VISIBLE && step >= STEP_OVERLAY && step < STEP_DONE) {
            boolean devam = false;
            switch (step) {
                case STEP_OVERLAY: devam = Settings.canDrawOverlays(this); break;
                case STEP_BATTERY: {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    devam = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
                    break;
                }
                case STEP_ADMIN: {
                    DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                    devam = dpm != null && dpm.isAdminActive(new ComponentName(this, NexusDeviceAdmin.class));
                    break;
                }
            }
            if (devam) sonrakiAdim();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // onResume zaten durumu kontrol edip gerekirse ilerliyor
    }
}
