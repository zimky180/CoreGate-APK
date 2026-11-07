package com.zimky.coregateUS.core

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProcessorName {
    // 同一基础代号可能对应多个频率版本；不把 MT6991 等模糊代号强行标成 Plus。
    private val names = mapOf(
        "SM8845" to "骁龙 8 Gen 5",
        "SM8850" to "骁龙 8 Elite Gen 5",
        "SM8750" to "骁龙 8 Elite",
        "SM8735" to "骁龙 8s Gen 4",
        "SM8650" to "骁龙 8 Gen 3",
        "SM8635" to "骁龙 8s Gen 3",
        "SM8550" to "骁龙 8 Gen 2",
        "SM8475" to "骁龙 8+ Gen 1",
        "SM8450" to "骁龙 8 Gen 1",
        "SM8350" to "骁龙 888",
        "SM8250" to "骁龙 865",
        "MT6991Z/CZA" to "天玑 9400+",
        "MT6991Z/CZ" to "天玑 9400",
        "MT6989Z/CZA" to "天玑 9300+",
        "MT6989Z/CZ" to "天玑 9300"
    )

    private fun usable(value: String) = value.isNotBlank() &&
        value.lowercase() !in setOf("unknown", "null", "default")

    fun resolve(candidates: List<String>): String {
        val values = candidates.map(String::trim).filter(::usable)
        // 厂商已给出商品名称时优先保留，包括 + / 领先版等后缀。
        values.firstOrNull {
            it.contains("snapdragon", true) || it.contains("dimensity", true) ||
                it.contains("骁龙") || it.contains("天玑") || it.contains("exynos", true) || it.contains("kirin", true)
        }?.let { return it.replace(Regex("(?i)snapdragon\\s*"), "骁龙 ")
            .replace(Regex("(?i)dimensity\\s*"), "天玑 ") }
        values.forEach { value -> names[value.uppercase()]?.let { return it } }
        return values.firstOrNull() ?: "未知处理器"
    }

    suspend fun load(): String = withContext(Dispatchers.IO) {
        val props = runCatching {
            val process = ProcessBuilder("/system/bin/getprop").start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val all = Regex("(?m)^\\[([^]]+)]: \\[(.*)]$").findAll(output)
                .associate { it.groupValues[1] to it.groupValues[2] }
            listOf("ro.soc.model", "ro.vendor.soc.model", "ro.vendor.qti.soc_model", "ro.board.platform")
                .mapNotNull(all::get)
        }.getOrDefault(emptyList())
        resolve(listOf(Build.SOC_MODEL) + props)
    }
}
