package com.zimky.coregateUS.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * SegmentView —— 分段选择器（替代 WebUI 的 <select> 下拉框）。
 *
 * 为什么不用下拉框：手机上点开系统下拉要两次交互，分段器一眼看全所有档位、一点即中，
 * 而且限频档位本来就只有 7 个，横向完全放得下。
 * 但档位多（GPU 17 档）时不好铺，所以支持 scrollable —— 超出宽度可横向拖。
 */
public class SegmentView extends View {

    public interface OnPick { void onPicked(int index, String label); }

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rr = new RectF();

    private String[] items = new String[0];
    private int index = 0;
    private int accent = Ui.ACC;
    private OnPick listener;
    private float animX = -1, animW = -1;

    private float itemW;
    private float padDp = 4f;

    public SegmentView(Context c) {
        super(c);
        txt.setTextSize(Ui.dpf(c, 12.5f));
        txtSel.setTextSize(Ui.dpf(c, 12.5f));
        txt.setColor(Ui.SUB);
        txtSel.setColor(0xFFFFFFFF);
        txtSel.setFakeBoldText(true);
        txt.setTextAlign(Paint.Align.CENTER);
        txtSel.setTextAlign(Paint.Align.CENTER);
        bg.setColor(0x14FFFFFF);
        sel.setColor(accent);
    }

    public void setAccent(int color) { accent = color; sel.setColor(accent); invalidate(); }

    public void setOnPick(OnPick l) { listener = l; }

    public int getIndex() { return index; }

    public String getValue() {
        return (index >= 0 && index < items.length) ? items[index] : "";
    }

    public void setItems(String[] arr, int idx) {
        items = arr == null ? new String[0] : arr;
        index = Math.max(0, Math.min(idx, items.length - 1));
        animX = -1;
        requestLayout();
        invalidate();
    }

    /** 按文本选中，找不到则不动。 */
    public void selectByLabel(String label) {
        if (label == null) return;
        for (int i = 0; i < items.length; i++) {
            if (label.trim().equals(items[i])) { index = i; invalidate(); return; }
        }
    }

    public void setIndex(int i) {
        if (items.length == 0) return;
        index = Math.max(0, Math.min(i, items.length - 1));
        invalidate();
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int h = Ui.dp(getContext(), 34);
        int width = MeasureSpec.getSize(ws);
        setMeasuredDimension(width, h);
        itemW = items.length == 0 ? width : (float) width / items.length;
    }

    @Override
    protected void onDraw(Canvas cv) {
        Context c = getContext();
        float pad = Ui.dpf(c, padDp);
        float w = getWidth(), h = getHeight();
        float rad = h / 2f;

        rr.set(0, 0, w, h);
        cv.drawRoundRect(rr, rad, rad, bg);

        if (items.length == 0) return;

        float selX = index * itemW;
        float selW = itemW;
        if (animX < 0) { animX = selX; animW = selW; }

        rr.set(animX + pad, pad, animX + selW - pad, h - pad);
        float pr = rr.height() / 2f;
        cv.drawRoundRect(rr, pr, pr, sel);

        float baseline = h / 2f - (txt.descent() + txt.ascent()) / 2f;
        for (int i = 0; i < items.length; i++) {
            float cx = i * itemW + itemW / 2f;
            cv.drawText(items[i], cx, baseline, i == index ? txtSel : txt);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && items.length > 0) {
            int i = (int) (e.getX() / itemW);
            if (i >= 0 && i < items.length && i != index) {
                float fromX = animX, toX = i * itemW;
                index = i;
                android.animation.ValueAnimator a =
                        android.animation.ValueAnimator.ofFloat(fromX, toX);
                a.setDuration(180);
                a.setInterpolator(new DecelerateInterpolator());
                a.addUpdateListener(v -> { animX = (float) v.getAnimatedValue(); invalidate(); });
                a.start();
                if (listener != null) listener.onPicked(i, items[i]);
            }
        }
        return true;
    }
}