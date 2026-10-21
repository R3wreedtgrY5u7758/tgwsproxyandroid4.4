package com.amurcanov.tgwsproxy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Автозапуск прокси после перезагрузки устройства (если включён в настройках). */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        try {
            SettingsStore store = new SettingsStore(context);
            if (!store.isAutoStart()) {
                Log.i(TAG, "Boot completed, autostart disabled");
                return;
            }
            boolean started = ProxyController.startFromSavedSettings(context, false);
            Log.i(TAG, "Boot completed, proxy autostart requested: " + started);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to autostart proxy after boot", t);
        }
    }
}
