package com.zimky.coregateUS.page

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.LogReader
import com.zimky.coregateUS.core.Paths
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.ui.BottomSpacer
import com.zimky.coregateUS.ui.CardDivider
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HairLine
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.KeyValueRow
import com.zimky.coregateUS.ui.MonoText
import com.zimky.coregateUS.ui.NavRow
import com.zimky.coregateUS.ui.PAGE_BOTTOM_INSET
import com.zimky.coregateUS.ui.Semantic
import com.zimky.coregateUS.ui.SubText
import com.zimky.coregateUS.ui.SwitchRow
import com.zimky.coregateUS.ui.Toaster
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 联系方式。
 *
 * 两个入口点击后用系统浏览器 / 对应 App 打开，失败则退化成弹提示，
 * 保证在没装酷安 / QQ 的机器上也不会点了没反应。
 */
private const val CONTACT_COOLAPK_URL = "https://www.coolapk.com/u/40403466"
private const val CONTACT_COOLAPK_HINT = "酷安 @zimky"
private const val CONTACT_QQ_URL =
    "https://qun.qq.com/universal-share/share?ac=1&authKey=NGIVdTO79IV8KXNxUtDnWurJXfcE4YvDRjqoUR1TmNyQ3YzDMPVl1gs0WaSPg8eS&busi_data=eyJncm91cENvZGUiOiI2OTYxOTIyNjIiLCJ0b2tlbiI6IlZ4WjNDblFGTWdVL29wclVEOVdzRFFQOXFMMWRKaWFkYlJ5dThoSEp3bnR3Z0V1Qm5KcXloMVFBeXJlUmE1K3EiLCJ1aW4iOiIxODA1OTIwMjMyIn0=&data=30cZ6rK_2N8u5oZcEMCIxtGJPK0eKSCj9MeW9UD9Lq5Ei7PXKz5HSwccU1CXUG2mkuHyA_EfQBNQqLapwI6KNw&svctype=4&tempid=h5_group_info"
private const val CONTACT_QQ_HINT = "QQ 群 696192262"

/**
 * 用系统 Intent 打开一个链接。
 *
 * 不申请任何权限，只管发一个 ACTION_VIEW；找不到能处理的 Activity
 * （比如机器上没装浏览器）时返回 false，调用方退回弹提示。
 */
