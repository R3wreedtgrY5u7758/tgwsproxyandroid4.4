package com.amurcanov.tgwsproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Foreground-сервис, который держит нативный прокси.
 * Логика та же, что в Kotlin-оригинале, но без корутин/Flow и без API выше 19
 * (всё, что новее, обёрнуто в проверки Build.VERSION.SDK_INT).
 */
public class ProxyService extends Service {

    public static final String ACTION_START = "com.amurcanov.tgwsproxy.START";
    public static final String ACTION_STOP = "com.amurcanov.tgwsproxy.STOP";
    public static final String ACTION_RESTART = "com.amurcanov.tgwsproxy.RESTART";

    public static final String EXTRA_BIND_IP = "EXTRA_BIND_IP";
    public static final String EXTRA_PORT = "EXTRA_PORT";
    public static final String EXTRA_IPS = "EXTRA_IPS";
    public static final String EXTRA_POOL_SIZE = "EXTRA_POOL_SIZE";
    public static final String EXTRA_CFPROXY_ENABLED = "EXTRA_CFPROXY_ENABLED";
    public static final String EXTRA_CFPROXY_PRIORITY = "EXTRA_CFPROXY_PRIORITY";
    public static final String EXTRA_CFPROXY_DOMAIN = "EXTRA_CFPROXY_DOMAIN";
    public static final String EXTRA_SECRET_KEY = "EXTRA_SECRET_KEY";

    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "TG_WS_Proxy_Service_v4";
    private static final String TAG = "ProxyService";

    private static final long WAKELOCK_TIMEOUT_MS = 30L * 60 * 1000;
    private static final long WAKELOCK_REFRESH_MS = 25L * 60 * 1000;
    private static final long STATS_UPDATE_MS = 3000L;
    private static final long NOTIFICATION_MIN_UPDATE_MS = 3000L;
    private static final long NATIVE_STOP_WAIT_MS = 3000L;

    // ---------- Состояние для UI (статика, т.к. сервис и Activity в одном процессе) ----------

    private static volatile boolean running;
    private static volatile boolean verified;
    private static volatile boolean busy;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<Runnable>();
    private static final Runnable FIRE = new Runnable() {
        @Override
        public void run() {
            for (Runnable r : LISTENERS) {
                try {
                    r.run();
                } catch (Throwable ignored) {
                }
            }
        }
    };

    /** Запущен нативный прокси (после успешного StartProxy). */
    public static boolean isRunning() { return running; }

    /** Прокси подтверждённо работает. */
    public static boolean isVerifiedRunning() { return verified; }

    /** Идёт запуск (запрос отправлен, но прокси ещё не готов). */
    public static boolean isBusy() { return busy; }

    public static void addListener(Runnable r) { if (!LISTENERS.contains(r)) LISTENERS.add(r); }
    public static void removeListener(Runnable r) { LISTENERS.remove(r); }

