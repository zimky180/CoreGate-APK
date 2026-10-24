package com.zimky.coregateUS.core

import android.content.Context
import java.io.File

/**
 * 「首次 root 授予已完成」的本地标记。
 *
 * 用户需求（原话）：
 *   「首次打开后就是完完全全的告诉你请输入 root，然后首次授予完之后第一屏永不再重现。」
 *
 * 所以语义是**单调一次性**的：
 *   - 从未授予过  → 开屏必须走 RootGate，老老实实要 root；
 *   - 授予成功过  → 之后每次启动直接进主页，Gate 页永不再出现。
 *
 * 这里刻意**不做**「每次启动后台再验一遍、失败就退回 Gate」那一套 ——
 * 那样会违背「第一屏永不再重现」。root 被撤销后的处理方式是：
 * 主页读不到配置 → 界面上给「请检查是否已给予 root 权限」的提示（见 MainActivity），
 * 而不是把用户踢回第一屏。
 *
 * 存储方式：App 私有目录下的一个空文件（filesDir 内，非 sdcard）。
 *   - 不需要任何权限，不需要 root，不受墓碑冻结影响；
 *   - 不引入 SharedPreferences / DataStore，避免为一行布尔值加依赖或改 Gradle；
 *   - 卸载 App 时随私有目录一起清掉，重装后会重新走一次首次授权，
 *     这是预期行为（新装的 App 本来就该重新要 root）。
 *
 * 注意：这是个**易失性缓存**，不是安全凭据。它只决定「开屏要不要拦」，
 * 真正的权限校验永远发生在每一次 RootShell.exec 的 su 调用上。
 */
object RootFlag {

    private const val NAME = "root_granted"

    /** 是否已成功授予过 root（曾经成功过即为 true） */
    fun isGranted(ctx: Context): Boolean = runCatching {
        File(ctx.filesDir, NAME).exists()
    }.getOrDefault(false)

    /** 记录「已成功授予过 root」。调用时机只能是 uid == 0 确认之后。 */
    fun markGranted(ctx: Context) {
        runCatching {
            File(ctx.filesDir, NAME).apply {
                if (!exists()) {
                    parentFile?.mkdirs()
                    createNewFile()
                }
            }
        }
    }

    /**
     * 清除标记（调试用）。
     *
     * 界面上没有入口，保留它是为了排查问题时能让 App 回到「首次启动」状态，
     * 例如改完授权流程想复现第一屏。
     */
    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, NAME).delete() }
    }
}
