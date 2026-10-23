package com.zimky.coregateUS.page;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zimky.coregateUS.core.ModuleBridge;
import com.zimky.coregateUS.core.SysProbe;
import com.zimky.coregateUS.ui.CoreGridView;
import com.zimky.coregateUS.ui.GlassCard;
import com.zimky.coregateUS.ui.PillView;
import com.zimky.coregateUS.ui.Ui;

/**
 * LockPage —— 第一栏：锁核运行状态。
 *
 * 这一页回答三个问题：
 *  1) 模块现在到底在不在跑？（守护进程 + 当前前台应用）
 *  2) 8 个核各自什么状态？（在线/离线 + 当前频率 + 体质占比）
 *  3) 我想立刻手动压一把怎么办？（「封锁核心」按钮，走 service.sh --lock-once）
 *
 * 注意 onStatus 是幂等的：只读 snapshot 改文字，绝不发起 root。
 * 唯一的 root 动作在按钮点击里，且按钮带 busy 防重入。
 */
public class LockPage extends Page {

    // ---- 顶部状态卡 ----
    private PillView statusPill;
    private TextView statusTitle;
    private TextView statusDesc;
    private TextView daemonLine;

    // ---- 核心网格卡 ----
    private CoreGridView grid;
    private TextView coreSummary;

    // ---- 锁核建议卡 ----
    private TextView adviceBody;
    private TextView adviceTag;

    // ---- 手动封锁 ----
    private View lockBtn;
    private TextView lockBtnText;
    private TextView lockResult;

    public LockPage(Host host) {
        super(host);
    }

    @Override
    public void onCreate() {
        buildStatusCard();
        buildCoreCard();
        buildAdviceCard();
        buildActionCard();
    }

