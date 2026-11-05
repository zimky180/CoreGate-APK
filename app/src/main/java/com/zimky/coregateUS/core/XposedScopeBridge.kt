package com.zimky.coregateUS.core

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Module app side only. Scope approval is not proof that target-side optimization ran. */
object XposedScopeBridge {
    const val TARGET = "com.ss.android.ugc.aweme"
    data class Status(
        val connected: Boolean = false,
        val framework: String = "",
        val approved: Boolean? = null,
        val requesting: Boolean = false,
        val message: String = "等待框架连接；未连接时无法判断是否勾选"
    )

    private val main = Handler(Looper.getMainLooper())
    private val mutableStatus = MutableStateFlow(Status())
    val status = mutableStatus.asStateFlow()
    private val lock = Mutex()
    @Volatile private var service: XposedService? = null
    @Volatile private var initialized = false
    // Accessed on main only; invalidates callbacks after timeout or service replacement.
    private var requestId = 0L

    @Synchronized
    fun initialize() {
        if (initialized) return
        initialized = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(bound: XposedService) {
                main.post {
                    requestId++
                    service = bound
                    mutableStatus.value = Status(connected = true, message = "框架已连接，等待查询作用域")
                }
            }
            override fun onServiceDied(dead: XposedService) {
                main.post {
                    if (service === dead) {
                        requestId++
                        service = null
                        mutableStatus.value = Status(message = "框架连接已断开，勾选状态未知")
                    }
                }
            }
        })
    }

    suspend fun refresh() = lock.withLock {
        val current = service ?: return@withLock
        val result = withContext(Dispatchers.IO) {
            runCatching { current.frameworkName + " " + current.frameworkVersion to current.scope.contains(TARGET) }
        }
        withContext(Dispatchers.Main) {
            if (service !== current) return@withContext
            result.fold(onSuccess = { (framework, approved) ->
                mutableStatus.value = mutableStatus.value.copy(
                    connected = true, framework = framework, approved = approved,
                    message = if (approved) "抖音已加入作用域；重启抖音后再验证实际效果" else "抖音尚未加入作用域"
                )
            }, onFailure = {
                mutableStatus.value = mutableStatus.value.copy(
                    approved = null, message = "作用域查询失败，请到 LSPosed 核对"
                )
            })
        }
    }

    /** Must be initiated by the user's button. Framework owns the approval dialog. */
    suspend fun requestScope() = lock.withLock {
        val current = service ?: return@withLock
        val token = withContext(Dispatchers.Main) {
            if (mutableStatus.value.requesting) return@withContext null
            val next = ++requestId
            mutableStatus.value = mutableStatus.value.copy(requesting = true, message = "已申请，等待框架确认")
            main.postDelayed({
                if (service === current && requestId == next && mutableStatus.value.requesting) {
                    requestId++
                    mutableStatus.value = mutableStatus.value.copy(
                        requesting = false, approved = null,
                        message = "尚未收到授权结果，请刷新或到 LSPosed 核对"
                    )
                }
            }, 60_000L)
            next
        } ?: return@withLock
        fun finish(message: String) {
            main.post {
                if (service === current && requestId == token) {
                    requestId++
                    mutableStatus.value = mutableStatus.value.copy(requesting = false, approved = null, message = message)
                }
            }
        }
        withContext(Dispatchers.IO) {
            try {
                current.requestScope(listOf(TARGET), object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) {
                        // Re-query framework scope; never treat callback text as verified state.
                        finish(if (TARGET in approved) "框架已批准申请，正在重新查询作用域" else "授权结果未包含抖音，请核对作用域")
                    }
                    override fun onScopeRequestFailed(message: String) {
                        finish("授权未完成，请刷新或到 LSPosed 手动勾选")
                    }
                })
            } catch (_: Exception) {
                finish("框架未能完成申请，请到 LSPosed 手动勾选")
            }
        }
    }
}
