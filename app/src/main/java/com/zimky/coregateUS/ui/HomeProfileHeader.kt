package com.zimky.coregateUS.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Calendar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeProfileHeader(onAvatarPosition: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit) {
    val cs = MiuixTheme.colorScheme
    val lifecycle = LocalLifecycleOwner.current
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var quoteOffset by rememberSaveable(today.toString()) { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                today = LocalDate.now()
                delay(30_000)
            }
        }
    }
    val greeting = when (hour) { in 5..10 -> "早上好！"; in 11..17 -> "中午好！"; else -> "晚上好！" }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("核心门控", color = cs.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(greeting, color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.size(52.dp).onGloballyPositioned(onAvatarPosition))
        }
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "换一句") {
            quoteOffset = (quoteOffset + 1) % DailyQuote.count
        }.padding(top = 6.dp)) {
            Text("每日一言 · ${today.monthValue}月${today.dayOfMonth}日", color = cs.primary, fontSize = 12.sp)
            Text(DailyQuote.forDate(today, quoteOffset), color = cs.onSurfaceVariantSummary,
                fontSize = 15.sp, lineHeight = 24.sp)
        }
    }
}

