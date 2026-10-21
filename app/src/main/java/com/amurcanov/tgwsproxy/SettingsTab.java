package com.amurcanov.tgwsproxy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Вкладка «Настройки». Все изменения сохраняются сразу; во время работы прокси поля блокируются. */
final class SettingsTab {

    interface Host {
        /** Применить смену темы/языка (пересоздать Activity). */
        void onAppearanceChanged();
    }

    private final Activity act;
    private final SettingsStore store;
    private final Host host;
    private final boolean dark;
    private final ScrollView root;

    private final TextView lockedHint;
    private final EditText ipEdit;
    private final EditText portEdit;
    private final CheckBox cfCheck;
    private final CheckBox customCfCheck;
    private final EditText customCfEdit;
    private final Button dcButton;
    private final RadioButton[] poolButtons = new RadioButton[3];
    private final int[] poolValues = {2, 4, 6};
    private final TextView secretText;
    private final Button secretBtn;
    private final CheckBox autostartCheck;

    private boolean binding;   // true, пока мы сами выставляем значения в элементах

    SettingsTab(Activity activity, SettingsStore settings, boolean isDark, Host h) {
        act = activity;
        store = settings;
        host = h;
        dark = isDark;

        root = new ScrollView(act);
        LinearLayout col = Ui.vbox(act);
        int pad = Ui.dp(act, 16);
        col.setPadding(pad, pad, pad, pad);
        root.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        lockedHint = Ui.text(act, act.getString(R.string.settings_locked), 13);
        lockedHint.setTextColor(Ui.ORANGE);
        lockedHint.setVisibility(View.GONE);
        col.addView(lockedHint, Ui.lpMargins(act, -1, -2, 0, 0, 0, 8));

        // ---- Подключение ----
        LinearLayout conn = Ui.card(act, dark);
        conn.addView(Ui.title(act, act.getString(R.string.connection)));

        ipEdit = new EditText(act);
        ipEdit.setSingleLine(true);
        ipEdit.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        ipEdit.setHint("127.0.0.1");
        ipEdit.addTextChangedListener(new Watcher() {
            @Override
            void changed(String v) {
                store.setBindIp(v.trim());
            }
        });
        conn.addView(Ui.hint(act, "IP"), Ui.lpMargins(act, -1, -2, 0, 8, 0, 0));
        conn.addView(ipEdit, Ui.lp(-1, -2));

        portEdit = new EditText(act);
        portEdit.setSingleLine(true);
        portEdit.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        portEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        portEdit.setHint(SettingsStore.DEFAULT_PORT);
        portEdit.addTextChangedListener(new Watcher() {
            @Override
            void changed(String v) {
                store.setPort(v.trim());
            }
        });
        conn.addView(Ui.hint(act, act.getString(R.string.port)), Ui.lpMargins(act, -1, -2, 0, 8, 0, 0));
        conn.addView(portEdit, Ui.lp(-1, -2));
        col.addView(conn, Ui.lp(-1, -2));

        // ---- Маршрут ----
        LinearLayout route = Ui.card(act, dark);
        route.addView(Ui.title(act, "Cloudflare / DC"));

        cfCheck = new CheckBox(act);
        cfCheck.setText(R.string.auto_cf_enabled);
        cfCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (binding) return;
                store.setCfEnabled(checked);
                store.setDcAuto(checked);   // как в оригинале: CF включён <=> адреса DC автоматические
                refresh();
            }
        });
        route.addView(cfCheck, Ui.lpMargins(act, -1, -2, 0, 6, 0, 0));

        customCfCheck = new CheckBox(act);
        customCfCheck.setText(R.string.custom_cf_domain);
        customCfCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (binding) return;
                store.setCustomCfDomainEnabled(checked);
                refresh();
            }
        });
        route.addView(customCfCheck);

        customCfEdit = new EditText(act);
        customCfEdit.setSingleLine(true);
        customCfEdit.setHint(R.string.custom_cf_domain_hint);
        customCfEdit.addTextChangedListener(new Watcher() {
            @Override
            void changed(String v) {
                store.setCustomCfDomain(v.trim());
            }
        });
        route.addView(customCfEdit, Ui.lp(-1, -2));

        dcButton = Ui.button(act, act.getString(R.string.configure_dc_addresses), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDcDialog();
            }
        });
        route.addView(dcButton, Ui.lpMargins(act, -1, -2, 0, 8, 0, 0));
        col.addView(route, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        // ---- Пул ----
        LinearLayout pool = Ui.card(act, dark);
        pool.addView(Ui.title(act, act.getString(R.string.ws_pool)));
        RadioGroup group = new RadioGroup(act);
        group.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(act);
            rb.setText("x" + poolValues[i]);
            rb.setId(1000 + i);
            poolButtons[i] = rb;
            group.addView(rb, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                if (binding) return;
                int idx = checkedId - 1000;
                if (idx >= 0 && idx < poolValues.length) store.setPoolSize(poolValues[idx]);
            }
        });
        pool.addView(group, Ui.lpMargins(act, -1, -2, 0, 6, 0, 0));
        col.addView(pool, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        // ---- Секретный ключ ----
        LinearLayout sec = Ui.card(act, dark);
        sec.addView(Ui.title(act, act.getString(R.string.secret_key)));
        secretText = Ui.text(act, "", 13);
        secretText.setTypeface(android.graphics.Typeface.MONOSPACE);
        secretText.setTextIsSelectable(true);
        sec.addView(secretText, Ui.lpMargins(act, -1, -2, 0, 6, 0, 0));
        secretBtn = Ui.button(act, act.getString(R.string.regenerate), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.setSecretKey(SettingsStore.generateSecret());
                refresh();
            }
        });
        sec.addView(secretBtn);
        col.addView(sec, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        // ---- Прочее ----
        LinearLayout misc = Ui.card(act, dark);
        autostartCheck = new CheckBox(act);
        autostartCheck.setText(R.string.autostart);
        autostartCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (!binding) store.setAutoStart(checked);
            }
        });
        misc.addView(autostartCheck);
        col.addView(misc, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        // ---- Оформление: тема и язык ----
        LinearLayout look = Ui.card(act, dark);
        look.addView(Ui.title(act, act.getString(R.string.theme)));
        LinearLayout themeRow = Ui.hbox(act);
        themeRow.addView(choiceButton(act.getString(R.string.theme_light), !store.isDark(), new Runnable() {
            @Override
            public void run() {
                if (store.isDark()) {
                    store.setDark(false);
                    host.onAppearanceChanged();
                }
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        themeRow.addView(choiceButton(act.getString(R.string.theme_dark), store.isDark(), new Runnable() {
            @Override
            public void run() {
                if (!store.isDark()) {
                    store.setDark(true);
                    host.onAppearanceChanged();
                }
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        look.addView(themeRow);

        look.addView(Ui.title(act, act.getString(R.string.language)), Ui.lpMargins(act, -1, -2, 0, 10, 0, 0));
        LinearLayout langRow = Ui.hbox(act);
        langRow.addView(choiceButton(act.getString(R.string.lang_ru), "ru".equals(store.getLang()), new Runnable() {
            @Override
            public void run() {
                if (!"ru".equals(store.getLang())) {
                    store.setLang("ru");
                    host.onAppearanceChanged();
                }
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        langRow.addView(choiceButton(act.getString(R.string.lang_en), "en".equals(store.getLang()), new Runnable() {
            @Override
            public void run() {
                if (!"en".equals(store.getLang())) {
                    store.setLang("en");
                    host.onAppearanceChanged();
                }
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        look.addView(langRow);
        col.addView(look, Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));

        refresh();
    }

    View getView() {
        return root;
    }

    /** Кнопка-«переключатель»: выбранная подсвечена и неактивна. */
    private Button choiceButton(String label, boolean selected, final Runnable onClick) {
        Button b = Ui.button(act, label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });
        b.setEnabled(!selected);
        return b;
    }

    void refresh() {
        boolean locked = ProxyService.isRunning() || ProxyService.isBusy();
        binding = true;
        try {
            setTextIfDifferent(ipEdit, store.getBindIp());
            setTextIfDifferent(portEdit, store.getPort());
            cfCheck.setChecked(store.isCfEnabled());
            customCfCheck.setChecked(store.isCustomCfDomainEnabled());
            setTextIfDifferent(customCfEdit, store.getCustomCfDomain());
            int pool = store.getPoolSize();
            for (int i = 0; i < poolValues.length; i++) {
                poolButtons[i].setChecked(poolValues[i] == pool);
            }
            String secret = store.getSecretKey();
            secretText.setText(secret.length() == 0 ? "\u2014" : secret);
            autostartCheck.setChecked(store.isAutoStart());
        } finally {
            binding = false;
        }

        boolean cf = store.isCfEnabled();
        lockedHint.setVisibility(locked ? View.VISIBLE : View.GONE);
        ipEdit.setEnabled(!locked);
        portEdit.setEnabled(!locked);
        cfCheck.setEnabled(!locked);
        customCfCheck.setEnabled(!locked && cf);
        customCfEdit.setEnabled(!locked && cf && store.isCustomCfDomainEnabled());
        dcButton.setEnabled(!locked && !cf);
        for (RadioButton rb : poolButtons) rb.setEnabled(!locked);
        secretBtn.setEnabled(!locked);
    }

    private static void setTextIfDifferent(EditText e, String v) {
        if (!e.getText().toString().equals(v)) e.setText(v);
    }

    // ---- диалог адресов DC ----

    private void showDcDialog() {
        final ScrollView sv = new ScrollView(act);
        final LinearLayout box = Ui.vbox(act);
        int pad = Ui.dp(act, 16);
        box.setPadding(pad, pad, pad, pad);
        sv.addView(box);
        buildDcFields(box);

        new AlertDialog.Builder(act)
                .setTitle(R.string.dc_addresses)
                .setView(sv)
                .setPositiveButton(R.string.done, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                    }
                })
                .show();
    }

    private void buildDcFields(final LinearLayout box) {
        box.removeAllViews();

        final CheckBox exp = new CheckBox(act);
        exp.setText(R.string.experimental_mode);
        exp.setChecked(store.isExperimental());
        exp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                store.setExperimental(checked);
                buildDcFields(box);
            }
        });
        box.addView(exp);

        int count = store.isExperimental() ? SettingsStore.DC_KEYS.length : 4;
        for (int i = 0; i < count; i++) {
            addDcField(box, "DC" + SettingsStore.DC_IDS[i], SettingsStore.DC_KEYS[i]);
        }
        if (store.isExperimental()) {
            box.addView(Ui.title(act, act.getString(R.string.media_dcs)), Ui.lpMargins(act, -1, -2, 0, 12, 0, 0));
            for (int i = 0; i < SettingsStore.DC_MEDIA_KEYS.length; i++) {
                addDcField(box, "DC" + SettingsStore.DC_IDS[i] + " media", SettingsStore.DC_MEDIA_KEYS[i]);
            }
        }
    }

    private void addDcField(LinearLayout box, String label, final String key) {
        box.addView(Ui.hint(act, label), Ui.lpMargins(act, -1, -2, 0, 8, 0, 0));
        EditText e = new EditText(act);
        e.setSingleLine(true);
        e.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        e.setText(store.getDc(key));
        e.addTextChangedListener(new Watcher() {
            @Override
            void changed(String v) {
                store.setDc(key, v.trim());
            }
        });
        box.addView(e, Ui.lp(-1, -2));
    }

    private abstract class Watcher implements TextWatcher {
        abstract void changed(String v);

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (!binding) changed(s.toString());
        }
    }
}
