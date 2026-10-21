package com.amurcanov.tgwsproxy;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * Мост к libtgwsproxy.so (Rust) через JNA — те же C-функции, что и в оригинале.
 * Библиотека грузится лениво, чтобы ошибку загрузки можно было показать в UI,
 * а не получить краш при старте приложения.
 */
public final class NativeProxy {

    /** Имена методов должны ТОЧНО совпадать с экспортируемыми функциями Rust. */
    public interface Lib extends Library {
        int StartProxy(String host, int port, String dcIps, String secret, int verbose);
        int StopProxy();
        void SetPoolSize(int size);
        void SetBufferSizeKb(int kb);
        void SetCfProxyCacheDir(String cacheDir);
        void SetCfProxyConfig(int enabled, int priority, String userDomain);
        void SetCfWorkerDomains(String domains);
        void SetFakeTlsDomain(String domain);
        void SetDisableSecure(int v);
        void SetForceTestDc(int v);
        void SetProxyProtocol(int v);
        Pointer GetSecretWithPrefix();
        Pointer GetStats();
        void FreeString(Pointer p);
    }

    private static Lib lib;
    private static Throwable loadError;

    private NativeProxy() {}

    private static synchronized Lib lib() {
        if (lib == null) {
            if (loadError != null) {
                throw new IllegalStateException(loadError.toString(), loadError);
            }
            try {
                lib = (Lib) Native.load("tgwsproxy", Lib.class);
            } catch (Throwable t) {
                loadError = t;
                throw new IllegalStateException(t.toString(), t);
            }
        }
        return lib;
    }

    /** null — библиотека загрузилась; иначе текст ошибки. Безопасно вызывать из фонового потока. */
    public static synchronized String checkAvailable() {
        try {
            lib();
            return null;
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            return String.valueOf(c.getMessage());
        }
    }

    public static int startProxy(String host, int port, String dcIps, String secret, int verbose) {
        return lib().StartProxy(host, port, dcIps, secret, verbose);
    }

    public static int stopProxy() {
        return lib().StopProxy();
    }

    public static void setPoolSize(int size) {
        lib().SetPoolSize(size);
    }

    public static void setCfProxyCacheDir(String dir) {
        lib().SetCfProxyCacheDir(dir);
    }

    public static void setCfProxyConfig(boolean enabled, boolean priority, String userDomain) {
        lib().SetCfProxyConfig(enabled ? 1 : 0, priority ? 1 : 0, userDomain);
    }

    public static String getStats() {
        Lib l = lib();
        Pointer p = l.GetStats();
        if (p == null) return null;
        String s = p.getString(0);
        l.FreeString(p);
        return s;
    }
}
