package com.zimky.coregateUS.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Foreground-only player for bundled assets and optional HTTPS streams. */
private class HomeStream(context: Context) {
    private val app = context.applicationContext
    private val audio = requireNotNull(app.getSystemService(AudioManager::class.java))
    private var media: MediaPlayer? = null
    private var prepared = false
    private var wantsPlay = false
    private var source = ""
    var onCompleted: () -> Unit = {}
    private var pendingSeek: Int? = null
    var requestVersion by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var duration by mutableIntStateOf(0)
        private set
    var message by mutableStateOf("点击播放，支持离线收听")
        private set
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if (change != AudioManager.AUDIOFOCUS_GAIN) pause()
        }.build()

    fun toggle(url: String) {
        if (playing || loading) { pause(); return }
        if (url.isBlank()) return
        wantsPlay = true
        if (prepared && source == url) { start(); return }
        releaseMedia()
        wantsPlay = true
        source = url
        loading = true
        requestVersion++
        message = if (url.startsWith("asset:///music/")) "正在加载…" else "正在读取音频…"
        val player = MediaPlayer()
        media = player
        try {
            player.setAudioAttributes(attributes)
            player.setOnPreparedListener {
                if (media === it) {
                    prepared = true
                    loading = false
                    duration = it.duration.coerceAtLeast(0)
                    message = "已就绪"
                    if (wantsPlay) start()
                }
            }
            player.setOnCompletionListener {
                if (media === it) {
                    playing = false
                    wantsPlay = false
                    position = duration
                    message = "播放结束"
                    audio.abandonAudioFocusRequest(focus)
                    onCompleted()
                }
            }
            player.setOnErrorListener { current, _, _ ->
                if (media === current) fail("加载失败，请检查文件读取权限或音频地址")
                true
            }
            player.setOnInfoListener { current, what, _ ->
                if (media === current && playing) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) message = "缓冲中…"
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) message = "正在播放"
                }
                false
            }
            player.setOnSeekCompleteListener {
                if (media === it) { pendingSeek = null; tick() }
            }
            if (url in bundledTracks.map { it.url }) {
                app.assets.openFd(url.removePrefix("asset:///" )).use { asset ->
                    player.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
            } else {
                player.setDataSource(app, Uri.parse(url))
            }
            player.prepareAsync()
        } catch (_: Exception) { fail("无法打开这个音频地址") }
    }

    private fun start() {
        if (!prepared) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            wantsPlay = false
            message = "暂时无法播放，请稍后重试"
            return
        }
        try {
            if (duration > 0 && position >= duration) media?.seekTo(0)
            media?.start()
            playing = true
            message = "正在播放"
        } catch (_: Exception) { fail("播放中断，请重试") }
    }

    fun pause() {
        wantsPlay = false
        if (loading) { releaseMedia(); message = "已取消加载"; return }
        try { if (playing) media?.pause() } catch (_: Exception) { releaseMedia() }
        playing = false
        message = if (prepared) "已暂停" else message
        audio.abandonAudioFocusRequest(focus)
    }

    fun tick() {
        if (prepared && pendingSeek == null) try { position = media?.currentPosition ?: 0 } catch (_: Exception) { }
    }

    fun seek(fraction: Float) {
        if (!prepared || duration <= 0) return
        try {
            position = (duration * fraction).toInt().coerceIn(0, duration)
            pendingSeek = position
            media?.seekTo(position.toLong(), MediaPlayer.SEEK_CLOSEST)
        } catch (_: Exception) { }
    }

    fun fail(text: String) { releaseMedia(); message = text }
    fun reset() { releaseMedia(); message = "点击播放" }
    private fun releaseMedia() {
        val previous = media
        media = null
        prepared = false
        playing = false
        loading = false
        wantsPlay = false
        position = 0
        duration = 0
        pendingSeek = null
        previous?.release()
        audio.abandonAudioFocusRequest(focus)
    }
    fun close() { releaseMedia() }
}

private data class HomeTrack(val id: String, val url: String, val title: String, val artist: String)

