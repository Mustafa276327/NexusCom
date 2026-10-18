package com.nexus.companion.nexus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class NexusHttpService extends Service {

    private static final String TAG = "NexusHttpService";
    private static final String CHANNEL_ID = "nexus_companion_channel";
    private static final int PORT = 8090;

    private ServerSocket serverSocket;
    private boolean running = false;
    private NexusCommandHandler commandHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NexusHttpService onCreate");
        createNotificationChannel();
        startForeground(1001, createNotification("Nexus Companion", "HTTP sunucu başlatılıyor..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "NexusHttpService onStartCommand");
        startForeground(1001, createNotification("Nexus Companion", "HTTP sunucu başlatılıyor..."));
        startServer();
        return START_STICKY;
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("0.0.0.0", PORT));
                running = true;
                Log.d(TAG, "Nexus HTTP sunucu port " + PORT + " üzerinde başlatıldı");
                updateNotification("Nexus Companion", "HTTP sunucu çalışıyor: Port " + PORT);

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(() -> handleClient(clientSocket)).start();
                    } catch (IOException e) {
                        if (running) {
                            Log.e(TAG, "Socket accept hatası: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Sunucu başlatılamadı: " + e.getMessage());
                updateNotification("Nexus Companion", "Sunucu hatası: " + e.getMessage());
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        try {
            byte[] buffer = new byte[8192];
            int bytesRead = socket.getInputStream().read(buffer);
            if (bytesRead <= 0) {
                socket.close();
                return;
            }

            String request = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            Log.d(TAG, "İstek alındı: " + request.substring(0, Math.min(request.length(), 200)));

            // POST isteğini ayrıştır
            String body = "";
            String[] lines = request.split("\r\n");
            boolean bodyStarted = false;
            for (String line : lines) {
                if (bodyStarted) {
                    body += line;
                }
                if (line.isEmpty()) {
                    bodyStarted = true;
                }
            }

            // Komutu işle
            String responseBody = NexusCommandHandler.handleCommand(body, this);

            // HTTP yanıtını gönder
            String response = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + responseBody.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n"
                    + responseBody;

            socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "İstemci işleme hatası: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nexus Companion",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Nexus AI Asistanı cihaz köprüsü");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private android.app.Notification createNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, com.nexus.companion.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(String title, String content) {
        startForeground(1001, createNotification(title, content));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Sunucu kapatma hatası: " + e.getMessage());
        }
        Log.d(TAG, "NexusHttpService durduruldu");
    }
}
