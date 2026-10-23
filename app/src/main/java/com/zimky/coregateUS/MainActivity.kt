package com.zimky.coregateUS

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zimky.coregateUS.core.CoreConfig
import com.zimky.coregateUS.core.RootShell
import com.zimky.coregateUS.core.SysProbe
import com.zimky.coregateUS.page.FreqPage
import com.zimky.coregateUS.page.LockPage
import com.zimky.coregateUS.page.PolicyPage
import com.zimky.coregateUS.page.SettingsPage
import com.zimky.coregateUS.ui.ToastHost
import com.zimky.coregateUS.ui.component.FloatingBottomBar
import com.zimky.coregateUS.ui.component.FloatingBottomBarItem
import com.zimky.coregateUS.ui.theme.AppSettings
import com.zimky.coregateUS.ui.theme.MiuixKernelSUTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MainActivity —— 全原生壳，零 WebView（Compose + Miuix 版）。
 *
 * 与旧 View 版行为一一对齐，三条硬性约束照旧：
 *   1) root 调用串行 + 合并。同一时刻只有一个 probe 在跑，期间来的请求只置「再跑一次」，
 *      手动刷新还有 1.2 秒最小间隔 —— 这是 v2.0.6 修掉安全弹窗的核心逻辑。
 *   2) 页面切换不销毁重建。四页 Composable 全部常驻在 composition 里，
 *      切 Tab 只改是否可见，滚动位置与输入内容都不丢。
 *   3) 页面只负责显示，轮询统一在 Activity 层做，两条线不打架。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixKernelSUTheme(appSettings = remember { AppSettings() }) {
                AppRoot()
            }
        }
    }
}

/** 轮询间隔。对齐 WebUI 的 8 秒（v2.0.6 从 5 秒放宽后的值）。 */
private const val POLL_MS = 8_000L

/** 手动刷新冷却，防抖。 */
private const val MIN_REFRESH_GAP = 1_200L

/** 底栏四栏文案。 */
private val TAB_TITLES = listOf("锁核", "策略", "限频", "设置")

/**
 * 内容区底部留白。
 *
 * 底栏是「悬浮」在内容之上的（Box + align(BottomCenter)），不是 Scaffold 的 bottomBar 槽位，
 * 所以内容区不会自动让出底栏占用的高度，必须手动留白，否则每页最后一张卡片会被底栏挡住。
 *
 * 这个值由三部分组成：
 *   1) 底栏自身高度 64.dp（FloatingBottomBar 内部 .height(64.dp)）；
 *   2) 底栏底部间距，正常是 28.dp，带手势条时是 8.dp + 手势条高度（约 24.dp），两者接近；
 *   3) 8.dp 缓冲，让最后一张卡片和底栏之间留一点缝，不贴死。
 *
 * 取 28.dp 这一档来算：64 + 28 + 8 = 100.dp。
 * 手势条机型实际底栏占位约 64 + 32 = 96.dp，100.dp 仍有余量，不会露。
 * 之所以不写成变量联动 barBottomPadding：那个值在 AppRoot 内部才能拿到，
 * 而 padding 是 Compose 每次重组都会重算的，写死一个够用的常量更简单也更稳。
 */
private val CONTENT_BOTTOM_PADDING = 100.dp

/**
 * 页面槽位：四页常驻 composition，切 Tab 只切换可见性，不销毁重建。
 *
 * 之前误写成 `if (visible) content()`，每次切 Tab 都会销毁整棵子树，
 * 滚动位置和输入框内容全部丢失，与类注释里「滚动位置与输入内容都不丢」的承诺不符。
 *
 * 现在改为常驻 + alpha 0/1 + 不可交互：
 *   - alpha 而不是 visibility，是因为 Compose 没有 Android View 那种 GONE，
 *     用 alpha 0 隐藏可以保留滚动状态与焦点；
 *   - 隐藏时清掉 pointerInput，避免透明的页面截走点击。
 * 注意：不可见页面仍在内存里，四页都是轻量列表，代价可接受。
 */
@Composable
private fun PageSlot(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (visible) 1f else 0f)
            .then(if (visible) Modifier else Modifier.pointerInput(Unit) {})
    ) {
        content()
    }
}