    private static void fire() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            FIRE.run();
        } else {
            MAIN.post(FIRE);
        }
    }

    /** Помечает «идёт запуск» ещё до того, как сервис стартовал, чтобы UI не мигал. */
    public static void markBusy() {
        busy = true;
        fire();
    }

    /** Сбросить «идёт запуск», если запрос на старт сервиса не удалось отправить. */
    public static void clearBusy() {
        if (!running) setState(false, false, false);
    }

    private static void setState(boolean isRunning, boolean isVerified, boolean isBusy) {
        running = isRunning;
        verified = isVerified;
        busy = isBusy;
        fire();
    }

    // ---------- Экземпляр сервиса ----------

    private final Handler main = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService scheduler;
    private PowerManager.WakeLock wakeLock;

    private volatile boolean stopInProgress;
    private volatile boolean starting;
    private boolean restartInProgress;

    private String lastBindIp = SettingsStore.DEFAULT_BIND_IP;
    private int lastPort = 1443;
    private String lastIps = "";
    private int lastPoolSize = 4;
    private boolean lastCfEnabled = true;
    private boolean lastCfPriority = true;
    private String lastCfDomain = "";
    private String lastSecretKey = "";

    private long notificationStartedAtMs;
    private String lastNotificationContent = "";
    private long lastNotificationAtMs;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopProxy();
        } else if (ACTION_RESTART.equals(action)) {
            restartProxy();
        } else if (ACTION_START.equals(action)) {
            startProxy(
                    str(intent.getStringExtra(EXTRA_BIND_IP), SettingsStore.DEFAULT_BIND_IP),
                    intent.getIntExtra(EXTRA_PORT, 1443),
                    str(intent.getStringExtra(EXTRA_IPS), ""),
                    intent.getIntExtra(EXTRA_POOL_SIZE, 4),
                    intent.getBooleanExtra(EXTRA_CFPROXY_ENABLED, true),
                    intent.getBooleanExtra(EXTRA_CFPROXY_PRIORITY, true),
                    str(intent.getStringExtra(EXTRA_CFPROXY_DOMAIN), ""),
                    str(intent.getStringExtra(EXTRA_SECRET_KEY), ""));
        } else if (!running && !starting) {
            // Сервис пересоздан системой без интента — запускать нечего.
            setState(false, false, false);
            stopSelf();
        }
        return START_REDELIVER_INTENT;
    }

    private static String str(String v, String def) {
        return v == null ? def : v;
    }

    private boolean isPortAvailable(String bindIp, int port) {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(bindIp, port));
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void startProxy(final String bindIp, final int port, final String ips, final int poolSize,
                            final boolean cfEnabled, final boolean cfPriority,
                            final String cfDomain, final String secretKey) {
        if (running || starting || stopInProgress) {
            // Запрос проигнорирован — не оставляем UI в состоянии «подключение…».
            if (!running && !starting) setState(false, false, false);
            return;
        }

        // Маркер начала сессии — LogManager по нему сбрасывает старые записи.
        Log.i(LogManager.TAG, LogManager.SESSION_MARKER);

        starting = true;
        setState(false, false, true);

        lastBindIp = bindIp;
        lastPort = port;
        lastIps = ips;
        lastPoolSize = poolSize;
        lastCfEnabled = cfEnabled;
        lastCfPriority = cfPriority;
        lastCfDomain = cfDomain;
        lastSecretKey = secretKey;

        notificationStartedAtMs = System.currentTimeMillis();
        lastNotificationContent = getString(R.string.notification_starting);
        lastNotificationAtMs = notificationStartedAtMs;

        startForeground(NOTIFICATION_ID, createNotification(lastNotificationContent));
        acquireWakeLock();
        stopInProgress = false;
        startScheduler();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                if (!isPortAvailable(bindIp, port)) {
                    final String msg = getString(R.string.port_is_not_available, port);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            starting = false;
                            Toast.makeText(ProxyService.this, msg, Toast.LENGTH_SHORT).show();
                            updateNotification(msg, true);
                            stopProxy();
                        }
                    });
                    return;
                }

                try {
                    NativeProxy.setPoolSize(poolSize);
                    NativeProxy.setCfProxyCacheDir(getCacheDir().getAbsolutePath());
                    NativeProxy.setCfProxyConfig(cfEnabled, cfPriority, cfDomain);
                    final int result = NativeProxy.startProxy(bindIp, port, ips, secretKey, 1);
                    if (result == 0) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                starting = false;
                                if (stopInProgress) {
                                    // Пока запускались, пользователь нажал «стоп» — гасим нативную часть.
                                    stopNativeAsync();
                                    return;
                                }
                                setState(true, true, false);
                                updateNotification(getString(R.string.notification_running), true);
                                Log.i(TAG, "Proxy ready: listening on port " + port);
                            }
                        });
                    } else {
                        Log.e(TAG, "StartProxy returned error code: " + result);
                        failStart(getString(R.string.notification_start_error_code, result));
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Failed to start proxy via JNA", e);
                    failStart(getString(R.string.notification_error, String.valueOf(e.getMessage())));
                }
            }
        }, "ProxyStart");
        t.setDaemon(true);
        t.start();
    }

    private void failStart(final String message) {
        main.post(new Runnable() {
            @Override
            public void run() {
                starting = false;
                stopInProgress = true;   // нативная часть не запустилась — просто завершаем сервис
                stopScheduler();
                updateNotification(message, true);
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finishStop();
                    }
                }, 3000);
            }
        });
    }

    private void startScheduler() {
        stopScheduler();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                updateStats();
            }
        }, STATS_UPDATE_MS, STATS_UPDATE_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                refreshWakeLock();
            }
        }, WAKELOCK_REFRESH_MS, WAKELOCK_REFRESH_MS, TimeUnit.MILLISECONDS);
    }

    private void stopScheduler() {
        ScheduledExecutorService s = scheduler;
        scheduler = null;
        if (s != null) s.shutdownNow();
    }

    private void updateStats() {
        if (!running || stopInProgress) return;
        try {
            String raw = NativeProxy.getStats();
            if (raw == null) return;
            double total = parseHumanBytes(extractStat(raw, "up=")) + parseHumanBytes(extractStat(raw, "down="));
            int active = 0;
            try {
                active = Integer.parseInt(extractStat(raw, "active="));
            } catch (NumberFormatException ignored) {
            }
            updateNotification(getString(R.string.notification_traffic, formatBytes(total), active), false);
        } catch (Throwable e) {
            Log.w(TAG, "Stats update failed", e);
        }
    }

    private static String extractStat(String stats, String key) {
        int idx = stats.indexOf(key);
        if (idx == -1) return "0B";
        int start = idx + key.length();
        int end = stats.indexOf(' ', start);
        return end == -1 ? stats.substring(start) : stats.substring(start, end);
    }

    private static double parseHumanBytes(String s) {
        String digits = s.replaceAll("[^0-9.]", "");
        double num;
        try {
            num = digits.length() == 0 ? 0.0 : Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            num = 0.0;
        }
        if (s.endsWith("TB")) return num * 1024.0 * 1024 * 1024 * 1024;
        if (s.endsWith("GB")) return num * 1024.0 * 1024 * 1024;
        if (s.endsWith("MB")) return num * 1024.0 * 1024;
        if (s.endsWith("KB")) return num * 1024.0;
        return num;
    }

    private static String formatBytes(double bytes) {
        if (bytes < 1024) return String.format(Locale.US, "%.0fB", bytes);
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1fKB", bytes / 1024);
        if (bytes < 1024.0 * 1024 * 1024) return String.format(Locale.US, "%.1fMB", bytes / (1024 * 1024));
        return String.format(Locale.US, "%.2fGB", bytes / (1024.0 * 1024 * 1024));
    }

    // ---------- Остановка / перезапуск ----------

    /** Останавливает нативный прокси в фоне; ждёт не дольше NATIVE_STOP_WAIT_MS. Вызывать НЕ из UI-потока. */
    private void stopNativeBlocking() {
        Thread stopper = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int r = NativeProxy.stopProxy();
                    Log.i(TAG, "StopProxy returned " + r);
                } catch (Throwable e) {
                    Log.w(TAG, "StopProxy failed", e);
                }
            }
        }, "ProxyStop");
        stopper.setDaemon(true);
        stopper.start();
        try {
            stopper.join(NATIVE_STOP_WAIT_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void stopNativeAsync() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                stopNativeBlocking();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        finishStop();
                    }
                });
            }
        }, "ProxyStopAsync");
        t.setDaemon(true);
        t.start();
    }

    private void stopProxy() {
        if (stopInProgress && !starting) {
            // уже останавливаемся
            return;
        }
        boolean wasStarting = starting;
        stopInProgress = true;
        restartInProgress = false;
        stopScheduler();
        updateNotification(getString(R.string.notification_stopping), true);

        if (wasStarting) {
            // Нативный запуск ещё идёт: остановкой займётся поток запуска (см. startProxy).
            // Если он завершится ошибкой — failStart() вызовет stopProxy() повторно,
            // а флаг starting к тому моменту уже будет сброшен.
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!starting) return;
                    // Страховка: запуск завис — завершаем принудительно.
                    starting = false;
                    stopNativeAsync();
                }
            }, 10000);
            return;
        }
        stopNativeAsync();
    }

    private void finishStop() {
        releaseWakeLock();
        setState(false, false, false);
        stopForeground(true);
        stopSelf();
    }

    private void restartProxy() {
        if (restartInProgress || stopInProgress || lastSecretKey.length() == 0) return;
        restartInProgress = true;
        updateNotification(getString(R.string.notification_restarting), true);
        stopScheduler();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                stopNativeBlocking();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        releaseWakeLock();
                        setState(false, false, true);
                        main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!restartInProgress) return;
                                restartInProgress = false;
                                startProxy(lastBindIp, lastPort, lastIps, lastPoolSize,
                                        lastCfEnabled, lastCfPriority, lastCfDomain, lastSecretKey);
                            }
                        }, 350);
                    }
                });
            }
        }, "ProxyRestart");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (running) {
            Log.w(TAG, "onTaskRemoved: proxy is running, service stays alive");
        }
    }

    // ---------- WakeLock ----------

    private synchronized void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TgWsProxy:ProxyService");
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        } catch (Throwable e) {
            Log.w(TAG, "Failed to acquire WakeLock", e);
        }
    }

    private synchronized void refreshWakeLock() {
        try {
            if (wakeLock != null) wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        } catch (Throwable e) {
            Log.w(TAG, "Failed to refresh WakeLock", e);
        }
    }

    private synchronized void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable e) {
            Log.w(TAG, "Failed to release WakeLock", e);
        }
        wakeLock = null;
    }

    // ---------- Уведомление ----------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(getString(R.string.notification_channel_description));
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private synchronized void updateNotification(String content, boolean force) {
        long now = System.currentTimeMillis();
        if (!force) {
            if (content.equals(lastNotificationContent)) return;
            if (lastNotificationAtMs != 0L && now - lastNotificationAtMs < NOTIFICATION_MIN_UPDATE_MS) return;
        }
        lastNotificationContent = content;
        lastNotificationAtMs = now;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, createNotification(content));
        } catch (Throwable e) {
            Log.w(TAG, "Failed to update notification", e);
        }
    }

    private PendingIntent pending(Intent intent, int requestCode, boolean activity) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return activity
                ? PendingIntent.getActivity(this, requestCode, intent, flags)
                : PendingIntent.getService(this, requestCode, intent, flags);
    }

    @SuppressWarnings("deprecation")
    private Notification createNotification(String content) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent restart = new Intent(this, ProxyService.class).setAction(ACTION_RESTART);
        Intent stop = new Intent(this, ProxyService.class).setAction(ACTION_STOP);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContentTitle(getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pending(open, 0, true))
                .setOngoing(true)
                .setWhen(notificationStartedAtMs == 0L ? System.currentTimeMillis() : notificationStartedAtMs)
                .addAction(android.R.drawable.ic_popup_sync,
                        getString(R.string.notification_restart), pending(restart, 1, false))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.notification_disconnect), pending(stop, 2, false));

        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_LOW);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            b.setCategory(Notification.CATEGORY_SERVICE);
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        return b.build();
    }

    @Override
    public void onDestroy() {
        stopScheduler();
        releaseWakeLock();
        if (!running && !starting) {
            setState(false, false, false);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