private fun openUrl(ctx: android.content.Context, url: String): Boolean {
    return try {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (t: Throwable) {
        false
    }
}

/**
 * 第 4 页：设置 / 名单 / 日志 / 诊断 / 关于。
 *
 * 顺序刻意如此：日常会碰的（总开关、名单）在上，排查才用的（日志、诊断）在下，
 * 关于页压底。诊断区折叠式堆在一起，不占日常视野。
 *
 * 视觉：Miuix [Card] + [Badge] + [TextButton]，与其余三页同一套组件语言。
 */
@Composable
fun SettingsPage(
    cfg: CoreConfig,
    snapshot: SysProbe.Snapshot?,
    packagesRaw: String,
    sh: RootShell,
    onRefresh: () -> Unit,
    save: (CoreConfig, String?, ((Boolean, String) -> Unit)?) -> Unit
) {
    val cs = MiuixTheme.colorScheme
    // 在 @Composable 上下文里取一次，供 NavRow 的普通 onClick lambda 捕获使用
    val ctx = LocalContext.current
    val s = snapshot
    val daemonAlive = s?.daemonAlive == true
    // ── 名单编辑缓冲：父级刷新回来时同步，用户改过则不覆盖 ──
    var listText by remember { mutableStateOf(packagesRaw) }
    var listDirty by remember { mutableStateOf(false) }
    if (!listDirty && listText != packagesRaw) listText = packagesRaw

    // ── 包名查询（真机反馈要求把搜索加回来）──
    // 用户最常见的疑问是「我的游戏到底进没进名单」，
    // 以前只能自己在那一大坨文本框里肉眼找，很容易看漏。
    // 这里单独给一个查询框，实时对当前名单做子串匹配：
    //   - 命中：直接高亮显示匹配到的那一行，告诉用户在第几行；
    //   - 没命中：明确提示「没进名单」，并给一个「加入名单」的按钮，
    //     点了就往名单末尾追加一行，同时把 listDirty 置位，
    //     用户再点旁边的「保存名单」即可落盘。
    // 匹配大小写不敏感，因为 Android 包名虽然全小写，但用户手抄时常带大写。
    var query by remember { mutableStateOf("") }


    // ── 日志状态 ──
    var logText by remember { mutableStateOf("") }
    var logFrom by remember { mutableStateOf("") }
    var logBusy by remember { mutableStateOf(false) }

    // ── 诊断刷新计数，点一次变一次 ──
    var diagTick by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            // 一级页必须自己铺满整屏：
            // 这四页是常驻 composition 的，靠 PageSlot 的 zIndex 叠在一起。
            // 根 Column 若只写 fillMaxWidth，竖向高度会随内容收缩，
            // 内容不足一屏时下面那块就不属于本页 —— 既会透出下层页，
            // 点击也会漏到别的页去（真机反馈的「按钮点不着」）。
            // fillMaxSize 同时给 verticalScroll 一个有界高度的视口。
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = PAGE_BOTTOM_INSET),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── 卡片一：模块总开关 ───────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "模块总开关", color = cs.onSurface, fontSize = 16.sp)
                Gap(12.dp)

                SwitchRow(
                    title = "启用 CoreGate",
                    desc = if (cfg.enabled) {
                        "守护进程会按策略自动锁核。"
                    } else {
                        "已停用：模块不再做任何锁核动作，但不会卸载。"
                    }
                ) {
                    Switch(
                        checked = cfg.enabled,
                        onCheckedChange = { on ->
                            save(cfg.copy(enabled = on), null) { ok, msg ->
                                if (ok) {
                                    Toaster.show(if (on) "CoreGate 已启用" else "CoreGate 已停用")
                                    onRefresh()
                                } else {
                                    Toaster.show("保存失败：$msg", true)
                                }
                            }
                        },
                        colors = switchColors()
                    )
                }

                Gap(12.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubText("运行状态")
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        when {
                            !cfg.enabled -> StateBadge("已停用", Semantic.OFF)
                            !daemonAlive -> StateBadge("未运行", Semantic.ERR)
                            else -> BreathingBadge("运行中", Semantic.OK)
                        }
                    }
                }

                // 模块就位状态一律来自 SysProbe 的同一次 root 探测结果：
                // true 就位 / false 确实缺失 / null 还没探测过（不能当成缺失）
                val svc = s?.svcState ?: SysProbe.svcCache
                if (svc == false) {
                    Gap(10.dp)
                    HintText("未找到 ${Paths.SERVICE}，模块可能没刷入或已损坏。", color = Semantic.WARN)
                } else if (svc == null) {
                    Gap(10.dp)
                    HintText("模块状态未探测（下拉刷新或稍候自动检测）", color = Semantic.WARN)
                }
            }
        }

        // ── 卡片：包名查询 ────────────────────────────────────────────
        // 装完模块最常干的事：确认某个游戏到底进没进名单。
        // 以前只能对着一大段文本肉眼找，这里给一个即时查询。
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "包名查询", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)
                SubText("确认某个应用是否已在游戏名单里，没在就一键加进去。")
                Gap(10.dp)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurface
                    ),
                    placeholder = { HintText("com.tencent.tmgp.sgame") },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                val q = query.trim()
                if (q.isNotEmpty()) {
                    Gap(12.dp)
                    val allLines = listText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val exactIdx = allLines.indexOfFirst { it.equals(q, ignoreCase = true) }
                    val fuzzy = allLines.filter { it.contains(q, ignoreCase = true) }
                    when {
                        exactIdx >= 0 -> {
                            StateBadge("已在名单", Semantic.OK)
                            Gap(6.dp)
                            MonoText("第 ${exactIdx + 1} 行：${allLines[exactIdx]}")
                        }
                        fuzzy.isNotEmpty() -> {
                            StateBadge("部分匹配", Semantic.WARN)
                            Gap(6.dp)
                            fuzzy.take(5).forEach { MonoText(it) }
                            if (fuzzy.size > 5) {
                                Gap(4.dp)
                                SubText("另有 ${fuzzy.size - 5} 条未展开")
                            }
                        }
                        else -> {
                            StateBadge("未进名单", Semantic.ERR)
                            Gap(8.dp)
                            TextButton(
                                text = "把「$q」加入名单",
                                onClick = {
                                    listText = appendPackage(listText, q)
                                    listDirty = true
                                    Toaster.show("已加到名单末尾，记得点「保存名单」")
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }
                }
            }
        }
        // ── 卡片二：游戏名单 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "游戏名单", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)
                SubText("每行一个包名。命中名单时模块会自动放开全部核心，退出后恢复锁核。")
                Gap(10.dp)
                OutlinedTextField(
                    value = listText,
                    onValueChange = {
                        listText = it
                        listDirty = it != packagesRaw
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp),
                    textStyle = TextStyle(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurface
                    ),
                    placeholder = { HintText("com.example.game") },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                Gap(8.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val count = listText.lineSequence().count { it.isNotBlank() }
                    HintText("共 $count 个包名" + if (listDirty) "（有未保存改动）" else "")
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            text = "保存名单",
                            onClick = {
                                save(cfg, listText) { ok, msg ->
                                    if (ok) {
                                        listDirty = false
                                        Toaster.show("名单已保存")
                                        onRefresh()
                                    } else {
                                        Toaster.show("保存失败：$msg", true)
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }

        // ── 卡片三：模块日志 ─────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "模块日志", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = if (logBusy) "读取中…" else "读取日志",
                        onClick = {
                            if (logBusy) return@TextButton
                            logBusy = true
                            LogReader.tail(sh, 160) { text, from, found ->
                                logBusy = false
                                if (!found) {
                                    logText = ""
                                    logFrom = ""
                                    Toaster.show("四个日志路径都没有文件", true)
                                } else {
                                    logText = text
                                    logFrom = from
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "清空日志",
                        onClick = {
                            LogReader.clear(sh) { code, _, _ ->
                                if (code == 0) {
                                    logText = ""
                                    Toaster.show("日志已清空")
                                } else {
                                    Toaster.show("清空失败，退出码 $code", true)
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }

                Gap(10.dp)
                if (logFrom.isNotEmpty()) {
                    HintText(logFrom)
                    Gap(6.dp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(cs.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .border(1.dp, cs.outline, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    if (logText.isEmpty()) {
                        HintText("还没读取日志。点上方「读取日志」拉取最近 160 行。")
                    } else {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MonoText(logText)
                            }
                        }
                    }
                }

                Gap(8.dp)
                HintText(
                    "按顺序回退查找：modules/…/log、modules_update/…/log、CoreGate/log、CoreGate/ 根目录。"
                )
            }
        }

        // ── 卡片四：诊断 ─────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "诊断", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HintText("点一次采集一次，不发自动请求")
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            text = "刷新诊断",
                            onClick = {
                                diagTick++
                                Toaster.show("已采集")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
                // diagTick 参与读取，保证刷新按钮能触发重组
                @Suppress("UNUSED_EXPRESSION") diagTick

                CardDivider()

                // ① 运行环境
                // 三态：已找到 / 缺失 / 未知（还没探测过），不再把「没探测」误报成「缺失」
                KeyValueRow(
                    "模块目录",
                    when (s?.svcState ?: SysProbe.svcCache) {
                        true -> "已找到"
                        false -> "缺失"
                        null -> "未知（未探测）"
                    }
                )
                KeyValueRow("service.sh", Paths.SERVICE)
                KeyValueRow("配置", Paths.FILE)
                KeyValueRow("配置已读", if (cfg.loaded) "是" else "否（读取失败）")
                KeyValueRow(
                    "Root 授权",
                    when {
                        s == null -> "未探测"
                        s!!.rootOk -> if (s!!.rootId.isNotEmpty()) "已提权 · ${s!!.rootId}" else "已提权"
                        else -> "未通过（su 没有返回 uid 0）"
                    }
                )

                Gap(8.dp)
                HairLine()
                Gap(8.dp)

                // ② 守护进程
                KeyValueRow("守护进程", if (daemonAlive) "存活" else "未运行")
                KeyValueRow("PID", if ((s?.daemonPid ?: -1) > 0) s!!.daemonPid.toString() else "—")

                Gap(8.dp)
                HairLine()
                Gap(8.dp)

                // ③ 频率上限
                KeyValueRow("小核上限", SysProbe.fmtMhz(s?.littleMaxCur ?: 0))
                KeyValueRow("大核上限", SysProbe.fmtMhz(s?.bigMaxCur ?: 0))
                KeyValueRow("GPU 上限", SysProbe.fmtGpu(s?.gpuMax ?: 0))

                val dbg = s?.dbg?.trim().orEmpty()
                if (dbg.isNotEmpty()) {
                    Gap(8.dp)
                    HairLine()
                    Gap(8.dp)
                    SubText("探测输出")
                    Gap(6.dp)
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            MonoText(dbg.take(600))
                        }
                    }
                }

                val hist = RootShell.history().take(8)
                if (hist.isNotEmpty()) {
                    Gap(8.dp)
                    HairLine()
                    Gap(8.dp)
                    SubText("最近 ${hist.size} 条命令")
                    Gap(6.dp)
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            hist.forEach { r ->
                                val cmd = if (r.cmd.length > 70) r.cmd.take(70) + "…" else r.cmd
                                val color = if (r.exit == 0) cs.onSurfaceVariantSummary else Semantic.ERR
                                MonoText(
                                    "[${r.exit}] ${r.costMs}ms  $cmd",
                                    color = color,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 卡片五：关于 ─────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "关于", color = cs.onSurface, fontSize = 16.sp)
                Gap(10.dp)

                KeyValueRow("名称", "CoreGate")
                KeyValueRow("作者", "zimky")
                KeyValueRow("版本", "2.2.3")
                KeyValueRow("模块目录", Paths.MODULE_DIR)
                KeyValueRow("数据目录", Paths.DIR)

                CardDivider()
                SubText(
                    "设计原则：只在运行时改 sysfs，配置存于 /data 用户数据区，" +
                        "不写任何系统分区，重启即还原，完全可逆、零残留。"
                )
                Gap(8.dp)
                HintText("APK 是唯一的操作入口，直接读写模块的 config.json 与 packages.txt，不依赖任何 WebUI。")
            }
        }

        // ── 卡片六：联系作者 ─────────────────────────────────────────
        // 放在最底部：属于「想看才看」的内容，不占日常视野。
        // 点击走系统 Intent；打不开（没装酷安/QQ/浏览器）才弹提示兜底。
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = "联系作者 / 反馈与建议", color = cs.onSurface, fontSize = 16.sp)
                Gap(4.dp)

                NavRow(
                    title = "酷安",
                    summary = CONTACT_COOLAPK_HINT,
                    onClick = {
                        if (!openUrl(ctx, CONTACT_COOLAPK_URL)) {
                            Toaster.show("打开酷安失败，手动搜：" + CONTACT_COOLAPK_HINT, true)
                        }
                    }
                )
                HairLine()
                NavRow(
                    title = "QQ 群",
                    summary = CONTACT_QQ_HINT,
                    onClick = {
                        if (!openUrl(ctx, CONTACT_QQ_URL)) {
                            Toaster.show("打开 QQ 失败，群号 " + CONTACT_QQ_HINT, true)
                        }
                    }
                )

                Gap(10.dp)
                HintText("用着有问题、或者想加点什么，直接来找我。说清楚机型和现象，能省一半排查时间。")
            }
        }

        BottomSpacer()
    }
}

/**
 * 往名单文本末尾追加一个包名。
 *
 * 名单是「每行一个包名」的纯文本，用户手动粘贴时常带空行与首尾空格，
 * 所以先去空行再判重，避免同一个包名被塞进去两次让用户困惑。
 */
private fun appendPackage(text: String, pkg: String): String {
    val target = pkg.trim()
    if (target.isEmpty()) return text
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.any { it.equals(target, ignoreCase = true) }) return text
    return (lines + target).joinToString("\n") + "\n"
}

/** 状态徽标：静态，白色文字，与 KernelSU 徽标一致。 */
/** 状态徽标：静态，白色文字，与 KernelSU 徽标一致。 */
@Composable
private fun StateBadge(text: String, color: Color) {
    Badge(containerColor = color, contentColor = Color.White) {
        Text(text = text, fontSize = 12.sp, color = Color.White)
    }
}

/** 呼吸徽标：守护进程运行中用轻微明暗变化提示「在跑」（0.55~1.0，周期 1.4 秒）。 */
@Composable
private fun BreathingBadge(text: String, color: Color) {
    val transition = rememberInfiniteTransition(label = "settingsBadgeBreath")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "settingsBadgeAlpha"
    )
    Box(modifier = Modifier.alpha(alpha)) {
        Badge(containerColor = color, contentColor = Color.White) {
            Text(text = text, fontSize = 12.sp, color = Color.White)
        }
    }
}

/** 输入框配色：跟随卡片底色与 Miuix 主题，避免默认 Material 灰底。 */
@Composable
private fun fieldColors(): androidx.compose.material3.TextFieldColors {
    val cs = MiuixTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = cs.onSurface,
        unfocusedTextColor = cs.onSurface,
        focusedContainerColor = cs.surfaceContainerHigh,
        unfocusedContainerColor = cs.surfaceContainerHigh,
        cursorColor = cs.primary,
        focusedBorderColor = cs.primary,
        unfocusedBorderColor = cs.outline,
        focusedPlaceholderColor = cs.onSurfaceVariantSummary,
        unfocusedPlaceholderColor = cs.onSurfaceVariantSummary
    )
}

/** 开关配色：统一走 Miuix 主题色，与其余三页保持一致。 */
@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MiuixTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    uncheckedTrackColor = MiuixTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MiuixTheme.colorScheme.outline
)