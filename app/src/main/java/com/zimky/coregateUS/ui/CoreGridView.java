package com.zimky.coregateUS.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.zimky.coregateUS.core.SysProbe;

/**
 * CoreGridView —— 八个 CPU 核心的实时可视化网格。
 *
 * 对应 WebUI 的 #coreGrid（grid-template-columns: repeat(auto-fill, minmax(62px,1fr))）。
 * 每个小格显示：核序号、当前频率（GHz/MHz 自动换算）、在线状态色条。
 * 绘制完全自绘，避免 8 个 View 嵌套带来的 measure 开销 —— 这块每 8 秒刷一次，必须轻。
 */
public class CoreGridView extends View {

    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint idxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint freqPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private SysProbe.Core[] cores = new SysProbe.Core[0];
    private long bigFreqHz = 0;   // 用来判断哪些核属于大簇（画不同底色）

    public CoreGridView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        idxPaint.setTextSize(10.5f * d);
        idxPaint.setColor(Ui.SUB);
        idxPaint.setTextAlign(Paint.Align.LEFT);
        freqPaint.setTextSize(12f * d);
        freqPaint.setTextAlign(Paint.Align.LEFT);
        card.setColor(0x12FFFFFF);
        cardBorder.setColor(0x1AFFFFFF);
        cardBorder.setStyle(Paint.Style.STROKE);
        cardBorder.setStrokeWidth(1f * d);
    }

    public void setCores(SysProbe.Core[] list) {
        cores = list == null ? new SysProbe.Core[0] : list;
        requestLayout();
        invalidate();
    }

    private static final int COLS = 4;

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        float d = getResources().getDisplayMetrics().density;
        float gap = 8f * d;
        int cellW = (int) ((w - gap * (COLS - 1)) / COLS);
        int cellH = (int) (cellW * 0.82f);
        int rows = Math.max(1, (cores.length + COLS - 1) / COLS);
        int h = (int) (rows * cellH + (rows - 1) * gap);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (cores.length == 0) return;
        float d = getResources().getDisplayMetrics().density;
        float gap = 8f * d;
        float w = getWidth();
        int cellW = (int) ((w - gap * (COLS - 1)) / COLS);
        int cellH = (int) (cellW * 0.82f);
        float rad = 12f * d;

        for (int i = 0; i < cores.length; i++) {
            int row = i / COLS, col = i % COLS;
            float left = col * (cellW + gap);
            float top = row * (cellH + gap);
            r.set(left, top, left + cellW, top + cellH);

            SysProbe.Core c = cores[i];
            boolean on = c.online;
            int accent = on ? coreColor(c) : Ui.OFF;
            boolean big = "big".equals(c.cluster);

            card.setColor(on ? 0x1AFFFFFF : 0x0AFFFFFF);
            cv.drawRoundRect(r, rad, rad, card);
            cardBorder.setColor(on ? Ui.alpha(accent, 0.55f) : 0x14FFFFFF);
            cv.drawRoundRect(r, rad, rad, cardBorder);

            // 顶部序号 + 簇标记
            String tag = "CPU" + c.id + (big ? " · 大核" : " · 小核");
            idxPaint.setColor(on ? Ui.SUB : Ui.alpha(Ui.SUB, 0.6f));
            cv.drawText(tag, left + 8f * d, top + 16f * d, idxPaint);

            // 中部频率
            String f = on ? SysProbe.fmtMhz(c.curFreq) : "离线";
            freqPaint.setColor(on ? Ui.TXT : Ui.OFF);
            freqPaint.setFakeBoldText(true);
            cv.drawText(f, left + 8f * d, top + cellH * 0.58f, freqPaint);

            // 底部状态条
            float barH = 3f * d;
            float barY = top + cellH - 8f * d;
            barPaint.setColor(0x1FFFFFFF);
            r.set(left + 8f * d, barY, left + cellW - 8f * d, barY + barH);
            cv.drawRoundRect(r, barH / 2f, barH / 2f, barPaint);
            if (on && c.maxFreq > 0) {
                float ratio = Math.min(1f, (float) c.curFreq / (float) c.maxFreq);
                barPaint.setColor(accent);
                r.set(left + 8f * d, barY,
                        left + 8f * d + (cellW - 16f * d) * ratio, barY + barH);
                cv.drawRoundRect(r, barH / 2f, barH / 2f, barPaint);
            }
        }
    }

    /** 频率越高颜色越「热」：低=绿，中=蓝，高=橙。 */
    private static int coreColor(SysProbe.Core c) {
        if (c.maxFreq <= 0) return Ui.OK;
        float ratio = (float) c.curFreq / (float) c.maxFreq;
        if (ratio < 0.45f) return Ui.OK;
        if (ratio < 0.75f) return Ui.ACC;
        return Ui.WARN;
    }
}