package com.zimky.coregate.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.zimky.coregate.R;
import com.zimky.coregate.core.CoreGateConfig;
import com.zimky.coregate.core.CoreGateService;
import com.zimky.coregate.core.RootShell;

/**
 * Tab4 —— 自定义。
 *
 * 内容分三卡：
 *   1. 锁核参数（auto_lock_count 1~4、poll_interval 5~600）
 *   2. 手动自定义输入（littleMhz / bigMhz / gpuMhz，留空则沿用日常限频页选的档位）
 *   3. 诊断 / 测试 / 清理（诊断、读状态、清临时、一键恢复）
 *
 * 设计纪律：
 *   - 任何写操作都走 CoreGateService（= su 串行队列），不直接拼 shell。
 *   - 留空的输入框表示「不覆盖该字段」，只写用户真正填的项，避免误伤日常限频页。
 *   - 输出区只追加，不清屏，方便用户对比多次操作结果。
 */
public class CustomFragment extends Fragment {

    private EditText etCount;
    private EditText etInterval;
    private EditText etLittle;
    private EditText etBig;
    private EditText etGpu;
    private TextView txtCustomHint;
    private TextView txtOutput;

    private boolean binding = false;
    private boolean loaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_custom, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        etCount = v.findViewById(R.id.et_count);
        etInterval = v.findViewById(R.id.et_interval);
        etLittle = v.findViewById(R.id.et_little);
        etBig = v.findViewById(R.id.et_big);
        etGpu = v.findViewById(R.id.et_gpu);
        txtCustomHint = v.findViewById(R.id.txt_custom_hint);
        txtOutput = v.findViewById(R.id.txt_output);

        Button btnSaveParams = v.findViewById(R.id.btn_save_params);
        Button btnApplyCustom = v.findViewById(R.id.btn_apply_custom);
        Button btnDiagnose = v.findViewById(R.id.btn_diagnose);
        Button btnTest = v.findViewById(R.id.btn_test);
        Button btnClean = v.findViewById(R.id.btn_clean);
        Button btnRestore = v.findViewById(R.id.btn_restore);

        btnSaveParams.setOnClickListener(x -> saveParams());
        btnApplyCustom.setOnClickListener(x -> applyCustom());
        btnDiagnose.setOnClickListener(x -> runDiagnose());
        btnTest.setOnClickListener(x -> readState());
        btnClean.setOnClickListener(x -> cleanTemp());
        btnRestore.setOnClickListener(x -> restoreOriginal());

