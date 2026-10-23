package com.zimky.coregateUS.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * PillView —— 状态胶囊（带发光圆点）。
 *
 * 对应 WebUI 的 .pill / .pill::before。左边一个会「呼吸」的小圆点 + 右侧文字。
 * 呼吸动画用 invalidate 的时间驱动，比 ValueAnimator 省事且不会叠加。
 */
public class PillView extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private String label = "";
    private int color = Ui.OK;
    private boolean breathing = true;

    public PillView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        txt.setTextSize(12.5f * d);
        txt.setFakeBoldText(true);
    }

    public void set(String text, int c) {
        label = text == null ? "" : text;
        color = c;
        invalidate();
    }

    public void setBreathing(boolean b) { breathing = b; invalidate(); }

    @Override
    protected void onMeasure(int ws, int hs) {
        Context c = getContext();
        float d = c.getResources().getDisplayMetrics().density;
        txt.getTextBounds(label, 0, label.length(), new android.graphics.Rect());
        float tw = txt.measureText(label);
        int w = (int) (tw + 30 * d);
        int h = (int) (28 * d);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();

        // 胶囊底
        bg.setColor(Ui.alpha(color, 0.16f));
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, h / 2f, h / 2f, bg);

        // 圆点 + 外发光
        float cx = 11 * d, cy = h / 2f, rad = 3.2f * d;
        dot.setColor(color);
        cv.drawCircle(cx, cy, rad, dot);

        // 呼吸：用系统时间做正弦，半径在 5~8dp 之间摆动
        long t = android.os.SystemClock.uptimeMillis();
        float phase = (float) Math.sin(t % 1800 / 1800.0 * Math.PI * 2);
        float gRad = rad + (4.5f + phase * 2.5f) * d * 0.5f;
        glow.setColor(Ui.alpha(color, 0.22f + 0.10f * (1 + phase)));
        cv.drawCircle(cx, cy, gRad, glow);

        // 文字
        txt.setColor(color);
        float baseline = h / 2f - (txt.descent() + txt.ascent()) / 2f;
        cv.drawText(label, 20 * d, baseline, txt);

        if (breathing) postInvalidateDelayed(60);
    }
}