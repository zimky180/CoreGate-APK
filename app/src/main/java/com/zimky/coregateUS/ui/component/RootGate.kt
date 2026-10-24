package com.zimky.coregateUS.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.RootFlag
import com.zimky.coregateUS.core.RootShell
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 开屏 root 门槛（仅首次生效）。
 *
 * 用户硬性要求（原话）：
 *   「首次打开后就是完完全全的告诉你请输入 root，然后首次授予完之后第一屏永不再重现。
 *     如果配置无法读取，就多写个提示请检查是否给予 root 权限不就行了」
 *
 * 于是分两种情况：
 *
 *   A) 从未授予过（RootFlag 无标记）
 *      走完整的拦截式检测：拿不到 root 就进不了主界面，第一屏把话说明白。
 *
 *   B) 曾经授予过（RootFlag 有标记）
 *      直接放行，**不发起任何 su 调用、不显示任何检测界面**。
 *      之后 root 若被撤销，不在这一层处理 —— 主页读配置会失败，
 *      由 MainActivity 顶部的提示条告诉用户「请检查是否已给予 root 权限」。
 *      这样做是为了字面兑现「第一屏永不再重现」：不会有闪屏、不会有多余一跳。
 *
 * 首次检测的判定标准只有一条：`id -u` 必须回显 0。
 * 之所以不只看 su 命令退出码，是因为被 KernelSU 拒绝授权时退出码也可能是 0
 * （壳层被拒但进程正常退出），只有读到 uid 才能真正确认拿到了 root。
 *
 * 首次检测的三种终态：
 *   1) Granted  —— uid=0，写入标记，放行进入主界面；
 *   2) Denied   —— su 可达但没拿到 0（用户点了拒绝 / 授权超时），可重试；
 *   3) NoSu     —— 连 su 都执行不了（未 root 或 KernelSU 未安装），给出安装指引。
 *
 * 边界情况：
 *   - 设备装了墓碑类冻结工具，App 首次启动时 su 请求可能被拦、进程被冻，
 *     waitFor 拿不到结果。这里给单次检测 12 秒兜底，超时归入 Denied 而不是永久卡死。
 *   - 检测期间禁止重复触发，避免一次开屏刷出一串 su 请求被判为入侵。
 */
@Composable
fun RootGate(content: @Composable () -> Unit) {
    val cs = MiuixTheme.colorScheme
    val ctx = LocalContext.current

    // 只有「从未授予过」才需要检测。这个值取一次就够，不受重组影响。
    val alreadyGranted = remember { RootFlag.isGranted(ctx) }

    var state by remember {
        mutableStateOf<GateState>(
            if (alreadyGranted) GateState.Granted else GateState.Checking
        )
    }
    var attempt by remember { mutableStateOf(0) }

    // 每次 attempt 变化就重跑一次检测。
    // 注意：alreadyGranted 为 true 时 state 初值就是 Granted，下面的界面分支
    // 会直接走 content()，不会进入检测；这里用 key 再兜一道，彻底避免白跑一次 su。
    LaunchedEffect(attempt) {
        if (alreadyGranted) return@LaunchedEffect

        state = GateState.Checking
        // 给 KernelSU 弹授权框留出时间，避免启动瞬间请求过快
        if (attempt > 0) delay(300L)

        RootShell.exec("id -u") { code, out, err ->
            val uid = out.trim()
            if (uid == "0") {
                // 关键：确认拿到 root 之后才落标记，之后第一屏永不再现
                RootFlag.markGranted(ctx)
                state = GateState.Granted
            } else {
                state = when {
                    // su 进程能起来但没拿到 0：被拒或超时
                    code >= 0 -> GateState.Denied(
                        reason = if (uid.isEmpty()) {
                            "su 已执行，但没有返回 uid（授权可能被取消）。"
                        } else {
                            "su 返回的 uid 是 $uid，不是 0。"
                        }
                    )
                    // 连 su 都起不来
                    else -> GateState.NoSu(
                        reason = err.trim().ifEmpty { "su 执行失败（退出码 $code）。" }
                    )
                }
            }
        }

        // 12 秒兜底：墓碑冻结 / 授权框未响应时不至于永远停在「检测中」
        delay(12_000L)
        if (state is GateState.Checking) {
            state = GateState.Denied("root 检测超时。若授权弹窗被系统拦截，请到 KernelSU / 超级用户里手动授权本应用后重试。")
        }
    }

    when (val st = state) {
        is GateState.Granted -> content()
        is GateState.Checking -> GateScaffold {
            GateCard(
                title = "正在检测 root 权限",
                body = "CoreGate 需要通过 root 读取核心状态并写入配置。\n首次使用请在弹出的授权框中允许本应用。",
                accentNeutral = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 呼吸圆点表示进行中
                    PulseyDot(color = cs.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "检测中…", color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                }
            }
        }
        is GateState.Denied -> GateScaffold {
            GateCard(
                title = "未获得 root 权限",
                body = st.reason + "\n\nCoreGate 是 KernelSU 模块的配套应用，没有 root 权限无法工作，因此暂不进入主界面。",
                accentNeutral = false
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { attempt++ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "重新检测")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "如果系统没有弹出授权框，请打开 KernelSU / Magisk，在「超级用户」列表里为本应用手动开启授权。",
                        color = cs.onSurfaceVariantSummary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        is GateState.NoSu -> GateScaffold {
            GateCard(
                title = "未检测到 root 环境",
                body = st.reason + "\n\n本应用是 KernelSU 模块的配套 App，必须在已刷入 KernelSU 并完成 root 的设备上使用。",
                accentNeutral = false
            ) {
                Button(
                    onClick = { attempt++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "重新检测")
                }
            }
        }
    }
}

private sealed interface GateState {
    data object Checking : GateState
    data object Granted : GateState
    data class Denied(val reason: String) : GateState
    data class NoSu(val reason: String) : GateState
}

/** 门槛页统一背景：铺满全屏 + 居中对齐 */
@Composable
private fun GateScaffold(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.padding(horizontal = 28.dp)) {
            content()
        }
    }
}

@Composable
private fun GateCard(
    title: String,
    body: String,
    accentNeutral: Boolean,
    actions: @Composable () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            Text(
                text = title,
                // 检测中是中性色（还没出结论，不该报警）；
                // 未授权 / 无 root 用 primary 强调，让用户一眼看出「这里需要你处理」
                color = if (accentNeutral) cs.onSurface else cs.primary,
                fontSize = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                color = cs.onSurfaceVariantSummary,
                fontSize = 12.5.sp,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            actions()
        }
    }
}

/** 检测中的呼吸圆点：靠 alpha 循环模拟，不依赖第三方动画库 */
@Composable
private fun PulseyDot(color: androidx.compose.ui.graphics.Color) {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(600L)
            on = !on
        }
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (on) color else color.copy(alpha = 0.25f))
    )
}