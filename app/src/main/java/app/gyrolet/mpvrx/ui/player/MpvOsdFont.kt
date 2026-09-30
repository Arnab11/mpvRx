/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.fonts.GoogleFontsRepository
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import java.io.File

internal object MpvOsdFont {
  const val FAMILY = "Google Sans Flex"
  private const val FILE_NAME = "mpvrx-google-sans-flex.ttf"

  /**
   * Bridge file that mirrors the app's downloaded Google font into mpv's
   * `fonts/` directory so `sub-font` can resolve it via fontconfig.
   * Only this file is ever managed here; bundled and user fonts are untouched.
   */
  private const val APP_FONT_BRIDGE_FILE_NAME = "mpvrx-app-font.ttf"
  private const val TAG = "MpvOsdFont"

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

  /**
   * Mirrors the app's downloaded Google font (if any) into mpv's fonts
   * directory, or removes a stale mirror. Runs on the caller's thread —
   * call from a background thread or startup asset preparation.
   */
  fun syncDownloadedAppFont(
    context: Context,
    appearancePreferences: AppearancePreferences,
    fontsRepository: GoogleFontsRepository,
  ) {
    val bridge = File(File(context.filesDir, "fonts"), APP_FONT_BRIDGE_FILE_NAME)
    val active =
      if (!appearancePreferences.useSystemFont.get() &&
        appearancePreferences.googleFontFamily.get().isNotBlank()
      ) {
        fontsRepository.activeFontFile().takeIf(File::isFile)
      } else {
        null
      }
    if (active == null) {
      if (bridge.isFile && !bridge.delete()) {
        Log.w(TAG, "Could not remove stale app font bridge")
      }
      return
    }
    val isCurrent =
      bridge.isFile &&
        bridge.length() == active.length() &&
        runCatching { bridge.readBytes().contentEquals(active.readBytes()) }.getOrDefault(false)
    if (isCurrent) return
    runCatching {
      bridge.parentFile?.mkdirs()
      active.copyTo(bridge, overwrite = true)
    }.onFailure { Log.w(TAG, "Could not mirror app font for mpv", it) }
  }
}