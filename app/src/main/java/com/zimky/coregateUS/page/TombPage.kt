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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.TombData
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import com.zimky.coregateUS.ui.Toaster
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 墓碑二级页（从「策略」页点进来）。
 *
 * 这一页就是墓碑本体：开关、冻结、解冻都在这里做，不再只做探测展示。
 *
 * 状态真相全部来自 `--tomb-status` 的三段输出：
 *   [on]      true / false
 *   [engine]  cgroup_v2 / cgroup_v1 / sigstop
 *   [item]    包名 \t pid \t true|false|gone
 *
 * true 表示已经回读确认冻住了，false 表示登记了但没冻住，
 * gone 表示登记过但进程已经退出。这三种必须在界面上能分辨，
 * 否则用户根本不知道自己开的东西到底有没有用。
 */
private const val SVC = "sh /data/adb/modules/CoreGate/service.sh"

@Composable
fun TombPage(
    sh: RootShell,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf(TombData.Snapshot()) }
    var inputPkg by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            var out = ""
            sh.exec("$SVC --tomb-status") { _, o, _ -> out = o }
            delay(350)
            snap = TombData.parse(out)
            loading = false
        }
    }

    fun act(cmd: String, okMsg: String) {
        if (busy) return
        busy = true
        scope.launch {
            sh.exec(cmd) { _, _, _ -> }
            delay(400)
            Toaster.show(okMsg)
            reload()
            onChanged()
            busy = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp, bottom = PAGE_BOTTOM_INSET),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 顶部：返回 + 标题 ──────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                text = "‹ 返回",
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(textColor = cs.primary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "墓碑", color = cs.onSurface, fontSize = 17.sp)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    loading -> cs.onSurfaceVariantSummary.copy(alpha = 0.3f)
                                    snap.effective -> cs.primary
                                    else -> cs.onSurfaceVariantSummary.copy(alpha = 0.35f)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "墓碑",
                        color = cs.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    SwitchCompact(
                        checked = snap.on == true,
                        enabled = !loading && !busy && snap.on != null,
                        onCheckedChange = { want ->
                            if (want) act("$SVC --tomb-on", "墓碑已开启")
                            else act("$SVC --tomb-off", "墓碑已关闭，后台已全部解冻")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val conclusion = when {
                    loading -> "正在读取……"
                    snap.on == null -> "还没读出来。可能是 root 授权被拒，或者模块还没装好。"
                    snap.on == false -> "墓碑没开。打开上面的开关，才会开始冻结后台应用。"
                    snap.frozenCount > 0 ->
                        "正在生效，已冻住 ${snap.frozenPackages} 个应用。只有被真正冻住的进程才不耗电。"
                    else -> "开关是开的，但现在一个都没冻住。可以在下面冻一个试试。"
                }
                Text(
                    text = conclusion,
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                if (snap.engine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoRow(
                        label = "冻结方式",
                        value = when (snap.engine) {
                            "cgroup_v2" -> "cgroup v2（本机推荐）"
                            "cgroup_v1" -> "cgroup v1（兼容模式）"
                            "sigstop" -> "信号暂停（保底方式）"
                            else -> snap.engine
                        }
                    )
                }

                if (!loading && snap.on != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    InfoRow(
                        label = "当前冻结",
                        value = "${snap.frozenPackages} 个应用 / ${snap.frozenCount} 个进程"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        text = "重新读取",
                        onClick = { if (!loading) reload() },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    if (snap.items.isNotEmpty()) {
                        TextButton(
                            text = if (busy) "处理中……" else "全部解冻",
                            onClick = { act("$SVC --tomb-thaw-all", "已全部解冻") },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }

        if (!loading && snap.items.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "正在冻结的应用",
                        color = cs.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "点一下某个应用可以单独解冻它。",
                        color = cs.onSurfaceVariantSummary,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    snap.grouped().forEachIndexed { idx, pair ->
                        if (idx > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(cs.onSurfaceVariantSummary.copy(alpha = 0.08f))
                            )
                        }
                        PkgRow(
                            pkg = pair.first,
                            items = pair.second,
                            busy = busy,
                            onThaw = {
                                act("$SVC --tomb-thaw ${pair.first}", "已解冻 ${pair.first}")
                            }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "手动冻结一个应用",
                    color = cs.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "填包名，比如 com.example.app。不确定就先别填。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = inputPkg,
                        onValueChange = { inputPkg = it.trim() },
                        singleLine = true,
                        textStyle = TextStyle(color = cs.onSurface, fontSize = 13.sp),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (inputPkg.isEmpty()) {
                                Text(
                                    text = "例如 com.example.app",
                                    color = cs.onSurfaceVariantSummary.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                            inner()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    text = if (busy) "处理中……" else "冻结",
                    onClick = {
                        val p = inputPkg.trim()
                        if (p.isEmpty()) Toaster.show("先填包名")
                        else act("$SVC --tomb-freeze $p", "已提交冻结 $p")
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "这一项改了什么", color = cs.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "冻的是应用的进程，不是禁用应用。它还在，也不会从任何列表里消失，" +
                        "只是不跑、不耗电。你主动打开它就会恢复。\n\n" +
                        "关掉上面的开关会立刻全部解冻，不会留下冻住的应用。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            }
        }

        HintText("冻结只改运行时的 cgroup 状态，重启后自动还原，不写任何系统分区。")
    }
}

/* ------------------------------------------------------------------ */
/* 组件                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun InfoRow(label: String, value: String) {
    val cs = MiuixTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, color = cs.onSurface, fontSize = 12.sp)
    }
}

@Composable
private fun PkgRow(
    pkg: String,
    items: List<TombData.Item>,
    busy: Boolean,
    onThaw: () -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val frozenN = items.count { it.frozen }
    val dotColor = when {
        frozenN == 0 -> cs.onSurfaceVariantSummary.copy(alpha = 0.35f)
        frozenN == items.size -> cs.primary
        else -> cs.primary.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { onThaw() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = pkg, color = cs.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = items.joinToString("   ") {
                    val tag = when {
                        it.frozen -> "真的冻住了"
                        it.gone -> "进程已退出"
                        else -> "没冻住"
                    }
                    "${it.pid} $tag"
                },
                color = cs.onSurfaceVariantSummary,
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (busy) "……" else "解冻",
            color = if (busy) cs.onSurfaceVariantSummary.copy(alpha = 0.4f) else cs.primary,
            fontSize = 12.sp
        )
    }
}

/**
 * 紧凑开关。
 *
 * 与 FreezePage 里的同名组件视觉一致；这里单独实现一份是为了避免
 * 把一个 private 组件跨文件暴露成公共 API —— 两页的开关都只有这一处用途。
 */
@Composable
private fun SwitchCompact(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MiuixTheme.colorScheme
    val track = when {
        !enabled -> cs.onSurfaceVariantSummary.copy(alpha = 0.15f)
        checked -> cs.primary
        else -> cs.surfaceContainerHigh
    }
    val knob = if (checked) Color.White else cs.onSurfaceVariantSummary.copy(alpha = 0.9f)

    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(track)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(knob)
        )
    }
}