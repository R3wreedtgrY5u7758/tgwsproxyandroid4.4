package com.amurcanov.tgwsproxy;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Единственная Activity. Вместо Jetpack Compose — обычные View из кода,
 * вместо Material3 — штатная тема Holo (есть в Android 4.x).
 */
public class MainActivity extends Activity implements SettingsTab.Host {

    private static final String STATE_TAB = "tab";

    private SettingsStore store;
    private boolean dark;

    private ProxyTab proxyTab;
    private SettingsTab settingsTab;
    private LogsTab logsTab;
    private InfoTab infoTab;
    private View[] tabViews;
    private TextView[] tabButtons;
    private FrameLayout content;
    private int selected;

    private final Handler main = new Handler(Looper.getMainLooper());

    private final Runnable serviceListener = new Runnable() {
        @Override
        public void run() {
            if (proxyTab != null) proxyTab.refresh();
            if (settingsTab != null) settingsTab.refresh();
        }
    };

    private final LogManager.Listener logListener = new LogManager.Listener() {
        @Override
        public void onLogsChanged() {
            if (logsTab != null && selected == 2) logsTab.refresh();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        String lang = new SettingsStore(base).getLang();
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);   // API 17+
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = new SettingsStore(this);
        dark = store.isDark();
        setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);

        proxyTab = new ProxyTab(this, store, dark);
        settingsTab = new SettingsTab(this, store, dark, this);
        logsTab = new LogsTab(this, store);
        infoTab = new InfoTab(this, store, dark);
        tabViews = new View[]{proxyTab.getView(), settingsTab.getView(), logsTab.getView(), infoTab.getView()};

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(dark ? 0xFF1B1D20 : 0xFFFFFFFF);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundColor(dark ? 0xFF25282C : 0xFFEAEFF4);
        int[] labels = {R.string.nav_proxy, R.string.settings, R.string.nav_logs, R.string.info};
        tabButtons = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER);
            int p = Ui.dp(this, 14);
            tv.setPadding(0, p, 0, p);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(idx);
                }
            });
            tabButtons[i] = tv;
            bar.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        selectTab(savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, 0) : 0);

        // Проверяем нативную библиотеку в фоне: при ошибке покажем понятный баннер, а не упадём.
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final String err = NativeProxy.checkAvailable();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (proxyTab != null) proxyTab.setNativeError(err);
                    }
                });
            }
        }, "NativeCheck");
        t.setDaemon(true);
        t.start();
    }

    private void selectTab(int idx) {
        selected = idx;
        content.removeAllViews();
        content.addView(tabViews[idx], new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < tabButtons.length; i++) {
            boolean on = i == idx;
            tabButtons[i].setTextColor(on ? Ui.ACCENT : (dark ? 0xFFB5BAC1 : 0xFF5C6470));
            tabButtons[i].setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (idx == 0) proxyTab.refresh();
        if (idx == 1) settingsTab.refresh();
        if (idx == 2) logsTab.refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ProxyService.addListener(serviceListener);
        LogManager.startListening(logListener);
        serviceListener.run();
    }

    @Override
    protected void onStop() {
        ProxyService.removeListener(serviceListener);
        LogManager.stopListening();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selected);
    }

    @Override
    public void onAppearanceChanged() {
        recreate();   // API 11+
    }
}