        loadConfig();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到本页重新拉一次，避免与其它页改动不同步
        loadConfig();
    }

    // =========================================================
    //  读配置 -> 填 UI
    // =========================================================

    private void loadConfig() {
        CoreGateService.loadConfigText(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                CoreGateConfig cfg = CoreGateConfig.parse(stdout);
                applyToUi(cfg);
                loaded = true;
            }
        });
    }

    private void applyToUi(CoreGateConfig cfg) {
        binding = true;
        if (etCount != null) etCount.setText(String.valueOf(cfg.autoLockCount));
        if (etInterval != null) etInterval.setText(String.valueOf(cfg.pollInterval));
        // 手动自定义输入：只反映当前生效值作为提示，用户可改可留空
        if (etLittle != null) etLittle.setHint("当前 " + cfg.littleMhz);
        if (etBig != null) etBig.setHint("当前 " + cfg.bigMhz);
        if (etGpu != null) etGpu.setHint("当前 " + cfg.gpuMhz);
        binding = false;
    }

    // =========================================================
    //  卡1：锁核参数
    // =========================================================

    private void saveParams() {
        int count = parseInt(etCount, -1);
        int interval = parseInt(etInterval, -1);

        if (count < 1 || count > 4) {
            toast("核心个数需在 1~4 之间");
            return;
        }
        if (interval < 5 || interval > 600) {
            toast("检测间隔需在 5~600 秒之间");
            return;
        }

        CoreGateService.loadConfigText(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                CoreGateConfig cfg = CoreGateConfig.parse(stdout);
                cfg.autoLockCount = count;
                cfg.pollInterval = interval;
                CoreGateService.saveConfig(cfg, new RootShell.Callback() {
                    @Override
                    public void onResult(int code, String out, String err) {
                        if (code == 0) toast("参数已保存");
                        else toast("保存失败：" + brief(err));
                    }
                });
            }
        });
    }

    // =========================================================
    //  卡2：手动自定义限频
    // =========================================================

    private void applyCustom() {
        Integer little = parseOrNull(etLittle);
        Integer big = parseOrNull(etBig);
        Integer gpu = parseOrNull(etGpu);

        if (little == null && big == null && gpu == null) {
            toast("三项都为空，未做任何改动");
            return;
        }
        // 合理性粗筛（不是硬性限制，只是拦明显笔误）
        if (little != null && (little < 200 || little > 5000)) { toast("小核数值看起来不合理（200~5000 MHz）"); return; }
        if (big != null && (big < 200 || big > 5000)) { toast("大核数值看起来不合理（200~5000 MHz）"); return; }
        if (gpu != null && (gpu < 100 || gpu > 2000)) { toast("GPU 数值看起来不合理（100~2000 MHz）"); return; }

        final Integer fl = little, fb = big, fg = gpu;

        CoreGateService.loadConfigText(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                CoreGateConfig cfg = CoreGateConfig.parse(stdout);
                if (fl != null) cfg.littleMhz = fl;
                if (fb != null) cfg.bigMhz = fb;
                if (fg != null) cfg.gpuMhz = fg;
                // 用户手动指定频率，视为要启用限频
                cfg.limitFreq = true;

                CoreGateService.saveConfig(cfg, new RootShell.Callback() {
                    @Override
                    public void onResult(int code, String out, String err) {
                        if (code == 0) {
                            toast("已应用并保存");
                            if (txtCustomHint != null) {
                                txtCustomHint.setText("已写入："
                                        + (fl != null ? "小核 " + fl + "MHz " : "")
                                        + (fb != null ? "大核 " + fb + "MHz " : "")
                                        + (fg != null ? "GPU " + fg + "MHz" : "")
                                        + "。写入硬件不存在的档位时，内核会自动对齐到最近可用档，属正常现象。");
                            }
                            // 清空输入框，避免下次误重复应用
                            if (etLittle != null) etLittle.setText("");
                            if (etBig != null) etBig.setText("");
                            if (etGpu != null) etGpu.setText("");
                            loadConfig();
                        } else {
                            toast("保存失败：" + brief(err));
                        }
                    }
                });
            }
        });
    }

    // =========================================================
    //  卡3：诊断 / 测试 / 清理
    // =========================================================

    private void runDiagnose() {
        append("== 环境诊断 ==");
        CoreGateService.diagnose(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                append(stdout);
                if (stderr != null && !stderr.trim().isEmpty()) append("[stderr] " + stderr);
            }
        });
    }

    private void readState() {
        append("== 模块状态 ==");
        CoreGateService.queryDaemon(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                String pid = CoreGateService.pick(stdout, "PID");
                String alive = CoreGateService.pick(stdout, "ALIVE");
                String state = CoreGateService.pick(stdout, "STATE");
                append("守护进程：" + ("1".equals(alive) ? "运行中" : "未运行")
                        + "（PID " + orDash(pid) + "）");
                append("当前状态：" + orDash(state));
            }
        });
        CoreGateService.queryCpu(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                append("小核上限：" + CoreGateService.khzToMhz(CoreGateService.pick(stdout, "P0_MAX")) + " MHz"
                        + "（硬件 " + CoreGateService.khzToMhz(CoreGateService.pick(stdout, "P0_HW")) + " MHz）");
                append("大核上限：" + CoreGateService.khzToMhz(CoreGateService.pick(stdout, "P6_MAX")) + " MHz"
                        + "（硬件 " + CoreGateService.khzToMhz(CoreGateService.pick(stdout, "P6_HW")) + " MHz）");
            }
        });
        CoreGateService.queryGpu(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                String clk = CoreGateService.pick(stdout, "GPU_CLK");
                String pwr = CoreGateService.pick(stdout, "GPU_PWR");
                append("GPU：" + orDash(CoreGateService.khzToMhz(clk)) + " MHz（档位 " + orDash(pwr) + "）");
            }
        });
    }

    private void cleanTemp() {
        CoreGateService.cleanTemp(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                append("== 清理临时文件 ==");
                append("完成（不会动你的 config.json / packages.txt）");
            }
        });
    }

    private void restoreOriginal() {
        append("== 一键恢复原始状态 ==");
        append("正在撤销限频、解锁核心、删除快照…");
        CoreGateService.restoreOriginal(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                append(stdout);
                append("恢复流程已执行。如需彻底移除模块，请在 KernelSU 里关闭/卸载 CoreGate。");
            }
        });
    }

    // =========================================================
    //  工具
    // =========================================================

    /** 追加一行到输出区（保留历史，便于对比多次结果）。 */
    private void append(final String text) {
        if (txtOutput == null || text == null) return;
        txtOutput.post(() -> {
            String old = txtOutput.getText() == null ? "" : txtOutput.getText().toString();
            if (old.startsWith("（输出将")) old = "";
            txtOutput.setText(old + (old.isEmpty() ? "" : "\n") + text.trim());
        });
    }

    private int parseInt(EditText et, int def) {
        if (et == null) return def;
        String s = et.getText() == null ? "" : et.getText().toString().trim();
        if (s.isEmpty()) return def;
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 空 -> null（表示不覆盖）；非法 -> null 并提示。 */
    private Integer parseOrNull(EditText et) {
        if (et == null) return null;
        String s = et.getText() == null ? "" : et.getText().toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String orDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private String brief(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private void toast(final String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() ->
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show());
    }
}
