package com.amurcanov.tgwsproxy;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

/**
 * Хранилище настроек на обычных SharedPreferences
 * (в оригинале — Jetpack DataStore, который на Android 4.4 не нужен и тяжёл).
 */
public final class SettingsStore {

    public static final String DEFAULT_DC_IP = "149.154.167.220";
    public static final String DEFAULT_PORT = "1443";
    public static final String DEFAULT_BIND_IP = "127.0.0.1";

    // Ключи адресов датацентров. Порядок = порядок в диалоге.
    public static final String[] DC_KEYS = {"dc1", "dc2", "dc3", "dc4", "dc5", "dc203"};
    public static final String[] DC_MEDIA_KEYS = {"dc1m", "dc2m", "dc3m", "dc4m", "dc5m", "dc203m"};
    public static final int[] DC_IDS = {1, 2, 3, 4, 5, 203};

    private static final String PREFS = "proxy_settings";

    private static final String K_PORT = "port";
    private static final String K_BIND_IP = "bind_ip";
    private static final String K_DC_AUTO = "dc_auto";
    private static final String K_EXPERIMENTAL = "experimental";
    private static final String K_POOL = "pool_size";
    private static final String K_CF = "cf_enabled";
    private static final String K_CF_CUSTOM_ON = "cf_custom_enabled";
    private static final String K_CF_CUSTOM = "cf_custom_domain";
    private static final String K_AUTOSTART = "autostart";
    private static final String K_SECRET = "secret_key";
    private static final String K_DARK = "theme_dark";
    private static final String K_LANG = "lang";
    private static final String K_LOG_INFO = "log_info";
    private static final String K_LOG_ERROR = "log_error";
    private static final String K_LOG_NULL = "log_null";

    private final SharedPreferences sp;

    public SettingsStore(Context context) {
        Context app = context.getApplicationContext();
        if (app == null) app = context;   // на всякий случай (вызов из attachBaseContext)
        sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- общие ----

    public String getPort() { return sp.getString(K_PORT, DEFAULT_PORT); }
    public void setPort(String v) { sp.edit().putString(K_PORT, v).apply(); }

    public String getBindIp() { return sp.getString(K_BIND_IP, DEFAULT_BIND_IP); }
    public void setBindIp(String v) { sp.edit().putString(K_BIND_IP, v).apply(); }

    public boolean isDcAuto() { return sp.getBoolean(K_DC_AUTO, true); }
    public void setDcAuto(boolean v) { sp.edit().putBoolean(K_DC_AUTO, v).apply(); }

    public boolean isExperimental() { return sp.getBoolean(K_EXPERIMENTAL, false); }
    public void setExperimental(boolean v) { sp.edit().putBoolean(K_EXPERIMENTAL, v).apply(); }

    public int getPoolSize() { return sp.getInt(K_POOL, 4); }
    public void setPoolSize(int v) { sp.edit().putInt(K_POOL, v).apply(); }

    public boolean isCfEnabled() { return sp.getBoolean(K_CF, true); }
    public void setCfEnabled(boolean v) { sp.edit().putBoolean(K_CF, v).apply(); }

    public boolean isCustomCfDomainEnabled() { return sp.getBoolean(K_CF_CUSTOM_ON, false); }
    public void setCustomCfDomainEnabled(boolean v) { sp.edit().putBoolean(K_CF_CUSTOM_ON, v).apply(); }

    public String getCustomCfDomain() { return sp.getString(K_CF_CUSTOM, ""); }
    public void setCustomCfDomain(String v) { sp.edit().putString(K_CF_CUSTOM, v).apply(); }

    public boolean isAutoStart() { return sp.getBoolean(K_AUTOSTART, false); }
    public void setAutoStart(boolean v) { sp.edit().putBoolean(K_AUTOSTART, v).apply(); }

    public boolean isDark() { return sp.getBoolean(K_DARK, false); }
    public void setDark(boolean v) { sp.edit().putBoolean(K_DARK, v).apply(); }

    /** "ru" или "en". По умолчанию — как в оригинале, русский. */
    public String getLang() { return sp.getString(K_LANG, "ru"); }
    public void setLang(String v) { sp.edit().putString(K_LANG, v).apply(); }

    public boolean isLogInfo() { return sp.getBoolean(K_LOG_INFO, true); }
    public boolean isLogError() { return sp.getBoolean(K_LOG_ERROR, false); }
    public boolean isLogNull() { return sp.getBoolean(K_LOG_NULL, false); }
    public void setLogFilters(boolean info, boolean error, boolean none) {
        sp.edit().putBoolean(K_LOG_INFO, info).putBoolean(K_LOG_ERROR, error)
                .putBoolean(K_LOG_NULL, none).apply();
    }

    // ---- адреса DC ----

    public String getDc(String key) {
        String def = ("dc2".equals(key) || "dc4".equals(key)) ? DEFAULT_DC_IP : "";
        return sp.getString(key, def);
    }

    public void setDc(String key, String value) { sp.edit().putString(key, value).apply(); }

    // ---- секретный ключ ----

    public String getSecretKey() { return sp.getString(K_SECRET, ""); }
    public void setSecretKey(String v) { sp.edit().putString(K_SECRET, v).apply(); }

    /** Возвращает валидный ключ (32 hex-символа), при необходимости генерирует и сохраняет новый. */
    public String ensureSecretKey() {
        String cur = getSecretKey().trim();
        if (isValidSecret(cur)) return cur;
        String gen = generateSecret();
        setSecretKey(gen);
        return gen;
    }

    public static String generateSecret() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xff));
        }
        return sb.toString();
    }

    public static boolean isValidSecret(String v) {
        if (v == null || v.length() != 32) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }
}
