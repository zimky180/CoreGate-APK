package com.zimky.coregateUS.page;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.zimky.coregateUS.core.Config;
import com.zimky.coregateUS.core.RootShell;
import com.zimky.coregateUS.core.SysProbe;
import com.zimky.coregateUS.ui.GlassCard;
import com.zimky.coregateUS.ui.Toaster;
import com.zimky.coregateUS.ui.Ui;

/**
 * Page —— 四个 Tab 页的公共基类。
 *
 * 为什么要有这层：
 *  1) 四页都需要「拿 status 快照 / 落盘 config / 弹提示 / 造卡片」这四件事，
 *     集中在这里，各页只管自己的内容。
 *  2) 页面切换时不销毁重建，而是复用（切 Tab 只 setVisibility），
 *     所以每页必须实现 onStatus(Snapshot) 做「幂等刷新」——只更新显示，
 *     不能在里面发起新的 root 请求，否则会跟轮询打架。
 *  3) 全局状态（config / packages / 最近一次快照）放在 Activity 里，
 *     Page 通过 host 回调读写，避免每页各存一份导致不同步。
 */
public abstract class Page {

    /** Page 与 Activity 的通信口。 */
    public interface Host {
        Context ctx();

        RootShell sh();

        /** 当前最新的配置副本（改动请走 save，不要直接改返回值）。 */
        Config cfg();

        /** packages.txt 的原始文本。 */
        String packages();

        /** 最近一次 status 快照，可能为 null（还没探测完）。 */
        SysProbe.Snapshot snapshot();

        /** 保存配置；packages 传 null 表示不动 packages.txt。 */
        void save(Config newCfg, String packages, SaveDone done);

        /** 主动请求一次状态刷新（会走 1.2 秒冷却，防 root 风暴）。 */
        void requestRefresh();

        Toaster toaster();
    }

    public interface SaveDone {
        void onSaved(boolean ok, String msg);
    }

    protected final Host host;
    protected final Context ctx;

    /** 页面根容器，由 Activity 塞进 ScrollView。 */
    protected final LinearLayout root;

    /** 外部调用 requestRefresh 时更新的「正在刷新」标记。 */
    protected boolean busy;

    protected Page(Host host) {
        this.host = host;
        this.ctx = host.ctx();
        this.root = Ui.vert(ctx);
        root.setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 28));
    }

    public View view() {
        return root;
    }

    /** 子类在这里建界面，只会被调用一次。 */
    public abstract void onCreate();

    /** 状态刷新（幂等）。snapshot 可能为 null。 */
    public void onStatus(SysProbe.Snapshot s) { }

    /** Tab 被切到前台。用于按需触发一次刷新。 */
    public void onEnter() { }

    /** 页面被销毁前调用。 */
    public void onDestroy() { }

    // ---------------------------------------------------------------
    // 便捷方法
    // ---------------------------------------------------------------

    protected GlassCard card() {
        GlassCard c = new GlassCard(ctx);
        return c;
    }

    /** 造一张带标题的卡片并直接加进 root。返回卡片本体供追加内容。 */
    protected GlassCard addCard(String title) {
        GlassCard c = new GlassCard(ctx);
        LinearLayout.LayoutParams p = Ui.lpW(14, ctx);
        root.addView(c, p);
        c.addCategory(title);
        return c;
    }

    protected void toast(String msg) {
        host.toaster().show(msg);
    }

    protected void toast(String msg, boolean err) {
        host.toaster().show(msg, err);
    }

    /** 保存；成功后自动提示。 */
    protected void saveMuted(Config c, String pkgs, String okMsg) {
        host.save(c, pkgs, new SaveDone() {
            @Override
            public void onSaved(boolean ok, String msg) {
                if (ok) {
                    if (okMsg != null) toast(okMsg);
                } else {
                    toast("保存失败：" + msg, true);
                }
            }
        });
    }

    protected Config draft() {
        return host.cfg().copy();
    }

    protected SysProbe.Snapshot snap() {
        return host.snapshot();
    }

    protected float d(float v) {
        return Ui.dpf(ctx, v);
    }

    protected int di(float v) {
        return Ui.dp(ctx, v);
    }
}