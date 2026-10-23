package com.zimky.coregateUS.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * GlassBackground —— 页面背景（渐变 + 两个模糊光斑）。
 *
 * 母本 WebUI 背景是四层 radial/linear 叠加 + 两个 filter:blur(70px) 的伪元素光斑。
 * 原生做 blur(70px) 太贵（每帧要整屏模糊），这里用 RadialGradient 的透明度
 * 平滑衰减来「假装」是模糊光斑 —— 视觉上几乎等价，代价接近零。
 */
public class GlassBackground extends View {

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spotA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spotB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spotC = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassBackground(Context c) {
        super(c);
        blurPaint.setMaskFilter(new BlurMaskFilter(Ui.dpf(c, 60f), BlurMaskFilter.Blur.NORMAL));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;

        // 底色：深蓝黑 → 更深（对应 body 的 linear-gradient）
        basePaint.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[]{0xFF0A0E15, 0xFF0D1220, 0xFF080B12},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));

        // 光斑 A：左上主色蓝
        spotA.setShader(new RadialGradient(
                w * 0.22f, h * 0.12f, w * 0.85f,
                new int[]{0x4D4F8CFF, 0x1A4F8CFF, 0x004F8CFF},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));

        // 光斑 B：右中青绿
        spotB.setShader(new RadialGradient(
                w * 0.92f, h * 0.38f, w * 0.70f,
                new int[]{0x3D34C759, 0x1434C759, 0x0034C759},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));

        // 光斑 C：底部紫
        spotC.setShader(new RadialGradient(
                w * 0.45f, h * 1.02f, w * 0.90f,
                new int[]{0x33A855F7, 0x10A855F7, 0x00A855F7},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        cv.drawRect(0, 0, w, h, basePaint);
        cv.drawRect(0, 0, w, h, spotA);
        cv.drawRect(0, 0, w, h, spotB);
        cv.drawRect(0, 0, w, h, spotC);
    }
}