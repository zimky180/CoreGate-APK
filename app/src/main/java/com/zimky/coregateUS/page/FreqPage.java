package com.zimky.coregateUS.page;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zimky.coregateUS.core.Config;
import com.zimky.coregateUS.core.FreqCap;
import com.zimky.coregateUS.core.SysProbe;
import com.zimky.coregateUS.ui.GlassCard;
import com.zimky.coregateUS.ui.PillView;
import com.zimky.coregateUS.ui.SwitchView;
import com.zimky.coregateUS.ui.Ui;

/**
 * FreqPage —— 第三栏：日常限频。
 *
 * 对应 WebUI 的「日常限频」区块：
 *  1) 限频总开关（limit_freq）
 *  2) 四行实况：小核簇 / 大核簇 / GPU 频率 / GPU 功耗档
 *  3) 目标档位说明（只读，与 service.sh 硬编码保持一致）
 *
 * 为什么只做只读实况：
 *  限频数值是写死在 service.sh 里的（CAP_LITTLE_MHZ 等），APK 改了也不生效，
 *  所以这里必须如实展示「模块认的目标是多少」，而不是给个能拖但没用的滑杆。
 *  实况数据全部来自 onStatus 的快照，本页不额外发起 root 请求。
 */
public class FreqPage extends Page {

    private SwitchView swLimit;
    private PillView limitPill;
    private TextView limitDesc;

    /** 四行实况：标题 / 目标 / 实测 / 状态标签 */
    private TextView littleActual;
    private TextView bigActual;
    private TextView gpuActual;
    private TextView pwrActual;

    private PillView littleTag;
    private PillView bigTag;
    private PillView gpuTag;
    private PillView pwrTag;

    private boolean syncing;

    public FreqPage(Host host) {
        super(host);
    }

    @Override
    public void onCreate() {
        buildSwitchCard();
        buildLiveCard();
        buildTargetCard();
        bindAll();
    }

