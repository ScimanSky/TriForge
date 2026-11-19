package com.triforge.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a picked photo into a small square JPEG for the profile.
 *
 * Only the cropped, shrunk copy leaves the phone: the original, with its
 * full resolution and its metadata (place, camera, date), never does.
 */
object AvatarImage {

    const val SIZE_PX = 384
    private const val QUALITY = 85

    suspend fun fromUri(resolver: ContentResolver, uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val decoded = decode(resolver, uri)
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
    }

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's rotation and can shrink while decoding.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val shortSide = min(info.size.width, info.size.height)
                if (shortSide > SIZE_PX * 2) {
                    val scale = SIZE_PX * 2f / shortSide
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE_PX) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("unreadable image")
        }
}
