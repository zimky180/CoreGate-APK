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
 * 系统属性一个字节都不写，关掉开关立刻恢复，重启必然还原。
 */
@Composable
fun SpoofPage(
    sh: RootShell,
    onBack: () -> Unit,
    onChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var st by remember { mutableStateOf(SpoofData.State()) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        val raw = readRaw(sh, "cat " + SpoofData.State.FILE + " 2>/dev/null")
        st = SpoofData.State.parse(raw)
    }

    fun persist(next: SpoofData.State, hint: String) {
        if (busy) return
        busy = true
        scope.launch {
            val out = readRaw(sh, SpoofData.State.buildSaveCmd(next))
            if (out.contains("OK")) {
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
                "老机型模拟",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // ── 卡 1：开关 ────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SwitchRow(
                    title = "启用老机型模拟",
                    desc = "只在 LSPosed 勾选的应用里生效",
                    trailing = {
                        SwitchCompact(
                            checked = st.enabled,
                            onCheckedChange = { want ->
                                persist(st.copy(enabled = want), if (want) "已开启" else "已关闭")
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
                            "已伪装为 OPPO A5（" + SpoofData.OPPO_A5.model + "）",
                            fontSize = 13.sp,
                            color = Semantic.OK
                        )
                    }
                }
            }
        }

        // ── 卡 2：生效步骤（三步，说清楚就够）─────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("怎么生效")
                Gap(10.dp)
                HintText("1. 上面开关打开")
                Gap(6.dp)
                HintText("2. LSPosed 里给 CoreGate 勾选抖音")
                Gap(6.dp)
                HintText("3. 强制停止抖音，重新打开")
                Gap(10.dp)
                CardDivider()
                Gap(10.dp)
                Text("目标包名", fontSize = 13.sp, color = Color.White)
                Gap(4.dp)
                MonoText("com.ss.android.ugc.aweme")
            }
        }

        // ── 卡 3：说明 ────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("用来干什么")
                Gap(8.dp)
                HintText("这个开关就是给抖音用的。打开以后，抖音会把本机认成 OPPO A5 这种老机器，自己进省电的那一档，用起来更省电。")
                Gap(8.dp)
                HintText("只改抖音读到的机型，不动手机本身，也不影响别的应用。关掉开关就恢复。")
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
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) Semantic.ACC else Color.White.copy(alpha = 0.12f))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else Color.White.copy(alpha = 0.55f))
        )
    }
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