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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SpoofData
import com.zimky.coregateUS.core.SpoofProfile
import com.zimky.coregateUS.ui.CardDivider
import com.zimky.coregateUS.ui.Gap
import com.zimky.coregateUS.ui.HintText
import com.zimky.coregateUS.ui.KeyValueRow
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
 * SpoofPage —— 应用级伪造机型（方案 C：只做三档）。
 *
 * ── 为什么这一页从「只读」变成「能点」──────────────────────────────
 * 旧版这一页特意只放说明和现状，理由是「直接写系统属性风险太高」。
 * 那个理由成立的前提是：改机型 = 改系统启动属性（resetprop / setprop 写
 * persist.sys.* / ro.product.*）。属性一旦写坏，zygote 起不来，确实会
 * 变成砖，那当然不能给开关。
 *
 * 但现在走的是完全不同的一条路 —— Xposed 在「目标应用的进程内」把
 * 属性读取的返回值换掉：
 *   1. 系统属性一个字节都不写。撤销开关 / 停用模块 / 卸载重启，全部
 *      原样回来，getprop 从头到尾读到的都是真机值；
 *   2. 钩子只对「作用域里勾选的应用」生效，没勾的 App 眼里你还是原机；
 *   3. 不存在「属性写坏导致 zygote 起不来」这条路径，最坏情况是某个 App
 *      识别出不一致而拒绝运行（关开关就好）。
 * 所以这一页的风险等级和锁核、限频是一个量级 —— 都是运行时行为，重启
 * 即还原，零残留。这才敢把开关放出来。
 *
 * ── 三条硬性约束（实现侧，改代码前先读）────────────────────────────
 *   1. Xposed 侧必须「先改属性读数、再改 Build 静态字段」，两边值保持
 *      完全一致。否则 getprop 说是 A、Java 层说是 B，比不改还容易被检测。
 *   2. 配置落在 /data/adb/CoreGate/spoof.json，由 App 写、Xposed 读，
 *      不进 config.json —— 那个文件是 service.sh 用 sed 逐键解析的，
 *      长字符串带 / 和 : 必踩 BRE 坑。
 *   3. 自定义档位优先级高于内置档位（State.effective 的语义），所以用户
 *      点选内置档位时必须把 custom 清空，否则他会以为「点了没反应」。
 */
