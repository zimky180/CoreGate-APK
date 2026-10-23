package com.zimky.coregateUS.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * SliderView —— 限频滑杆。
 *
 * 为什么不用 SeekBar：
 * 1) SeekBar 的刻度必须是等比的整数区间，而本机的频率档位是「不等比」的离散表
 *    （小核 23 档、大核 21 档、GPU 17 档，且相邻两档间隔几十到上百 MHz 不等）。
 *    用滑块索引（0..n-1）映射到真实档位，才能保证写进 sysfs 的值一定是内核存在的档。
 * 2) 需要画刻度点、拖拽时浮出数值气泡，SeekBar 改起来比重写还麻烦。
 *
 * 交互：拖动时实时回调 onChanged(index)，抬手时回调 onSettled(index)。
 * 上层只在 onSettled 里写文件，避免拖动过程中疯狂落盘。
 */
public class SliderView extends View {

    public interface Listener {
        /** 拖动过程中持续回调，用于更新上方的数值展示。 */
        void onChanged(int index);

        /** 抬手后回调，用于真正落盘。 */
        void onSettled(int index);
    }

    private final Paint trackOn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackOff = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private final List<Integer> steps = new ArrayList<>();
    private int index = 0;
    private boolean dragging = false;
    private Listener listener;

    private float padY;      // 上下留白（给滑块半径留空间）
    private float knobR;     // 滑块半径
    private float tickR;     // 刻度点半径

    public SliderView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        padY = 14f * d;
        knobR = 10.5f * d;   // 21dp 直径，对齐 WebUI 的 .slider thumb
        tickR = 1.6f * d;

        trackOn.setColor(Ui.ACC);
        trackOff.setColor(0x33FFFFFF);
        knobP.setColor(0xFFFFFFFF);
        knobRing.setColor(Ui.alpha(Ui.ACC, 0.55f));
        knobRing.setStyle(Paint.Style.STROKE);
        knobRing.setStrokeWidth(1.6f * d);
        tickP.setColor(0x66FFFFFF);
        shadowP.setColor(Ui.alpha(Ui.ACC, 0.30f));
        setClickable(true);
    }

    public void setListener(Listener l) { this.listener = l; }

    /** 传入真实档位表（升序）。值本身不参与计算，只用来决定刻度数量。 */
    public void setSteps(List<Integer> values) {
        steps.clear();
        if (values != null) steps.addAll(values);
        invalidate();
    }

    public int getIndex() { return index; }

    /** 静默设置（不触发回调），用于从 config 回填。 */
    public void setIndexSilent(int i) {
        if (steps.isEmpty()) { index = 0; invalidate(); return; }
        index = Math.max(0, Math.min(steps.size() - 1, i));
        invalidate();
    }

    public void setIndex(int i) {
        setIndexSilent(i);
        if (listener != null) listener.onChanged(index);
    }

    public int getValue() {
        if (steps.isEmpty()) return 0;
        return steps.get(Math.max(0, Math.min(steps.size() - 1, index)));
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int h = (int) (padY * 2 + knobR * 2 + 8 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(300, ws), resolveSize(h, hs));
    }

    /** 滑轨可用区间（左右各留出一个滑块半径，保证滑块不出界）。 */
    private float x0() { return getPaddingLeft() + knobR; }
    private float x1() { return getWidth() - getPaddingRight() - knobR; }

    private float trackCenterY() { return getHeight() / 2f; }

    private float xOfIndex(int i) {
        if (steps.size() <= 1) return (x0() + x1()) / 2f;
        float t = i / (float) (steps.size() - 1);
        return x0() + (x1() - x0()) * t;
    }

    private int indexOfX(float x) {
        if (steps.size() <= 1) return 0;
        float t = (x - x0()) / (x1() - x0());
        t = Math.max(0f, Math.min(1f, t));
        // 就近吸附到真实档位：不等比档位下「按几何位置取整」才是对的
        return Math.round(t * (steps.size() - 1));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float cy = trackCenterY();
        float h = 5f * getResources().getDisplayMetrics().density;
        trackRect.set(x0(), cy - h / 2f, x1(), cy + h / 2f);

        cv.drawRoundRect(trackRect, h / 2f, h / 2f, trackOff);

        float kx = xOfIndex(index);
        if (kx > x0()) {
            RectF on = new RectF(x0(), cy - h / 2f, kx, cy + h / 2f);
            cv.drawRoundRect(on, h / 2f, h / 2f, trackOn);
        }

        // 刻度点：档位多于 24 个就每 2 个画一个，避免糊成一条线
        int n = steps.size();
        if (n > 1) {
            int step = n > 24 ? 2 : 1;
            for (int i = 0; i < n; i += step) {
                cv.drawCircle(xOfIndex(i), cy, tickR, tickP);
            }
        }

        // 拖动时在滑块外圈加一层柔光，让拖拽有反馈
        if (dragging) {
            cv.drawCircle(kx, cy, knobR + 7f * getResources().getDisplayMetrics().density, shadowP);
        }

        cv.drawCircle(kx, cy, knobR, knobP);
        cv.drawCircle(kx, cy, knobR, knobRing);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (steps.isEmpty()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                int ni = indexOfX(e.getX());
                if (ni != index) {
                    index = ni;
                    invalidate();
                    if (listener != null) listener.onChanged(index);
                } else {
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                invalidate();
                if (e.getActionMasked() == MotionEvent.ACTION_UP && listener != null) {
                    listener.onSettled(index);
                }
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }
}