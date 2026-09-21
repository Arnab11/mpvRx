/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.theme

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.gyrolet.mpvrx.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Wallpapers that are drawn in code, so no image assets are needed.
 *
 * A preset is stored in preferences as `preset:<id>` (see [WallpaperPreset.uri]) and turned into a
 * bitmap on demand by [createWallpaperPresetBitmap]. The same [drawWallpaperPreset] function is used
 * for the small picker cards, so the card always matches the real wallpaper.
 */
enum class WallpaperPreset(
  val id: String,
  @StringRes val labelRes: Int,
) {
  Aurora("aurora", R.string.wallpaper_preset_aurora),
  Sunset("sunset", R.string.wallpaper_preset_sunset),
  Ocean("ocean", R.string.wallpaper_preset_ocean),
  Midnight("midnight", R.string.wallpaper_preset_midnight),
  Mist("mist", R.string.wallpaper_preset_mist),
  Blossom("blossom", R.string.wallpaper_preset_blossom),
  ;

  val uri: String get() = PREFIX + id

  companion object {
    const val PREFIX = "preset:"

    fun isPresetUri(uri: String): Boolean = uri.startsWith(PREFIX, ignoreCase = true)

    fun fromUri(uri: String): WallpaperPreset? = entries.firstOrNull { uri.equals(it.uri, ignoreCase = true) }
  }
}

private const val PRESET_WIDTH_PX = 1080
private const val PRESET_HEIGHT_PX = 2340

fun createWallpaperPresetBitmap(
  preset: WallpaperPreset,
  width: Int = PRESET_WIDTH_PX,
  height: Int = PRESET_HEIGHT_PX,
): Bitmap {
  val image = ImageBitmap(width, height)
  CanvasDrawScope().draw(
    density = Density(1f),
    layoutDirection = LayoutDirection.Ltr,
    canvas = ComposeCanvas(image),
    size = Size(width.toFloat(), height.toFloat()),
  ) {
    drawWallpaperPreset(preset)
  }
  return image.asAndroidBitmap()
}

fun DrawScope.drawWallpaperPreset(preset: WallpaperPreset) {
  when (preset) {
    WallpaperPreset.Aurora -> drawAurora()
    WallpaperPreset.Sunset -> drawSunset()
    WallpaperPreset.Ocean -> drawOcean()
    WallpaperPreset.Midnight -> drawMidnight()
    WallpaperPreset.Mist -> drawMist()
    WallpaperPreset.Blossom -> drawBlossom()
  }
}

private fun DrawScope.glow(
  color: Color,
  cx: Float,
  cy: Float,
  radius: Float,
  alpha: Float,
) {
  drawCircle(
    brush =
      Brush.radialGradient(
        colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
        center = Offset(cx, cy),
        radius = radius,
      ),
    radius = radius,
    center = Offset(cx, cy),
  )
}

/** Filled silhouette with a soft rolling ridge line. */
private fun DrawScope.ridge(
  baseY: Float,
  amplitude: Float,
  freqA: Float,
  phaseA: Float,
  freqB: Float,
  phaseB: Float,
  brush: Brush,
) {
  val path = Path()
  val steps = 60
  path.moveTo(0f, size.height)
  for (i in 0..steps) {
    val t = i / steps.toFloat()
    val wave =
      0.65f * sin((t * freqA * 2f * PI + phaseA).toFloat()) +
        0.35f * sin((t * freqB * 2f * PI + phaseB).toFloat())
    path.lineTo(t * size.width, baseY - amplitude * wave)
  }
  path.lineTo(size.width, size.height)
  path.close()
  drawPath(path, brush)
}

private fun DrawScope.drawAurora() {
  val w = size.width
  val h = size.height
  drawRect(
    Brush.verticalGradient(listOf(Color(0xFF050B24), Color(0xFF0A1A44), Color(0xFF0B3A4A))),
  )
  glow(Color(0xFF2AF5B0), w * 0.28f, h * 0.30f, w * 0.85f, 0.55f)
  glow(Color(0xFF8A5CFF), w * 0.80f, h * 0.46f, w * 0.75f, 0.50f)
  glow(Color(0xFF29B6F6), w * 0.35f, h * 0.72f, w * 0.80f, 0.35f)
  ridge(
    baseY = h * 0.90f,
    amplitude = h * 0.03f,
    freqA = 1.2f,
    phaseA = 0.4f,
    freqB = 2.6f,
    phaseB = 1.1f,
    brush = Brush.verticalGradient(listOf(Color(0xFF07161F), Color(0xFF030A10)), startY = h * 0.85f, endY = h),
  )
}