@Composable
fun SpoofPage(
    sh: RootShell,
    onBack: () -> Unit,
    onChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var st by remember { mutableStateOf(SpoofData.State()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    // 真机真实值，只读展示，用作「告诉用户你没把系统改坏」的对照
    var realModel by remember { mutableStateOf("…") }
    var realBrand by remember { mutableStateOf("…") }
    var realDevice by remember { mutableStateOf("…") }

    /**
     * 一次读完 spoof.json + 三条真实属性。
     * sh.exec 是异步回调（回调经 main.post 回主线程），每个值都靠 suspend
     * 等回调真正落地，不能读变量完就走。
     */
    suspend fun reload() {
        loading = true
        val raw = readRaw(sh, "cat " + SpoofData.State.FILE + " 2>/dev/null")
        st = SpoofData.parse(raw)
        realModel = readProp(sh, "ro.product.model")
        realBrand = readProp(sh, "ro.product.brand")
        realDevice = readProp(sh, "ro.product.device")
        loading = false
    }

    /**
     * 保存新状态。走 base64 原子写，回显 OK 才认成功 ——
     * 失败时坚决不改本地 st，避免界面显示「已开启」而磁盘上是旧的。
     */
    fun persist(next: SpoofData.State, hint: String) {
        if (busy) return
        busy = true
        scope.launch {
            val out = readRaw(sh, SpoofData.buildSaveCmd(next))
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
                "伪造机型",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // ── 卡 1：总开关 ──────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SwitchRow(
                    title = "启用伪造机型",
                    desc = "只对 LSPosed 作用域里勾选的应用生效",
                    trailing = {
                        SwitchCompact(
                            checked = st.enabled,
                            onCheckedChange = { want ->
                                if (want && st.effective() == null) {
                                    Toaster.show("先在下面选一个机型档位", true)
                                } else {
                                    persist(
                                        st.copy(enabled = want),
                                        if (want) "已开启" else "已关闭"
                                    )
                                }
                            }
                        )
                    }
                )

                val eff = st.effective()
                if (st.enabled && eff != null) {
                    Gap(10.dp)
                    CardDivider()
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
                        Text("当前生效：" + eff.model, fontSize = 13.sp, color = Semantic.OK)
                    }
                }
            }
        }

        // ── 卡 2：三档内置机型 ────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("选择机型")
                Gap(6.dp)
                HintText("自定义档位优先于内置档位")
                Gap(12.dp)

                SpoofData.BUILTIN.forEachIndexed { idx, p ->
                    if (idx > 0) CardDivider()
                    ProfileRow(
                        profile = p,
                        selected = st.custom == null && st.profileId == p.id,
                        onClick = {
                            // 自定义优先级更高，不清掉的话点内置档位会「没反应」
                            persist(st.copy(profileId = p.id, custom = null), "已选 " + p.title)
                        }
                    )
                }
            }
        }

        // ── 卡 3：自定义（仅在存在时显示）───────────────────────
        val custom = st.custom
        if (custom != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) { LabelText("自定义机型") }
                        Text(
                            "清除",
                            fontSize = 13.sp,
                            color = Semantic.ERR,
                            modifier = Modifier.clickable {
                                persist(st.copy(custom = null), "已清除自定义")
                            }
                        )
                    }
                    Gap(10.dp)
                    KeyValueRow("机型", custom.model)
                    KeyValueRow("品牌", custom.brand)
                    KeyValueRow("设备", custom.device)
                    KeyValueRow("厂商", custom.manufacturer)
                }
            }
        }

        // ── 卡 4：附加选项 ────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                SwitchRow(
                    title = "伪装指纹",
                    desc = "Build.FINGERPRINT，风控严格的应用会读",
                    trailing = {
                        SwitchCompact(
                            checked = st.spoofFingerprint,
                            onCheckedChange = { want ->
                                val fp = st.effective()?.fingerprint
                                if (want && fp.isNullOrBlank()) {
                                    Toaster.show("当前档位没有可用的指纹", true)
                                } else {
                                    persist(
                                        st.copy(spoofFingerprint = want),
                                        if (want) "已开启指纹伪装" else "已关闭指纹伪装"
                                    )
                                }
                            }
                        )
                    }
                )

                CardDivider()

                SwitchRow(
                    title = "仅作用域内生效",
                    desc = "关闭则对全部应用生效，容易被系统自身识别到",
                    trailing = {
                        SwitchCompact(
                            checked = st.scopeOnly,
                            onCheckedChange = { want ->
                                persist(
                                    st.copy(scopeOnly = want),
                                    if (want) "已限制为作用域内" else "已对全部应用生效"
                                )
                            }
                        )
                    }
                )

                CardDivider()

                LabelText("作用域包名")
                Gap(6.dp)
                if (st.scopePackages.isEmpty()) {
                    HintText("未勾选任何应用。请在 LSPosed 里为本模块勾选作用域，勾不到的应用不会生效。")
                } else {
                    st.scopePackages.forEach { pkg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.weight(1f)) { MonoText(pkg) }
                            Text(
                                "移除",
                                fontSize = 12.sp,
                                color = Semantic.ERR,
                                modifier = Modifier.clickable {
                                    persist(
                                        st.copy(scopePackages = st.scopePackages - pkg),
                                        "已移除 " + pkg
                                    )
                                }
                            )
                        }
                        Gap(6.dp)
                    }
                }
            }
        }

        // ── 卡 5：真机真实值（只读对照）──────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("本机真实值")
                Gap(6.dp)
                HintText("本模块不写系统属性，下面永远是真实值")
                Gap(10.dp)
                KeyValueRow("ro.product.model", realModel)
                KeyValueRow("ro.product.brand", realBrand)
                KeyValueRow("ro.product.device", realDevice)
            }
        }

        // ── 卡 5.5：这一页到底用来干什么 ─────────────────────────
        //
        // 这是本模块里唯一一个「目的是省电」的功能，必须写清楚，
        // 否则用户会以为它是个解锁画质的玩具，选错档位反而更费电。
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("用来干什么")
                Gap(8.dp)
                HintText(
                    "把本机伪装成老机型，是为了让抖音这类 App 自己切到「老机型省电模式」：" +
                        "它们检测到机型老旧后，会主动降分辨率、降码率、少做特效和预解码，" +
                        "CPU 与 GPU 的负载就下来了，发热和耗电随之下降。"
                )
                Gap(8.dp)
                HintText(
                    "所以「OPPO A5」这一档是省电用的，不是画质用的；" +
                        "「iPhone 17 Pro Max」那一档反过来，是让 App 放开高清画质，会更耗电。"
                )
                Gap(8.dp)
                HintText("只想省电就选老机型档，并只给抖音这类耗电大户勾作用域。")
            }
        }
        Spacer(Modifier.height(12.dp))
        // ── 卡 6：生效步骤 ────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                LabelText("怎么让它生效")
                Gap(12.dp)
                StepRow(1, "在 LSPosed 中勾选作用域", "LSPosed 里找到 CoreGate，勾上要伪装的 App")
                Gap(10.dp)
                StepRow(2, "在这里选档位并打开总开关", "选完会自动写入 /data/adb/CoreGate/spoof.json")
                Gap(10.dp)
                StepRow(3, "强制停止目标应用再打开", "不需要重启手机")
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (loading) {
        LaunchedEffect(Unit) { Toaster.show("正在读取配置…") }
    }
}

/** 单选机型行：左侧 18dp 圆圈，选中时内嵌 7dp 白点。 */
@Composable
private fun ProfileRow(
    profile: SpoofProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) Semantic.ACC else Color.White.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                profile.title,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Gap(3.dp)
            MonoText(profile.model + " / " + profile.device, color = Semantic.OFF)
            if (profile.subtitle.isNotBlank()) {
                Gap(3.dp)
                HintText(profile.subtitle)
            }
        }
    }
}

/** 步骤行：左侧 18dp 序号圆点 + 标题 + 说明。 */
@Composable
private fun StepRow(no: Int, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Semantic.ACC.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                no.toString(),
                fontSize = 10.sp,
                color = Semantic.ACC,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = Color.White)
            Gap(3.dp)
            HintText(desc)
        }
    }
}

/**
 * 紧凑开关，与 FreezePage 同款视觉。
 * 这里是本文件内的 private 副本 —— FreezePage 那份也是 private，
 * 分属不同文件不冲突；后续若要抽公共组件再统一。
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

/** 读一条系统属性，空值统一显示「读不到」。 */
private suspend fun readProp(sh: RootShell, key: String): String {
    val v = readRaw(sh, "getprop " + key).trim()
    return if (v.isEmpty()) "读不到" else v
}