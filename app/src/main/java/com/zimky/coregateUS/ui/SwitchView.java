package com.zimky.coregateUS.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * SwitchView —— 自绘开关。
 *
 * 尺寸严格对齐 WebUI 的 .switch：50×29 dp，滑块 21dp，勾选位移 21dp。
 * 不用 CheckBox/SwitchCompat：一来要甩掉 appcompat 依赖，二来系统控件样式压不住。
 */
public class SwitchView extends View {

    public interface OnChange { void onChanged(boolean value); }

    private static final float W_DP = 50f, H_DP = 29f, K_DP = 21f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float t = 0f;          // 0=关 1=开（动画插值）
    private boolean checked;
    private OnChange listener;
    private ValueAnimator anim;

    public SwitchView(Context c) {
        super(c);
        shadow.setColor(0x40000000);
        knob.setColor(0xFFFFFFFF);
        setFocusable(true);
        setClickable(true);
    }

    public boolean isChecked() { return checked; }

    public void setOnChange(OnChange l) { listener = l; }

    /** 静默设置（不回调，用于刷新界面时对齐状态）。 */
    public void setCheckedSilent(boolean v) {
        checked = v;
        t = v ? 1f : 0f;
        invalidate();
    }

    public void setChecked(boolean v) {
        if (checked == v) return;
        checked = v;
        animateTo(v ? 1f : 0f);
        if (listener != null) listener.onChanged(v);
    }

    private void animateTo(float target) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(t, target);
        anim.setDuration(180);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(Ui.dp(getContext(), W_DP), Ui.dp(getContext(), H_DP));
    }

    @Override
    protected void onDraw(Canvas cv) {
        Context c = getContext();
        float w = getWidth(), h = getHeight();
        float rad = h / 2f;

        // 轨道：关=灰，开=主色（插值过渡）
        int offCol = 0xFF2A3140;
        track.setColor(blend(offCol, Ui.ACC, t));
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, rad, rad, track);

        // 滑块
        float k = Ui.dpf(c, K_DP);
        float pad = (h - k) / 2f;
        float travel = w - k - pad * 2f;
        float left = pad + travel * t;
        float cy = h / 2f;
        // 轻投影
        shadow.setAlpha((int) (72 * (1 - 0.3f * t)));
        cv.drawCircle(left + k / 2f, cy + Ui.dpf(c, 1.2f), k / 2f, shadow);
        cv.drawCircle(left + k / 2f, cy, k / 2f, knob);
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * f);
        int gg = (int) (ag + (bg - ag) * f);
        int cc = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (rr << 16) | (gg << 8) | cc;
    }
}