@Composable
private fun AppRoot() {
    val main = remember { Handler(Looper.getMainLooper()) }

    // ---- 宿主状态：配置 / 包名列表 / 最近一次快照 ----
    var cfg by remember { mutableStateOf(CoreConfig()) }
    var packagesRaw by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<SysProbe.Snapshot?>(null) }
    var currentTab by rememberSaveable { mutableStateOf(0) }

    // 刷新：串行 + 合并
    var refreshing by remember { mutableStateOf(false) }
    var refreshAgain by remember { mutableStateOf(false) }
    var lastRefreshAt by remember { mutableStateOf(0L) }
    var alive by remember { mutableStateOf(true) }

    // 底栏 backdrop：底栏内部会自行与自己的 tabsBackdrop 合并，这里给一个即可。
    // 注意这里必须用 rememberLayerBackdrop（非空），不能用 rememberBlurBackdrop ——
    // 后者是给 BlurredBar 用的可空版本，而 FloatingBottomBar 的形参是非空 Backdrop。
    // 这里的写法与 KSU ui/MainActivity.kt 完全一致。
    // 关键：colorScheme 只能在 @Composable 上下文读取，而 rememberLayerBackdrop 的
    // lambda 不是 @Composable，所以必须先在外层取出 surfaceColor 再闭包捕获。
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    /**
     * 拉一次状态。
     *
     * 串行：同一时刻只允许一个 probe 在跑，期间来的请求只标记「再跑一次」。
     * 冷却：fromUser=true 时也照样有 1.2 秒最小间隔，因为狂点刷新正是
     *       v2.0.6 里弹安全警告的元凶。
     */
    fun refresh(fromUser: Boolean) {
        if (!alive) return
        val now = System.currentTimeMillis()
        if (fromUser && now - lastRefreshAt < MIN_REFRESH_GAP) {
            refreshAgain = true
            return
        }
        if (refreshing) {
            refreshAgain = true
            return
        }
        refreshing = true
        lastRefreshAt = now
        SysProbe.probe(RootShell) { s ->
            refreshing = false
            snapshot = s
            if (refreshAgain) {
                refreshAgain = false
                // 稍等一下再补，别把两次 probe 挤在一起
                main.postDelayed({ refresh(false) }, 300L)
            }
        }
    }

    // 首次进入：先读配置，读到后再拉一次状态
    LaunchedEffect(Unit) {
        CoreConfig.load(RootShell) { c, pkgs, _ ->
            cfg = c
            packagesRaw = pkgs.orEmpty()
            refresh(false)
        }
    }

    // 生命周期：回到前台立刻刷一次
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            alive = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 轮询循环：只在 resumed 时真的去拉，切后台就空转不消耗 root
    LaunchedEffect(lifecycleOwner) {
        while (alive) {
            delay(POLL_MS)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                refresh(false)
            }
        }
    }

    /**
     * 保存配置。写盘成功后本地副本同步更新（避免界面回跳），
     * 再延迟一点补一次刷新，让守护进程的新状态尽快反映到界面。
     */
    fun save(newCfg: CoreConfig, packages: String?, done: ((Boolean, String) -> Unit)?) {
        val cmd = CoreConfig.buildSaveCmd(newCfg, packages)
        RootShell.exec(cmd) { code, _, err ->
            val ok = code == 0
            if (ok) {
                cfg = newCfg.copy(loaded = true)
                if (packages != null) packagesRaw = packages
                main.postDelayed({ refresh(false) }, 400L)
            }
            done?.invoke(
                ok,
                if (ok) "OK" else err.takeIf { it.isNotEmpty() } ?: "退出码 $code"
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        // 内容区：四页常驻，只显示当前那个。
        // 底部留白 = 底栏高度（64dp）+ 底栏底部间距 + 一点缓冲，避免最后一张卡片被底栏挡住。
        // 注意这里的留白要和下面 FloatingBottomBar 的 padding 保持一致，
        // 底栏从「贴合屏幕底部」改成了「KSU 同款 28dp 悬浮」后，需要多留出这段距离。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = CONTENT_BOTTOM_PADDING)
        ) {
            PageSlot(visible = currentTab == 0) {
                LockPage(cfg, snapshot, packagesRaw, RootShell, { refresh(true) }, ::save)
            }
            PageSlot(visible = currentTab == 1) {
                PolicyPage(cfg, snapshot, packagesRaw, RootShell, { refresh(true) }, ::save)
            }
            PageSlot(visible = currentTab == 2) {
                FreqPage(cfg, snapshot, packagesRaw, RootShell, { refresh(true) }, ::save)
            }
            PageSlot(visible = currentTab == 3) {
                SettingsPage(cfg, snapshot, packagesRaw, RootShell, { refresh(true) }, ::save)
            }
        }

        // 悬浮液态底栏
        //
        // 宽度策略严格对齐 KernelSU 官方（BottomBarMiuix.kt + MainActivity 的 Scaffold(bottomBar=…)）：
        //   - KSU 把底栏交给 Scaffold 的 bottomBar 槽位，该槽位横向撑满，所以组件内部的
        //     width(IntrinsicSize.Min) 拿到的是「整屏宽 - 边距」这个确定值，不会塌缩；
        //   - 这里用 Box 排版，等价写法是显式 fillMaxWidth() 撑满 + 左右 28.dp 边距；
        //   - 底部间距照抄 KSU：有导航栏 inset 时取 8.dp + inset，否则 28.dp。
        //   缺了 fillMaxWidth()，IntrinsicSize.Min 会把宽度收缩到最小值，
        //   再被 CircleShape 裁成圆 → 文字竖排、圆圈极小（v2.2.0 初版实测到的缺陷）。
        val navBottomInset = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val barBottomPadding = if (navBottomInset != 0.dp) 8.dp + navBottomInset else 28.dp
        FloatingBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, bottom = barBottomPadding),
            selectedIndex = currentTab,
            onSelected = { currentTab = it },
            backdrop = backdrop,
            tabsCount = TAB_TITLES.size,
            isBlurEnabled = true
        ) { activateTab ->
            TAB_TITLES.forEachIndexed { i, title ->
                FloatingBottomBarItem(
                    selected = currentTab == i,
                    onClick = { activateTab(i) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }

        // 轻提示层：盖在内容与底栏之上，顶部居中淡入淡出
        ToastHost(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        )
    }
}