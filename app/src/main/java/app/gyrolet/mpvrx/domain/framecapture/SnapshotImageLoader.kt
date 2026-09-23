/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.framecapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes snapshot images from either a MediaStore URI or a plain file path.
 *
 * Every read is two-pass: bounds first, then a sampled decode. A frame is a full-resolution video
 * still — five to ten megapixels is routine — so decoding one at native size into a grid cell would
 * blow the heap within a screenful.
 */
object SnapshotImageLoader {

  suspend fun load(
    context: Context,
    imageUri: String?,
    imagePath: String?,
    targetMaxPx: Int,
    force: Boolean = false,
  ): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      // A bounds-only pass always yields a null bitmap and reports the size through the options
      // object instead, so its return value says nothing about whether the read worked — the
      // dimensions do.
      decode(context, imageUri, imagePath, bounds, force)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

      val options =
        BitmapFactory.Options().apply {
          inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetMaxPx)
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      decode(context, imageUri, imagePath, options, force)
    }.getOrNull()
  }

  private fun decode(
    context: Context,
    imageUri: String?,
    imagePath: String?,
    options: BitmapFactory.Options,
    force: Boolean,
  ): Bitmap? {
    if (imageUri != null) {
      val uri = Uri.parse(imageUri)
      val bitmap =
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } }
          .getOrNull()
      if (bitmap != null || !force) return bitmap
    }
    val path = imagePath ?: return null
    if (!File(path).exists()) return null
    return BitmapFactory.decodeFile(path, options)
  }

  /**
   * Largest power-of-two divisor that keeps the longer edge at or above [targetMaxPx]. Matches the
   * rounding used by `calculateThumbnailSampleSize` in the thumbnail package.
   */
  internal fun sampleSizeFor(width: Int, height: Int, targetMaxPx: Int): Int {
    if (width <= targetMaxPx && height <= targetMaxPx) return 1
    var sample = 1
    val maxDimension = maxOf(width, height)
    while (maxDimension / (sample * 2) >= targetMaxPx) sample *= 2
    return sample
  }

  /** Grid thumbnails stay small; §4.3 caps them at 256 px. */
  const val THUMBNAIL_MAX_PX = 256

  /** The detail viewer wants detail but not the native 4K frame. */
  const val VIEWER_MAX_PX = 2048
}
