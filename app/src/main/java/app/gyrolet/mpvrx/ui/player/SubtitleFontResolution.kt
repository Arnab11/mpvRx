/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import app.gyrolet.mpvrx.domain.fonts.GoogleFontsRepository
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.DEFAULT_SUBTITLE_FONT_FAMILY
import app.gyrolet.mpvrx.preferences.LEGACY_DEFAULT_SUBTITLE_FONT_FAMILY
import app.gyrolet.mpvrx.preferences.SubtitlesPreferences

/**
 * Resolution order for the mpv subtitle font family.
 *
 * 1. Explicit subtitle font chosen in the subtitle font dropdown.
 * 2. The app's own font (a Google font downloaded in Appearance), when active.
 * 3. Bundled Google Sans Flex default.
 *
 * Primary and secondary subtitles share one family: official mpv has no
 * `secondary-sub-font`, secondary inherits `sub-font`. A blank or legacy
 * `sans-serif` stored value is treated as "not set" (one-time migration for
 * installs predating the Google Sans Flex default).
 */
fun resolveSubtitleFontFamily(
  explicitFont: String,
  useSystemAppFont: Boolean,
  appFontFamily: String,
  hasDownloadedAppFont: Boolean,
): String {
  explicitFont.takeUnless { it.isBlank() || it == LEGACY_DEFAULT_SUBTITLE_FONT_FAMILY }
    ?.let { return it }
  if (!useSystemAppFont && appFontFamily.isNotBlank() && hasDownloadedAppFont) {
    return appFontFamily
  }
  return DEFAULT_SUBTITLE_FONT_FAMILY
}

fun resolveSubtitleFontFamily(
  subtitlesPreferences: SubtitlesPreferences,
  appearancePreferences: AppearancePreferences,
  fontsRepository: GoogleFontsRepository,
): String =
  resolveSubtitleFontFamily(
    explicitFont = subtitlesPreferences.font.get(),
    useSystemAppFont = appearancePreferences.useSystemFont.get(),
    appFontFamily = appearancePreferences.googleFontFamily.get(),
    hasDownloadedAppFont = fontsRepository.activeFontFile().isFile,
  )

/**
 * Display text for the "Default" subtitle font row: plain "Default" when it
 * resolves to the bundled font, otherwise "Default (<family>)".
 */
fun defaultSubtitleFontDisplayName(effectiveFamily: String): String =
  if (effectiveFamily == DEFAULT_SUBTITLE_FONT_FAMILY) {
    "Default"
  } else {
    "Default ($effectiveFamily)"
  }