private val bundledTracks = listOf(
    HomeTrack("builtin_a01", "asset:///music/a01.m4a", "甲乙丙丁", "李佳薇"),
    HomeTrack("builtin_a02", "asset:///music/a02.m4a", "左转灯", "派伟俊 & mac ova seas"),
    HomeTrack("builtin_a03", "asset:///music/a03.m4a", "迷失环境", "IN-K & 王忻辰"),
    HomeTrack("builtin_a04", "asset:///music/a04.m4a", "坏女孩", "徐良"),
    HomeTrack("builtin_a05", "asset:///music/a05.m4a", "素颜", "许嵩 & 何曼婷"),
    HomeTrack("builtin_a06", "asset:///music/a06.m4a", "后会无期", "徐良 & 汪苏泷"),
)

private fun readTracks(prefs: android.content.SharedPreferences): List<HomeTrack> {
    val raw = prefs.getString("tracks", null)
    if (raw != null) return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            HomeTrack(item.getString("id"), item.getString("url"), item.optString("title"), item.optString("artist"))
        }.filter { it.url.isNotBlank() }
    }.getOrDefault(emptyList())
    val oldUrl = prefs.getString("url", "").orEmpty()
    return if (oldUrl.isBlank()) emptyList() else listOf(HomeTrack("legacy", oldUrl,
        prefs.getString("title", "我的音乐").orEmpty(), ""))
}

