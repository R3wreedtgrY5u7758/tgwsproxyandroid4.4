package com.amurcanov.tgwsproxy;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Читает собственные логи приложения через `logcat` (на Android 4.1+ приложению
 * доступны только свои записи, права READ_LOGS не нужны).
 *
 * Отличия от оригинала:
 *  - вместо корутин — обычные потоки;
 *  - флаг `--pid` в logcat Android 4.4 не существует, поэтому фильтруем по тегу TgWsProxy;
 *  - при старте прокси сервис пишет маркер SESSION_MARKER, и всё, что было до него,
 *    отбрасывается (logcat при подключении «проигрывает» старый буфер).
 */
public final class LogManager {

    public interface Listener {
        void onLogsChanged();
    }

    public static final String TAG = "TgWsProxy";
    public static final String SESSION_MARKER = "@@session-start@@";

    private static final int MAX_ENTRIES = 50;
    private static final int MERGE_DEPTH = 10;
    private static final long UI_THROTTLE_MS = 150;

    // Эмодзи/пиктограммы вырезаем — на 4.4 для них нет шрифта и вместо них «квадратики».
    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F300}-\\x{1F5FF}\\x{1F900}-\\x{1F9FF}\\x{1F600}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}"
                    + "\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}\\x{2B50}\\x{2B55}"
                    + "\\x{23E9}-\\x{23FA}\\x{25B6}\\x{FE0F}]");

    private static final String[] ESSENTIAL = {
            "pool", "key:", "started", "address:", "error", "failed", "blocked",
            "Пул", "Ключ:", "запущен", "Адрес:", "ошибка", "провалены", "заблокирован"
    };

    private static final Object LOCK = new Object();
    private static final List<LogEntry> logs = new ArrayList<LogEntry>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile Listener listener;
    private static volatile boolean active;
    private static Thread reader;
    private static Process process;
    private static boolean notifyScheduled;

    private static final Runnable NOTIFY = new Runnable() {
        @Override
        public void run() {
            synchronized (LOCK) {
                notifyScheduled = false;
            }
            Listener l = listener;
            if (l != null) l.onLogsChanged();
        }
    };

    private LogManager() {}

    /** Копия текущего списка (безопасно читать из UI-потока). */
    public static List<LogEntry> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<LogEntry>(logs);
        }
    }

    public static synchronized void startListening(Listener l) {
        listener = l;
        if (active) return;
        active = true;
        synchronized (LOCK) {
            logs.clear();
        }
        reader = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "LogReader");
        reader.setDaemon(true);
        reader.start();
    }

    public static synchronized void stopListening() {
        listener = null;
        active = false;
        Process p = process;
        process = null;
        if (p != null) {
            try {
                p.destroy();
            } catch (Throwable ignored) {
            }
        }
        reader = null;
    }

    public static void clearLogs() {
        synchronized (LOCK) {
            logs.clear();
        }
        scheduleNotify();
    }

    private static void readLoop() {
        Process p = null;
        try {
            p = new ProcessBuilder("logcat", "-v", "tag", TAG + ":V", "*:S")
                    .redirectErrorStream(true)
                    .start();
            synchronized (LogManager.class) {
                if (!active) {
                    p.destroy();
                    return;
                }
                process = p;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            try {
                String line;
                while (active && (line = br.readLine()) != null) {
                    handleLine(line);
                }
            } finally {
                try {
                    br.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
            // logcat недоступен — просто не показываем логи
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void handleLine(String raw) {
        if (raw.contains(SESSION_MARKER)) {
            synchronized (LOCK) {
                logs.clear();
            }
            scheduleNotify();
            return;
        }
        LogEntry e = parseLine(raw);
        if (e == null) return;
        synchronized (LOCK) {
            addMerged(e);
        }
        scheduleNotify();
    }

    // вызывать под LOCK
    private static void addMerged(LogEntry entry) {
        int size = logs.size();
        int stop = Math.max(0, size - MERGE_DEPTH);
        for (int i = size - 1; i >= stop; i--) {
            LogEntry ex = logs.get(i);
            if (ex.message.equals(entry.message)) {
                logs.remove(i);
                logs.add(new LogEntry(ex.message, ex.count + 1, ex.priority, ex.isError, ex.isEssential));
                return;
            }
        }
        logs.add(entry);
        while (logs.size() > MAX_ENTRIES) {
            logs.remove(0);
        }
    }

    private static void scheduleNotify() {
        synchronized (LOCK) {
            if (notifyScheduled) return;
            notifyScheduled = true;
        }
        MAIN.postDelayed(NOTIFY, UI_THROTTLE_MS);
    }

    private static String after(String s, String marker) {
        int i = s.indexOf(marker);
        return i < 0 ? s : s.substring(i + marker.length());
    }

    static LogEntry parseLine(String raw) {
        String message;
        boolean isError;
        int priority;

        if (raw.contains("[ERROR]")) {
            message = after(raw, "[ERROR]").trim();
            isError = true;
            priority = 6;
        } else if (raw.contains("[WARN]")) {
            message = after(raw, "[WARN]").trim();
            isError = false;
            priority = 5;
        } else if (raw.contains("[DEBUG]")) {
            message = after(raw, "[DEBUG]").trim();
            isError = false;
            priority = 3;
        } else if (raw.contains(TAG)) {
            String msg = after(raw, TAG + ":").trim();
            // Убираем меняющиеся метрики (↑3.3KB ↓1.1KB), чтобы одинаковые строки схлопывались.
            int up = msg.indexOf('\u2191');
            if (up >= 0) msg = msg.substring(0, up).trim();
            int down = msg.indexOf('\u2193');
            if (down >= 0) msg = msg.substring(0, down).trim();
            message = msg;
            isError = false;
            priority = 4;
        } else {
            return null;
        }

        message = EMOJI.matcher(message).replaceAll("").trim();
        if (message.length() == 0) return null;

        String lower = message.toLowerCase(Locale.ROOT);
        boolean essential = false;
        for (String marker : ESSENTIAL) {
            if (lower.contains(marker.toLowerCase(Locale.ROOT))) {
                essential = true;
                break;
            }
        }
        return new LogEntry(message, 1, priority, isError, essential);
    }
}