    // ===============================================================
    // 卡片 1：运行状态
    // ===============================================================
    private void buildStatusCard() {
        GlassCard c = addCard("锁核运行状态");

        LinearLayout head = Ui.horz(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);

        statusPill = new PillView(ctx);
        head.addView(statusPill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        statusTitle = Ui.title(ctx, "读取中…");
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = di(10);
        head.addView(statusTitle, tp);

        c.addWrap(head, 10);

        statusDesc = Ui.sub(ctx, "正在探测模块状态…");
        c.addWrap(statusDesc, 8);

        daemonLine = Ui.hint(ctx, "守护进程：—");
        c.addWrap(daemonLine, 0);
    }

    // ===============================================================
    // 卡片 2：核心状态
    // ===============================================================
    private void buildCoreCard() {
        GlassCard c = addCard("核心状态");

        coreSummary = Ui.hint(ctx, "在线 — / 8");
        c.addWrap(coreSummary, 10);

        grid = new CoreGridView(ctx);
        c.addSpaced(grid, 12);

        c.addDivider();

        TextView legend = Ui.hint(ctx, "绿色 = 低频省电 · 蓝色 = 中频 · 橙色 = 高频大核（优先封锁对象）");
        c.addWrap(legend, 0);
    }

    // ===============================================================
    // 卡片 3：锁核建议
    // ===============================================================
    private void buildAdviceCard() {
        GlassCard c = addCard("锁核建议");

        LinearLayout row = Ui.horz(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);

        adviceTag = Ui.text(ctx, "—", 11f, Ui.ACC);
        adviceTag.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(adviceTag, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        c.addWrap(row, 8);

        adviceBody = Ui.sub(ctx, "等待探测结果…");
        c.addWrap(adviceBody, 0);
    }

    // ===============================================================
    // 卡片 4：手动封锁
    // ===============================================================
    private void buildActionCard() {
        GlassCard c = addCard("手动操作");

        TextView desc = Ui.sub(ctx, "立即对当前在线的高频核心执行一次封锁。"
                + "这是一次性动作，不会改变配置；日常自动锁核仍由守护进程负责。");
        c.addWrap(desc, 12);

        lockBtn = makeButton("封锁高频核心", Ui.ACC);
        c.addSpaced(lockBtn, 12);

        lockResult = Ui.hint(ctx, "");
        lockResult.setVisibility(View.GONE);
        c.addWrap(lockResult, 0);
    }

    /** 造一个手势友好的圆角主按钮（纯代码，不依赖 ripple drawable）。 */
    private View makeButton(String label, int color) {
        LinearLayout box = Ui.horz(ctx);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.pill(ctx, color, 0, 14));
        box.setPadding(0, di(13), 0, di(13));
        box.setClickable(true);
        box.setFocusable(true);

        TextView t = Ui.text(ctx, label, 14f, 0xFFFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        box.setTag(t);
        return box;
    }

    private void setBtnText(View btn, String s) {
        Object tag = btn.getTag();
        if (tag instanceof TextView) ((TextView) tag).setText(s);
    }

    // ===============================================================
    // 交互
    // ===============================================================

    /** 由 onCreate 之后统一绑定，避免在 build 阶段拿不到 host。 */
    public void bind() {
        if (lockBtn == null) return;
        lockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLockOnce();
            }
        });
    }

    private void doLockOnce() {
        if (busy) return;
        if (!ModuleBridge.serviceExists()) {
            showResult("未找到模块 service.sh，请确认 CoreGate 已正确安装并重启过。", true);
            return;
        }
        busy = true;
        setBtnText(lockBtn, "封锁中…");
        lockBtn.setAlpha(0.6f);

        // count 传 -1：表示按 config.json 的 lock_cores 决定封锁数量，
        // 与 WebUI 里「立即封锁」按钮完全同一路径。
        ModuleBridge.lockOnce(host.sh(), -1, new ModuleBridge.Callback() {
            @Override
            public void onDone(boolean ok, String msg) {
                busy = false;
                setBtnText(lockBtn, "封锁高频核心");
                lockBtn.setAlpha(1f);
                showResult(msg, !ok);
                if (ok) {
                    // 触发一次全局刷新，让网格与状态卡跟上
                    host.requestRefresh();
                }
            }
        });
    }

    private void showResult(String msg, boolean err) {
        if (msg == null) msg = "";
        lockResult.setVisibility(View.VISIBLE);
        lockResult.setTextColor(err ? Ui.ERR : Ui.OK);
        lockResult.setText(msg);
    }

    // ===============================================================
    // 幂等刷新
    // ===============================================================
    @Override
    public void onStatus(SysProbe.Snapshot s) {
        if (s == null) {
            statusPill.set("探测中", Ui.OFF);
            statusPill.setBreathing(true);
            statusTitle.setText("读取中…");
            statusDesc.setText("正在探测模块状态…");
            return;
        }

        // ---- 运行状态 ----
        boolean alive = s.daemonAlive;
        boolean locked = s.onlineCount > 0 && s.onlineCount < totalCores(s);

        if (!alive) {
            statusPill.set("未运行", Ui.ERR);
            statusPill.setBreathing(false);
            statusTitle.setText("守护进程未启动");
            statusDesc.setText("模块可能已被禁用，或安装后尚未重启。可尝试重新刷入模块并重启设备。");
        } else if (locked) {
            statusPill.set("锁核中", Ui.OK);
            statusPill.setBreathing(true);
            statusTitle.setText("已封锁 " + (totalCores(s) - s.onlineCount) + " 个核心");
            statusDesc.setText("当前前台应用未被识别为游戏/录屏，空闲大核已离线以节省功耗。");
        } else {
            statusPill.set("全核在线", Ui.ACC);
            statusPill.setBreathing(false);
            statusTitle.setText("全部核心在线");
            statusDesc.setText("当前处于游戏或录屏/相机场景，或模块已禁用锁核，性能已全部放开。");
        }

        StringBuilder d = new StringBuilder("守护进程：");
        d.append(alive ? "运行中" : "未运行");
        if (alive && s.daemonPid > 0) {
            d.append(" · PID ").append(s.daemonPid);
        }
        String pkg = s.statePkg != null ? s.statePkg.trim() : "";
        if (pkg.length() > 0 && !"-".equals(pkg)) {
            d.append(" · 前台 ").append(pkg);
        } else {
            d.append(" · 前台 —");
        }
        daemonLine.setText(d.toString());

        // ---- 核心网格 ----
        if (s.cores != null && !s.cores.isEmpty()) {
            grid.setCores(s.cores.toArray(new SysProbe.Core[0]));
        }
        coreSummary.setText("在线 " + s.onlineCount + " / " + totalCores(s)
                + " · 小核簇上限 " + SysProbe.fmtMhz(s.littleMaxCur)
                + " · 大核簇上限 " + SysProbe.fmtMhz(s.bigMaxCur));

        // ---- 建议 ----
        updateAdvice(s);
    }

    private int totalCores(SysProbe.Snapshot s) {
        return (s.cores == null || s.cores.isEmpty()) ? 8 : s.cores.size();
    }

    /**
     * 锁核建议的判定：找出「在线的大核」作为可封锁候选，
     * 给出条数与预期收益，完全对齐 WebUI 的说明口径。
     */
    private void updateAdvice(SysProbe.Snapshot s) {
        if (s.cores == null || s.cores.isEmpty()) {
            adviceTag.setText("—");
            adviceBody.setText("等待探测结果…");
            return;
        }

        int bigOnline = 0;
        int littleOnline = 0;
        for (SysProbe.Core c : s.cores) {
            if (!c.online) continue;
            if ("big".equals(c.cluster)) bigOnline++;
            else littleOnline++;
        }

        if (!s.daemonAlive) {
            adviceTag.setText("不可用");
            adviceTag.setTextColor(Ui.ERR);
            adviceBody.setText("守护进程未运行，锁核不会自动执行。请先确认模块已启用并重启。");
            return;
        }

        if (bigOnline == 0) {
            adviceTag.setText("无需封锁");
            adviceTag.setTextColor(Ui.OK);
            adviceBody.setText("当前在线核心均为小核（" + littleOnline + " 个），已经处于低功耗状态，"
                    + "没有可封锁的高频大核。");
            return;
        }

        adviceTag.setText("建议封锁 " + bigOnline + " 个");
        adviceTag.setTextColor(Ui.WARN);

        StringBuilder b = new StringBuilder();
        b.append("检测到 ").append(bigOnline).append(" 个大核仍在线（共 ")
                .append(littleOnline).append(" 个小核在线）。");
        b.append("大核是功耗大头，日常场景下封掉它们能明显降低待机与轻度使用的功耗；");
        b.append("进入游戏或录屏/相机时模块会自动恢复全部核心。");
        adviceBody.setText(b.toString());
    }

    @Override
    public void onEnter() {
        if (snap() == null) {
            host.requestRefresh();
        }
    }
}