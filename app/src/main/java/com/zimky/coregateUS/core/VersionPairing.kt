package com.zimky.coregateUS.core

import com.zimky.coregateUS.BuildConfig

object VersionPairing {
    enum class Status { MATCH, MISMATCH, UNKNOWN }

    fun evaluate(
        installed: Boolean?, moduleName: String?, moduleCode: Int?,
        apkName: String = BuildConfig.VERSION_NAME, apkCode: Int = BuildConfig.VERSION_CODE
    ): Status {
        if (installed != true) return Status.UNKNOWN
        fun normalize(value: String): String = value.trim().removePrefix("v").removeSuffix("-pro").trim()
        val name = moduleName?.let(::normalize)?.takeIf { it.isNotBlank() }
        val apk = normalize(apkName)
        val code = moduleCode?.takeIf { it > 0 }
        if ((name != null && name != apk) ||
            (code != null && code != apkCode)) return Status.MISMATCH
        return if (name != null && code != null) Status.MATCH else Status.UNKNOWN
    }
}