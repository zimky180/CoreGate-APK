package com.zimky.coregate.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.zimky.coregate.R;
import com.zimky.coregate.core.CoreGateService;
import com.zimky.coregate.core.RootShell;

import java.util.Locale;

/**
 * Tab1 — 锁核状态。
 *
 * 只读展示 + 一个主动操作（立即锁定）。
 * 所有 root 操作都走 CoreGateService，本类不直接拼 shell。
 */
public class LockStatusFragment extends Fragment {

    private View dotDaemon;
    private TextView txtDaemon, txtState, txtFocus, txtSnapshot, txtCluster, txtAdvice, txtLockHint;
    private LinearLayout coreContainer;
    private EditText etLockCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_lock_status, container, false);

        dotDaemon = root.findViewById(R.id.dot_daemon);
        txtDaemon = root.findViewById(R.id.txt_daemon);
        txtState = root.findViewById(R.id.txt_state);
        txtFocus = root.findViewById(R.id.txt_focus);
        txtSnapshot = root.findViewById(R.id.txt_snapshot);
        txtCluster = root.findViewById(R.id.txt_cluster);
        txtAdvice = root.findViewById(R.id.txt_advice);
        txtLockHint = root.findViewById(R.id.txt_lock_hint);
        coreContainer = root.findViewById(R.id.core_container);
        etLockCount = root.findViewById(R.id.et_lock_count);

        Button btnRefresh = root.findViewById(R.id.btn_refresh);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });

        Button btnLockNow = root.findViewById(R.id.btn_lock_now);
        btnLockNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockNow();
            }
        });

        refresh();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从别的 Tab 切回来时刷新一次，保证状态不过期
        if (isVisible() && coreContainer != null) refresh();
    }

    /** 读取守护进程 + CPU + 前台包名 + 建议，刷新界面。 */
    private void refresh() {
        if (coreContainer == null) return;

        // 守护进程
        CoreGateService.queryDaemon(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (!isAdded() || txtDaemon == null) return;
                boolean alive = "1".equals(CoreGateService.pick(stdout, "ALIVE"));
                String pid = CoreGateService.pick(stdout, "PID");
                String state = CoreGateService.pick(stdout, "STATE");
                String snap = CoreGateService.pick(stdout, "SNAP");

                dotDaemon.setBackgroundResource(alive ? R.drawable.dot_green : R.drawable.dot_red);
                txtDaemon.setText(alive
                        ? "守护进程：运行中（PID " + (pid.isEmpty() ? "—" : pid) + "）"
                        : "守护进程：未运行");
                txtState.setText("当前状态：" + (state.isEmpty() ? "—" : state));
                txtSnapshot.setText("频率快照：" + ("1".equals(snap)
                        ? "已记录"
                        : "未记录（关限频后按硬件上限恢复）"));
            }
        });

        // 前台包名
        CoreGateService.queryFocusPkg(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (!isAdded() || txtFocus == null) return;
                String pkg = CoreGateService.pick(stdout, "PKG");
                if (pkg.isEmpty()) pkg = "—";
                txtFocus.setText("前台包名：" + pkg);
            }
        });

        // CPU 核心
        CoreGateService.queryCpu(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (!isAdded()) return;
                renderCpu(stdout);
            }
        });

        // 锁核建议（诊断摘要）
        CoreGateService.diagnose(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (!isAdded() || txtAdvice == null) return;
                txtAdvice.setText(buildAdvice(stdout));
            }
        });
    }

    /** 从诊断输出里提炼一句人话建议。 */
    private String buildAdvice(String dump) {
        if (dump == null || dump.trim().isEmpty()) return "暂无建议。";
        String p0 = CoreGateService.pick(dump, "P0");
        String p6 = CoreGateService.pick(dump, "P6");
        String gpu = CoreGateService.pick(dump, "GPU");

        StringBuilder sb = new StringBuilder();
        if (p0.isEmpty() && p6.isEmpty()) {
            sb.append("未读到 CPU 频率节点，请确认模块已刷入且 root 正常。");
        } else {
            sb.append("小核上限 ").append(mhz(p0))
              .append(" MHz · 大核上限 ").append(mhz(p6))
              .append(" MHz");
        }
        if (!gpu.isEmpty()) {
            sb.append("\nGPU 上限 ").append(mhz(gpu)).append(" MHz");
        }
        return sb.toString();
    }

    private String mhz(String khz) {
        return CoreGateService.khzToMhz(khz);
    }

    /** 把 queryCpu 的输出渲染成 8 行核心状态。 */
    private void renderCpu(String out) {
        if (out == null) out = "";

        String p0Max = CoreGateService.pick(out, "P0_MAX");
        String p6Max = CoreGateService.pick(out, "P6_MAX");

        txtCluster.setText("小核簇上限："
                + mhz(p0Max) + " MHz / 大核簇上限："
                + mhz(p6Max) + " MHz");

        coreContainer.removeAllViews();
        for (int i = 0; i < 8; i++) {
            String on = CoreGateService.pick(out, "C" + i + "_ON");
            String freq = CoreGateService.pick(out, "C" + i + "_FREQ");
            // 空视为在线（cpu0 永不下线，且部分内核不导出 online 节点）
            boolean online = !"0".equals(on);
            coreContainer.addView(buildCoreRow(i, online, freq));
        }
    }

    /** 单行核心状态：色点 + 文案。 */
    private View buildCoreRow(int index, boolean online, String freqKhz) {
        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(6);
        row.setLayoutParams(rp);

        View dot = new View(getActivity());
        int size = dp(8);
        dot.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(online ? 0xFF34D399 : 0xFFEF4444);
        dot.setBackground(shape);
        row.addView(dot);

        TextView tv = new TextView(getActivity());
        String name = (index < 6) ? "小核" : "大核";
        String state = online ? "在线" : "已封锁";
        tv.setText(String.format(Locale.US,
                "  CPU%d（%s）— %s · %s MHz", index, name, state, mhz(freqKhz)));
        tv.setTextColor(0xFFE6ECF5);
        tv.setTextSize(13f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        return row;
    }

    /** 立即按用户填的数量封锁核心。 */
    private void lockNow() {
        final int count = parseCount();
        if (count < 0) {
            txtLockHint.setText("请输入 1~4 之间的封锁核心数。");
            return;
        }
        txtLockHint.setText("正在锁定 " + count + " 个核心…");

        CoreGateService.applyLock(count, new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (!isAdded() || txtLockHint == null) return;
                txtLockHint.setText(exitCode == 0
                        ? ("已请求封锁 " + count + " 个核心；cpu0 保持在线，重启即还原。")
                        : "锁定请求失败，请检查 root 权限。");
                refresh();
            }
        });
    }

    /** 读取输入框，非法回 -1。 */
    private int parseCount() {
        if (etLockCount == null) return -1;
        String s = etLockCount.getText().toString().trim();
        if (s.isEmpty()) return -1;
        try {
            int n = Integer.parseInt(s);
            if (n < 1 || n > 4) return -1;
            return n;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}