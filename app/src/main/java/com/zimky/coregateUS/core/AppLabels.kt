package com.zimky.coregateUS.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * 包名 -> 应用显示名（中文名）解析。
 *
 * 【为什么要单独做一个东西】
 *   冻结管理页原来只显示包名（com.oplus.logkit 这种），用户根本判断不出
 *   这玩意儿该不该冻。用户原话：「你别光写包名，把名字也写上，要不然
 *   用户无法正常分析要不要冻结」。
 *
 * 【为什么需要 <queries>】
 *   Android 11 起有包可见性限制：清单里没声明过的包，即使装在手机上，
 *   `getApplicationInfo` 也会抛 NameNotFoundException。所以清单里用
 *   `<queries>` 点名了冻结表里那 63 个包（不用 QUERY_ALL_PACKAGES，
 *   避免被判「枚举全部应用」）。见 AndroidManifest.xml。
 *
 * 【为什么不每次实时查】
 *   一个组展开就是 52 行，每行都走一次 PackageManager 虽然是内存操作，
 *   但在滚动列表里反复调用依然浪费。这里一次性解析成不可变 Map，
 *   由调用方 remember 住。
 *
 * 【查不到时怎么办】
 *   老老实实返回 null，UI 层退回显示包名。绝不编造名字 ——
 *   显示一个错的名字比显示包名更糟，用户会照着错名字做决定。
 */
object AppLabels {

    /**
     * 批量解析应用名。
     *
     * @param ctx  任意 Context（建议用 applicationContext）
     * @param pkgs 要解析的包名集合
     * @return 包名 -> 应用名；查不到的包不会出现在返回值里
     */
    fun resolve(ctx: Context, pkgs: Collection<String>): Map<String, String> {
        val pm = ctx.packageManager
        val out = HashMap<String, String>(pkgs.size)
        for (pkg in pkgs) {
            val label = labelOf(pm, pkg) ?: continue
            // 有些包（尤其系统组件）的 label 就是包名本身，或者干脆是空串，
            // 这种情况跟「查不到」等价，没必要占一行
            if (label.isBlank() || label == pkg) continue
            out[pkg] = label
        }
        return out
    }

    /** 单个包的应用名；查不到返回 null。 */
    fun labelOf(pm: PackageManager, pkg: String): String? {
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai)?.toString()
        } catch (_: PackageManager.NameNotFoundException) {
            // 包没装，或者清单 <queries> 没点名 -> 不可见。交给 UI 回退显示包名
            null
        } catch (_: Throwable) {
            // 极少数系统包在取 label 时抛 SecurityException 之类，
            // 不能让一个包的异常把整页名字都打没
            null
        }
    }

    /** [FreezeData.GROUPS] 里出现过的所有包名（去重），供一次性预解析。 */
    fun allFreezePkgs(): Set<String> {
        val set = LinkedHashSet<String>()
        for (g in FreezeData.GROUPS) set.addAll(g.pkgs)
        return set
    }
}