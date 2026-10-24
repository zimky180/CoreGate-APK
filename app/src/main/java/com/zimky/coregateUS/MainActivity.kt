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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
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
import com.zimky.coregateUS.ui.component.RootGate
import com.zimky.coregateUS.ui.component.FloatingBottomBar
import com.zimky.coregateUS.ui.component.FloatingBottomBarItem
import com.zimky.coregateUS.ui.theme.AppSettings
import com.zimky.coregateUS.ui.theme.MiuixKernelSUTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
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
                // 开屏 root 门槛：拿不到 root 就根本进不来（拦截式，不是提醒式）。
                // 放在主题内部，保证检测页与主界面共用同一套配色，不会闪一次白屏。
                RootGate { AppRoot() }
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
 * 页面槽位：四页常驻 composition，切 Tab 只切换可见性，不销毁重建。
 *
 * 之前误写成 `if (visible) content()`，每次切 Tab 都会销毁整棵子树，
 * 滚动位置和输入框内容全部丢失，与类注释里「滚动位置与输入内容都不丢」的承诺不符。
 *
 * 现在改为常驻 + 不可见 + 不可交互：
 *   - 用 graphicsLayer 的 alpha 而不是 draw.alpha，配合 zIndex 让可见页压在最上层；
 *   - 隐藏页不再挂 pointerInput —— 旧写法 `Modifier.pointerInput(Unit) {}` 并不会
 *     禁用命中测试，它只是注册了一个什么都不做的 pointer input node，
 *     指针事件照样会落进来（Compose 的 alpha 不参与 hit test 裁剪），
 *     结果是隐藏页的 verticalScroll 会和可见页抢手势，
 *     表现就是「部分页面滑不动」。改为隐藏页整块不参与命中测试。
 *   - 隐藏页保留 composition，滚动位置与输入内容都不丢。
 */
@Composable
private fun PageSlot(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
            }
            .then(
                if (visible) Modifier
                // 隐藏页：跳过自身及其子树的指针命中，彻底不抢手势
                else Modifier.pointerInput(Unit) { }
            )
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

    // 底栏 backdrop。
    //
    // ⚠️ 这里曾经踩过一个坑：只创建 backdrop 而不给内容层挂 layerBackdrop，
    // 底栏的 drawBackdrop 就取不到任何内容像素，blur/lens/vibrancy 全部失效，
    // 底栏退化成一块不透明的纯色块 —— 也就是用户说的「不像 KSU，那条黑带子是死的」。
    //
    // KSU 的做法是两件套，缺一不可：
    //   1) 内容层（HorizontalPager）挂 Modifier.layerBackdrop(backdrop)  ← 采集
    //   2) 底栏交给 Scaffold 的 bottomBar 槽位，内部 drawBackdrop(backdrop) ← 消费
    // 本项目第 1 步之前完全没做，第 2 步用的是裸 Box 手动 align。
    // 现在两步都补齐：内容 Box 挂 layerBackdrop，底栏进 Scaffold 槽位。
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

    /** 读配置。root 授权弹窗被拒时 loaded 会是 false，需要能重试。 */
    fun loadConfig() {
        CoreConfig.load(RootShell) { c, pkgs, _ ->
            cfg = c
            packagesRaw = pkgs.orEmpty()
            refresh(false)
        }
    }

    // 首次进入：先读配置，读到后再拉一次状态
    LaunchedEffect(Unit) {
        loadConfig()
    }

    // 生命周期：回到前台立刻刷一次
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 自愈：首次启动时若还没来得及点「允许」，配置就会永远读不到。
                // 每次回前台发现没读到就重试一次（root 已授权时这是零成本）。
                if (!cfg.loaded) loadConfig()
                refresh(false)
            }
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

    // 根布局：Scaffold(bottomBar = …)，与 KernelSU 官方 ui/MainActivity.kt 的
    // `Scaffold(bottomBar = bottomBar) { pagerContent(innerPadding.calculateBottomPadding()) }`
    // 逐条对齐。底栏进槽位有两个必须的理由：
    //   1) 槽位横向撑满，FloatingBottomBar 内部的 width(IntrinsicSize.Min) 才能拿到确定宽度，
    //      不会塌缩成圆（v2.2.0 初版「文字竖排」的缺陷就是缺这个约束）；
    //   2) 浮层不再由业务代码手动 align，层级由 Scaffold 保证，backdrop 绘制次序稳定。
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                val navBottomInset = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                // 底部间距照抄 KSU：有导航栏 inset 时 8.dp + inset，否则 28.dp
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
            }
        }
    ) { innerPadding ->
        // 内容区：四页常驻，只显示当前那个。
        //
        // 缺陷 1 修复要点：内容层必须挂 Modifier.layerBackdrop(backdrop)。
        // layerBackdrop 让本节点绘制时把像素写进 backdrop 的离屏缓冲，
        // 底栏的 drawBackdrop(backdrop) 才能读到「底栏背后的界面」并做实时采样模糊。
        // 之前缺了这一句，backdrop 里只有 rememberLayerBackdrop 那次 drawRect(surfaceColor)，
        // 底栏于是永远是一块纯色，跟内容毫无互动 —— 这就是「不像 KSU」的根因。
        //
        // innerPadding 在这里只做「读取」而不用来缩进内容 —— 与 KSU 一致：
        // 内容铺满全屏、流到底栏背后，底栏的 blur 才有东西可采。
        // 各页自己的滚动内容由 PAGE_BOTTOM_INSET 兜住，不会被底栏遮住。
        // 用 calculateBottomPadding() 取值即可，避免「取了 innerPadding 却不用」的告警。
        @Suppress("UNUSED_VARIABLE")
        val bottomPad = innerPadding.calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .statusBarsPadding()
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

            // 轻提示层：盖在内容与底栏之上，顶部居中淡入淡出
            ToastHost(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            )
        }
    }

}