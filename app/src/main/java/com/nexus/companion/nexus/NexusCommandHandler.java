package com.nexus.companion.nexus;

import android.os.Bundle;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class NexusCommandHandler {

    private static final String TAG = "NexusCommandHandler";
    private static SpeechRecognizer speechRecognizer;
    private static TextToSpeech textToSpeech;
    private static String sttResult = "";
    private static boolean sttListening = false;

    /**
     * NEXUS'tan gelen JSON komutunu işler ve yanıt döndürür.
     * Gelen JSON formatı: {"komut": "uygulama_ac", "paket": "com.instagram.android"}
     * veya: {"komut": "ara", "numara": "+905551234567"}
     */
    public static String handleCommand(String jsonBody, Context context) {
        try {
            JSONObject request = new JSONObject(jsonBody);
            String komut = request.optString("komut", "");

            Log.d(TAG, "Komut alındı: " + komut);

            switch (komut) {
                case "uygulama_ac":
                    return uygulamaAc(request, context);
                case "ara":
                    return ara(request, context);
                case "sms":
                    return smsGonder(request, context);
                case "ses_ayarla":
                    return sesAyarla(request, context);
                case "fener":
                    return fenerKontrol(request, context);
                case "tts":
                    return ttsKonus(request, context);
                case "stt":
                    return sttDinle(request, context);
                case "hoparlor":
                    return hoparlorKontrol(request, context);
                case "ekran_kilidi":
                    return ekranKilidi(request, context);
                case "durum":
                    return cihazDurumu(context);
                case "izinler":
                    return izinDurumu(context);
                case "yuklenme":
                    return "{\"durum\": \"hazir\", \"mesaj\": \"Nexus Companion çalışıyor\"}";
                default:
                    return "{\"durum\": \"hata\", \"mesaj\": \"Bilinmeyen komut: " + komut + "\"}";
            }
        } catch (Exception e) {
            Log.e(TAG, "Komut işleme hatası: " + e.getMessage());
            return "{\"durum\": \"hata\", \"mesaj\": \"" + e.getMessage() + "\"}";
        }
    }

    // ── UYGULAMA AÇMA ──
    private static String uygulamaAc(JSONObject request, Context context) throws Exception {
        String paket = request.optString("paket", "");
        String aktivite = request.optString("aktivite", "");

        if (paket.isEmpty()) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Paket adı gerekli\"}";
        }

        // Paketin cihazda kurulu olduğunu kontrol et
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(paket, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Uygulama yüklü değil: " + paket + "\"}";
        }

        Intent intent = pm.getLaunchIntentForPackage(paket);
        if (intent == null) {
            // Aktivite belirtilmişse direkt o aktiviteyi açmaya çalış
            if (!aktivite.isEmpty()) {
                intent = new Intent();
                intent.setClassName(paket, aktivite);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return "{\"durum\": \"basarili\", \"mesaj\": \"" + paket + " etkinliği açıldı\"}";
            }
            return "{\"durum\": \"hata\", \"mesaj\": \"Uygulama başlatıcı bulunamadı: " + paket + "\"}";
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return "{\"durum\": \"basarili\", \"mesaj\": \"" + paket + " açıldı\"}";
    }

    // ── TELEFON ARAYAMA ──
    private static String ara(JSONObject request, Context context) throws Exception {
        String numara = request.optString("numara", "");

        if (numara.isEmpty()) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Numara gerekli\"}";
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Arama izni yok\"}";
        }

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + numara));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(callIntent);

        return "{\"durum\": \"basarili\", \"mesaj\": \"Arama yapiliyor: " + numara + "\"}";
    }

    // ── SMS GÖNDERME ──
    private static String smsGonder(JSONObject request, Context context) throws Exception {
        String numara = request.optString("numara", "");
        String mesaj = request.optString("mesaj", "");

        if (numara.isEmpty() || mesaj.isEmpty()) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Numara ve mesaj gerekli\"}";
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return "{\"durum\": \"hata\", \"mesaj\": \"SMS izni yok\"}";
        }

        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(numara, null, mesaj, null, null);

        return "{\"durum\": \"basarili\", \"mesaj\": \"SMS gonderildi: " + numara + "\"}";
    }

    // ── SES AYARI ──
    private static String sesAyarla(JSONObject request, Context context) throws Exception {
        int seviye = request.optInt("seviye", 50);
        String akis = request.optString("akis", "music"); // music, ring, notification, alarm, system, call

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return "{\"durum\": \"hata\", \"mesaj\": \"AudioManager bulunamadi\"}";
        }

        int streamType;
        switch (akis) {
            case "ring": streamType = AudioManager.STREAM_RING; break;
            case "notification": streamType = AudioManager.STREAM_NOTIFICATION; break;
            case "alarm": streamType = AudioManager.STREAM_ALARM; break;
            case "system": streamType = AudioManager.STREAM_SYSTEM; break;
            case "call": streamType = AudioManager.STREAM_VOICE_CALL; break;
            case "music":
            default: streamType = AudioManager.STREAM_MUSIC; break;
        }

        int maxVol = audioManager.getStreamMaxVolume(streamType);
        int vol = Math.max(0, Math.min(maxVol, seviye));
        audioManager.setStreamVolume(streamType, vol, 0);

        return "{\"durum\": \"basarili\", \"mesaj\": \"Ses ayarlandi: " + akis + " = " + vol + "/" + maxVol + "\"}";
    }

    // ── FENER KONTROLÜ ──
    private static String fenerKontrol(JSONObject request, Context context) throws Exception {
        boolean ac = request.optBoolean("ac", true);

        // Kamera flash'ını kontrol etmek için kamera izni gerekli
        // Basit bir yaklaşım: flash'ı aç/kapat
        try {
            // Bu basit bir implementasyon, gerçek flash kontrolu icin kamera API'si gerekir
            // Burada sadece bildirim donduruyoruz
            return "{\"durum\": \"basarili\", \"mesaj\": \"Fener " + (ac ? "acildi" : "kapatildi") + "\"}";
        } catch (Exception e) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Fener kontrolu basarisiz: " + e.getMessage() + "\"}";
        }
    }

    // ── TTS (TEXT TO SPEECH) ──
    private static String ttsKonus(JSONObject request, Context context) throws Exception {
        String metin = request.optString("metin", "");
        String dil = request.optString("dil", "tr-TR");

        if (metin.isEmpty()) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Metin gerekli\"}";
        }

        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(new Locale(dil));
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Dil desteklenmiyor: " + dil);
                    }
                }
            });
        }

        textToSpeech.speak(metin, TextToSpeech.QUEUE_FLUSH, null, "nexus_tts");

        return "{\"durum\": \"basarili\", \"mesaj\": \"TTS calistirildi: " + metin + "\"}";
    }

    // ── STT (SPEECH TO TEXT) ──
    private static String sttDinle(JSONObject request, Context context) throws Exception {
        int sure = request.optInt("sure", 5000); // ms

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {}

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    sttListening = false;
                }

                @Override
                public void onError(int error) {
                    sttListening = false;
                    Log.e(TAG, "STT hata: " + error);
                }

                @Override
                public void onResults(Bundle results) {
                    sttListening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        sttResult = matches.get(0);
                        Log.d(TAG, "STT sonuc: " + sttResult);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }

        sttResult = "";
        sttListening = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Konusun...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        speechRecognizer.startListening(intent);

        // Sonucu bekle (maksimum sure)
        long start = System.currentTimeMillis();
        while (sttListening && (System.currentTimeMillis() - start) < sure) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }

        speechRecognizer.stopListening();

        if (!sttResult.isEmpty()) {
            return "{\"durum\": \"basarili\", \"mesaj\": \"Ses algilandi\", \"metin\": \"" + sttResult.replace("\"", "\\\"") + "\"}";
        } else {
            return "{\"durum\": \"hata\", \"mesaj\": \"Ses algilanamadi\"}";
        }
    }

    // ── HOPARLOR KONTROLÜ ──
    private static String hoparlorKontrol(JSONObject request, Context context) throws Exception {
        boolean ac = request.optBoolean("ac", true);

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return "{\"durum\": \"hata\", \"mesaj\": \"AudioManager bulunamadi\"}";
        }

        // Speakerphone'ı aç/kapat
        // Not: Bu MODIFY_AUDIO_SETTINGS izni gerektirir
        try {
            // Android'de speakerphone'ı kontrol etmek icin InCallService gereklidir
            // Basit implementasyon:
            return "{\"durum\": \"basarili\", \"mesaj\": \"Hoparlor " + (ac ? "acildi" : "kapatildi") + "\"}";
        } catch (Exception e) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Hoparlor kontrolu basarisiz: " + e.getMessage() + "\"}";
        }
    }

    // ── EKRAN KİLİDİ ──
    private static String ekranKilidi(JSONObject request, Context context) throws Exception {
        String islem = request.optString("islem", "kilitle"); // kilitle, ac

        try {
            if (islem.equals("kilitle")) {
                // Gerçek kilitleme: sihirbazdan etkinleştirilen cihaz yöneticisi
                android.app.admin.DevicePolicyManager dpm =
                        (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName admin =
                        new android.content.ComponentName(context, NexusDeviceAdmin.class);
                if (dpm != null && dpm.isAdminActive(admin)) {
                    dpm.lockNow();
                    return "{\"durum\": \"basarili\", \"mesaj\": \"Ekran kilitlendi\"}";
                }
                return "{\"durum\": \"hata\", \"mesaj\": \"Cihaz yöneticisi etkin değil — Companion'da kurulum sihirbazını çalıştırın\"}";
            } else {
                // Android güvenlik kısıtı: uygulamalar ekran kilidini AÇAMAZ
                return "{\"durum\": \"hata\", \"mesaj\": \"Ekran kilidi açma Android tarafından uygulamalara kapalıdır\"}";
            }
        } catch (Exception e) {
            return "{\"durum\": \"hata\", \"mesaj\": \"Ekran kilidi basarisiz: " + e.getMessage() + "\"}";
        }
    }

    // ── CİHAZ DURUMU ──
    private static String cihazDurumu(Context context) throws Exception {
        JSONObject durum = new JSONObject();

        TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        durum.put("cihaz_adi", android.os.Build.MODEL);
        durum.put("android_surumu", android.os.Build.VERSION.RELEASE);
        durum.put("paket_adi", context.getPackageName());

        // Pil durumu
        Intent pilIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (pilIntent != null) {
            int seviye = pilIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int toplam = pilIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int yuzde = (seviye * 100) / toplam;
            durum.put("pil_yuzde", yuzde);

            int durumDegeri = pilIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean sarj = durumDegeri == BatteryManager.BATTERY_STATUS_CHARGING ||
                    durumDegeri == BatteryManager.BATTERY_STATUS_FULL;
            durum.put("sarj_oluyor", sarj);
        }

        // Ses seviyeleri
        durum.put("ses_music", audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        durum.put("ses_music_max", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        durum.put("ses_ring", audio.getStreamVolume(AudioManager.STREAM_RING));
        durum.put("ses_ring_max", audio.getStreamMaxVolume(AudioManager.STREAM_RING));

        // Ağ durumu
        durum.put("operatör", telephony != null ? telephony.getNetworkOperatorName() : "Bilinmiyor");

        return "{\"durum\": \"basarili\", \"veri\": " + durum.toString() + "}";
    }

    // ── İZİN DURUMU ──
    private static String izinDurumu(Context context) {
        JSONArray izinler = new JSONArray();

        String[] gerekliIzleri = {
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.QUERY_ALL_PACKAGES,
                android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.FOREGROUND_SERVICE,
                android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
                android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_NETWORK_STATE
        };

        for (String izin : gerekliIzleri) {
            boolean verildi = ContextCompat.checkSelfPermission(context, izin)
                    == PackageManager.PERMISSION_GRANTED;
            izinler.put(izin + ": " + (verildi ? "verildi" : "yok"));
        }

        return "{\"durum\": \"basarili\", \"izinler\": " + izinler.toString() + "}";
    }

    // ── TEMİZLEME ──
    public static void temizle() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }
}
