package com.zimky.coregateUS.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

/**
 * GlassCard —— 液态玻璃卡片。
 *
 * 母本 WebUI 的卡片靠 CSS 的 backdrop-filter: blur(20px) saturate(150%) 做真跟手毛玻璃，
 * 原生没有等价的「模糊背后像素」API（RenderEffect 只能模糊自身内容），
 * 所以这里用三层叠加做视觉降级：
 *   1) 半透明白底（CARD_BG）；
 *   2) 150° 斜向高光渐变（左上更亮，模拟玻璃受光面）；
 *   3) 1px 半透明描边 + 顶部 inset 亮线（模拟玻璃边缘折射）。
 * 圆角统一 20dp，对齐 .card 的 border-radius。
 */
public class GlassCard extends LinearLayout {

    private static final float RADIUS_DP = 20f;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float radius;

    public GlassCard(Context c) {
        super(c);
        setOrientation(VERTICAL);
        radius = Ui.dpf(c, RADIUS_DP);
        int pad = Ui.dp(c, 16);
        setPadding(pad, pad, pad, pad);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Ui.dpf(c, 1f));
        borderPaint.setColor(Ui.CARD_BORDER);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(Ui.dpf(c, 1f));
        edgePaint.setColor(0x33FFFFFF);

        // API 21+ 都能用 outline，provide 圆角以让阴影/裁剪正确
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setClipToOutline(false);
        if (Build.VERSION.SDK_INT >= 21) setElevation(0f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // 高光渐变：左上 → 右下（对应 CSS 的 150deg）
        bgPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{0x1FFFFFFF, 0x0FFFFFFF, 0x07FFFFFF},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void dispatchDraw(Canvas cv) {
        r.set(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f);
        // 1) 半透明底
        bgPaint.setShader(null);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Ui.CARD_BG);
        cv.drawRoundRect(r, radius, radius, bgPaint);
        // 2) 斜向高光
        if (getWidth() > 0 && getHeight() > 0) {
            Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
            hl.setShader(new LinearGradient(
                    0, 0, getWidth(), getHeight(),
                    new int[]{0x1AFFFFFF, 0x00FFFFFF, 0x0AFFFFFF},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            cv.drawRoundRect(r, radius, radius, hl);
        }
        // 3) 描边
        cv.drawRoundRect(r, radius, radius, borderPaint);
        // 4) 顶部亮线（玻璃上边缘受光）
        float inset = radius * 0.55f;
        cv.drawLine(inset, 1.2f, getWidth() - inset, 1.2f, edgePaint);
        super.dispatchDraw(cv);
    }

    // ---------------------------------------------------------------
    // 内容便捷方法（各 Page 复用，避免到处 setLayoutParams）
    // ---------------------------------------------------------------

    /** 卡片小标题：12sp、SUB 色、加粗、字距略宽，对应 WebUI 的 .card h3。 */
    public void addCategory(String title) {
        if (title == null || title.isEmpty()) return;
        android.widget.TextView t = Ui.text(getContext(), title, 12f, Ui.SUB);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Ui.dp(getContext(), 10);
        addView(t, p);
    }

    /** 追加一条分隔线。 */
    public void addDivider() {
        addView(Ui.divider(getContext()));
    }

    /** 追加一个普通子视图，带底部间距。 */
    public void addSpaced(android.view.View v, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Ui.dp(getContext(), bottomDp);
        addView(v, p);
    }

    /** 追加一个不拉伸宽度的子视图（如按钮行、胶囊）。 */
    public void addWrap(android.view.View v, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Ui.dp(getContext(), bottomDp);
        addView(v, p);
    }
}