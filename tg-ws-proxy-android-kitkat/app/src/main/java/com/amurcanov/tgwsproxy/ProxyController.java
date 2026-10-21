package com.amurcanov.tgwsproxy;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Запуск/остановка ProxyService по сохранённым настройкам. */
public final class ProxyController {

    private ProxyController() {}

    /** @return true, если запуск запрошен; false — если настройки неверны (например, порт). */
    public static boolean startFromSavedSettings(Context context, boolean showInvalidPortToast) {
        SettingsStore s = new SettingsStore(context);

        int port;
        try {
            port = Integer.parseInt(s.getPort().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException("range");
        } catch (NumberFormatException e) {
            if (showInvalidPortToast) {
                Toast.makeText(context, context.getString(R.string.invalid_port), Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        String bindIp = s.getBindIp().trim();
        if (bindIp.length() == 0) bindIp = SettingsStore.DEFAULT_BIND_IP;

        boolean cfEnabled = s.isCfEnabled();
        boolean customCf = s.isCustomCfDomainEnabled();
        String customCfDomain = s.getCustomCfDomain().trim();
        String secret = s.ensureSecretKey();

        List<String> dcs = new ArrayList<String>();
        if (!s.isDcAuto()) {
            // DC1..DC4 — всегда; DC5/DC203 и media-варианты — только в экспериментальном режиме.
            for (int i = 0; i < 4; i++) {
                appendDc(dcs, SettingsStore.DC_IDS[i], s.getDc(SettingsStore.DC_KEYS[i]));
            }
            if (s.isExperimental()) {
                appendDc(dcs, SettingsStore.DC_IDS[4], s.getDc(SettingsStore.DC_KEYS[4]));
                appendDc(dcs, SettingsStore.DC_IDS[5], s.getDc(SettingsStore.DC_KEYS[5]));
                for (int i = 0; i < 6; i++) {
                    appendDc(dcs, -SettingsStore.DC_IDS[i], s.getDc(SettingsStore.DC_MEDIA_KEYS[i]));
                }
            }
        }
        StringBuilder ips = new StringBuilder();
        for (int i = 0; i < dcs.size(); i++) {
            if (i > 0) ips.append(',');
            ips.append(dcs.get(i));
        }

        Intent i = new Intent(context, ProxyService.class);
        i.setAction(ProxyService.ACTION_START);
        i.putExtra(ProxyService.EXTRA_BIND_IP, bindIp);
        i.putExtra(ProxyService.EXTRA_PORT, port);
        i.putExtra(ProxyService.EXTRA_IPS, ips.toString());
        i.putExtra(ProxyService.EXTRA_POOL_SIZE, s.getPoolSize());
        i.putExtra(ProxyService.EXTRA_CFPROXY_ENABLED, cfEnabled);
        i.putExtra(ProxyService.EXTRA_CFPROXY_PRIORITY, true);
        i.putExtra(ProxyService.EXTRA_CFPROXY_DOMAIN, (customCf && cfEnabled) ? customCfDomain : "");
        i.putExtra(ProxyService.EXTRA_SECRET_KEY, secret);

        ProxyService.markBusy();
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        } catch (RuntimeException e) {
            ProxyService.clearBusy();
            return false;
        }
        return true;
    }

    public static void stop(Context context) {
        Intent i = new Intent(context, ProxyService.class);
        i.setAction(ProxyService.ACTION_STOP);
        context.startService(i);
    }

    private static void appendDc(List<String> out, int dc, String value) {
        String ip = value == null ? "" : value.trim();
        if (ip.length() > 0) out.add(dc + ":" + ip);
    }
}
