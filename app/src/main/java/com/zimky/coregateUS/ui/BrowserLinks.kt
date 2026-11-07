package com.zimky.coregateUS.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Offer only activities registered to handle general web links. */
internal fun openInBrowser(context: Context, url: String): Boolean = runCatching {
    val uri = Uri.parse(url)
    require(uri.scheme == "https" || uri.scheme == "http")
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val browsers = context.packageManager.queryIntentActivities(
        probe, PackageManager.ResolveInfoFlags.of(
            (PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER).toLong()
        )
    ).filter { resolved ->
        val filter = resolved.filter
        resolved.activityInfo.exported && filter != null &&
            filter.hasAction(Intent.ACTION_VIEW) &&
            filter.hasCategory(Intent.CATEGORY_BROWSABLE) &&
            filter.hasDataScheme("https") &&
            filter.countDataAuthorities() == 0 && filter.countDataPaths() == 0
    }
        .distinctBy { it.activityInfo.packageName }
    if (browsers.isEmpty()) return false
    val targets = browsers.map { browser ->
        Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = ComponentName(browser.activityInfo.packageName, browser.activityInfo.name)
        }
    }
    val launch = if (targets.size == 1) targets.first() else {
        Intent.createChooser(targets.first(), "选择浏览器").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
        }
    }
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess