package com.zimky.coregateUS.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zimky.coregateUS.core.XposedScopeBridge
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SpoofData
import com.zimky.coregateUS.ui.CardDivider
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.LabelText
import com.zimky.coregateUS.ui.MonoText
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import com.zimky.coregateUS.ui.Semantic
import com.zimky.coregateUS.ui.SwitchRow
import com.zimky.coregateUS.ui.Toaster
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * SpoofPage —— 只给抖音伪造 OPPO A5。
 *
 * 就一个开关。打开 → 去 LSPosed 勾选抖音 → 强制停止抖音再打开，完事。
 * 不做机型选择、不做自定义、不做包名列表，因为没有第二个使用场景。
 *
 * 使用范围按用户要求收窄到「只针对抖音」：
 * 这个页面不做游戏相关的任何提示或逻辑，用户看到的每一句文案都只提抖音。
 *
 * 原理（一句话）：Xposed 在抖音进程里改掉属性读取的返回值，
 * 系统属性不写入；配置更改后需重启抖音，让目标进程重新加载。
 */
@Composable
fun SpoofPage(
    sh: RootShell,
    onBack: () -> Unit,
    onChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val framework by XposedScopeBridge.status.collectAsState()
    var checkingScope by remember { mutableStateOf(false) }

    var st by remember { mutableStateOf(SpoofData.State()) }
    var busy by remember { mutableStateOf(false) }
    val cs = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

    suspend fun reload() {
        val raw = readRaw(sh, "cat " + SpoofData.State.FILE + " 2>/dev/null")
        st = SpoofData.State.parse(raw)
    }

    fun persist(next: SpoofData.State, hint: String) {
        if (busy) return
        busy = true
        scope.launch {
            val out = readRaw(sh, SpoofData.State.buildSaveCmd(next))
            if (out.lineSequence().any { it.trim() == "OK" }) {
                st = next
                Toaster.show(hint)
                onChanged()
            } else {
                Toaster.show("保存失败，检查 root 权限", true)
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    // Poll only while this page is resumed; returning from manager refreshes scope too.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            while (true) {
                XposedScopeBridge.refresh()
                delay(2500L)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = PAGE_BOTTOM_INSET),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 顶部返回栏 ────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ 返回", color = Semantic.ACC, fontSize = 15.sp)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "抖音老机型优化",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface
            )
        }

        // ── 卡 1：开关 ────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SwitchRow(
                    title = "启用抖音老机型优化",
                    desc = "保存后需重启抖音，实际优化效果取决于抖音版本",
                    trailing = {
                        SwitchCompact(
                            checked = st.enabled,
                            onCheckedChange = { want ->
                                persist(st.copy(enabled = want), if (want) "优化配置已保存，请重启抖音" else "已关闭，请重启抖音恢复")
                            }
                        )
                    }
                )

                if (st.enabled) {
                    Gap(10.dp)
                    CardDivider()
                    Gap(10.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Semantic.OK)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "配置已开启 · 等待验证抖音内的实际效果",
                            fontSize = 13.sp,
                            color = Semantic.OK
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("LSPosed 授权与作用域")
                Gap(8.dp)
                Text(
                    when {
                        !framework.connected -> "框架未连接 · 勾选状态未知"
                        framework.approved == true -> "抖音已勾选"
                        framework.approved == false -> "抖音未勾选"
                        else -> "勾选状态待确认"
                    },
                    color = cs.onSurface, fontSize = 15.sp
                )
                if (framework.framework.isNotBlank()) {
                    Gap(4.dp)
                    HintText(framework.framework)
                }
                Gap(6.dp)
                HintText(framework.message)
                TextButton(
                    enabled = framework.connected && !framework.requesting && framework.approved != true,
                    onClick = { scope.launch { XposedScopeBridge.requestScope() } }
                ) {
                    Text(if (framework.requesting) "等待框架确认…" else "申请勾选抖音", color = cs.primary)
                }
                TextButton(
                    enabled = framework.connected && !checkingScope,
                    onClick = {
                        checkingScope = true
                        scope.launch {
                            try { XposedScopeBridge.refresh() }
                            finally { checkingScope = false }
                        }
                    }
                ) { Text("刷新勾选状态", color = cs.primary) }
                HintText("申请由框架决定是否显示授权弹窗。未连接或申请不可用时，请在 LSPosed 中手动勾选。已勾选仅代表作用域授权，不代表抖音已经启用老机型优化。")
            }
        }

        // ── 卡 2：生效步骤（三步，说清楚就够）─────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("怎么生效")
                Gap(10.dp)
                HintText("1. 上面开关打开")
                Gap(6.dp)
                HintText("2. 点击申请勾选抖音，或到 LSPosed 手动勾选")
                Gap(6.dp)
                HintText("3. 强制停止抖音，重新打开")
                Gap(10.dp)
                CardDivider()
                Gap(10.dp)
                Text("目标包名", fontSize = 13.sp, color = cs.onSurface)
                Gap(4.dp)
                MonoText("com.ss.android.ugc.aweme")
            }
        }

        // ── 卡 3：说明 ────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("用来干什么")
                Gap(8.dp)
                HintText("通过现有机型兼容配置尝试触发抖音的老机型优化。不同版本的策略可能不同，配置保存成功并不代表抖音已启用优化。")
                Gap(8.dp)
                HintText("关闭后请强制停止并重新打开抖音。只有在抖音内观察到相应功能或效果后，才能确认实际生效。")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 紧凑开关，与 FreezePage 同款视觉。
 * 这里是本文件内的 private 副本 —— FreezePage 那份也是 private，
 * 分属不同文件不冲突。
 */
@Composable
private fun SwitchCompact(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val cs = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = cs.primary,
            uncheckedThumbColor = cs.onSurfaceVariantSummary,
            uncheckedTrackColor = cs.surfaceContainerHigh
        )
    )
}

/**
 * 跑一条 root 命令，返回原始 stdout（空返回 ""）。
 *
 * 必须 suspend + 超时：RootShell 的回调经 Handler(mainLooper).post 投递，
 * 若在主线程上同步等待，回调永远投递不进来 —— 直接死锁。
 */
private suspend fun readRaw(sh: RootShell, cmd: String): String =
    withTimeoutOrNull(4000) {
        suspendCancellableCoroutine { cont ->
            sh.exec(cmd) { _, o, _ ->
                if (cont.isActive) cont.resume(o)
            }
        }
    } ?: ""