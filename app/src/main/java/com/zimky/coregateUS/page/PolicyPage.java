package com.zimky.coregateUS.page;

import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zimky.coregateUS.core.Config;
import com.zimky.coregateUS.core.SysProbe;
import com.zimky.coregateUS.ui.GlassCard;
import com.zimky.coregateUS.ui.PillView;
import com.zimky.coregateUS.ui.SwitchView;
import com.zimky.coregateUS.ui.Ui;

/**
 * PolicyPage —— 第二栏：场景策略。
 *
 * 对应 WebUI 里的三块内容：
 *  1) 录屏 / 相机自动恢复（capture_boost）
 *  2) 充电时不锁核（no_lock_charging）
 *  3) 轮询间隔（poll_interval，5~600 秒）
 *
 * 设计原则：改动即保存（无「保存」按钮），但轮询间隔用输入框 + 失焦提交，
 * 避免每敲一个数字就写一次盘。
 */
public class PolicyPage extends Page {

    private SwitchView swCapture;
    private SwitchView swCharging;

    private EditText etInterval;
    private TextView intervalHint;

    /** 场景实况（只读） */
    private PillView scenePill;
    private TextView sceneText;

    /** 防止 setCheckedSilent 引发的回调又被当成用户操作。 */
    private boolean syncing;

    public PolicyPage(Host host) {
        super(host);
    }

    @Override
    public void onCreate() {
        buildCaptureCard();
        buildChargingCard();
        buildIntervalCard();
        buildSceneCard();
        bindAll();
    }

