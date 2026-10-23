package com.zimky.coregateUS.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.C
import com.zimky.coregateUS.ui.GlassCard
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.StatusPill
import com.zimky.coregateUS.ui.SubText
import com.zimky.coregateUS.ui.SwitchRow
import com.zimky.coregateUS.ui.Toaster

/**
 * 第 2 页：录屏/相机恢复、充电策略、轮询间隔、当前场景判断。
 *
 * 与旧 PolicyPage.java 对齐的三条关键行为：
 *  1. 开关是「由 cfg 单向驱动」，onCheckedChange 里只做 save，不需要 syncing 标记；
 *  2. 轮询间隔只在失焦时提交，且与当前值相同则不写盘（避免无意义 root）；
 *  3. 输入框获得焦点期间，外部刷新回来的数字不覆盖用户正在编辑的内容。
 */
@Composable
fun PolicyPage(
    cfg: CoreConfig,
    snapshot: SysProbe.Snapshot?,
    packagesRaw: String,
    sh: RootShell,
    onRefresh: () -> Unit,
    save: (CoreConfig, String?, ((Boolean, String) -> Unit)?) -> Unit
) {
    val s = snapshot
    val daemonAlive = s?.daemonAlive == true
    val total = s?.cores?.size ?: 0
    val onlineCount = s?.onlineCount ?: 0
    val allOnline = total > 0 && onlineCount == total
    val lockedCount = (total - onlineCount).coerceAtLeast(0)
    val pkg = s?.statePkg?.takeIf { it.isNotBlank() }

    // 输入框文本与焦点状态
    var intervalText by remember { mutableStateOf(cfg.pollInterval.toString()) }
    var inputFocused by remember { mutableStateOf(false) }

    // 外部刷新回来的配置变化：仅在未聚焦时同步显示值
    if (!inputFocused && intervalText != cfg.pollInterval.toString()) {
        intervalText = cfg.pollInterval.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── 卡片一：录屏 / 相机自动恢复 ──────────────────────────────
        GlassCard(title = "录屏 / 相机自动恢复") {
            SwitchRow(
                title = "录屏或拍照时恢复全部核心",
                desc = "开启后，检测到录屏或相机正在使用时自动解锁所有核心，避免录制卡顿、拍照掉帧。",
                trailing = {
                    Switch(
                        checked = cfg.captureBoost,
                        onCheckedChange = { on ->
                            save(cfg.copy(captureBoost = on), null) { ok, _ ->
                                if (ok) Toaster.show(if (on) "已开启录屏/相机自动恢复" else "已关闭录屏/相机自动恢复")
                            }
                        },
                        colors = switchColors()
                    )
                }
            )
            Spacer(Modifier.height(10.dp))
            HintText("检测名单内置了常见录屏与相机包名（含 ColorOS / OxygenOS）。关闭此项后，模块只认 packages.txt 里的游戏列表。")
        }

        // ── 卡片二：充电策略 ─────────────────────────────────────────
        GlassCard(title = "充电策略") {
            SwitchRow(
                title = "充电时不锁核",
                desc = "插上充电器时保持全部核心在线。充电时发热本就明显，锁核收益有限，而放开核心能让充电管理和后台任务更快完成。",
                trailing = {
                    Switch(
                        checked = cfg.noLockCharging,
                        onCheckedChange = { on ->
                            save(cfg.copy(noLockCharging = on), null) { ok, _ ->
                                if (ok) Toaster.show(if (on) "已开启充电不锁核" else "已关闭充电不锁核")
                            }
                        },
                        colors = switchColors()
                    )
                }
            )
            Spacer(Modifier.height(10.dp))
            HintText("默认关闭：即便在充电，也照常锁核。")
        }

        // ── 卡片三：轮询间隔 ─────────────────────────────────────────
        GlassCard(title = "轮询间隔") {
            SubText("守护进程每隔多少秒检查一次前台应用。间隔越短响应越快，但唤醒次数越多，待机功耗也略高；间隔越长越省电，但进出游戏的反应会变慢。")
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { v ->
                        // 只接受数字，最多 3 位，避免用户输入出奇怪内容
                        intervalText = v.filter { it.isDigit() }.take(3)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .width(84.dp)
                        .onFocusChanged { state ->
                            val wasFocused = inputFocused
                            inputFocused = state.isFocused
                            // 失焦即提交
                            if (wasFocused && !state.isFocused) {
                                commitInterval(intervalText, cfg) { text, newCfg ->
                                    intervalText = text
                                    if (newCfg != null) {
                                        save(newCfg, null) { ok, _ ->
                                            if (ok) Toaster.show("轮询间隔已设为 ${newCfg.pollInterval} 秒")
                                        }
                                    }
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = C.TXT
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.ACC.copy(alpha = 0.7f),
                        unfocusedBorderColor = C.CARD_BORDER,
                        focusedContainerColor = Color(0x0DFFFFFF),
                        unfocusedContainerColor = Color(0x0DFFFFFF),
                        cursorColor = C.ACC
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text("秒", color = C.SUB, fontSize = 14.sp)
            }

            Spacer(Modifier.height(10.dp))
            HintText("允许范围 5 ~ 600 秒，默认 30 秒。留空或填非法值将回退为 30。")
        }

        // ── 卡片四：当前场景判断 ─────────────────────────────────────
        GlassCard(title = "当前场景判断") {
            if (!daemonAlive) {
                StatusPill("未运行", C.ERR)
                Spacer(Modifier.height(10.dp))
                SubText("守护进程未运行，场景判断暂停。")
            } else if (allOnline) {
                StatusPill("已放开", C.ACC)
                Spacer(Modifier.height(10.dp))
                SubText(
                    if (pkg != null) {
                        "前台应用 $pkg 命中游戏或录屏/相机名单，全部核心已恢复。"
                    } else {
                        "全部核心在线：可能命中了游戏/录屏名单，或锁核总开关被关闭。"
                    }
                )
            } else {
                StatusPill("锁核中", C.OK, breathing = true)
                Spacer(Modifier.height(10.dp))
                SubText(
                    if (pkg != null) {
                        "前台应用 $pkg 未命中游戏/录屏名单。"
                    } else {
                        "当前前台应用无法读取（ColorOS 限制），按非游戏处理。"
                    }
                )
                Spacer(Modifier.height(10.dp))
                SubText("已封锁 $lockedCount 个核心以节省功耗。")
            }
        }

        BottomSpacer()
    }
}

/**
 * 提交轮询间隔。
 *
 * @param text 输入框当前文本
 * @param cfg  当前配置
 * @param done text = 回填输入框的规范化文本；newCfg = 需要写盘的新配置（null 表示不需要写）
 */
private fun commitInterval(
    text: String,
    cfg: CoreConfig,
    done: (text: String, newCfg: CoreConfig?) -> Unit
) {
    val raw = text.trim().toIntOrNull()
    // 留空或非法：回退默认 30
    val normalized = if (raw == null) 30 else CoreConfig.clampInterval(raw)
    // 与当前配置一致：不写盘，只把输入框回填成规范值
    if (normalized == cfg.pollInterval) {
        done(normalized.toString(), null)
        return
    }
    done(normalized.toString(), cfg.copy(pollInterval = normalized))
}

/** 开关配色：与色板保持一致。 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = C.ACC,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color(0xFFB7BFCC),
    uncheckedTrackColor = Color(0x1FFFFFFF),
    uncheckedBorderColor = C.CARD_BORDER
)