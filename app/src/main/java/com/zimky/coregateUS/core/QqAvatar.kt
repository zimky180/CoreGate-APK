package com.zimky.coregateUS.core

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Random cached QQ avatar; does not claim to identify the currently signed-in account. */
object QqAvatar {
    suspend fun load(): ImageBitmap? {
        val listing = execute("find '$DIRECTORY' -maxdepth 1 -type f 2>/dev/null") ?: return null
        val candidates = listing.lineSequence().filter {
            it.startsWith("$DIRECTORY/") && !it.removePrefix("$DIRECTORY/").contains('/') &&
                !it.contains('\r') && it.length < 1024
        }.toList().shuffled()
        for (path in candidates.take(20)) {
            val quoted = "'" + path.replace("'", "'\\''") + "'"
            val encoded = execute("n=\$(stat -c %s $quoted) || exit 1; [ \"\$n\" -gt 0 ] && [ \"\$n\" -le 2097152 ] || exit 1; base64 $quoted") ?: continue
            val image = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, info)
                    require(info.outWidth in 1..16384 && info.outHeight in 1..16384)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(info.outWidth, info.outHeight) / inSampleSize > 512) inSampleSize *= 2
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                }.getOrNull()
            }
            if (image != null) return image
        }
        return null
    }

    private suspend fun execute(command: String): String? = suspendCancellableCoroutine { cont ->
        RootShell.execPrivate(command) { code, output, _ ->
            if (cont.isActive) cont.resume(if (code == 0) output else null)
        }
    }

    private const val DIRECTORY = "/storage/emulated/0/Android/data/com.tencent.mobileqq/Tencent/MobileQQ/head/_SSOhd"
}