    // ===============================================================
    // 卡片 1：录屏 / 相机
    // ===============================================================
    private void buildCaptureCard() {
        GlassCard c = addCard("录屏 / 相机自动恢复");

        swCapture = new SwitchView(ctx);
        c.addSpaced(Ui.row(ctx, "录屏或拍照时恢复全部核心",
                "开启后，检测到录屏或相机正在使用时自动解锁所有核心，避免录制卡顿、拍照掉帧。",
                swCapture), 12);

        c.addDivider();

        TextView note = Ui.hint(ctx, "检测名单内置了常见录屏与相机包名（含 ColorOS / OxygenOS）。
关闭此项后，模块只认 packages.txt 里的游戏列表。");
        c.addWrap(note, 0);
    }

    // ===============================================================
    // 卡片 2：充电策略
    // ===============================================================
    private void buildChargingCard() {
        GlassCard c = addCard("充电策略");

        swCharging = new SwitchView(ctx);
        c.addSpaced(Ui.row(ctx, "充电时不锁核",
                "插上充电器时保持全部核心在线。充电时发热本就明显，锁核收益有限，"
                        + "而放开核心能让充电管理和后台任务更快完成。",
                swCharging), 12);

        c.addDivider();

        TextView note = Ui.hint(ctx, "默认关闭：即便在充电，也照常锁核。");
        c.addWrap(note, 0);
    }

    // ===============================================================
    // 卡片 3：轮询间隔
    // ===============================================================
    private void buildIntervalCard() {
        GlassCard c = addCard("轮询间隔");

        TextView desc = Ui.sub(ctx, "守护进程每隔多少秒检查一次前台应用，据此决定是否锁核。"
                + "间隔越短响应越快，但唤醒次数越多；间隔越长越省电，但进出游戏的反应会变慢。");
        c.addWrap(desc, 12);

        LinearLayout box = Ui.horz(ctx);
        box.setGravity(Gravity.CENTER_VERTICAL);

        etInterval = new EditText(ctx);
        etInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
        etInterval.setTextSize(15f);
        etInterval.setTextColor(Ui.TXT);
        etInterval.setHintTextColor(Ui.SUB);
        etInterval.setHint("30");
        etInterval.setSingleLine(true);
        etInterval.setBackground(null);
        etInterval.setPadding(di(14), di(11), di(14), di(11));
        etInterval.setBackground(Ui.pill(ctx, 0x1AFFFFFF, Ui.CARD_BORDER, 12));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(di(84),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        box.addView(etInterval, ep);

        TextView unit = Ui.label(ctx, "秒");
        unit.setTextColor(Ui.SUB);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        up.leftMargin = di(10);
        box.addView(unit, up);

        c.addWrap(box, 10);

        intervalHint = Ui.hint(ctx, "允许范围 5 ~ 600 秒，默认 30 秒。留空或填非法值将回退为 30。");
        c.addWrap(intervalHint, 0);
    }

    // ===============================================================
    // 卡片 4：场景实况
    // ===============================================================
    private void buildSceneCard() {
        GlassCard c = addCard("当前场景判断");

        LinearLayout row = Ui.horz(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);

        scenePill = new PillView(ctx);
        row.addView(scenePill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView cap = Ui.label(ctx, "模块判定");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = di(10);
        row.addView(cap, cp);

        c.addWrap(row, 10);

        sceneText = Ui.sub(ctx, "等待探测结果…");
        c.addWrap(sceneText, 0);
    }

    // ===============================================================
    // 绑定
    // ===============================================================
    private void bindAll() {
        swCapture.setOnChange(new SwitchView.OnChange() {
            @Override
            public void onChanged(boolean checked) {
                if (syncing) return;
                Config c = draft();
                c.captureBoost = checked;
                saveMuted(c, null, checked ? "已开启录屏/相机自动恢复" : "已关闭录屏/相机自动恢复");
            }
        });

        swCharging.setOnChange(new SwitchView.OnChange() {
            @Override
            public void onChanged(boolean checked) {
                if (syncing) return;
                Config c = draft();
                c.noLockCharging = checked;
                saveMuted(c, null, checked ? "充电时将保持全核在线" : "充电时仍照常锁核");
            }
        });

        etInterval.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) commitInterval();
            }
        });
    }

    /** 提交输入框的间隔值。 */
    private void commitInterval() {
        if (etInterval == null) return;
        String raw = etInterval.getText() == null ? "" : etInterval.getText().toString().trim();
        int v = 30;
        if (raw.length() > 0) {
            try {
                v = Integer.parseInt(raw);
            } catch (Throwable t) {
                v = 30;
            }
        }
        int clamped = Config.clampInterval(v);
        etInterval.setText(String.valueOf(clamped));

        Config cur = host.cfg();
        if (cur != null && cur.pollInterval == clamped) {
            return; // 没变化就不写盘，避免无意义的 root 调用
        }
        Config c = draft();
        c.pollInterval = clamped;
        saveMuted(c, null, "轮询间隔已设为 " + clamped + " 秒");
    }

    // ===============================================================
    // 幂等刷新
    // ===============================================================
    @Override
    public void onStatus(SysProbe.Snapshot s) {
        // 只在用户没在编辑时同步，避免把正在输入的数字按掉
        syncing = true;
        try {
            Config c = host.cfg();
            if (c != null) {
                swCapture.setCheckedSilent(c.captureBoost);
                swCharging.setCheckedSilent(c.noLockCharging);
                if (etInterval != null && !etInterval.isFocused()) {
                    String cur = etInterval.getText() == null
                            ? "" : etInterval.getText().toString().trim();
                    String want = String.valueOf(c.pollInterval);
                    if (!want.equals(cur)) etInterval.setText(want);
                }
            }
        } finally {
            syncing = false;
        }

        if (s == null) {
            scenePill.set("探测中", Ui.OFF);
            scenePill.setBreathing(true);
            sceneText.setText("等待探测结果…");
            return;
        }

        // 场景判定：优先看 state 里的前台包名与在线核心数
        String pkg = s.statePkg == null ? "" : s.statePkg.trim();
        boolean hasPkg = pkg.length() > 0 && !"-".equals(pkg);
        int total = (s.cores == null || s.cores.isEmpty()) ? 8 : s.cores.size();
        boolean allOnline = s.onlineCount >= total;

        if (!s.daemonAlive) {
            scenePill.set("未运行", Ui.ERR);
            scenePill.setBreathing(false);
            sceneText.setText("守护进程未运行，场景判断暂停。");
            return;
        }

        if (allOnline) {
            scenePill.set("已放开", Ui.ACC);
            scenePill.setBreathing(false);
            String reason;
            if (hasPkg) {
                reason = "前台应用 " + pkg + " 命中游戏或录屏/相机名单，全部核心已恢复。";
            } else {
                reason = "全部核心在线：可能命中了游戏/录屏名单，或锁核总开关被关闭。";
            }
            sceneText.setText(reason);
        } else {
            scenePill.set("锁核中", Ui.OK);
            scenePill.setBreathing(true);
            String extra = hasPkg
                    ? "前台应用 " + pkg + " 未命中游戏/录屏名单。"
                    : "当前前台应用无法读取（ColorOS 限制），按非游戏处理。";
            sceneText.setText(extra + "已封锁 " + (total - s.onlineCount) + " 个核心以节省功耗。");
        }
    }

    @Override
    public void onEnter() {
        Config c = host.cfg();
        if (c != null) {
            syncing = true;
            try {
                swCapture.setCheckedSilent(c.captureBoost);
                swCharging.setCheckedSilent(c.noLockCharging);
                if (etInterval != null && !etInterval.isFocused()) {
                    etInterval.setText(String.valueOf(c.pollInterval));
                }
            } finally {
                syncing = false;
            }
        }
    }
}