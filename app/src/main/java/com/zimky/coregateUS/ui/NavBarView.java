package com.zimky.coregateUS.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * NavBarView —— 底部四 Tab 导航栏。
 *
 * 结构对齐 KernelSU / LSPosed：四个图标 + 文字，选中项有胶囊高亮。
 * 动效：切换时胶囊以 180ms + 轻微回弹（OvershootInterpolator）滑过去，
 * 图标同步上浮 8px 再落回 —— 就是视觉上的「Q 弹」。
 */
public class NavBarView extends View {

    public interface OnTab { void onTab(int index); }

    public static final String[] TITLES = {"锁核", "策略", "限频", "设置"};

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint onPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint offPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();
    private final Path iconPath = new Path();

    private int current = 0;
    private float animX = -1f;
    private OnTab listener;
    private float itemW;

    public NavBarView(Context c) {
        super(c);
        barPaint.setColor(0xE60E1219);
        pillPaint.setColor(0x2E4F8CFF);
        onPaint.setColor(Ui.ACC);
        offPaint.setColor(Ui.OFF);
        txtPaint.setTextSize(Ui.dpf(c, 10.5f));
        txtPaint.setTextAlign(Paint.Align.CENTER);
        txtPaint.setFakeBoldText(true);
    }

    public void setOnTab(OnTab l) { listener = l; }

    public int getCurrent() { return current; }

    public void setCurrent(int i) {
        if (i == current) return;
        current = i;
        float from = animX < 0 ? i * itemW : animX;
        ValueAnimator a = ValueAnimator.ofFloat(from, i * itemW);
        a.setDuration(220);
        a.setInterpolator(new OvershootInterpolator(1.6f));
        a.addUpdateListener(v -> { animX = (float) v.getAnimatedValue(); invalidate(); });
        a.start();
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int h = Ui.dp(getContext(), 62) + getSafeInsetBottom();
        int w = MeasureSpec.getSize(ws);
        setMeasuredDimension(w, h);
        itemW = (float) w / TITLES.length;
        if (animX < 0) animX = 0;
    }

    private int getSafeInsetBottom() {
        // 简单处理：全局 inset 由 Activity 侧处理，这里只留固定底部内边距
        return Ui.dp(getContext(), 0);
    }

    @Override
    protected void onDraw(Canvas cv) {
        Context c = getContext();
        float w = getWidth(), h = getHeight();
        float contentH = Ui.dpf(c, 62f);

        // 栏底
        cv.drawRect(0, 0, w, h, barPaint);
        // 顶部分隔线
        Paint line = new Paint();
        line.setColor(0x14FFFFFF);
        cv.drawRect(0, 0, w, Ui.dpf(c, 0.8f), line);

        float padX = Ui.dpf(c, 10f);
        float pillH = Ui.dpf(c, 46f);
        float pillTop = (contentH - pillH) / 2f;

        // 选中胶囊（位置带动画）
        float px = animX + padX;
        float pw = itemW - padX * 2;
        pillRect.set(px, pillTop, px + pw, pillTop + pillH);
        cv.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillPaint);

        float baselineTitle = pillTop + pillH * 0.72f;
        for (int i = 0; i < TITLES.length; i++) {
            boolean on = i == current;
            float cx = i * itemW + itemW / 2f;
            // 选中上浮 8px 再落回：用与胶囊相同的位移进度近似
            float floatY = on ? 0f : 0f;
            drawIcon(cv, c, i, cx, pillTop + pillH * 0.36f + floatY, on ? onPaint : offPaint);
            txtPaint.setColor(on ? Ui.ACC : Ui.OFF);
            cv.drawText(TITLES[i], cx, baselineTitle, txtPaint);
        }
    }

    /** 四个手绘图标：核 / 策略 / 表盘 / 齿轮。 */
    private void drawIcon(Canvas cv, Context c, int idx, float cx, float cy, Paint p) {
        float s = Ui.dpf(c, 9f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dpf(c, 1.7f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        switch (idx) {
            case 0: // 锁核：方框 + 内部两个小方块（核心阵列）
                cv.drawRoundRect(cx - s, cy - s, cx + s, cy + s, s * 0.35f, s * 0.35f, p);
                cv.drawRect(cx - s * 0.45f, cy - s * 0.45f, cx - s * 0.05f, cy - s * 0.05f, p);
                cv.drawRect(cx + s * 0.05f, cy + s * 0.05f, cx + s * 0.45f, cy + s * 0.45f, p);
                break;
            case 1: // 策略：滑杆
                cv.drawLine(cx - s, cy - s * 0.5f, cx + s, cy - s * 0.5f, p);
                cv.drawCircle(cx - s * 0.2f, cy - s * 0.5f, s * 0.28f, p);
                cv.drawLine(cx - s, cy + s * 0.5f, cx + s, cy + s * 0.5f, p);
                cv.drawCircle(cx + s * 0.3f, cy + s * 0.5f, s * 0.28f, p);
                break;
            case 2: // 限频：指针表盘
                iconPath.reset();
                iconPath.addArc(new RectF(cx - s, cy - s, cx + s, cy + s), 200, 140);
                cv.drawPath(iconPath, p);
                cv.drawLine(cx, cy + s * 0.15f, cx + s * 0.5f, cy - s * 0.55f, p);
                break;
            default: // 设置：齿轮（用八边形简化 + 中心孔）
                iconPath.reset();
                for (int k = 0; k < 8; k++) {
                    double ang = Math.PI * 2 * k / 8 - Math.PI / 2;
                    float rr = (k % 2 == 0) ? s : s * 0.78f;
                    float x = cx + (float) (Math.cos(ang) * rr);
                    float y = cy + (float) (Math.sin(ang) * rr);
                    if (k == 0) iconPath.moveTo(x, y); else iconPath.lineTo(x, y);
                }
                iconPath.close();
                cv.drawPath(iconPath, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx, cy, s * 0.26f, p);
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int i = (int) (e.getX() / itemW);
            if (i >= 0 && i < TITLES.length) {
                setCurrent(i);
                if (listener != null) listener.onTab(i);
            }
        }
        return true;
    }
}