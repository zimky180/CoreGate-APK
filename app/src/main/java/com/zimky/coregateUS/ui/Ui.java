package com.zimky.coregateUS.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ui —— 色板、尺寸、文字样式的一站式工厂。
 *
 * 色值是母本 WebUI 的 :root 变量原样搬过来的，保证 APK 和 WebUI 观感一致。
 */
public final class Ui {

    // ---- 色板（对齐 webroot/index.html :root） ----
    public static final int ACC   = 0xFF4F8CFF;   // --acc  主色
    public static final int OK    = 0xFF34C759;   // --ok   正常
    public static final int WARN  = 0xFFFF9F0A;   // --warn 注意
    public static final int ERR   = 0xFFFF453A;   // --err  异常
    public static final int OFF   = 0xFF5A6474;   // --off  关闭
    public static final int TXT   = 0xFFEEF2F9;   // --txt  正文
    public static final int SUB   = 0xFF9AA5B8;   // --sub  次要

    public static final int CARD_BG      = 0x17FFFFFF;  // 卡片底色（白 9%）
    public static final int CARD_BG_SOLID= 0xFF161A22;  // 不透明兜底
    public static final int CARD_BORDER  = 0x21FFFFFF;  // rgba(255,255,255,.13)
    public static final int DIVIDER      = 0x14FFFFFF;

    // ---- 尺寸 ----
    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static float dpf(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    // ---- 文字 ----
    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 17f, TXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView sub(Context c, String s) {
        TextView t = text(c, s, 12.5f, SUB);
        t.setLineSpacing(dpf(c, 3f), 1f);
        return t;
    }

    public static TextView label(Context c, String s) {
        return text(c, s, 14f, TXT);
    }

    public static TextView hint(Context c, String s) {
        return text(c, s, 11.5f, SUB);
    }

    // ---- 布局 ----
    public static LinearLayout vert(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horz(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpW(int marginBottomDp, Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(c, marginBottomDp);
        return p;
    }

    public static LinearLayout.LayoutParams lpWeight(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        return p;
    }

    public static View spacer(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, hDp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        p.topMargin = dp(c, 12);
        p.bottomMargin = dp(c, 12);
        v.setLayoutParams(p);
        v.setBackgroundColor(DIVIDER);
        return v;
    }

    /** 一行的左右排列：左标题+副标题，右侧放控件。 */
    public static LinearLayout row(Context c, String title, String desc, View right) {
        LinearLayout row = horz(c);
        LinearLayout left = vert(c);
        left.addView(label(c, title));
        if (desc != null && !desc.isEmpty()) {
            TextView d = hint(c, desc);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.topMargin = dp(c, 3);
            d.setLayoutParams(dp);
            left.addView(d);
        }
        LinearLayout.LayoutParams lpl = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpl.rightMargin = dp(c, 12);
        row.addView(left, lpl);
        if (right != null) row.addView(right);
        return row;
    }

    /** 圆角背景（纯色）。 */
    public static android.graphics.drawable.Drawable roundRect(Context c, int color, float radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(dpf(c, radiusDp));
        return g;
    }

    /** 描边胶囊。 */
    public static android.graphics.drawable.Drawable pill(Context c, int fill, int stroke, float radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dpf(c, radiusDp));
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    public static int alpha(int color, float a) {
        int al = (int) (Color.alpha(color) * a);
        return Color.argb(al, Color.red(color), Color.green(color), Color.blue(color));
    }
}