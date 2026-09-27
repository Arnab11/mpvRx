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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Decoded snapshot thumbnails keyed by capture id, shared by every screen that shows snapshot cells.
 *
 * Extracted rather than duplicated because the library page and a folder page both need it, and the
 * interesting part is not the map but the in-flight de-duplication: a grid can compose the same item
 * several times per frame, and decoding a multi-megapixel frame per recomposition would sink the list.
 *
 * [scope] is the owner's scope, so the work dies with the screen that asked for it.
 */
class SnapshotThumbnailStore(
  private val context: Context,
  private val scope: CoroutineScope,
  private val targetMaxPx: Int = SnapshotImageLoader.THUMBNAIL_MAX_PX,
) {

  private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
  val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

  private val inFlight = mutableSetOf<Long>()

  fun load(capture: FrameCapture) {
    if (_thumbnails.value.containsKey(capture.id) || !inFlight.add(capture.id)) return
    scope.launch {
      try {
        val bitmap =
          withContext(Dispatchers.IO) {
            SnapshotImageLoader.load(
              context = context,
              imageUri = capture.imageUri,
              imagePath = capture.imagePath,
              targetMaxPx = targetMaxPx,
            )
          }
        if (bitmap != null) _thumbnails.value = _thumbnails.value + (capture.id to bitmap)
      } finally {
        inFlight.remove(capture.id)
      }
    }
  }

  /** Drops bitmaps for records that no longer exist, so a recycled id cannot show a stale image. */
  fun forget(ids: Collection<Long>) {
    if (ids.isEmpty()) return
    _thumbnails.value = _thumbnails.value - ids.toSet()
  }
}