private fun DrawScope.drawSunset() {
  val w = size.width
  val h = size.height
  val horizon = h * 0.62f
  drawRect(
    Brush.verticalGradient(
      0f to Color(0xFF1B1140),
      0.35f to Color(0xFF6A1B9A),
      0.55f to Color(0xFFE8546B),
      0.68f to Color(0xFFFFB35C),
      0.68f to Color(0xFF2A1240),
      1f to Color(0xFF120824),
    ),
  )
  glow(Color(0xFFFFE29A), w * 0.5f, horizon, w * 0.75f, 0.75f)
  drawCircle(Color(0xFFFFF3C4), radius = w * 0.13f, center = Offset(w * 0.5f, horizon - w * 0.02f))
  ridge(
    baseY = horizon + h * 0.03f,
    amplitude = h * 0.04f,
    freqA = 1.5f,
    phaseA = 0.2f,
    freqB = 3.4f,
    phaseB = 2.0f,
    brush = Brush.verticalGradient(listOf(Color(0xFF3A1650), Color(0xFF1A0B2C)), startY = horizon, endY = h),
  )
  ridge(
    baseY = h * 0.80f,
    amplitude = h * 0.05f,
    freqA = 1.1f,
    phaseA = 2.4f,
    freqB = 2.3f,
    phaseB = 0.6f,
    brush = Brush.verticalGradient(listOf(Color(0xFF241038), Color(0xFF0B0416)), startY = h * 0.75f, endY = h),
  )
}

private fun DrawScope.drawOcean() {
  val w = size.width
  val h = size.height
  drawRect(
    Brush.verticalGradient(listOf(Color(0xFF03122E), Color(0xFF0A4D8C), Color(0xFF1AA6B7))),
  )
  glow(Color(0xFF7FE7FF), w * 0.75f, h * 0.18f, w * 0.7f, 0.30f)
  val layers = 5
  for (i in 0 until layers) {
    val t = i / (layers - 1).toFloat()
    ridge(
      baseY = h * (0.50f + 0.11f * i),
      amplitude = h * (0.020f + 0.006f * i),
      freqA = 1.6f + 0.5f * i,
      phaseA = i * 1.3f,
      freqB = 3.1f + 0.4f * i,
      phaseB = i * 0.7f + 1f,
      brush =
        Brush.verticalGradient(
          listOf(
            Color(0xFF0E7FB8).copy(alpha = 0.25f + 0.10f * t),
            Color(0xFF031B3F).copy(alpha = 0.60f + 0.10f * t),
          ),
          startY = h * (0.45f + 0.11f * i),
          endY = h,
        ),
    )
  }
}

private fun DrawScope.drawMidnight() {
  val w = size.width
  val h = size.height
  drawRect(
    Brush.verticalGradient(listOf(Color(0xFF02030A), Color(0xFF0B1030), Color(0xFF1B1F4B))),
  )
  val unit = w / 1080f
  val random = Random(7)
  repeat(170) {
    val x = random.nextFloat() * w
    val y = random.nextFloat() * h * 0.85f
    val r = (0.7f + random.nextFloat() * 2.6f) * unit
    val a = 0.35f + random.nextFloat() * 0.65f
    drawCircle(Color.White.copy(alpha = a), radius = r, center = Offset(x, y))
  }
  glow(Color(0xFFFFF6D5), w * 0.72f, h * 0.19f, w * 0.45f, 0.35f)
  drawCircle(Color(0xFFFFF6E0), radius = w * 0.06f, center = Offset(w * 0.72f, h * 0.19f))
  drawCircle(Color(0xFF0B1030).copy(alpha = 0.55f), radius = w * 0.05f, center = Offset(w * 0.745f, h * 0.185f))
  ridge(
    baseY = h * 0.93f,
    amplitude = h * 0.025f,
    freqA = 1.0f,
    phaseA = 1.0f,
    freqB = 2.2f,
    phaseB = 0.3f,
    brush = Brush.verticalGradient(listOf(Color(0xFF0A0D24), Color(0xFF02030A)), startY = h * 0.9f, endY = h),
  )
}

private fun DrawScope.drawMist() {
  val h = size.height
  drawRect(
    Brush.verticalGradient(listOf(Color(0xFF0C2A2A), Color(0xFF1E5C55), Color(0xFF8FC9B4))),
  )
  val layers = listOf(
    Triple(0.46f, Color(0xFF3F8F7C), 0.55f),
    Triple(0.58f, Color(0xFF2B6F63), 0.75f),
    Triple(0.70f, Color(0xFF1B4F49), 0.90f),
    Triple(0.83f, Color(0xFF0E322F), 1.00f),
  )
  layers.forEachIndexed { index, (y, color, alpha) ->
    ridge(
      baseY = h * y,
      amplitude = h * (0.05f - 0.005f * index),
      freqA = 1.1f + 0.35f * index,
      phaseA = index * 1.7f,
      freqB = 2.4f + 0.5f * index,
      phaseB = index * 0.9f + 0.5f,
      brush =
        Brush.verticalGradient(
          listOf(color.copy(alpha = alpha), Color(0xFF071B1A).copy(alpha = alpha)),
          startY = h * (y - 0.06f),
          endY = h,
        ),
    )
  }
}

private fun DrawScope.drawBlossom() {
  val w = size.width
  val h = size.height
  drawRect(
    Brush.verticalGradient(listOf(Color(0xFFFFE8EF), Color(0xFFFFD9E2), Color(0xFFFFEEDC))),
  )
  glow(Color(0xFFFF8FB1), w * 0.15f, h * 0.18f, w * 0.85f, 0.65f)
  glow(Color(0xFFFFB98A), w * 0.90f, h * 0.48f, w * 0.80f, 0.60f)
  glow(Color(0xFFB79CFF), w * 0.25f, h * 0.86f, w * 0.90f, 0.55f)
}
