package com.zimky.coregate;

import android.os.Bundle;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.zimky.coregate.ui.CustomFragment;
import com.zimky.coregate.ui.FreqCapFragment;
import com.zimky.coregate.ui.LockStatusFragment;
import com.zimky.coregate.ui.StrategyFragment;
import com.zimky.coregate.widget.GlassBottomBar;

/**
 * CoreGate 主界面。
 *
 * 布局：FrameLayout（内容容器，铺满）+ 悬浮 GlassBottomBar（叠在底部）。
 * 内容区与导航栏解耦，导航栏悬浮在内容之上，内容滚动时其后方画面实时变化，
 * GlassBottomBar 的 RenderEffect 模糊因此「跟手」。
 */
public class MainActivity extends AppCompatActivity {

    private GlassBottomBar bar;
    private Fragment[] fragments;
    private int current = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 内容延伸到状态栏 / 导航栏下方（沉浸式），玻璃栏才能压住内容
        Window w = getWindow();
        w.setStatusBarColor(0x00000000);
        w.setNavigationBarColor(0x00000000);

        setContentView(R.layout.activity_main);

        bar = findViewById(R.id.glass_bar);
        bar.setTitles(new String[]{
                getString(R.string.tab_lock),
                getString(R.string.tab_strategy),
                getString(R.string.tab_freq),
                getString(R.string.tab_custom)
        });
        bar.setColors(
                getResources().getColor(R.color.accent),
                0x99FFFFFF,
                0xFFFFFFFF);

        fragments = new Fragment[]{
                new LockStatusFragment(),
                new StrategyFragment(),
                new FreqCapFragment(),
                new CustomFragment()
        };

        bar.setOnTabSelectedListener(new GlassBottomBar.OnTabSelectedListener() {
            @Override
            public void onTabSelected(int index) {
                switchTab(index);
            }
        });

        // 首屏
        switchTab(0);
    }

    /** 切换内容 Fragment（用 show/hide 保留各页状态，避免每次重建丢数据）。 */
    private void switchTab(int index) {
        if (index == current) return;

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, 180);

        // 先隐藏已显示的
        if (current >= 0) {
            ft.hide(fragments[current]);
        }

        Fragment target = fragments[index];
        if (target.isAdded()) {
            ft.show(target);
        } else {
            ft.add(R.id.content_container, target, "tab_" + index);
        }
        ft.commitAllowingStateLoss();

        current = index;
        bar.setCurrentIndex(index);
    }

    @Override
    public void onBackPressed() {
        // 非首屏时返回键回到首屏，首屏时才退出
        if (current != 0) {
            switchTab(0);
        } else {
            super.onBackPressed();
        }
    }
}