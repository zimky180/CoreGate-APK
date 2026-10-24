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
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 伪造机型二级页（从「策略」页点进来）。
 *
 * 当前只做「说明 + 现状展示」，不提供任何开关。
 * 原因（对用户负责）：改机型属性走的是 resetprop 一类的系统属性注入，
 * 一旦把关键属性改错，后果是开不了机，属于需要救砖的高风险操作，
 * 不该做成一个随手能点的开关。
 *
 * 所以本页现在只回答三个问题：
 *   1. 这个功能是干什么的
 *   2. 现在有没有生效（读 ro.product.model 这类只读属性）
 *   3. 为什么还没开放
 *
 * 只读属性不会产生任何写入，安全。
 *
 * 本页不接收 CoreConfig：它既没有开关也不读写配置，
 * 多留一个用不上的参数只会让人误以为这里会存东西。
 *
 * @param onBack 返回上一页
 */
@Composable
fun SpoofPage(
    sh: RootShell,
    onBack: () -> Unit
) {
    val cs = MiuixTheme.colorScheme

    // 三条属性异步读。绝对不能在这个 Composable 里同步等 root 回调 ——
    // RootShell 的回调是 main.post 投递到主线程的，主线程一旦 await 就会被自己堵死。
    var model by remember { mutableStateOf("读取中…") }
    var vendor by remember { mutableStateOf("读取中…") }
    var device by remember { mutableStateOf("读取中…") }

    LaunchedEffect(Unit) {
        // 串行读三条，走同一条 root 队列，不会并发申请 root
        model = readProp(sh, "ro.product.model")
        vendor = readProp(sh, "ro.product.manufacturer")
        device = readProp(sh, "ro.product.device")
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
            Text(text = "伪造机型", color = cs.onSurface, fontSize = 17.sp)
        }

        // ── 说明卡 ────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "这是干什么的",
                    color = cs.onSurface,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "有些游戏只认特定机型，识别不到就锁画质或拒绝启动。" +
                        "伪造机型就是把系统报告的手机型号换成一个游戏认的值，让它当成别的高端机来跑。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // ── 现状卡 ────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "现在的真实情况", color = cs.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "手机当前报告的型号：$model",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "手机当前报告的厂商：$vendor",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "手机当前报告的设备代号：$device",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                HintText("以上三项是系统自己报的，本页只读不改。")
            }
        }

        // ── 为什么还没开放 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "为什么这里还不能改", color = cs.onSurface, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "改机型要动系统的启动属性。属性改错不是「功能失效」这么轻，" +
                        "而是可能直接进不去系统，只能靠救砖模块或者 Recovery 手动救回来。\n\n" +
                        "所以这一项暂时只放说明和现状，不放能点的开关。" +
                        "等改动方式能做到和锁核一样「重启就还原、出错也能自己恢复」的时候再开放。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            }
        }

        HintText("本页不写任何东西，看到的只是系统当前的原始值。")
    }
}

/**
 * 读一条系统属性，读不到就给一个容易理解的说法。
 *
 * 用 getprop 是因为它是 root 环境下最稳的只读取值方式，且不会写入。
 *
 * 注意这里必须是 suspend：RootShell 的回调经 main.post 回到主线程，
 * 若在主线程上阻塞等待，回调就永远投递不进来 —— 死锁。
 * 所以用 suspendCancellableCoroutine 挂起而不是阻塞，回调一到就恢复。
 */
private suspend fun readProp(sh: RootShell, key: String): String =
    withTimeoutOrNull(3000) {
        suspendCancellableCoroutine<String> { cont ->
            sh.exec("getprop $key") { _, o, _ ->
                if (cont.isActive) cont.resume(o.trim())
            }
        }
    }?.let { if (it.isEmpty()) "读不到" else it } ?: "读不到"