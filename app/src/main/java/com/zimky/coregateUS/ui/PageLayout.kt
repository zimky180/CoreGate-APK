package com.zimky.coregateUS.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 一级页内容标题，沿用 Miuix 主题与文字组件。 */
@Composable
fun PageHeading(title: String, summary: String) {
    val cs = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = title, color = cs.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(text = summary, color = cs.onSurfaceVariantSummary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

/** 卡片外分组标题；分组说明可换行，适配较大的系统字体。 */
@Composable
fun SectionHeading(title: String, summary: String? = null) {
    val cs = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, color = cs.onSurfaceVariantSummary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (summary != null) {
            Text(text = summary, color = cs.onSurfaceVariantSummary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** 首页状态卡中的只读摘要，不发起额外探测。 */
@Composable
fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MiuixTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Crossfade(targetState = value, animationSpec = tween(220), label = "summaryValue") { shown ->
            Text(text = shown, color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text = label, color = cs.onSurfaceVariantSummary, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
