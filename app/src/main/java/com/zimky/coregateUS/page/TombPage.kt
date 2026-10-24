package com.zimky.coregateUS.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.FreezeData
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 墓碑二级页（从「策略」页点进来）。
 *
 * 设计立场（用户明确要求）：**不做引擎，只做探测展示**。
 *
 * 为什么不自己做一套墓碑：
 *   后台冻结是那种「做好了没人夸、做崩了开不了机」的东西，
 *   而且它盯的是整机所有后台进程，跟本模块「只盯 CPU 核心」的定位不是一回事。
 *   用户手机上已经有专门做这件事的工具，本页的作用是
 *   「让你一眼确认那个工具到底有没有真的在干活」，而不是再造一个。
 *
 * 怎么判断「在不在生效」：
 *   冻结类工具真正生效时，后台包会处于 suspended 状态。
 *   本页面用 `pm list packages -d` 与 suspend 状态交叉判断，
 *   凡是「本模块没冻结、但系统里确实处于挂起态」的包，就是别的工具干的活。
 *   数量明显不为零 → 说明有东西在替你冻后台 → 判定为生效。
 *
 * 只读，不写任何东西。
 *
 * @param tombAlive 外部已知的探测结果，null 表示外层也没探到
 * @param onProbed 本页自己探到结果后回填给外层，让策略页摘要同步
 * @param onBack 返回上一页
 */
@Composable
fun TombPage(
    sh: RootShell,
    tombAlive: Boolean?,
    onProbed: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    val cs = MiuixTheme.colorScheme

    // 本页自己探一次，结果写进这两个状态
    var alive by remember { mutableStateOf(tombAlive) }
    var foreignCount by remember { mutableStateOf<Int?>(null) }
    var checking by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        checking = true
        val raw = execSuspend(sh, "sh /data/adb/modules/CoreGate/service.sh --freeze-status")
        // exec 是异步回调，给它一点时间落地再解析（与 FreezePage 同一处理）
        delay(350)
        val snap = FreezeData.parse(raw)
        val mine = snap.applied.keys
        val foreign = snap.actual.keys.filterNot { it in mine }
        foreignCount = foreign.size
        val ok = foreign.isNotEmpty()
        alive = ok
        checking = false
        onProbed(ok, foreign.size)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = PAGE_BOTTOM_INSET),
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

        // ── 结论卡：一眼看结果 ────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "现在的真实情况", color = cs.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        checking -> "正在看…"
                        alive == true -> "有工具在冻结后台，说明墓碑确实在生效"
                        else -> "没看到有东西在冻结后台，墓碑目前没有生效"
                    },
                    color = cs.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (val n = foreignCount) {
                        null -> "被别的工具挂起的应用数量：还没数出来"
                        0 -> "被别的工具挂起的应用数量：0 个"
                        else -> "被别的工具挂起的应用数量：$n 个"
                    },
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                HintText("这个数字是「本模块没动、但系统里确实被挂起」的应用数量。它不是本模块冻的。")
            }
        }

        // ── 说明卡 ────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "这是干什么的", color = cs.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "墓碑是指「应用退到后台就被挂起来、不占资源」这一类做法。" +
                        "它能省电、也能省内存，但前提是那套工具真的在干活。\n\n" +
                        "很多人的情况是装了个冻结工具，以为一直在生效，其实早就被系统杀掉了，" +
                        "所以后台一直在跑、电一直在掉。这一页就是用来看清这件事的。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            }
        }

        // ── 为什么不做一套 ────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "为什么这里不能替你冻结", color = cs.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "后台冻结跟锁核不是一类事。它管的是整台手机的所有应用，" +
                        "一旦漏掉了该活着的那个，电话、闹钟、消息推送就可能收不到，严重的会开不了机。\n\n" +
                        "所以这一项只负责「看」，不负责「做」。你要冻结后台，请用你原来那套工具；" +
                        "这一页负责告诉你它到底有没有在工作。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            }
        }

        HintText("本页只读，不会冻结或解冻任何应用。")
    }
}

/**
 * 执行一条 root 命令并拿到它的标准输出（挂起，不阻塞主线程）。
 *
 * 与 SpoofPage.readProp 同因同解：RootShell 的回调经 main.post 回到主线程，
 * 若在主线程上同步等待，回调永远投递不进来 —— 死锁。
 */
private suspend fun execSuspend(sh: RootShell, cmd: String): String =
    withTimeoutOrNull(4000) {
        suspendCancellableCoroutine<String> { cont ->
            sh.exec(cmd) { _, o, _ ->
                if (cont.isActive) cont.resume(o)
            }
        }
    }.orEmpty()
