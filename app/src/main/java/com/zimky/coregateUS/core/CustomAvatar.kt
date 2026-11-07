package com.zimky.coregateUS.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copies a selected image into private storage so it survives picker permission expiry. */
object CustomAvatar {
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "profile-avatar.png"))

    suspend fun load(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            file(context).openRead().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }.getOrNull()
    }

    suspend fun save(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 512) {
                    decoder.setTargetSize((info.size.width.toLong() * 512 / longest).toInt().coerceAtLeast(1),
                        (info.size.height.toLong() * 512 / longest).toInt().coerceAtLeast(1))
                }
            }
            val target = file(context)
            val output = target.startWrite()
            try {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                target.finishWrite(output)
            } catch (error: Exception) {
                target.failWrite(output)
                throw error
            }
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
