package com.amurcanov.tgwsproxy;

import android.app.Activity;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Вкладка «Логи». Фильтры те же, что в оригинале: важные строки всегда, Info и Warn/Error — по галочкам. */
final class LogsTab {

    private final Activity act;
    private final SettingsStore store;
    private final LinearLayout root;
    private final ScrollView scroll;
    private final TextView logView;
    private final CheckBox infoCheck;
    private final CheckBox errorCheck;
    private final CheckBox nullCheck;
    private boolean binding;

    LogsTab(Activity activity, SettingsStore settings) {
        act = activity;
        store = settings;

        root = Ui.vbox(act);
        int pad = Ui.dp(act, 12);
        root.setPadding(pad, pad, pad, 0);

        LinearLayout head = Ui.hbox(act);
        TextView title = Ui.title(act, act.getString(R.string.event_log));
        title.setTextSize(18);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.button(act, act.getString(R.string.copy), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.copyToClipboard(act, "logs", logView.getText().toString());
                Ui.toast(act, R.string.copied);
            }
        }));
        head.addView(Ui.button(act, act.getString(R.string.clear), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogManager.clearLogs();
            }
        }));
        root.addView(head, Ui.lp(-1, -2));

        LinearLayout filters = Ui.hbox(act);
        infoCheck = filterBox("Info", filters);
        errorCheck = filterBox("Warn/Error", filters);
        nullCheck = filterBox("NULL", filters);
        root.addView(filters, Ui.lp(-1, -2));

        scroll = new ScrollView(act);
        logView = Ui.text(act, "", 12);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        int tp = Ui.dp(act, 4);
        logView.setPadding(tp, tp, tp, tp);
        scroll.addView(logView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        binding = true;
        infoCheck.setChecked(store.isLogInfo());
        errorCheck.setChecked(store.isLogError());
        nullCheck.setChecked(store.isLogNull());
        binding = false;

        refresh();
    }

    private CheckBox filterBox(String label, LinearLayout parent) {
        CheckBox cb = new CheckBox(act);
        cb.setText(label);
        cb.setTextSize(13);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (binding) return;
                store.setLogFilters(infoCheck.isChecked(), errorCheck.isChecked(), nullCheck.isChecked());
                refresh();
            }
        });
        parent.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return cb;
    }

    View getView() {
        return root;
    }

    void refresh() {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        if (nullCheck.isChecked()) {
            sb.append(act.getString(R.string.null_logs_disabled));
        } else {
            boolean showInfo = infoCheck.isChecked();
            boolean showErr = errorCheck.isChecked();
            List<LogEntry> all = LogManager.snapshot();
            for (LogEntry e : all) {
                boolean visible = e.isEssential
                        || (showInfo && e.priority == 4)
                        || (showErr && e.priority >= 5);
                if (!visible) continue;

                int start = sb.length();
                sb.append(e.message);
                if (e.count > 1) sb.append(" (x").append(String.valueOf(e.count)).append(")");
                int end = sb.length();
                sb.append('\n');

                int color = 0;
                if (e.priority >= 6) color = Ui.RED;
                else if (e.priority == 5) color = Ui.ORANGE;
                else if (e.priority == 3) color = Ui.GREY;
                if (color != 0) {
                    sb.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        logView.setText(sb);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}
