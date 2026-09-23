/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One captured video frame. The image itself lives in the system gallery — this row only records
 * where to find it and which moment of which video it came from.
 *
 * [imageUri] is the MediaStore URI on API 29+; [imagePath] is an absolute file path used below that,
 * where screenshots go to the public Pictures directory instead. Both are written when available and
 * readers prefer the URI.
 */
@Entity(
  tableName = "frame_captures",
  indices = [Index(value = ["capturedAt"])],
)
data class FrameCaptureEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val imageUri: String?,
  val imagePath: String?,
  val videoUri: String,
  val videoPath: String?,
  val videoTitle: String,
  val positionMs: Long,
  val capturedAt: Long = System.currentTimeMillis(),
)
