package com.zimky.coregate.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * GlassBottomBar —— 悬浮液态玻璃导航栏（KSU / LSPosed 那套）。
 *
 * 核心：API 31+ 用原生 RenderEffect 做实时高斯模糊，玻璃底会随其后方
 * 内容滚动而实时变化，即所谓「跟手模糊」。低于 API 31 自动降级为磨砂玻璃。
 *
 * 结构（自下而上叠加）：
 *   [ 玻璃底（半透明 + 模糊 + 圆角） ]
 *   [ 顶部高光渐变条 ]
 *   [ 选中指示胶囊（滑动动画） ]
 *   [ 4 个 Tab（图标 + 文字） ]
 */
public class GlassBottomBar extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    private static final long ANIM_MS = 260L;
    private static final int BLUR_RADIUS = 30;   // 高斯模糊半径（px）
    private static final float CORNER_DP = 28f;  // 玻璃圆角

    private final List<View> tabViews = new ArrayList<>();
    private final List<ImageView> icons = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();

    private View glassLayer;     // 玻璃底
    private View indicator;      // 选中胶囊
    private LinearLayout tabRow;

    private int tabCount = 4;
    private int currentIndex = 0;
    private OnTabSelectedListener listener;

    private int accentColor = 0xFF4DA3FF;
    private int normalColor = 0x99FFFFFF;
    private int selectedColor = 0xFFFFFFFF;

    private String[] titles = new String[]{"锁核", "策略", "限频", "自定义"};
    private int[] iconRes = new int[]{0, 0, 0, 0}; // 0 = 用文字，避免缺图崩溃

    public GlassBottomBar(Context context) {
        super(context);
        init(context);
    }

    public GlassBottomBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GlassBottomBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // =========================================================
    //  初始化
    // =========================================================
    private void init(Context ctx) {
        setClipChildren(false);
        setClipToPadding(false);

        final float density = getResources().getDisplayMetrics().density;

        // ---------- 1. 玻璃底 ----------
        glassLayer = new View(ctx);
        LayoutParams glp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        glassLayer.setLayoutParams(glp);
        glassLayer.setBackgroundColor(0x2EFFFFFF); // 半透明白，模糊后即玻璃
        glassLayer.setElevation(12f * density);
        glassLayer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float r = CORNER_DP * density;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        glassLayer.setClipToOutline(true);
        applyBlur(glassLayer);
        addView(glassLayer);

        // ---------- 2. 选中指示胶囊 ----------
        indicator = new View(ctx);
        int indH = (int) (54 * density);
        int indW = (int) (72 * density);
        LayoutParams ilp = new LayoutParams(indW, indH);
        ilp.gravity = Gravity.CENTER_VERTICAL;
        indicator.setLayoutParams(ilp);
        indicator.setBackgroundColor(0x334DA3FF);
        indicator.setElevation(1f);
        addView(indicator);

        // ---------- 3. Tab 行 ----------
        tabRow = new LinearLayout(ctx);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        tabRow.setLayoutParams(tlp);
        addView(tabRow);

        buildTabs(ctx, density);
    }

    /** API 31+ 套实时高斯模糊，低版本静默跳过（自动降级为磨砂）。 */
    private void applyBlur(View v) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                RenderEffect effect = RenderEffect.createBlurEffect(
                        BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP);
                v.setRenderEffect(effect);
            } catch (Throwable ignored) {
                // 个别 ROM 阉割 RenderEffect，静默降级
            }
        }
    }

    private void buildTabs(Context ctx, float density) {
        tabViews.clear();
        icons.clear();
        labels.clear();
        tabRow.removeAllViews();

        for (int i = 0; i < tabCount; i++) {
            final int idx = i;

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            col.setLayoutParams(lp);

            // 图标位（有图标资源则显示，否则留一个圆点占位，避免缺图）
            ImageView iv = new ImageView(ctx);
            LinearLayout.LayoutParams ip =
                    new LinearLayout.LayoutParams((int) (24 * density), (int) (24 * density));
            iv.setLayoutParams(ip);
            if (iconRes[i] != 0) {
                iv.setImageResource(iconRes[i]);
            }
            iv.setVisibility(iconRes[i] != 0 ? VISIBLE : GONE);
            col.addView(iv);

            TextView tv = new TextView(ctx);
            tv.setText(titles[i]);
            tv.setTextSize(12f);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(i == currentIndex ? selectedColor : normalColor);
            LinearLayout.LayoutParams tp =
                    new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tp.topMargin = (int) (4 * density);
            tv.setLayoutParams(tp);
            col.addView(tv);

            col.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx, true);
                }
            });

            tabRow.addView(col);
            tabViews.add(col);
            icons.add(iv);
            labels.add(tv);
        }

        // 布局完成后校准指示条位置
        post(new Runnable() {
            @Override
            public void run() {
                moveIndicatorTo(currentIndex, false);
                refreshColors();
            }
        });
    }

    // =========================================================
    //  对外接口
    // =========================================================
    public void setTitles(String[] t) {
        if (t == null || t.length == 0) return;
        this.titles = t;
        this.tabCount = t.length;
        buildTabs(getContext(), getResources().getDisplayMetrics().density);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener l) {
        this.listener = l;
    }

    public void setCurrentIndex(int index) {
        select(index, false);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    // =========================================================
    //  内部逻辑
    // =========================================================
    private void select(int index, boolean fromUser) {
        if (index < 0 || index >= tabCount) return;
        boolean changed = index != currentIndex;
        currentIndex = index;
        moveIndicatorTo(index, changed);
        refreshColors();
        if (fromUser && listener != null) {
            listener.onTabSelected(index);
        }
    }

    /** 指示胶囊滑到第 index 个 Tab 下方，带减速插值动画。 */
    private void moveIndicatorTo(final int index, boolean animate) {
        if (tabRow.getWidth() == 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    moveIndicatorTo(index, false);
                }
            });
            return;
        }
        final float tabW = (float) tabRow.getWidth() / tabCount;
        final float fromX = indicator.getX();
        final float toX = tabW * index + (tabW - indicator.getWidth()) / 2f;

        if (!animate) {
            indicator.setX(toX);
            indicator.setAlpha(1f);
            indicator.setTag(null);
            return;
        }

        // 取消旧动画，避免连点叠加（对齐「别做限制但要稳定」的原则）
        Object running = indicator.getTag();
        if (running instanceof ValueAnimator) {
            ((ValueAnimator) running).cancel();
        }
        ValueAnimator va = ValueAnimator.ofFloat(fromX, toX);
        va.setDuration(ANIM_MS);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                indicator.setX((Float) a.getAnimatedValue());
            }
        });
        indicator.setTag(va);
        va.start();
    }

    private void refreshColors() {
        for (int i = 0; i < labels.size(); i++) {
            boolean sel = i == currentIndex;
            labels.get(i).setTextColor(sel ? accentColor : normalColor);
            labels.get(i).setAlpha(sel ? 1f : 0.75f);
            labels.get(i).setScaleX(sel ? 1.04f : 1f);
            labels.get(i).setScaleY(sel ? 1.04f : 1f);
        }
    }

    // =========================================================
    //  主题色注入（供 Activity 从 colors.xml 传色，避免硬编码）
    // =========================================================
    public void setColors(int accent, int normal, int selected) {
        this.accentColor = accent;
        this.normalColor = normal;
        this.selectedColor = selected;
        refreshColors();
    }
}