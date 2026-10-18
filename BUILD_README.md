# NEXUS Companion App - Build ve Kurulum Talimatları

Bu uygulama, NEXUS AI asistanına cihaz seviyesinde erişim sağlayan bir Android Companion App'tir.

## 1. Gereksinimler

Android APK derlemek için şu araçlara ihtiyacınız vardır:

### Seçenek A: Bilgisayarda Derleme (Önerilen)
- **Android Studio** veya **Android SDK Command-line Tools**
- **JDK 8+**
- **Gradle**

### Seçenek B: Termux'ta Derleme (Zor, Çalışmayabilir)
```
pkg install openjdk-17
pkg install android-tools
```

## 2. APK Derleme Adımları

### Adım 1: Proje Klasörünü Bilgisayara Taşı
```
cp -r /storage/emulated/0/nexus_depo/nexus_companion ~/Desktop/
cd ~/Desktop/nexus_companion
```

### Adım 2: Derleme (Komut Satırı)
```bash
# Gradle wrapper oluştur (ilk kez)
gradle wrapper

# Debug APK derle
./gradlew assembleDebug

# Çıktı dosyası:
app/build/outputs/apk/debug/app-debug.apk
```

### Adım 3: APK'yı Cihaza Taşı
```bash
# USB üzerinden veya herhangi bir yöntemle
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/nexus_depo/NexusCompanion.apk
```

## 3. Cihaza Kurulum

### 3.1. Bilinmeyen Kaynaklardan Kurulum İzni
Ayarlar → Güvenlik → "Bilinmeyen kaynaklardan kur" → Aç

### 3.2. APK'yı Kur
```bash
# Termux'ta
pm install -r /storage/emulated/0/nexus_depo/NexusCompanion.apk
```

### 3.3. İzinleri Ver
Uygulamayı açın ve şu izinleri verin:
- Mikrofon
- Telefon (ara)
- SMS (gönder/oku)
- Uygulamaları gör (query all packages)
- Üst üste görünen pencere (SYSTEM_ALERT_WINDOW)
- Bildirim

## 4. NEXUS ile Entegrasyon

Companion App, `http://cihaz_ip:8090` üzerinde HTTP sunucu açar.

NEXUS'tan komut göndermek için `panel/server.py`'ye veya doğrudan HTTP istekle:

```python
import requests

# Uygulama aç
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "uygulama_ac",
    "paket": "com.instagram.android"
})

# Arama yap
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "ara",
    "numara": "+905551234567"
})

# SMS gönder
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "sms",
    "numara": "+905551234567",
    "mesaj": "Merhaba Nexus!"
})

# Ses ayarla
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "ses_ayarla",
    "akis": "music",
    "seviye": 80
})

# Fener aç
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "fener",
    "ac": true
})

# TTS - Konuş
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "tts",
    "metin": "Merhaba, ben Nexus",
    "dil": "tr-TR"
})

# STT - Dinle
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "stt",
    "sure": 5000
})

# Cihaz durumu
requests.post("http://192.168.1.100:8090/api", json={
    "komut": "durum"
})
```

## 5. NEXUS Kodunda Entegrasyon

`/storage/emulated/0/nexus/beyin/sesli_komut.py` dosyasına şu fonksiyonu ekleyin:

```python
import requests

def companion_app_komut(komut_dict, cihaz_ip="127.0.0.1"):
    """Companion App'e komut gönderir."""
    try:
        r = requests.post(
            f"http://{cihaz_ip}:8090/api",
            json=komut_dict,
            timeout=10
        )
        return r.json()
    except Exception as e:
        return {"durum": "hata", "mesaj": str(e)}
```

## 6. Desteklenen Komutlar

| Komut | Parametreler | Açıklama |
|-------|-------------|----------|
| `uygulama_ac` | `paket` | Uygulama açar |
| `ara` | `numara` | Telefon araması yapar |
| `sms` | `numara`, `mesaj` | SMS gönderir |
| `ses_ayarla` | `akis`, `seviye` | Ses seviyesini ayarlar |
| `fener` | `ac` | Fener açar/kapatır |
| `tts` | `metin`, `dil` | Konuşma sentezi |
| `stt` | `sure` | Konuşmayı metne dönüştürür |
| `hoparlor` | `ac` | Hoparlör kontrolü |
| `ekran_kilidi` | `islem` | Ekran kilidi |
| `durum` | - | Cihaz durumu |
| `izinler` | - | İzin durumu |

## 7. Sorun Giderme

### Sunucu başlamıyor?
- Mikrofon izni verildiğinden emin olun
- Ayarlar → Uygulamalar → Nexus Companion → İzinler

### Uygulama açılamıyor?
- Paket adının doğru olduğundan emin olun
- `pm list packages` ile kontrol edin

### Bağlantı hatası?
- Cihaz IP'sinin doğru olduğundan emin olun
- Port 8090'ın açık olduğundan emin olun
- Aynı Wi-Fi ağında olun

## 8. Dosya Konumu

- APK: `/storage/emulated/0/nexus_depo/NexusCompanion.apk`
- Kaynak kod: `/storage/emulated/0/nexus_depo/nexus_companion/`

---

**Not:** Bu uygulama, NEXUS AI asistanının cihaz seviyesinde eylemler yapabilmesi için gereken tüm sistem izinlerini alır. İzinleri dikkatli verin.