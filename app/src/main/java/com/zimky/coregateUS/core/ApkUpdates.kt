package com.zimky.coregateUS.core

import com.zimky.coregateUS.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Public Releases only. No credentials or Actions artifact access is needed. */
object ApkUpdates {
    const val REPOSITORY = "zimky180/CoreGate-App"
    const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
    data class Release(
        val version: String, val title: String, val notes: String,
        val published: String, val pageUrl: String, val apkUrl: String,
        val assetName: String
    )
    sealed interface Result {
        data class Available(val release: Release) : Result
        data class Current(val version: String) : Result
        data object NoRelease : Result
        data class Failed(val message: String) : Result
    }

    private fun versionParts(value: String): List<Int>? {
        val match = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    /** Null means unrecognized version, which must never be reported as up to date. */
    fun compareVersions(candidate: String, current: String): Int? {
        val a = versionParts(candidate) ?: return null
        val b = versionParts(current) ?: return null
        a.indices.forEach { i -> if (a[i] != b[i]) return a[i].compareTo(b[i]) }
        return 0
    }

    private fun trustedReleaseUrl(value: String, asset: Boolean): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.path.startsWith("/$REPOSITORY/releases/${if (asset) "download/" else "tag/"}")
    }.getOrDefault(false)

    fun parseRelease(json: JSONObject): Release? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val version = json.optString("tag_name")
        if (versionParts(version) == null) return null
        val assets = json.optJSONArray("assets") ?: return null
        val candidates = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
            val name = it.optString("name").lowercase()
            name.endsWith(".apk") && !name.contains("unsigned") &&
                trustedReleaseUrl(it.optString("browser_download_url"), true)
        }.sortedBy { if (it.optString("name").contains("debug", true)) 1 else 0 }
        val apk = candidates.firstOrNull() ?: return null
        val page = json.optString("html_url")
        if (!trustedReleaseUrl(page, false)) return null
        return Release(version, json.optString("name").ifBlank { version },
            json.optString("body").take(12000), json.optString("published_at"),
            page, apk.optString("browser_download_url"), apk.optString("name"))
    }

    suspend fun check(current: String = BuildConfig.VERSION_NAME): Result = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$REPOSITORY/releases?per_page=100")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "CoreGate/${BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            when (status) {
                404 -> return@withContext Result.NoRelease
                403, 429 -> return@withContext Result.Failed("GitHub 请求暂时受限，请稍后重试。")
            }
            if (status != 200) return@withContext Result.Failed("GitHub 返回 HTTP $status，请稍后重试。")
            val body = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                val content = StringBuilder()
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    content.append(buffer, 0, count)
                    kotlin.check(content.length <= 2_000_000) { "Response too large" }
                }
                content.toString()
            }
            val list = JSONArray(body)
            val releases = (0 until list.length()).mapNotNull { parseRelease(list.getJSONObject(it)) }
            val latest = releases.maxWithOrNull(Comparator { a, b -> compareVersions(a.version, b.version) ?: 0 })
                ?: return@withContext Result.NoRelease
            val order = compareVersions(latest.version, current)
                ?: return@withContext Result.Failed("当前版本格式无法比较，请前往 GitHub 查看发布记录。")
            if (order > 0) Result.Available(latest) else Result.Current(latest.version)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Failed("检查失败，请检查网络连接后重试。")
        } finally {
            connection.disconnect()
        }
    }
}