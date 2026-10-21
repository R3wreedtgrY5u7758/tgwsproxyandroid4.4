package com.amurcanov.tgwsproxy;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Вкладка «Инфо»: справка, ссылки и сборка отчёта для багрепорта. */
final class InfoTab {

    private static final String REPO_URL = "https://github.com/amurcanov/tg-ws-proxy-android";
    private static final String RELEASES_URL = REPO_URL + "/releases";
    private static final String ISSUES_URL = REPO_URL + "/issues";
    private static final String AUTHOR_URL = "https://github.com/amurcanov";
    private static final String ORIGINAL_URL = "https://github.com/Flowseal/tg-ws-proxy";
    private static final String REFERENCE_URL = "https://github.com/Flowseal/tg-ws-proxy/issues/389";

    private final Activity act;
    private final SettingsStore store;
    private final ScrollView root;

    InfoTab(Activity activity, SettingsStore settings, boolean dark) {
        act = activity;
        store = settings;

        root = new ScrollView(act);
        LinearLayout col = Ui.vbox(act);
        int pad = Ui.dp(act, 16);
        col.setPadding(pad, pad, pad, pad);
        root.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // О приложении
        LinearLayout about = Ui.card(act, dark);
        TextView name = Ui.text(act, act.getString(R.string.app_name) + "  v" + BuildConfig.VERSION_NAME, 18);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        about.addView(name);
        about.addView(Ui.hint(act, act.getString(R.string.kitkat_about)), Ui.lpMargins(act, -1, -2, 0, 6, 0, 0));
        col.addView(about, Ui.lp(-1, -2));

        // Действия
        LinearLayout actions = Ui.card(act, dark);
        actions.addView(Ui.title(act, act.getString(R.string.actions)));
        actions.addView(Ui.button(act, act.getString(R.string.build_report), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.copyToClipboard(act, "report", buildReport());
                Ui.toast(act, R.string.report_copied);
            }
        }));
        actions.addView(Ui.hint(act, act.getString(R.string.report_subtitle)));
        actions.addView(link(act.getString(R.string.open_github_issue), ISSUES_URL));
        actions.addView(link(act.getString(R.string.releases_page), RELEASES_URL));
        col.addView(actions, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        // Справка
        LinearLayout help = Ui.card(act, dark);
        help.addView(Ui.title(act, act.getString(R.string.help)));
        addHelp(help, act.getString(R.string.help_auto_dc_title), act.getString(R.string.help_auto_dc_text));
        addHelp(help, act.getString(R.string.auto_cf_enabled), act.getString(R.string.help_cloudflare_text));
        addHelp(help, act.getString(R.string.ws_pool), act.getString(R.string.help_ws_pool_text));
        addHelp(help, act.getString(R.string.secret_key), act.getString(R.string.help_secret_key_text));
        addHelp(help, act.getString(R.string.experimental_mode), act.getString(R.string.help_experimental_text));
        addHelp(help, act.getString(R.string.autostart), act.getString(R.string.help_autostart_text));
        addHelp(help, act.getString(R.string.help_slow_connect_title), act.getString(R.string.help_slow_connect_text));
        col.addView(help, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        // О проекте
        LinearLayout links = Ui.card(act, dark);
        links.addView(Ui.title(act, act.getString(R.string.about_project)));
        links.addView(link(act.getString(R.string.android_author), AUTHOR_URL));
        links.addView(link(act.getString(R.string.android_fork_repo), REPO_URL));
        links.addView(link(act.getString(R.string.original_tg_ws_proxy), ORIGINAL_URL));
        links.addView(link(act.getString(R.string.useful_material), REFERENCE_URL));
        col.addView(links, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));
    }

    View getView() {
        return root;
    }

    private View link(String label, final String url) {
        return Ui.button(act, label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.openUrl(act, url);
            }
        });
    }

    private void addHelp(LinearLayout parent, String title, String body) {
        TextView t = Ui.text(act, title, 15);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        parent.addView(t, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));
        TextView b = Ui.text(act, body, 13);
        parent.addView(b, Ui.lpMargins(act, -1, -2, 0, 2, 0, 0));
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(act.getString(R.string.report_app_version, BuildConfig.VERSION_NAME)).append('\n');
        sb.append(act.getString(R.string.report_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)).append('\n');
        sb.append(act.getString(R.string.report_device, Build.MANUFACTURER, Build.BRAND, Build.MODEL)).append('\n');
        sb.append(act.getString(R.string.report_device_code, Build.DEVICE)).append('\n');
        sb.append(act.getString(R.string.report_product, Build.PRODUCT)).append('\n');
        sb.append(act.getString(R.string.report_all_abi, Build.CPU_ABI + ", " + Build.CPU_ABI2)).append('\n');
        sb.append('\n').append(act.getString(R.string.report_settings)).append('\n');

        String mode = store.isCfEnabled() ? "Cloudflare" : act.getString(R.string.report_mode_direct);
        sb.append(act.getString(R.string.report_mode, mode)).append('\n');
        if (store.isCfEnabled() && store.isCustomCfDomainEnabled()) {
            sb.append(act.getString(R.string.report_cf_domain, store.getCustomCfDomain())).append('\n');
        }
        sb.append(act.getString(R.string.report_ws_pool, store.getPoolSize())).append('\n');
        sb.append(act.getString(R.string.report_port, store.getPort())).append('\n');

        String nativeErr = NativeProxy.checkAvailable();
        if (nativeErr != null) {
            sb.append('\n').append(act.getString(R.string.native_load_error, nativeErr)).append('\n');
        }

        sb.append('\n').append(act.getString(R.string.report_recent_errors)).append('\n');
        List<LogEntry> logs = LogManager.snapshot();
        int shown = 0;
        for (int i = logs.size() - 1; i >= 0 && shown < 10; i--) {
            LogEntry e = logs.get(i);
            if (e.priority >= 5) {
                sb.append("- ").append(e.message).append('\n');
                shown++;
            }
        }
        if (shown == 0) sb.append(act.getString(R.string.none_lower)).append('\n');
        return sb.toString();
    }
}
