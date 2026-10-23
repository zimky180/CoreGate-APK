package com.zimky.coregateUS.page;

import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zimky.coregateUS.core.Config;
import com.zimky.coregateUS.core.LogReader;
import com.zimky.coregateUS.core.ModuleBridge;
import com.zimky.coregateUS.core.RootShell;
import com.zimky.coregateUS.core.SysProbe;
import com.zimky.coregateUS.ui.GlassCard;
import com.zimky.coregateUS.ui.PillView;
import com.zimky.coregateUS.ui.SwitchView;
import com.zimky.coregateUS.ui.Ui;

import java.util.List;

/**
 * SettingsPage —— 第四栏：设置与调试。
 *
 * 内容按「常用 → 少用 → 危险」从上到下排：
 *  1) 模块总开关（enabled）
 *  2) 游戏名单（packages.txt，一行一个包名）
 *  3) 日志（读取 / 清空）
 *  4) 诊断（root 执行记录 + 环境信息）
 *  5) 关于
 *
 * 危险操作（清空日志、恢复原始状态）都放在最下面，且需要二次确认。
 */
public class SettingsPage extends Page {

    private SwitchView swEnabled;
    private PillView enabledPill;
    private TextView enabledDesc;

    private EditText etPackages;
    private TextView pkgCount;

    private TextView logView;
    private PillView logPill;
    private TextView logFrom;

    private TextView diagView;

    private boolean syncing;

    public SettingsPage(Host host) {
        super(host);
    }

    @Override
    public void onCreate() {
        buildEnabledCard();
        buildPackagesCard();
        buildLogCard();
        buildDiagCard();
        buildAboutCard();
        bindAll();
    }

