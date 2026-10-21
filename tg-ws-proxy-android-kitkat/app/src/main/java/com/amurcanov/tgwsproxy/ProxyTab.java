package com.amurcanov.tgwsproxy;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Главная вкладка: статус, кнопка запуска/остановки, применение прокси в Telegram. */
final class ProxyTab {

    private static final String[] TELEGRAM_PACKAGES = {
            "org.telegram.messenger", "com.radolyn.ayugram", "com.exteragram.messenger",
            "org.telegram.plus", "ir.ilmili.telegraph", "org.telegram.BifToGram",
            "tw.nekomimi.nekogram", "xyz.nextalone.nagram", "uz.unnarsx.cherrygram",
            "org.telegram.mdgram", "org.forkclient.messenger.beta", "app.nicegram",
            "top.qwq2333.nullgram", "com.iMe.android", "ru.dahl.messenger",
            "com.scriptsaz.litegram", "org.thunderdog.challegram"
    };

    private final Activity act;
    private final SettingsStore store;
    private final ScrollView root;

    private final TextView errorBanner;
    private final TextView statusText;
    private final TextView infoText;
    private final Button mainBtn;
    private final Button applyBtn;
    private final Button copyBtn;
    private final TextView linkText;

    ProxyTab(Activity activity, SettingsStore settings, boolean dark) {
        act = activity;
        store = settings;

        root = new ScrollView(act);
        LinearLayout col = Ui.vbox(act);
        int pad = Ui.dp(act, 16);
        col.setPadding(pad, pad, pad, pad);
        root.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView appTitle = Ui.text(act, act.getString(R.string.app_name), 24);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(appTitle);

        TextView desc = Ui.hint(act, act.getString(R.string.hero_description));
        col.addView(desc, Ui.lpMargins(act, -1, -2, 0, 4, 0, 12));

        errorBanner = Ui.text(act, "", 14);
        errorBanner.setTextColor(0xFFFFFFFF);
        int bp = Ui.dp(act, 12);
        errorBanner.setPadding(bp, bp, bp, bp);
        Ui.setBackground(errorBanner, Ui.rounded(act, Ui.RED, 8));
        errorBanner.setVisibility(View.GONE);
        col.addView(errorBanner, Ui.lpMargins(act, -1, -2, 0, 0, 0, 12));

        LinearLayout statusCard = Ui.card(act, dark);
        statusText = Ui.text(act, "", 22);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        infoText = Ui.hint(act, "");
        statusCard.addView(statusText);
        statusCard.addView(infoText, Ui.lpMargins(act, -1, -2, 0, 4, 0, 0));
        col.addView(statusCard, Ui.lpMargins(act, -1, -2, 0, 0, 0, 12));

        mainBtn = Ui.button(act, "", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMainClick();
            }
        });
        mainBtn.setTextSize(20);
        col.addView(mainBtn, Ui.lpMargins(act, -1, Ui.dp(act, 64), 0, 0, 0, 8));

        applyBtn = Ui.button(act, act.getString(R.string.apply_in_telegram), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyToTelegram(buildUrl());
            }
        });
        copyBtn = Ui.button(act, act.getString(R.string.copy_link), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.copyToClipboard(act, "proxy", buildUrl());
                Ui.toast(act, R.string.copied);
            }
        });
        LinearLayout row = Ui.hbox(act);
        row.addView(applyBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(copyBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(row, Ui.lp(-1, -2));

        LinearLayout linkCard = Ui.card(act, dark);
        linkCard.addView(Ui.title(act, act.getString(R.string.proxy_link)));
        linkText = Ui.text(act, "", 12);
        linkText.setTypeface(Typeface.MONOSPACE);
        linkText.setGravity(Gravity.START);
        linkText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.copyToClipboard(act, "proxy", buildUrl());
                Ui.toast(act, R.string.copied);
            }
        });
        linkCard.addView(linkText, Ui.lpMargins(act, -1, -2, 0, 6, 0, 4));
        linkCard.addView(Ui.hint(act, act.getString(R.string.link_hint)));
        col.addView(linkCard, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        refresh();
    }

    View getView() {
        return root;
    }

    /** Показывает ошибку загрузки нативной библиотеки (null — скрыть). */
    void setNativeError(String err) {
        if (err == null) {
            errorBanner.setVisibility(View.GONE);
        } else {
            errorBanner.setText(act.getString(R.string.native_load_error, err));
            errorBanner.setVisibility(View.VISIBLE);
        }
    }

    private String buildUrl() {
        int port;
        try {
            port = Integer.parseInt(store.getPort().trim());
        } catch (NumberFormatException e) {
            port = 1443;
        }
        String secret = store.getSecretKey().trim();
        if (secret.length() == 0) secret = "00000000000000000000000000000000";
        String ip = store.getBindIp().trim();
        if (ip.length() == 0) ip = SettingsStore.DEFAULT_BIND_IP;
        return "https://t.me/proxy?server=" + ip + "&port=" + port + "&secret=dd" + secret;
    }

    private void onMainClick() {
        if (ProxyService.isRunning() || ProxyService.isBusy()) {
            ProxyController.stop(act);
        } else {
            ProxyController.startFromSavedSettings(act, true);
        }
        refresh();
    }

    void refresh() {
        boolean verified = ProxyService.isVerifiedRunning();
        boolean working = ProxyService.isRunning() || ProxyService.isBusy();

        if (verified) {
            statusText.setText(R.string.status_connected);
            statusText.setTextColor(Ui.GREEN);
        } else if (working) {
            statusText.setText(R.string.status_connecting);
            statusText.setTextColor(Ui.ORANGE);
        } else {
            statusText.setText(R.string.status_disconnected);
            statusText.setTextColor(Ui.GREY);
        }

        mainBtn.setText(working ? R.string.btn_stop : R.string.btn_start);
        applyBtn.setEnabled(verified);
        copyBtn.setEnabled(true);

        String mode = store.isCfEnabled() ? "CF" : act.getString(R.string.direct_mode);
        infoText.setText(mode + "  \u00b7  "
                + act.getString(R.string.pool_short, store.getPoolSize()) + "  \u00b7  "
                + act.getString(R.string.port_short, store.getPort()) + "  \u00b7  v"
                + BuildConfig.VERSION_NAME);

        linkText.setText(buildUrl());
    }

    // ---- открыть ссылку в установленных клиентах Telegram ----

    private void applyToTelegram(String url) {
        PackageManager pm = act.getPackageManager();
        List<Intent> targets = new ArrayList<Intent>();
        for (String pkg : TELEGRAM_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.setPackage(pkg);
                targets.add(i);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        if (targets.isEmpty()) {
            Toast.makeText(act, R.string.telegram_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (targets.size() == 1) {
                act.startActivity(targets.get(0));
            } else {
                Intent chooser = Intent.createChooser(targets.get(0), act.getString(R.string.chooser_title));
                Intent[] rest = targets.subList(1, targets.size()).toArray(new Intent[0]);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, rest);
                act.startActivity(chooser);
            }
        } catch (Exception e) {
            Toast.makeText(act, R.string.telegram_not_found, Toast.LENGTH_SHORT).show();
        }
    }
}
