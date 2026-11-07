package com.zimky.coregateUS.ui.contributors

import com.zimky.coregateUS.R
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class AuthorProfile(
    val login: String,
    val name: String,
    val contributionSummary: String,
    val qq: String? = null,
    @DrawableRes val avatarRes: Int? = null
)

/** Fixed order: owner, administrators, testing and creative contributors. QQ numbers supplied by the owner. */
internal val contributorProfiles = listOf(
    AuthorProfile("owner", "zimky", "项目创始人 · 独立开发与维护\n从零构建 CoreGate，负责整体架构、模块逻辑与全部 UI 实现，持续承担版本迭代、问题修复与项目维护。",
        qq = "1805920232", avatarRes = R.drawable.contributor_owner),
    AuthorProfile("friend-1", "寒玉", "社区管理 · 需求与反馈\n汇集用户需求，整理问题与 Bug 反馈，协助开发者跟进处理，连接用户与项目。",
        qq = "3858519152", avatarRes = R.drawable.contributor_feedback),
    AuthorProfile("friend-2", "热情", "社区管理 · 交流与互动\n组织日常交流、带动群内互动，让用户有一个轻松活跃的交流空间。",
        qq = "591570259", avatarRes = R.drawable.contributor_community),
    AuthorProfile("tester-pro-e", "峩", "Pro 内测贡献 · 测试与调试\n参与 Pro 版本的测试与调试，协助内测验证。",
        qq = "490601192", avatarRes = R.drawable.contributor_pro_e),
    AuthorProfile("tester-dongan", "冬安", "测试贡献 · 问题反馈\n参与测试版体验，提供宝贵的 Bug 反馈，帮助发现问题并完善使用体验。",
        qq = "2408646386", avatarRes = R.drawable.contributor_dongan),
    AuthorProfile("tester-canghai", "沧海", "测试贡献 · 问题反馈\n参与测试版体验，提供宝贵的 Bug 反馈，帮助发现问题并完善使用体验。",
        qq = "3560356758", avatarRes = R.drawable.contributor_canghai),
    AuthorProfile("tester-jiekai", "解开超载的心", "核心建议 · 游戏场景锁核\n提出很多有价值的建议，促成游戏场景自定义大核锁核功能；多次反馈 Bug，并及时指出过影响严重的问题，帮助项目排查隐患、完善稳定性。", 
        qq = "3543500780", avatarRes = R.drawable.contributor_jiekai),
    AuthorProfile("tester-doze", "doze", "测试贡献 · 问题反馈\n参与测试版体验，提供宝贵的 Bug 反馈，帮助发现问题并完善使用体验。",
        qq = "1542101707", avatarRes = R.drawable.contributor_doze),
    AuthorProfile("creative-binghongchang", "冰红肠大王", "创意贡献 · 想法与建议\n分享创意与功能设想，为 CoreGate 的后续完善提供灵感。",
        qq = "3995822963", avatarRes = R.drawable.contributor_binghongchang),
    AuthorProfile("ideas-chenyang", "晨阳", "思路贡献 · 限频方案\n分享限频思路，为 CoreGate 的限频方案完善提供参考。",
        qq = "2137315028", avatarRes = R.drawable.contributor_chenyang),
    AuthorProfile("ideas-jiegantian", "节甘天baby", "思路贡献 · 限频方案\n分享限频思路，为 CoreGate 的限频方案完善提供参考。",
        qq = "3565733924", avatarRes = R.drawable.contributor_jiegantian)
)

@Composable
internal fun AuthorAvatar(profile: AuthorProfile, modifier: Modifier = Modifier) {
    val avatar = profile.avatarRes
    if (avatar == null) {
        Box(modifier.clip(CircleShape).background(MiuixTheme.colorScheme.surfaceContainerHigh))
    } else {
        Image(painterResource(avatar), contentDescription = profile.name,
            modifier = modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    }
}