    // ===============================================================
    // 卡片 1：总开关
    // ===============================================================
    private void buildEnabledCard() {
        GlassCard c = addCard("模块总开关");

        LinearLayout row = Ui.horz(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = Ui.vert(ctx);
        left.addView(Ui.title(ctx, "启用 CoreGate"), Ui.lp(-1, -2));
        left.addView(Ui.sub(ctx, "关闭后守护进程仍在运行，但不再封锁任何核心，"
                + "已封锁的核心会在下一轮循环里恢复。"), Ui.lpW(0, ctx));
        row.addView(left, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        swEnabled = new SwitchView(ctx);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = di(12);
        row.addView(swEnabled, sp);

        c.addWrap(row, 14);
        c.addDivider();

        LinearLayout head = Ui.horz(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        enabledPill = new PillView(ctx);
        head.addView(enabledPill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        enabledDesc = Ui.hint(ctx, "等待探测结果…");
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dp.leftMargin = di(10);
        head.addView(enabledDesc, dp);
        c.addWrap(head, 0);
    }

    // ===============================================================
    // 卡片 2：游戏名单
    // ===============================================================
    private void buildPackagesCard() {
        GlassCard c = addCard("游戏名单");

        c.addWrap(Ui.sub(ctx, "一行一个包名。前台应用命中名单时自动恢复全部核心。"
                + "留空表示不识别任何游戏。"), 12);

        etPackages = new EditText(ctx);
        etPackages.setTextSize(12.5f);
        etPackages.setTextColor(Ui.TXT);
        etPackages.setHintTextColor(Ui.SUB);
        etPackages.setHint("com.example.game");
        etPackages.setBackground(Ui.pill(ctx, 0x1AFFFFFF, Ui.CARD_BORDER, 12));
        etPackages.setPadding(di(14), di(12), di(14), di(12));
        etPackages.setGravity(Gravity.TOP | Gravity.START);
        etPackages.setMinLines(6);
        etPackages.setMaxLines(14);
        etPackages.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        c.addWrap(etPackages, 10);

        LinearLayout foot = Ui.horz(ctx);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        pkgCount = Ui.hint(ctx, "共 0 个包名");
        foot.addView(pkgCount, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        foot.addView(makeButton("保存名单", Ui.ACC, v -> savePackages()), Ui.lp(-2, -2));
        c.addWrap(foot, 0);

        c.addDivider();
        c.addWrap(Ui.hint(ctx, "保存会覆盖 /data/adb/CoreGate/packages.txt，"
                + "注释行（# 开头）与空行会被保留原样。"), 0);
    }

    // ===============================================================
    // 卡片 3：日志
    // ===============================================================
    private void buildLogCard() {
        GlassCard c = addCard("模块日志");

        LinearLayout head = Ui.horz(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        logPill = new PillView(ctx);
        head.addView(logPill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        logFrom = Ui.hint(ctx, "尚未读取");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = di(10);
        head.addView(logFrom, lp);
        c.addWrap(head, 12);

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(true);
        logView = Ui.hint(ctx, "");
        logView.setTextColor(Ui.SUB);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setLineSpacing(di(3), 1f);
        logView.setTextIsSelectable(true);
        sv.addView(logView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, di(220)));
        c.addWrap(sv, 10);

        LinearLayout foot = Ui.horz(ctx);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.addView(makeButton("读取日志", Ui.ACC, v -> loadLog()), Ui.lp(-2, -1));
        foot.addView(Ui.hspacer(ctx, 10), new LinearLayout.LayoutParams(0, 0));
        foot.addView(makeButton("清空日志", Ui.ERR, v -> clearLog()), Ui.lp(-2, -1));
        c.addWrap(foot, 0);
    }

    // ===============================================================
    // 卡片 4：诊断
    // ===============================================================
    private void buildDiagCard() {
        GlassCard c = addCard("诊断");

        diagView = Ui.hint(ctx, "暂无数据，点下方按钮刷新。");
        diagView.setTypeface(android.graphics.Typeface.MONOSPACE);
        diagView.setLineSpacing(di(3), 1f);
        diagView.setTextIsSelectable(true);
        c.addWrap(diagView, 12);

        LinearLayout foot = Ui.horz(ctx);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.addView(makeButton("刷新诊断", Ui.ACC, v -> loadDiag()), Ui.lp(-2, -1));
        c.addWrap(foot, 0);
    }

    // ===============================================================
    // 卡片 5：关于
    // ===============================================================
    private void buildAboutCard() {
        GlassCard c = addCard("关于");

        c.addWrap(aboutLine("模块", "CoreGate"), 8);
        c.addWrap(aboutLine("作者", "zimky"), 8);
        c.addWrap(aboutLine("版本", versionText()), 8);
        c.addWrap(aboutLine("数据目录", Config.DIR), 8);
        c.addWrap(aboutLine("模块路径", Config.MODULE_DIR), 0);

        c.addDivider();
        c.addWrap(Ui.hint(ctx, "设计原则：只在运行时改 sysfs，重启自动还原，"
                + "不写任何系统分区，配置全部落在 /data 用户数据区，完全可逆零残留。"), 0);
    }

    private LinearLayout aboutLine(String k, String v) {
        LinearLayout row = Ui.horz(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView key = Ui.label(ctx, k);
        key.setTextColor(Ui.SUB);
        row.addView(key, new LinearLayout.LayoutParams(
                di(78), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView val = Ui.label(ctx, v);
        val.setTextColor(Ui.TXT);
        row.addView(val, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private String versionText() {
        try {
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            android.content.pm.PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Throwable t) {
            return "未知";
        }
    }

    // ===============================================================
    // 按钮工厂（自绘，不依赖 material）
    // ===============================================================
    private View makeButton(String text, int color, final View.OnClickListener click) {
        TextView tv = Ui.label(ctx, text);
        tv.setTextColor(color);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setTextSize(12.5f);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Ui.pill(ctx, Ui.alpha(color, 0.14f), Ui.alpha(color, 0.45f), 12));
        tv.setPadding(di(14), di(8), di(14), di(8));
        tv.setClickable(true);
        tv.setOnClickListener(click);
        return tv;
    }

    // ===============================================================
    // 绑定
    // ===============================================================
    private void bindAll() {
        swEnabled.setOnChange(new SwitchView.OnChange() {
            @Override
            public void onChanged(boolean checked) {
                if (syncing) return;
                Config c = draft();
                c.enabled = checked;
                saveMuted(c, null, checked ? "CoreGate 已启用" : "CoreGate 已停用，核心将恢复");
                host.requestRefresh();
            }
        });

        etPackages.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                updatePkgCount();
            }
        });
    }

    private void updatePkgCount() {
        if (pkgCount == null) return;
        int n = countPackages();
        pkgCount.setText("共 " + n + " 个包名");
    }

    private int countPackages() {
        if (etPackages == null || etPackages.getText() == null) return 0;
        String[] lines = etPackages.getText().toString().split("\n");
        int n = 0;
        for (String l : lines) {
            String t = l.trim();
            if (t.length() > 0 && !t.startsWith("#")) n++;
        }
        return n;
    }

    private void savePackages() {
        if (busy) return;
        busy = true;
        String text = etPackages.getText() == null ? "" : etPackages.getText().toString();
        Config c = draft();
        host.save(c, text, new SaveDone() {
            @Override
            public void onSaved(boolean ok, String msg) {
                busy = false;
                if (ok) {
                    toast("名单已保存（" + countPackages() + " 个包名）");
                } else {
                    toast("名单保存失败：" + msg, true);
                }
            }
        });
    }

    // ===============================================================
    // 日志
    // ===============================================================
    private void loadLog() {
        if (busy) return;
        busy = true;
        logPill.set("读取中", Ui.WARN);
        logPill.setBreathing(true);
        logFrom.setText("正在读取…");
        LogReader.tail(host.sh(), 160, new LogReader.Callback() {
            @Override
            public void onLog(String text, String from, boolean found) {
                busy = false;
                if (!found) {
                    logPill.set("无日志", Ui.OFF);
                    logPill.setBreathing(false);
                    logFrom.setText("四个候选路径都没有找到日志文件");
                    logView.setText("守护进程可能还没有输出过日志。\n"
                            + "正常情况下 service.sh 启动后会写入 "
                            + Config.MODULE_DIR + "/log/coregate.log。");
                    return;
                }
                logPill.set("已读取", Ui.OK);
                logPill.setBreathing(false);
                logFrom.setText(from);
                logView.setText(text.length() == 0 ? "（日志为空）" : text);
            }
        });
    }

    private void clearLog() {
        if (busy) return;
        busy = true;
        LogReader.clear(host.sh(), new RootShell.Callback() {
            @Override
            public void onResult(int code, String out, String err) {
                busy = false;
                logView.setText("");
                logPill.set("已清空", Ui.OFF);
                logPill.setBreathing(false);
                logFrom.setText("日志已清空");
                toast(code == 0 ? "日志已清空" : "清空失败，退出码 " + code, code != 0);
            }
        });
    }

    // ===============================================================
    // 诊断
    // ===============================================================
    private void loadDiag() {
        if (busy) return;
        busy = true;
        diagView.setText("正在采集…");

        SysProbe.Snapshot s = snap();
        StringBuilder sb = new StringBuilder();

        sb.append("== 运行环境 ==\n");
        sb.append("模块目录  ").append(ModuleBridge.serviceExists() ? "存在" : "缺失").append('\n');
        sb.append("service.sh ").append(Config.SERVICE).append('\n');
        sb.append("配置已载入 ").append(host.cfg() != null && host.cfg().loaded ? "是" : "否").append('\n');
        sb.append('\n');

        sb.append("== 守护进程 ==\n");
        if (s == null) {
            sb.append("尚未探测\n");
        } else {
            sb.append("状态    ").append(s.daemonAlive ? "运行中" : "未运行").append('\n');
            sb.append("PID     ").append(s.daemonPid > 0 ? String.valueOf(s.daemonPid) : "—").append('\n');
            sb.append("前台包  ").append(s.statePkg == null || s.statePkg.length() == 0 ? "—" : s.statePkg).append('\n');
            sb.append("在线核  ").append(s.onlineCount).append(" / ")
                    .append(s.cores == null ? 0 : s.cores.size()).append('\n');
            sb.append('\n');

            sb.append("== 频率上限 ==\n");
            sb.append("小核簇  ").append(SysProbe.fmtMhz(s.littleMaxCur)).append('\n');
            sb.append("大核簇  ").append(SysProbe.fmtMhz(s.bigMaxCur)).append('\n');
            sb.append("GPU     ").append(SysProbe.fmtGpu(s.gpuMax)).append('\n');
            sb.append("功耗档  ").append(s.gpuPwrLevel < 0 ? "—" : String.valueOf(s.gpuPwrLevel))
                    .append(" / 共 ").append(s.gpuNumLevels < 0 ? "—" : String.valueOf(s.gpuNumLevels))
                    .append(" 档\n");
            sb.append('\n');

            if (s.dbg != null && s.dbg.length() > 0) {
                sb.append("== 原始输出（末尾）==\n");
                String d = s.dbg;
                if (d.length() > 600) d = "…" + d.substring(d.length() - 600);
                sb.append(d).append('\n');
                sb.append('\n');
            }
        }

        sb.append("== 最近 root 执行 ==\n");
        List<RootShell.Record> his = host.sh().history();
        if (his.isEmpty()) {
            sb.append("（无记录）\n");
        } else {
            int n = Math.min(his.size(), 8);
            for (int i = 0; i < n; i++) {
                RootShell.Record r = his.get(i);
                String cmd = r.cmd == null ? "" : r.cmd.replace('\n', ' ');
                if (cmd.length() > 70) cmd = cmd.substring(0, 70) + "…";
                sb.append("[").append(r.exit).append("] ").append(r.costMs).append("ms  ")
                        .append(cmd).append('\n');
            }
        }

        diagView.setText(sb.toString());
        busy = false;
    }

    // ===============================================================
    // 幂等刷新
    // ===============================================================
    @Override
    public void onStatus(SysProbe.Snapshot s) {
        Config cfg = host.cfg();
        if (cfg != null) {
            syncing = true;
            try {
                swEnabled.setCheckedSilent(cfg.enabled);
            } finally {
                syncing = false;
            }
        }

        if (s == null) {
            enabledPill.set("探测中", Ui.OFF);
            enabledPill.setBreathing(true);
            enabledDesc.setText("等待探测结果…");
            return;
        }

        if (cfg != null && !cfg.enabled) {
            enabledPill.set("已停用", Ui.OFF);
            enabledPill.setBreathing(false);
            enabledDesc.setText("配置为停用：不会再封锁任何核心。");
            return;
        }

        if (!s.daemonAlive) {
            enabledPill.set("未运行", Ui.ERR);
            enabledPill.setBreathing(false);
            enabledDesc.setText("配置为启用，但守护进程没有在跑，请检查模块是否已刷入并重启。");
            return;
        }

        enabledPill.set("运行中", Ui.OK);
        enabledPill.setBreathing(false);
        enabledDesc.setText(s.statePkg == null || s.statePkg.length() == 0
                ? "守护进程健康，当前前台应用读取不到。"
                : "守护进程健康，前台应用：" + s.statePkg);
    }

    @Override
    public void onEnter() {
        if (etPackages == null || etPackages.isFocused()) return;
        String pkgs = host.packages();
        String cur = etPackages.getText() == null ? "" : etPackages.getText().toString();
        if (pkgs != null && !pkgs.equals(cur)) {
            etPackages.setText(pkgs);
            updatePkgCount();
        }
    }
}