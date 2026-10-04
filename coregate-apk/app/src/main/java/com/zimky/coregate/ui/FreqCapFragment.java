package com.zimky.coregate.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
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
 * Tab3 —— 日常限频。
 *
 * 档位表取自参考模块『晨夕』的实测档位（本机 SM8845 内核真实支持的频率），
 * 只搬设计不搬代码；写入仍走 service.sh 的限频逻辑（config.json 字段）。
 *
 * 默认：小核 2266 / 大核 1651 / GPU 500。
 */
public class FreqCapFragment extends Fragment {

    // 档位表（MHz）—— 对齐晨夕预设，硬件真实存在
    private static final int[] LITTLE = {1800, 2000, 2100, 2200, 2266, 2400, 2600};
    private static final int[] BIG = {1400, 1500, 1600, 1651, 1800, 2000, 2200};
    // GPU: 1225 -> 195，共 17 档（pwrlevel 0~16）
    private static final int[] GPU = {
            1225, 1050, 900, 800, 735, 700, 650, 600, 550,
            500, 450, 400, 350, 300, 260, 220, 195};

    private static final int DEF_LITTLE = 2266;
    private static final int DEF_BIG = 1651;
    private static final int DEF_GPU = 500;

    private Switch swLimit;
    private Spinner spLittle, spBig, spGpu;
    private TextView txtLive, txtHint;
    private Button btnRefresh, btnApply;

    private CoreGateConfig cfg = new CoreGateConfig();
    private boolean binding = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_freq_cap, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        swLimit = v.findViewById(R.id.sw_limit);
        spLittle = v.findViewById(R.id.sp_little);
        spBig = v.findViewById(R.id.sp_big);
        spGpu = v.findViewById(R.id.sp_gpu);
        txtLive = v.findViewById(R.id.txt_freq_live);
        txtHint = v.findViewById(R.id.txt_freq_hint);
        btnRefresh = v.findViewById(R.id.btn_refresh_freq);
        btnApply = v.findViewById(R.id.btn_apply_freq);

        bindSpinner(spLittle, LITTLE);
        bindSpinner(spBig, BIG);
        bindSpinner(spGpu, GPU);

        swLimit.setOnCheckedChangeListener((b, checked) -> onSwitchChanged());
        // 下方「刷新限频实况」不设冷却，用户想看就给看
        btnRefresh.setOnClickListener(x -> refreshLive());
        btnApply.setOnClickListener(x -> applyTiers());

        loadConfig();
        refreshLive();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConfig();
    }

    // ---------------------------------------------------------
    //  读
    // ---------------------------------------------------------

    private void loadConfig() {
        CoreGateService.loadConfigText(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                cfg = CoreGateConfig.parse(stdout);
                applyToUi();
            }
        });
    }

    private void applyToUi() {
        binding = true;
        swLimit.setChecked(cfg.limitFreq);
        selectValue(spLittle, LITTLE, cfg.littleMhz);
        selectValue(spBig, BIG, cfg.bigMhz);
        selectValue(spGpu, GPU, cfg.gpuMhz);
        binding = false;
        txtHint.setText("当前档位：小核 " + cfg.littleMhz + " / 大核 " + cfg.bigMhz
                + " / GPU " + cfg.gpuMhz + " MHz。");
    }

    /** 读实况：簇 max 频率 + GPU 实况。 */
    private void refreshLive() {
        txtLive.setText("实况读取中…");
        CoreGateService.queryCpu(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                final String p0 = CoreGateService.pick(stdout, "P0_MAX");
                final String p6 = CoreGateService.pick(stdout, "P6_MAX");
                CoreGateService.queryGpu(new RootShell.Callback() {
                    @Override
                    public void onResult(int ec2, String out2, String err2) {
                        String clk = CoreGateService.pick(out2, "GPU_CLK");
                        String pwr = CoreGateService.pick(out2, "GPU_PWR");
                        String clkMhz = CoreGateService.khzToMhz(clk);
                        txtLive.setText(
                                "小核簇上限 " + safeMhz(p0) + " MHz\n"
                                        + "大核簇上限 " + safeMhz(p6) + " MHz\n"
                                        + "GPU 上限 " + clkMhz + " MHz（pwrlevel " + orDash(pwr) + "）");
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------
    //  写
    // ---------------------------------------------------------

    private void onSwitchChanged() {
        if (binding) return;
        cfg.limitFreq = swLimit.isChecked();
        saveConfig(swLimit.isChecked() ? "已开启日常限频。" : "已关闭，频率将恢复到开启前状态。");
    }

    private void applyTiers() {
        cfg.littleMhz = LITTLE[spLittle.getSelectedItemPosition()];
        cfg.bigMhz = BIG[spBig.getSelectedItemPosition()];
        cfg.gpuMhz = GPU[spGpu.getSelectedItemPosition()];
        cfg.limitFreq = true;   // 应用档位即视为开启限频
        binding = true;
        swLimit.setChecked(true);
        binding = false;
        saveConfig("档位已应用：小核 " + cfg.littleMhz + " / 大核 " + cfg.bigMhz
                + " / GPU " + cfg.gpuMhz + " MHz。");
    }

    private void saveConfig(final String okMsg) {
        txtHint.setText("保存中…");
        CoreGateService.saveConfig(cfg, new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (exitCode == 0) {
                    txtHint.setText(okMsg);
                    toast(okMsg);
                } else {
                    txtHint.setText("保存失败：" + brief(stderr));
                }
            }
        });
    }

    // ---------------------------------------------------------
    //  工具
    // ---------------------------------------------------------

    private void bindSpinner(Spinner sp, int[] vals) {
        String[] items = new String[vals.length];
        for (int i = 0; i < vals.length; i++) items[i] = vals[i] + " MHz";
        ArrayAdapter<String> ad = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
    }

    private void selectValue(Spinner sp, int[] vals, int target) {
        for (int i = 0; i < vals.length; i++) {
            if (vals[i] == target) {
                sp.setSelection(i);
                return;
            }
        }
    }

    private String safeMhz(String khz) {
        if (khz == null || khz.trim().isEmpty()) return "—";
        return CoreGateService.khzToMhz(khz);
    }

    private String orDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s.trim();
    }

    private String brief(String s) {
        if (s == null || s.trim().isEmpty()) return "未知错误";
        s = s.trim();
        int nl = s.indexOf('\n');
        if (nl > 0) s = s.substring(0, nl);
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private void toast(String msg) {
        if (getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }
}