package com.zimky.coregateUS;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.zimky.coregateUS.core.Config;
import com.zimky.coregateUS.core.RootShell;
import com.zimky.coregateUS.core.SysProbe;
import com.zimky.coregateUS.page.FreqPage;
import com.zimky.coregateUS.page.LockPage;
import com.zimky.coregateUS.page.Page;
import com.zimky.coregateUS.page.PolicyPage;
import com.zimky.coregateUS.page.SettingsPage;
import com.zimky.coregateUS.ui.GlassBackground;
import com.zimky.coregateUS.ui.NavBarView;
import com.zimky.coregateUS.ui.Toaster;
import com.zimky.coregateUS.ui.Ui;

/**
 * MainActivity —— 全原生壳，零 WebView。
 *
 * 分工说明：
 *   - 模块自带的 WebUI（webroot/index.html）继续留在 KernelSU 里，
 *     服务那些懒得装 APK 的用户；
 *   - 这个 APK 是完全独立的原生实现，不加载任何网页，也不依赖 WebView 运行时。
 *     两者共用同一套后端：/data/adb/CoreGate/config.json + service.sh 命令行。
 *     所以无论从哪边改配置，另一边刷新后都能读到同一份数据。
 *
 * 三条硬性约束（都是踩坑得来的）：
 *   1) root 调用必须串行 + 合并。v2.0.6 里 WebUI 狂点刷新导致 ksu.exec 并发
 *      大量 root shell，被系统安全机制判定为入侵并弹窗。这里用一个 Promise 式的
 *      pending 队列，同一时刻只有一个 refresh 在跑，期间来的请求只标记「再跑一次」。
 *   2) 页面切换不销毁重建。切 Tab 只改 setVisibility，四页的 View 树一直活着，
 *      这样滚动位置、输入框内容都不会丢。
 *   3) onStatus 只更新显示，不发起 root 请求。轮询在 Activity 里统一做，
 *      页面内部的刷新按钮自己按需触发，两条线不打架。
 */
public class MainActivity extends Activity implements Page.Host {

    /** 轮询间隔。对齐 WebUI 的 8 秒（v2.0.6 从 5 秒放宽后的值）。 */
    private static final long POLL_MS = 8000L;

    /** 手动刷新冷却，防抖。 */
    private static final long MIN_REFRESH_GAP = 1200L;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout shell;
    private Toaster toaster;

    private Page[] pages;
    private ScrollView[] scrollers;
    private NavBarView nav;

    private Config cfg;
    private String packagesRaw = "";
    private SysProbe.Snapshot lastSnapshot;

    private boolean refreshing;
    private boolean refreshAgain;

    private long lastRefreshAt;
    private int currentTab;