@Composable
fun BoxScope.HomeMusicPlayer() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("home_music", Context.MODE_PRIVATE) }
    val stream = remember(context) { HomeStream(context) }
    var tracks by remember {
        val saved = readTracks(prefs)
        val additions = when {
            !prefs.getBoolean("bundled_seeded_v1", false) -> bundledTracks
            !prefs.getBoolean("bundled_seeded_v2", false) -> bundledTracks.filter { it.id in setOf("builtin_a05", "builtin_a06") }
            !prefs.getBoolean("bundled_seeded_v3", false) -> bundledTracks.filter { it.id == "builtin_a06" }
            else -> emptyList()
        }
        val initial = saved + additions.filter { built -> saved.none { it.id == built.id } }
        mutableStateOf(initial)
    }
    var currentId by remember { mutableStateOf(prefs.getString("current_id", null)) }
    val track = tracks.firstOrNull { it.id == currentId } ?: tracks.firstOrNull()
    var direction by remember { mutableIntStateOf(1) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val history = remember { mutableStateListOf<String>() }
    var showQueue by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    val cs = MiuixTheme.colorScheme
    fun persist(list: List<HomeTrack>, id: String?) {
        val array = JSONArray()
        list.forEach { item -> array.put(JSONObject().put("id", item.id).put("url", item.url)
            .put("title", item.title).put("artist", item.artist)) }
        prefs.edit().putString("tracks", array.toString()).putString("current_id", id).apply()
    }
    LaunchedEffect(Unit) {
        if (!prefs.getBoolean("bundled_seeded_v3", false)) {
            persist(tracks, currentId ?: tracks.firstOrNull()?.id)
            prefs.edit().putBoolean("bundled_seeded_v1", true)
                .putBoolean("bundled_seeded_v2", true)
                .putBoolean("bundled_seeded_v3", true).apply()
        }
    }
    fun select(item: HomeTrack, step: Int) {
        if (item.id == track?.id) return
        val continuePlaying = stream.playing || stream.loading
        direction = step
        currentId = item.id
        persist(tracks, item.id)
        stream.reset()
        if (continuePlaying) stream.toggle(item.url)
    }
    fun randomTrack(): HomeTrack? = tracks.filter { it.id != track?.id }.randomOrNull() ?: track
    fun step(delta: Int) {
        if (tracks.size < 2) return
        if (delta < 0) {
            var previous: HomeTrack? = null
            while (history.isNotEmpty() && previous == null) {
                val id = history.removeAt(history.lastIndex)
                previous = tracks.firstOrNull { it.id == id && it.id != track?.id }
            }
            select(previous ?: randomTrack() ?: return, -1)
        } else {
            track?.let { history.add(it.id) }
            select(randomTrack() ?: return, 1)
        }
    }
    val latestFinish by rememberUpdatedState<() -> Unit>({
        val next = randomTrack()
        if (next != null) {
            track?.let { history.add(it.id) }
            direction = 1
            currentId = next.id
            persist(tracks, next.id)
            stream.reset()
            stream.toggle(next.url)
        }
    })
    SideEffect { stream.onCompleted = { latestFinish() } }
    LaunchedEffect(Unit) {
        if (prefs.getBoolean("autoplay_on_start", true)) {
            tracks.randomOrNull()?.let {
                currentId = it.id
                persist(tracks, it.id)
                stream.toggle(it.url)
            }
        }
    }
    val localPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        var title = "本地音乐"
                        var artist = "未知歌手"
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) title = it.getString(0)?.substringBeforeLast('.') ?: title
                        }
                        val metadata = MediaMetadataRetriever()
                        try {
                            runCatching { metadata.setDataSource(context, uri)
                            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                            }
                        } finally { metadata.release() }
                        HomeTrack(UUID.randomUUID().toString(), uri.toString(), title, artist)
                    }
                }
                imported.onSuccess { item ->
                    val existing = tracks.firstOrNull { it.url == item.url }
                    if (existing == null) {
                        val list = tracks + item
                        tracks = list
                        if (currentId == null) currentId = item.id
                        persist(list, currentId)
                        Toaster.show("已导入：${item.title}")
                    } else Toaster.show("这首歌已在歌单中")
                }.onFailure { Toaster.show("无法导入，请选择可读取的音频文件", true) }
            }
        }
    }
    LaunchedEffect(stream.playing) {
        while (stream.playing) { stream.tick(); delay(250) }
    }
    LaunchedEffect(stream.loading, stream.requestVersion) {
        if (stream.loading) { delay(30_000); if (stream.loading) stream.fail("连接超时，请检查音频地址后重试") }
    }
    DisposableEffect(owner, stream) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stream.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); stream.close() }
    }
    // One surface remains anchored at the right edge throughout both transitions.
    Box(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = PAGE_BOTTOM_INSET + 8.dp)) {
        Card(Modifier.widthIn(max = 280.dp).shadow(10.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .animateContentSize(animationSpec = tween(320), alignment = Alignment.BottomEnd)) {
            if (expanded) {
                Column(Modifier.width(280.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(targetState = track, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                            transitionSpec = {
                                (slideInHorizontally(tween(280)) { it * direction / 3 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(240)) { -it * direction / 3 } + fadeOut(tween(160)))
                            }, label = "musicTrack") { item ->
                            Column(Modifier.fillMaxWidth().clickable { editingId = item?.id; edit = true }) {
                                Text(item?.title?.ifBlank { "未命名歌曲" } ?: "随身听", color = cs.onSurface,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item?.artist?.ifBlank { "未填写歌手" } ?: "添加喜欢的歌曲",
                                    color = cs.onSurfaceVariantSummary, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TextButton("收起", onClick = { expanded = false })
                    }
                    Text("随机播放 · ${stream.message}", color = cs.onSurfaceVariantSummary, fontSize = 10.sp, maxLines = 1)
                    key(track?.id) { MusicSeekBar(stream.position, stream.duration, stream.playing, stream::seek) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        MusicTransportButton("previous", "上一首", tracks.size > 1) { step(-1) }
                        MusicTransportButton(if (stream.loading) "cancel" else if (stream.playing) "pause" else "play",
                            if (stream.loading) "取消加载" else if (stream.playing) "暂停" else "播放", true) {
                            val selected = track
                            if (selected == null) { editingId = null; edit = true } else stream.toggle(selected.url)
                        }
                        MusicTransportButton("next", "随机下一首", tracks.size > 1) { step(1) }
                        TextButton("歌单", onClick = { showQueue = true })
                    }
                }
            } else {
                Box(Modifier.size(width = 56.dp, height = 48.dp).clickable { expanded = true },
                    contentAlignment = Alignment.Center) {
                    Text(if (stream.playing) "‹ ♫" else "‹ ♪", color = cs.primary, fontSize = 20.sp)
                }
            }
        }
    }
    if (showQueue) Dialog(onDismissRequest = { showQueue = false }) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("我的歌单 · ${tracks.size}", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (tracks.isEmpty()) Text("添加歌曲后，即可使用上一首和下一首。", color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                tracks.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            val old = tracks.indexOfFirst { it.id == track?.id }
                            select(item, if (index >= old) 1 else -1)
                            showQueue = false
                        }.padding(vertical = 10.dp)) {
                            Text((if (item.id == track?.id) "♪ " else "") + item.title,
                                color = if (item.id == track?.id) cs.primary else cs.onSurface, fontSize = 15.sp)
                            Text(item.artist.ifBlank { "未填写歌手" }, color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
                        }
                        TextButton("编辑", onClick = { editingId = item.id; showQueue = false; edit = true })
                    }
                }
                TextButton("添加网络歌曲", onClick = { editingId = null; showQueue = false; edit = true })
                TextButton("导入本地音乐", onClick = { showQueue = false; localPicker.launch(arrayOf("audio/*")) })
                TextButton("关闭", onClick = { showQueue = false })
            }
        }
    }
    if (edit) {
        val original = tracks.firstOrNull { it.id == editingId }
        var draftUrl by remember(editingId) { mutableStateOf(original?.url.orEmpty()) }
        var draftTitle by remember(editingId) { mutableStateOf(original?.title.orEmpty()) }
        var draftArtist by remember(editingId) { mutableStateOf(original?.artist.orEmpty()) }
        var error by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { edit = false }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (original == null) "添加歌曲" else "编辑歌曲", color = cs.onSurface,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (original?.url?.startsWith("asset:///") == true)
                        "内置歌曲可直接离线播放，也可以修改歌名和歌手。" else
                        "填写歌名、歌手与 HTTPS 音频地址，或导入本地文件。", color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
                    MusicInput(draftTitle, { draftTitle = it.take(80) }, "歌曲名称")
                    MusicInput(draftArtist, { draftArtist = it.take(80) }, "歌手")
                    if (original?.url?.startsWith("asset:///") != true && original?.url?.startsWith("content://") != true)
                        MusicInput(draftUrl, { draftUrl = it.take(4096); error = "" }, "https://… 音频地址")
                    if (original == null) TextButton("导入本地音乐", onClick = {
                        edit = false; localPicker.launch(arrayOf("audio/*"))
                    })
                    if (error.isNotBlank()) Text(error, color = cs.error, fontSize = 12.sp)
                    if (original != null) TextButton("从歌单移除", onClick = {
                        val list = tracks.filterNot { it.id == original.id }
                        if (track?.id == original.id) { stream.reset(); currentId = list.firstOrNull()?.id }
                        tracks = list; persist(list, currentId); edit = false
                    })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton("取消", onClick = { edit = false })
                        TextButton("保存", onClick = {
                            val candidate = draftUrl.trim()
                            val uri = Uri.parse(candidate)
                            if (!(original != null && candidate == original.url && (bundledTracks.any { it.url == candidate } || Uri.parse(candidate).scheme == "content")) &&
                                (uri.scheme != "https" || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank())) {
                                error = "请填写有效的 HTTPS 音频地址"
                            } else if (draftTitle.isBlank() || draftArtist.isBlank()) {
                                error = "请填写歌名和歌手，以便正确显示"
                            } else {
                                val item = HomeTrack(original?.id ?: UUID.randomUUID().toString(), candidate,
                                    draftTitle.trim(), draftArtist.trim())
                                val list = if (original == null) tracks + item else tracks.map { if (it.id == item.id) item else it }
                                if (track == null) currentId = item.id
                                if (track?.id == item.id && track.url != candidate) stream.reset()
                                tracks = list
                                persist(list, currentId ?: list.firstOrNull()?.id)
                                edit = false
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicInput(value: String, onChange: (String) -> Unit, hint: String) {
    val cs = MiuixTheme.colorScheme
    BasicTextField(value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = cs.onSurface, fontSize = 14.sp),
        cursorBrush = SolidColor(cs.primary),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerHigh).padding(14.dp),
        decorationBox = { inner -> Box {
            if (value.isEmpty()) Text(hint, color = cs.onSurfaceVariantSummary, fontSize = 14.sp)
            inner()
        } })
}