    // ===============================================================
    // 卡片 1：开关
    // ===============================================================
    private void buildSwitchCard() {
        GlassCard c = addCard("日常限频");

        LinearLayout row = Ui.horz(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = Ui.vert(ctx);
        TextView t = Ui.title(ctx, "限制最高频率");
        left.addView(t, Ui.lp(-1, -2));
        TextView sub = Ui.sub(ctx, "把 CPU 与 GPU 的最高频率压到低档，日常使用几乎无感，"
                + "但发热和功耗会明显下降。玩游戏时若命中名单会自动放开。");
        left.addView(sub, Ui.lpW(0, ctx));
        row.addView(left, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        swLimit = new SwitchView(ctx);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = di(12);
        row.addView(swLimit, sp);

        c.addWrap(row, 14);
    }

    // ===============================================================
    // 卡片 2：实况
    // ===============================================================
    private void buildLiveCard() {
        GlassCard c = addCard("限频实况");

        LinearLayout head = Ui.horz(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        limitPill = new PillView(ctx);
        head.addView(limitPill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        limitDesc = Ui.hint(ctx, "等待探测结果…");
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dp.leftMargin = di(10);
        head.addView(limitDesc, dp);
        c.addWrap(head, 12);

        c.addDivider();

        littleActual = Ui.label(ctx, "—");
        littleTag = new PillView(ctx);
        c.addWrap(liveRow("小核簇", littleActual, littleTag), 10);

        bigActual = Ui.label(ctx, "—");
        bigTag = new PillView(ctx);
        c.addWrap(liveRow("大核簇", bigActual, bigTag), 10);

        gpuActual = Ui.label(ctx, "—");
        gpuTag = new PillView(ctx);
        c.addWrap(liveRow("GPU 频率", gpuActual, gpuTag), 10);

        pwrActual = Ui.label(ctx, "—");
        pwrTag = new PillView(ctx);
        c.addWrap(liveRow("GPU 功耗档", pwrActual, pwrTag), 0);
    }

    /** 一行实况：左侧名称 + 中间实测值 + 右侧状态胶囊。 */
    private LinearLayout liveRow(String name, TextView actual, PillView tag) {
        LinearLayout row = Ui.horz(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView n = Ui.label(ctx, name);
        n.setTextColor(Ui.SUB);
        row.addView(n, new LinearLayout.LayoutParams(
                di(86), LinearLayout.LayoutParams.WRAP_CONTENT));

        actual.setTextColor(Ui.TXT);
        row.addView(actual, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(tag, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // ===============================================================
    // 卡片 3：目标档位
    // ===============================================================
    private void buildTargetCard() {
        GlassCard c = addCard("限频目标档位");

        c.addWrap(Ui.sub(ctx, "目标值由模块脚本固定，与 WebUI 完全一致。真机的频率档位不是等比的，"
                + "所以这里展示的是内核会真正落到的那一档，而不是百分比。"), 12);

        c.addDivider();

        c.addWrap(targetLine("小核簇", "≤ " + FreqCap.LITTLE_MHZ + " MHz"), 10);
        c.addWrap(targetLine("大核簇", "≤ " + FreqCap.BIG_MHZ + " MHz"), 10);
        c.addWrap(targetLine("GPU 频率", "≤ " + FreqCap.GPU_MHZ + " MHz"), 10);
        c.addWrap(targetLine("GPU 功耗档", "= " + FreqCap.GPU_PWRLEVEL + "（0 最高，数字越大越省）"), 0);

        c.addDivider();

        c.addWrap(Ui.hint(ctx, "说明：写入 max_gpuclk 时会 +800000 对齐，"
                + "因此实测值会是 500000000 Hz 而不是 500000000 以外的零头，属正常现象。"), 0);
    }

    private LinearLayout targetLine(String name, String value) {
        LinearLayout row = Ui.horz(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = Ui.label(ctx, name);
        n.setTextColor(Ui.SUB);
        row.addView(n, new LinearLayout.LayoutParams(
                di(86), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView v = Ui.label(ctx, value);
        v.setTextColor(Ui.TXT);
        row.addView(v, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    // ===============================================================
    // 绑定
    // ===============================================================
    private void bindAll() {
        swLimit.setOnChange(new SwitchView.OnChange() {
            @Override
            public void onChanged(boolean checked) {
                if (syncing) return;
                Config c = draft();
                c.limitFreq = checked;
                // 限频是脚本在下一轮循环里落地的，存完后主动刷一次实况
                saveMuted(c, null, checked ? "已开启日常限频，下一轮循环生效" : "已关闭日常限频");
                host.requestRefresh();
            }
        });
    }

    // ===============================================================
    // 幂等刷新
    // ===============================================================
    @Override
    public void onStatus(SysProbe.Snapshot s) {
        Config cfg = host.cfg();
        boolean limitOn = cfg != null && cfg.limitFreq;

        syncing = true;
        try {
            swLimit.setCheckedSilent(limitOn);
        } finally {
            syncing = false;
        }

        if (s == null) {
            limitPill.set("探测中", Ui.OFF);
            limitPill.setBreathing(true);
            limitDesc.setText("等待探测结果…");
            fillUnknown(littleActual, littleTag);
            fillUnknown(bigActual, bigTag);
            fillUnknown(gpuActual, gpuTag);
            fillUnknown(pwrActual, pwrTag);
            return;
        }

        FreqCap.Item li = FreqCap.judgeLittle(s, limitOn);
        FreqCap.Item bi = FreqCap.judgeBig(s, limitOn);
        FreqCap.Item gi = FreqCap.judgeGpu(s, limitOn);
        FreqCap.Item pi = FreqCap.judgePwr(s, limitOn);

        applyItem(littleActual, littleTag, li);
        applyItem(bigActual, bigTag, bi);
        applyItem(gpuActual, gpuTag, gi);
        applyItem(pwrActual, pwrTag, pi);

        // 总状态：关着就是灰；开着但有一项未生效就报警
        if (!limitOn) {
            limitPill.set("未开启", Ui.OFF);
            limitPill.setBreathing(false);
            limitDesc.setText("限频关闭，CPU / GPU 按系统默认策略运行。");
            return;
        }

        boolean anyOver = li.state == FreqCap.STATE_OVER
                || bi.state == FreqCap.STATE_OVER
                || gi.state == FreqCap.STATE_OVER
                || pi.state == FreqCap.STATE_OVER;
        boolean allOk = li.state == FreqCap.STATE_OK
                && bi.state == FreqCap.STATE_OK
                && gi.state == FreqCap.STATE_OK
                && pi.state == FreqCap.STATE_OK;

        if (allOk) {
            limitPill.set("已生效", Ui.OK);
            limitPill.setBreathing(false);
            limitDesc.setText("四项全部落在目标档位。");
        } else if (anyOver) {
            limitPill.set("未完全生效", Ui.ERR);
            limitPill.setBreathing(false);
            limitDesc.setText("有项目仍未压到目标值，可能刚改动、还没到下一轮循环，"
                    + "或被其他模块 / 系统策略改写。");
        } else {
            limitPill.set("部分生效", Ui.WARN);
            limitPill.setBreathing(true);
            limitDesc.setText("部分项目读不到实测值，稍等片刻再看。");
        }
    }

    private void applyItem(TextView actual, PillView tag, FreqCap.Item item) {
        actual.setText(item.actual);
        tag.set(FreqCap.stateText(item.state), FreqCap.stateColor(item.state));
        tag.setBreathing(false);
    }

    private void fillUnknown(TextView actual, PillView tag) {
        actual.setText("—");
        tag.set("未知", FreqCap.stateColor(FreqCap.STATE_UNKNOWN));
        tag.setBreathing(false);
    }

    @Override
    public void onEnter() {
        Config c = host.cfg();
        if (c != null) {
            syncing = true;
            try {
                swLimit.setCheckedSilent(c.limitFreq);
            } finally {
                syncing = false;
            }
        }
    }
}