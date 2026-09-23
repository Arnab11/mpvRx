/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.framecapture

/**
 * A captured frame, decoupled from the Room row.
 *
 * [imageUri] and [imagePath] are two views of the same gallery file: the MediaStore URI on API 29+,
 * an absolute path below that. Only one is populated, and readers should prefer the URI. [videoUri]
 * holds whatever mpv reported for the source — a bare path for local files, a scheme-qualified URI
 * otherwise — with [videoPath] filled in for the local case.
 */
data class FrameCapture(
  val id: Long,
  val imageUri: String?,
  val imagePath: String?,
  val videoUri: String,
  val videoPath: String?,
  val videoTitle: String,
  val positionMs: Long,
  val capturedAt: Long,
) {
  /** Position formatted as `H:MM:SS` (or `M:SS` under an hour) for the grid caption. */
  val formattedPosition: String
    get() = formatPosition(positionMs)
}

private fun formatPosition(positionMs: Long): String {
  val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
  val hours = totalSeconds / 3600L
  val minutes = (totalSeconds % 3600L) / 60L
  val seconds = totalSeconds % 60L
  return if (hours > 0L) {
    String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
  }
}