    private boolean resumed;
    private boolean destroyed;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            if (resumed) refresh(false);
            ui.postDelayed(this, POLL_MS);
        }
    };

    // ===============================================================
    // 生命周期
    // ===============================================================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        shell = new FrameLayout(this);
        shell.setBackgroundColor(0xFF080B12);

        // 背景：渐变 + 光斑，铺满整个屏幕
        GlassBackground bg = new GlassBackground(this);
        shell.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 内容区：四页的 ScrollView 叠在一起，只显示当前那个。
        // 底部预留导航栏高度，避免最后一张卡片被挡住。
        FrameLayout content = new FrameLayout(this);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.bottomMargin = Ui.dp(this, 62);
        shell.addView(content, clp);

        pages = new Page[]{
                new LockPage(this),
                new PolicyPage(this),
                new FreqPage(this),
                new SettingsPage(this)
        };
        scrollers = new ScrollView[pages.length];

        for (int i = 0; i < pages.length; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setClipToPadding(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(pages[i].view(), new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            sv.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            content.addView(sv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scrollers[i] = sv;
        }

        // 导航栏钉在底部
        nav = new NavBarView(this);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.gravity = android.view.Gravity.BOTTOM;
        shell.addView(nav, nlp);
        nav.setOnTab(new NavBarView.OnTab() {
            @Override
            public void onTab(int index) {
                switchTab(index);
            }
        });

        toaster = new Toaster(this, shell);

        setContentView(shell);

        // 四页建界面（每页只建一次）
        for (Page p : pages) p.onCreate();

        // 状态栏 / 导航栏配色跟随深色底
        applyBarStyle();

        // 首次进来先读配置，读到再拉一次状态
        reloadConfig(true);
    }

    private void applyBarStyle() {
        try {
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            // 深色背景要配浅色图标：去掉 LIGHT_STATUS_BAR
            flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            decor.setSystemUiVisibility(flags);
            getWindow().setStatusBarColor(0xFF080B12);
            getWindow().setNavigationBarColor(0xE60E1219);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        ui.removeCallbacks(pollTask);
        ui.postDelayed(pollTask, POLL_MS);
        // 回到前台立刻刷一次，别让用户盯着旧数据
        refresh(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        // 切后台就停轮询，别再消耗 root
        ui.removeCallbacks(pollTask);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacks(pollTask);
        ui.removeCallbacksAndMessages(null);
        for (Page p : pages) {
            if (p != null) p.onDestroy();
        }
        super.onDestroy();
    }

    // ===============================================================
    // Tab 切换
    // ===============================================================
    private void switchTab(int index) {
        if (index < 0 || index >= pages.length) return;
        if (index == currentTab && scrollers[index].getVisibility() == View.VISIBLE) return;
        for (int i = 0; i < pages.length; i++) {
            scrollers[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        currentTab = index;
        nav.setCurrent(index);
        // 切过去的时候先把已有快照画上，再请页面自己做按需刷新
        pages[index].onStatus(lastSnapshot);
        pages[index].onEnter();
    }

    // ===============================================================
    // 配置读写
    // ===============================================================
    private void reloadConfig(final boolean alsoRefresh) {
        Config.load(RootShell.get(), new Config.LoadCallback() {
            @Override
            public void onConfig(Config c, String pkg, String dbg) {
                cfg = c;
                packagesRaw = pkg == null ? "" : pkg;
                // 配置读到了，把已有快照先画一遍（比如包名列表、开关状态）
                pushStatus();
                if (alsoRefresh) refresh(false);
            }
        });
    }

    @Override
    public void save(final Config newCfg, final String packages, final Page.SaveDone done) {
        if (newCfg == null) {
            if (done != null) done.onSaved(false, "配置为空");
            return;
        }
        final String cmd = Config.buildSaveCmd(newCfg, packages);
        RootShell.get().exec(cmd, new RootShell.Callback() {
            @Override
            public void onResult(int code, String out, String err) {
                boolean ok = code == 0;
                if (ok) {
                    // 保存成功：本地副本同步更新，避免界面回跳
                    cfg = newCfg.copy();
                    cfg.loaded = true;
                    if (packages != null) packagesRaw = packages;
                    pushStatus();
                }
                if (done != null) {
                    done.onSaved(ok, ok ? "OK" : (err != null && err.length() > 0 ? err : ("退出码 " + code)));
                }
                // 配置变了，让守护进程在下一轮循环生效前，界面也能立刻反映
                if (ok) ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh(false);
                    }
                }, 400);
            }
        });
    }

    // ===============================================================
    // 状态刷新（串行 + 合并）
    // ===============================================================
    @Override
    public void requestRefresh() {
        refresh(true);
    }

    /**
     * 拉一次状态。
     *
     * 串行：同一时刻只允许一个 probe 在跑。跑的时候再来的请求不排队堆积，
     *       只把 refreshAgain 置 true，等当前的跑完再补一次。
     * 冷却：force=true（用户主动点的按钮）也照样有 1.2 秒的最小间隔，
     *       因为「狂点刷新」正是 v2.0.6 里弹安全警告的元凶。
     */
    private void refresh(boolean fromUser) {
        if (destroyed) return;

        long now = System.currentTimeMillis();
        if (fromUser && now - lastRefreshAt < MIN_REFRESH_GAP) {
            // 冷却中：不发起 root，只标记，等这轮结束后补跑
            refreshAgain = true;
            return;
        }

        if (refreshing) {
            refreshAgain = true;
            return;
        }

        refreshing = true;
        lastRefreshAt = now;

        SysProbe.probe(RootShell.get(), new SysProbe.Callback() {
            @Override
            public void onSnapshot(SysProbe.Snapshot s) {
                refreshing = false;
                lastSnapshot = s;
                pushStatus();
                if (refreshAgain) {
                    refreshAgain = false;
                    // 稍等一下再补，别把两次 probe 挤在一起
                    ui.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            refresh(false);
                        }
                    }, 300);
                }
            }
        });
    }

    /** 把最新快照推给四页 + 状态胶囊。幂等，纯更新显示。 */
    private void pushStatus() {
        if (pages == null) return;
        // 只推当前可见页 + 关键页也行，但四页都幂等且很轻，全推更简单不易漏
        for (Page p : pages) {
            if (p != null) p.onStatus(lastSnapshot);
        }
    }

    // ===============================================================
    // Page.Host 实现
    // ===============================================================
    @Override
    public Context ctx() {
        return this;
    }

    @Override
    public RootShell sh() {
        return RootShell.get();
    }

    @Override
    public Config cfg() {
        if (cfg == null) cfg = Config.defaults();
        return cfg;
    }

    @Override
    public String packages() {
        return packagesRaw;
    }

    @Override
    public SysProbe.Snapshot snapshot() {
        return lastSnapshot;
    }

    @Override
    public Toaster toaster() {
        return toaster;
    }
}
