package com.amurcanov.tgwsproxy;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Мелкие помощники для построения интерфейса из кода (без XML и без AppCompat). */
final class Ui {

    static final int ACCENT = 0xFF279FDA;
    static final int GREEN = 0xFF2E9E5B;
    static final int ORANGE = 0xFFE08A1E;
    static final int RED = 0xFFD64545;
    static final int GREY = 0xFF8A8F98;

    private Ui() {}

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpMargins(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    static TextView text(Context c, CharSequence t, float sp) {
        TextView tv = new TextView(c);
        tv.setText(t);
        tv.setTextSize(sp);
        return tv;
    }

    static TextView title(Context c, CharSequence t) {
        TextView tv = text(c, t, 16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(ACCENT);
        return tv;
    }

    static TextView hint(Context c, CharSequence t) {
        TextView tv = text(c, t, 13);
        tv.setTextColor(GREY);
        return tv;
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Карточка с отступами и фоном, подходящим под светлую/тёмную тему. */
    static LinearLayout card(Context c, boolean dark) {
        LinearLayout l = vbox(c);
        int pad = dp(c, 14);
        l.setPadding(pad, pad, pad, pad);
        setBackground(l, rounded(c, dark ? 0xFF2B2E33 : 0xFFF0F3F7, 10));
        return l;
    }

    @SuppressWarnings("deprecation")
    static void setBackground(View v, android.graphics.drawable.Drawable d) {
        v.setBackground(d);
    }

    static Button button(Context c, CharSequence label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        return v;
    }

    static void copyToClipboard(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    static void toast(Context c, int resId) {
        Toast.makeText(c, resId, Toast.LENGTH_SHORT).show();
    }

    static void openUrl(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(a, R.string.no_browser);
        }
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
