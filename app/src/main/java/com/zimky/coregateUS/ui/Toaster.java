package com.zimky.coregateUS.ui;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Toaster —— 页面内轻提示。
 *
 * 不用系统 Toast：系统 Toast 在 Android 11+ 强制带 App 图标、位置固定在底部、
 * 还会被系统限流，观感很「第三方」。这里自己在 Activity 的 root 上加一个胶囊，
 * 淡入 → 停留 → 淡出，完全可控。
 */
public class Toaster {

    private final FrameLayout root;
    private final Context ctx;
    private View current;

    public Toaster(Context ctx, FrameLayout root) {
        this.ctx = ctx;
        this.root = root;
    }

    public void show(String msg) { show(msg, false); }

    public void show(String msg, boolean isError) {
        if (msg == null || msg.isEmpty()) return;
        if (current != null) {
            root.removeView(current);
            current = null;
        }

        TextView tv = Ui.text(ctx, msg, 13f, isError ? Ui.ERR : Ui.TXT);
        int padH = Ui.dp(ctx, 16), padV = Ui.dp(ctx, 11);
        tv.setPadding(padH, padV, padH, padV);
        tv.setBackground(Ui.pill(ctx, 0xF21A202B, isError ? 0x66FF453A : 0x2EFFFFFF, 14f));
        tv.setMaxWidth((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.86f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(ctx, 72);
        tv.setLayoutParams(lp);
        tv.setAlpha(0f);
        tv.setTranslationY(-Ui.dpf(ctx, 8f));

        root.addView(tv);
        current = tv;

        tv.animate().alpha(1f).translationY(0f).setDuration(180).start();
        tv.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (current != tv) return;
                ObjectAnimator fade = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0f);
                fade.setDuration(220);
                fade.start();
                tv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (current == tv) {
                            root.removeView(tv);
                            current = null;
                        }
                    }
                }, 240);
            }
        }, 1800);
    }
}