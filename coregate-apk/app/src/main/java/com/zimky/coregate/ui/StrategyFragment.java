package com.zimky.coregate.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
 * Tab2 —— 恢复策略。
 *
 * 负责：录屏/相机自动恢复开关、游戏自动恢复（包名列表）、
 *      息屏自动锁核、充电时仍锁核。
 *
 * 每次改动立即整体写回 config.json（原子替换），无需重启。
 */
public class StrategyFragment extends Fragment {

    private Switch swCapture;
    private Switch swScreenOff;
    private Switch swCharge;
    private EditText etPackages;
    private TextView txtGameCount;
    private TextView txtSaveHint;

    private CoreGateConfig cfg = new CoreGateConfig();
    private boolean binding = false;   // 绑定期间不触发保存

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_strategy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        swCapture = v.findViewById(R.id.sw_capture);
        swScreenOff = v.findViewById(R.id.sw_screen_off);
        swCharge = v.findViewById(R.id.sw_charge);
        etPackages = v.findViewById(R.id.et_packages);
        txtGameCount = v.findViewById(R.id.txt_game_count);
        txtSaveHint = v.findViewById(R.id.txt_save_hint);
        Button btnSavePkg = v.findViewById(R.id.btn_save_packages);

        swCapture.setOnCheckedChangeListener((b, checked) -> onSwitchChanged());
        swScreenOff.setOnCheckedChangeListener((b, checked) -> onSwitchChanged());
        swCharge.setOnCheckedChangeListener((b, checked) -> onSwitchChanged());

        btnSavePkg.setOnClickListener(x -> savePackages());

        loadConfig();
        loadPackages();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 切回本页时重新拉一次，避免与其它入口不同步
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
        swCapture.setChecked(cfg.captureBoost);
        swScreenOff.setChecked(cfg.lockOnScreenOff);
        swCharge.setChecked(cfg.keepOnCharge);
        binding = false;
    }

    private void loadPackages() {
        CoreGateService.loadPackages(new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                String txt = stdout == null ? "" : stdout.trim();
                etPackages.setText(txt);
                updateGameCount(txt);
            }
        });
    }

    // ---------------------------------------------------------
    //  写
    // ---------------------------------------------------------

    private void onSwitchChanged() {
        if (binding) return;

        cfg.captureBoost = swCapture.isChecked();
        cfg.lockOnScreenOff = swScreenOff.isChecked();
        cfg.keepOnCharge = swCharge.isChecked();

        txtSaveHint.setText("保存中…");
        CoreGateService.saveConfig(cfg, new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (exitCode == 0) {
                    txtSaveHint.setText("已保存，立即生效，无需重启。");
                } else {
                    txtSaveHint.setText("保存失败：" + brief(stderr) + "（可下拉刷新重试）");
                }
            }
        });
    }

    private void savePackages() {
        String pkgs = etPackages.getText().toString();
        CoreGateService.savePackages(pkgs, new RootShell.Callback() {
            @Override
            public void onResult(int exitCode, String stdout, String stderr) {
                if (exitCode == 0) {
                    updateGameCount(pkgs);
                    toast("游戏列表已保存");
                } else {
                    toast("保存失败：" + brief(stderr));
                }
            }
        });
    }

    // ---------------------------------------------------------
    //  工具
    // ---------------------------------------------------------

    /** 统计非空行数 = 有效包名个数。 */
    private void updateGameCount(String pkgs) {
        int n = 0;
        if (pkgs != null) {
            for (String ln : pkgs.split("\n")) {
                String t = ln.trim();
                if (!t.isEmpty() && !t.startsWith("#")) n++;
            }
        }
        txtGameCount.setText(n == 0 ? "未配置" : (n + " 个"));
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
