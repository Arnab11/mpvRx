/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.content.Context
import android.util.AtomicFile
import app.gyrolet.mpvrx.R
import java.io.File

internal object MpvOsdFont {
  const val FAMILY = "Google Sans Flex"
  private const val FILE_NAME = "mpvrx-google-sans-flex.ttf"

  fun ensureInstalled(context: Context) {
    val fontBytes = context.resources.openRawResource(R.font.gflex_variable).use { it.readBytes() }
    val fontsDirectory = File(context.filesDir, "fonts")
    check(fontsDirectory.isDirectory || fontsDirectory.mkdirs()) {
      "Could not create mpv fonts directory"
    }

    val target = File(fontsDirectory, FILE_NAME)
    val isCurrent =
      target.isFile &&
        target.length() == fontBytes.size.toLong() &&
        runCatching { target.readBytes().contentEquals(fontBytes) }.getOrDefault(false)
    if (isCurrent) return

    val atomicFile = AtomicFile(target)
    val output = atomicFile.startWrite()
    try {
      output.write(fontBytes)
      atomicFile.finishWrite(output)
    } catch (error: Throwable) {
      atomicFile.failWrite(output)
      throw error
    }
  }
}