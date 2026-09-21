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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.gyrolet.mpvrx.R
import kotlin.math.PI
import kotlin.math.cos
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

private fun waveAt(
  t: Float,
  freqA: Float,
  phaseA: Float,
  freqB: Float,
  phaseB: Float,
): Float =
  0.65f * sin((t * freqA * 2f * PI + phaseA).toFloat()) +
    0.35f * sin((t * freqB * 2f * PI + phaseB).toFloat())

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
    val wave = waveAt(t, freqA, phaseA, freqB, phaseB)
    path.lineTo(t * size.width, baseY - amplitude * wave)
  }
  path.lineTo(size.width, size.height)
  path.close()
  drawPath(path, brush)
}

/** Small round stars, thinning out towards [maxY] so they fade into the horizon. */
private fun DrawScope.stars(
  seed: Int,
  count: Int,
  maxY: Float,
  minAlpha: Float = 0.3f,
  tint: Color = Color.White,
) {
  val unit = size.width / 1080f
  val random = Random(seed)
  repeat(count) {
    val x = random.nextFloat() * size.width
    val y = random.nextFloat() * maxY
    val r = (0.7f + random.nextFloat() * 2.4f) * unit
    val a = minAlpha + random.nextFloat() * (1f - minAlpha)
    val fade = 1f - (y / maxY) * 0.6f
    drawCircle(tint.copy(alpha = (a * fade).coerceIn(0f, 1f)), radius = r, center = Offset(x, y))
  }
}

/** A handful of larger stars with a soft halo and a little cross-shaped glint. */
private fun DrawScope.brightStars(
  seed: Int,
  count: Int,
  maxY: Float,
) {
  val unit = size.width / 1080f
  val random = Random(seed)
  repeat(count) {
    val x = random.nextFloat() * size.width
    val y = random.nextFloat() * maxY
    val r = (2.4f + random.nextFloat() * 1.8f) * unit
    val arm = r * 4.5f
    val glint = Color.White.copy(alpha = 0.55f)
    glow(Color(0xFFCFE8FF), x, y, r * 7f, 0.30f)
    drawCircle(Color.White, radius = r, center = Offset(x, y))
    drawLine(glint, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
    drawLine(glint, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
  }
}

/** Three stacked triangles, base at [baseY], used as tree silhouettes on the ridges. */
private fun DrawScope.pine(
  cx: Float,
  baseY: Float,
  height: Float,
  color: Color,
) {
  for (tier in 0 until 3) {
    val top = baseY - height + height * 0.26f * tier
    val bottom = top + height * 0.46f
    val halfWidth = height * (0.11f + 0.04f * tier)
    val path = Path()
    path.moveTo(cx, top)
    path.lineTo(cx + halfWidth, bottom)
    path.lineTo(cx - halfWidth, bottom)
    path.close()
    drawPath(path, color)
  }
}

/** Scatters [pine]s along the same wave a [ridge] with these parameters follows. */
private fun DrawScope.pinesAlongRidge(
  baseY: Float,
  amplitude: Float,
  freqA: Float,
  phaseA: Float,
  freqB: Float,
  phaseB: Float,
  seed: Int,
  count: Int,
  minHeight: Float,
  maxHeight: Float,
  color: Color,
) {
  val random = Random(seed)
  repeat(count) {
    val t = random.nextFloat()
    val height = minHeight + random.nextFloat() * (maxHeight - minHeight)
    val ridgeY = baseY - amplitude * waveAt(t, freqA, phaseA, freqB, phaseB)
    pine(t * size.width, ridgeY + height * 0.06f, height, color)
  }
}

/** Thin vertical shafts of light standing on [baseY], brighter towards the bottom. */
private fun DrawScope.verticalRays(
  baseY: Float,
  seed: Int,
  count: Int,
  color: Color,
  maxLength: Float,
  alpha: Float,
  fromX: Float,
  toX: Float,
) {
  val unit = size.width / 1080f
  val random = Random(seed)
  repeat(count) {
    val x = fromX + random.nextFloat() * (toX - fromX)
    val rayWidth = (4f + random.nextFloat() * 18f) * unit
    val length = maxLength * (0.35f + 0.65f * random.nextFloat())
    val rayAlpha = alpha * (0.35f + 0.65f * random.nextFloat())
    drawRect(
      brush =
        Brush.verticalGradient(
          listOf(Color.Transparent, color.copy(alpha = rayAlpha)),
          startY = baseY - length,
          endY = baseY,
        ),
      topLeft = Offset(x, baseY - length),
      size = Size(rayWidth, length),
    )
  }
}

/** One aurora "curtain": a wavy bright lower edge fading upwards. */
private fun DrawScope.auroraCurtain(
  baseY: Float,
  amplitude: Float,
  height: Float,
  freqA: Float,
  phaseA: Float,
  freqB: Float,
  phaseB: Float,
  color: Color,
  alpha: Float,
) {
  val steps = 48
  val path = Path()
  for (i in 0..steps) {
    val t = i / steps.toFloat()
    val y = baseY - amplitude * waveAt(t, freqA, phaseA, freqB, phaseB)
    if (i == 0) path.moveTo(0f, y) else path.lineTo(t * size.width, y)
  }
  for (i in steps downTo 0) {
    val t = i / steps.toFloat()
    val lower = baseY - amplitude * waveAt(t, freqA, phaseA, freqB, phaseB)
    val reach = height * (0.55f + 0.45f * sin(t * 5f + phaseA))
    path.lineTo(t * size.width, lower - reach)
  }
  path.close()
  drawPath(
    path,
    Brush.verticalGradient(
      0f to Color.Transparent,
      0.55f to color.copy(alpha = alpha * 0.5f),
      1f to color.copy(alpha = alpha),
      startY = baseY - amplitude - height,
      endY = baseY + amplitude,
    ),
  )
}

/** Soft cones of light fanning out from ([cx], [cy]); [angles] are degrees from straight down. */
private fun DrawScope.lightBeams(
  cx: Float,
  cy: Float,
  length: Float,
  angles: List<Float>,
  spread: Float,
  color: Color,
  alpha: Float,
) {
  angles.forEach { degrees ->
    val radians = degrees * (PI / 180.0)
    val dirX = sin(radians).toFloat()
    val dirY = cos(radians).toFloat()
    val endX = cx + dirX * length
    val endY = cy + dirY * length
    val path = Path()
    path.moveTo(cx, cy)
    path.lineTo(endX + dirY * spread, endY - dirX * spread)
    path.lineTo(endX - dirY * spread, endY + dirX * spread)
    path.close()
    drawPath(
      path,
      Brush.linearGradient(
        listOf(color.copy(alpha = alpha), Color.Transparent),
        start = Offset(cx, cy),
        end = Offset(endX, endY),
      ),
    )
  }
}

/** Horizontal band of haze that fades in and out, sitting between two ridges. */
private fun DrawScope.fogBand(
  y: Float,
  thickness: Float,
  alpha: Float,
) {
  drawRect(
    brush =
      Brush.verticalGradient(
        listOf(Color.Transparent, Color(0xFFD5F2E4).copy(alpha = alpha), Color.Transparent),
        startY = y,
        endY = y + thickness,
      ),
    topLeft = Offset(0f, y),
    size = Size(size.width, thickness),
  )
}

private fun DrawScope.drawAurora() {
  val w = size.width
  val h = size.height
  drawRect(
    Brush.verticalGradient(listOf(Color(0xFF030716), Color(0xFF081842), Color(0xFF0B3A4A))),
  )
  stars(seed = 11, count = 150, maxY = h * 0.66f)
  brightStars(seed = 3, count = 7, maxY = h * 0.40f)
  glow(Color(0xFF2AF5B0), w * 0.28f, h * 0.30f, w * 0.85f, 0.45f)
  glow(Color(0xFF8A5CFF), w * 0.80f, h * 0.46f, w * 0.75f, 0.42f)
  glow(Color(0xFF29B6F6), w * 0.35f, h * 0.72f, w * 0.80f, 0.30f)
  // Shafts of light standing under the curtains.
  verticalRays(
    baseY = h * 0.50f,
    seed = 21,
    count = 30,
    color = Color(0xFF2AF5B0),
    maxLength = h * 0.30f,
    alpha = 0.30f,
    fromX = 0f,
    toX = w * 0.75f,
  )
  verticalRays(
    baseY = h * 0.58f,
    seed = 22,
    count = 22,
    color = Color(0xFF9B6BFF),
    maxLength = h * 0.24f,
    alpha = 0.26f,
    fromX = w * 0.25f,
    toX = w,
  )
  auroraCurtain(
    baseY = h * 0.48f,
    amplitude = h * 0.035f,
    height = h * 0.30f,
    freqA = 0.9f,
    phaseA = 0.6f,
    freqB = 2.1f,
    phaseB = 1.7f,
    color = Color(0xFF2AF5B0),
    alpha = 0.62f,
  )
  auroraCurtain(
    baseY = h * 0.57f,
    amplitude = h * 0.030f,
    height = h * 0.22f,
    freqA = 1.3f,
    phaseA = 2.2f,
    freqB = 2.8f,
    phaseB = 0.4f,
    color = Color(0xFF9B6BFF),
    alpha = 0.50f,
  )
  auroraCurtain(
    baseY = h * 0.68f,
    amplitude = h * 0.028f,
    height = h * 0.16f,
    freqA = 1.0f,
    phaseA = 4.0f,
    freqB = 3.2f,
    phaseB = 2.5f,
    color = Color(0xFF29D6F6),
    alpha = 0.34f,
  )
  // Far ridge with a light treeline.
  ridge(
    baseY = h * 0.84f,
    amplitude = h * 0.04f,
    freqA = 0.8f,
    phaseA = 2.0f,
    freqB = 2.0f,
    phaseB = 0.5f,
    brush = Brush.verticalGradient(listOf(Color(0xFF0D2B36), Color(0xFF07161F)), startY = h * 0.80f, endY = h),
  )
  pinesAlongRidge(
    baseY = h * 0.84f,
    amplitude = h * 0.04f,
    freqA = 0.8f,
    phaseA = 2.0f,
    freqB = 2.0f,
    phaseB = 0.5f,
    seed = 31,
    count = 16,
    minHeight = h * 0.018f,
    maxHeight = h * 0.030f,
    color = Color(0xFF0A222C),
  )
  // Near ridge with a dark treeline.
  ridge(
    baseY = h * 0.90f,
    amplitude = h * 0.03f,
    freqA = 1.2f,
    phaseA = 0.4f,
    freqB = 2.6f,
    phaseB = 1.1f,
    brush = Brush.verticalGradient(listOf(Color(0xFF07161F), Color(0xFF030A10)), startY = h * 0.85f, endY = h),
  )
  pinesAlongRidge(
    baseY = h * 0.90f,
    amplitude = h * 0.03f,
    freqA = 1.2f,
    phaseA = 0.4f,
    freqB = 2.6f,
    phaseB = 1.1f,
    seed = 32,
    count = 14,
    minHeight = h * 0.030f,
    maxHeight = h * 0.055f,
    color = Color(0xFF030A10),
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

private class MistLayer(
  val y: Float,
  val color: Color,
  val alpha: Float,
)

private fun DrawScope.drawMist() {
  val w = size.width
  val h = size.height
  drawRect(
    Brush.verticalGradient(
      0f to Color(0xFF061A1E),
      0.30f to Color(0xFF12423F),
      0.52f to Color(0xFF3E8C7C),
      0.74f to Color(0xFF9ED4BE),
      1f to Color(0xFFB8E6D2),
    ),
  )
  stars(seed = 5, count = 90, maxY = h * 0.34f, minAlpha = 0.2f)

  // Pale sun low in the haze, with light fanning out of it.
  val sunX = w * 0.72f
  val sunY = h * 0.31f
  glow(Color(0xFFEFFFF6), sunX, sunY, w * 0.62f, 0.50f)
  lightBeams(
    cx = sunX,
    cy = sunY,
    length = h * 0.62f,
    angles = listOf(-38f, -24f, -12f, 0f, 11f, 23f, 36f),
    spread = w * 0.06f,
    color = Color(0xFFEFFFF6),
    alpha = 0.16f,
  )
  drawCircle(Color(0xFFF4FFF8).copy(alpha = 0.90f), radius = w * 0.05f, center = Offset(sunX, sunY))

  val layers =
    listOf(
      MistLayer(0.42f, Color(0xFF7FBFAA), 0.55f),
      MistLayer(0.50f, Color(0xFF5AA090), 0.65f),
      MistLayer(0.58f, Color(0xFF3F8677), 0.78f),
      MistLayer(0.67f, Color(0xFF2B6A5F), 0.88f),
      MistLayer(0.76f, Color(0xFF1B4F49), 0.95f),
      MistLayer(0.86f, Color(0xFF0E322F), 1.00f),
    )
  layers.forEachIndexed { index, layer ->
    val amplitude = h * (0.05f - 0.005f * index)
    val freqA = 1.1f + 0.35f * index
    val phaseA = index * 1.7f
    val freqB = 2.4f + 0.5f * index
    val phaseB = index * 0.9f + 0.5f
    ridge(
      baseY = h * layer.y,
      amplitude = amplitude,
      freqA = freqA,
      phaseA = phaseA,
      freqB = freqB,
      phaseB = phaseB,
      brush =
        Brush.verticalGradient(
          listOf(layer.color.copy(alpha = layer.alpha), Color(0xFF071B1A).copy(alpha = layer.alpha)),
          startY = h * (layer.y - 0.06f),
          endY = h,
        ),
    )
    // Trees only on the three nearest ridges, hazier the further back they are.
    if (index >= layers.size - 3) {
      pinesAlongRidge(
        baseY = h * layer.y,
        amplitude = amplitude,
        freqA = freqA,
        phaseA = phaseA,
        freqB = freqB,
        phaseB = phaseB,
        seed = 40 + index,
        count = 10 + 3 * (index - (layers.size - 3)),
        minHeight = h * (0.012f + 0.004f * (index - (layers.size - 3))),
        maxHeight = h * (0.022f + 0.008f * (index - (layers.size - 3))),
        color = lerp(layer.color, Color(0xFF071B1A), 0.55f).copy(alpha = layer.alpha),
      )
    }
    // Haze pooling between this ridge and the next one.
    if (index < layers.lastIndex) {
      fogBand(y = h * layer.y + h * 0.02f, thickness = h * 0.11f, alpha = 0.20f + 0.03f * index)
    }
  }
}

private fun DrawScope.drawBlossom() {
  val w = size.width
  val h = size.height
  val unit = w / 1080f
  drawRect(
    Brush.verticalGradient(listOf(Color(0xFFFFE8EF), Color(0xFFFFD9E2), Color(0xFFFFEEDC))),
  )
  glow(Color(0xFFFF8FB1), w * 0.15f, h * 0.18f, w * 0.85f, 0.65f)
  glow(Color(0xFFFFB98A), w * 0.90f, h * 0.48f, w * 0.80f, 0.60f)
  glow(Color(0xFFB79CFF), w * 0.25f, h * 0.86f, w * 0.90f, 0.55f)

  // Out-of-focus light spots.
  val bokeh = Random(9)
  repeat(9) {
    val color = if (bokeh.nextBoolean()) Color.White else Color(0xFFFFB3C7)
    glow(
      color,
      bokeh.nextFloat() * w,
      h * (0.10f + 0.85f * bokeh.nextFloat()),
      (36f + bokeh.nextFloat() * 74f) * unit,
      0.10f + bokeh.nextFloat() * 0.10f,
    )
  }

  drawSakuraBranch()
  drawFallingPetals()
}

private fun bezierPoint(
  p0: Offset,
  p1: Offset,
  p2: Offset,
  p3: Offset,
  t: Float,
): Offset {
  val u = 1f - t
  val a = u * u * u
  val b = 3f * u * u * t
  val c = 3f * u * t * t
  val d = t * t * t
  return Offset(
    a * p0.x + b * p1.x + c * p2.x + d * p3.x,
    a * p0.y + b * p1.y + c * p2.y + d * p3.y,
  )
}

private fun quadPoint(
  p0: Offset,
  control: Offset,
  p1: Offset,
  t: Float,
): Offset {
  val u = 1f - t
  return Offset(
    u * u * p0.x + 2f * u * t * control.x + t * t * p1.x,
    u * u * p0.y + 2f * u * t * control.y + t * t * p1.y,
  )
}

/** Draws a curve as short round-capped segments whose width tapers from [startWidth] to [endWidth]. */
private fun DrawScope.drawTaperedCurve(
  color: Color,
  startWidth: Float,
  endWidth: Float,
  steps: Int = 22,
  point: (Float) -> Offset,
) {
  var previous = point(0f)
  for (i in 1..steps) {
    val s = i / steps.toFloat()
    val next = point(s)
    drawLine(color, previous, next, strokeWidth = startWidth + (endWidth - startWidth) * s, cap = StrokeCap.Round)
    previous = next
  }
}

/** A petal pointing up from ([cx], [cy]) with a small notch in its tip. */
private fun petalPath(
  cx: Float,
  cy: Float,
  r: Float,
): Path {
  val path = Path()
  path.moveTo(cx, cy)
  path.cubicTo(cx - r * 0.85f, cy - r * 0.30f, cx - r * 0.60f, cy - r * 1.05f, cx - r * 0.10f, cy - r * 0.98f)
  path.lineTo(cx, cy - r * 0.86f)
  path.lineTo(cx + r * 0.10f, cy - r * 0.98f)
  path.cubicTo(cx + r * 0.60f, cy - r * 1.05f, cx + r * 0.85f, cy - r * 0.30f, cx, cy)
  path.close()
  return path
}

private fun DrawScope.sakuraFlower(
  cx: Float,
  cy: Float,
  r: Float,
  rotation: Float,
) {
  val center = Offset(cx, cy)
  glow(Color(0xFFFF8FB1), cx, cy, r * 2.2f, 0.18f)
  val petalBrush =
    Brush.radialGradient(
      colors = listOf(Color(0xFFFF6F9C), Color(0xFFFFB3CB), Color(0xFFFFEAF1)),
      center = center,
      radius = r * 1.05f,
    )
  for (i in 0 until 5) {
    rotate(rotation + i * 72f, center) {
      val petal = petalPath(cx, cy, r)
      drawPath(petal, petalBrush)
      drawPath(petal, Color(0xFFFF8FB1).copy(alpha = 0.55f), style = Stroke(width = r * 0.03f))
    }
  }
  for (i in 0 until 6) {
    val angle = (rotation + i * 60f + 20f) * (PI / 180.0)
    val end = Offset(cx + (sin(angle) * r * 0.42f).toFloat(), cy - (cos(angle) * r * 0.42f).toFloat())
    drawLine(Color(0xFFE0457B).copy(alpha = 0.9f), center, end, strokeWidth = r * 0.035f)
    drawCircle(Color(0xFFFFD27A), radius = r * 0.06f, center = end)
  }
  drawCircle(Color(0xFFD6336C).copy(alpha = 0.85f), radius = r * 0.10f, center = center)
}

private fun DrawScope.sakuraBud(
  cx: Float,
  cy: Float,
  r: Float,
) {
  drawCircle(Color(0xFFFF6F9C), radius = r * 0.55f, center = Offset(cx, cy))
  drawCircle(Color(0xFFFFB3CB), radius = r * 0.32f, center = Offset(cx - r * 0.12f, cy - r * 0.14f))
}

/** Sakura branch reaching in from the top-right corner, with blossoms and a few buds. */
private fun DrawScope.drawSakuraBranch() {
  val w = size.width
  val h = size.height
  val unit = w / 1080f
  val bark = Color(0xFF5B3445)
  val barkLight = Color(0xFF7D4B5F).copy(alpha = 0.55f)

  val p0 = Offset(w * 1.06f, h * 0.030f)
  val p1 = Offset(w * 0.88f, h * 0.015f)
  val p2 = Offset(w * 0.66f, h * 0.090f)
  val p3 = Offset(w * 0.30f, h * 0.112f)

  drawTaperedCurve(bark, 16f * unit, 6f * unit) { bezierPoint(p0, p1, p2, p3, it) }
  drawTaperedCurve(barkLight, 5f * unit, 2f * unit) { bezierPoint(p0, p1, p2, p3, it) + Offset(-2f * unit, -3f * unit) }

  // Twigs: where they leave the main branch (0..1), then a control point and an end point.
  val twigs =
    listOf(
      Triple(0.12f, Offset(0.97f * w, 0.075f * h), Offset(0.90f * w, 0.115f * h)),
      Triple(0.28f, Offset(0.90f * w, 0.105f * h), Offset(0.82f * w, 0.175f * h)),
      Triple(0.52f, Offset(0.71f * w, 0.150f * h), Offset(0.62f * w, 0.205f * h)),
      Triple(0.76f, Offset(0.56f * w, 0.140f * h), Offset(0.46f * w, 0.190f * h)),
      Triple(0.92f, Offset(0.36f * w, 0.100f * h), Offset(0.24f * w, 0.135f * h)),
    )
  val baseRadius = w * 0.036f
  val flowers = ArrayList<Triple<Offset, Float, Float>>() // centre, radius factor, rotation
  val buds = ArrayList<Pair<Offset, Float>>() // centre, radius factor

  twigs.forEachIndexed { index, (t, control, end) ->
    val start = bezierPoint(p0, p1, p2, p3, t)
    drawTaperedCurve(bark, 8f * unit, 3f * unit) { quadPoint(start, control, end, it) }
    flowers.add(Triple(end, 1.0f + 0.12f * (index % 2), index * 37f))
    flowers.add(Triple(quadPoint(start, control, end, 0.55f), 0.70f + 0.08f * (index % 3), index * 53f + 20f))
    buds.add(quadPoint(start, control, end, 0.80f) + Offset(10f * unit, 16f * unit) to 0.55f)
  }
  listOf(0.10f to 1.10f, 0.36f to 0.90f, 0.62f to 1.20f, 0.86f to 0.95f).forEachIndexed { index, (t, factor) ->
    val onBranch = bezierPoint(p0, p1, p2, p3, t)
    flowers.add(Triple(onBranch + Offset(0f, -6f * unit), factor, index * 71f + 9f))
  }

  buds.forEach { (center, factor) -> sakuraBud(center.x, center.y, baseRadius * factor) }
  flowers.forEach { (center, factor, rotation) -> sakuraFlower(center.x, center.y, baseRadius * factor, rotation) }
}

/** Petals drifting down across the whole wallpaper, denser near the top where they fall from. */
private fun DrawScope.drawFallingPetals() {
  val w = size.width
  val h = size.height
  val unit = w / 1080f
  val random = Random(21)
  val palette =
    listOf(Color(0xFFFFB3C7), Color(0xFFFFC7D6), Color(0xFFFF9DB8), Color(0xFFFFDCE6), Color(0xFFFFFFFF))
  repeat(48) {
    val depth = random.nextFloat()
    val fall = random.nextFloat()
    val x = random.nextFloat() * w
    val y = h * (0.05f + 0.90f * fall * fall)
    val r = (10f + 26f * depth * depth) * unit
    val alpha = 0.55f + 0.40f * random.nextFloat()
    val rotation = random.nextFloat() * 360f
    val color = palette[random.nextInt(palette.size)].copy(alpha = alpha)
    rotate(rotation, Offset(x, y)) {
      drawPath(petalPath(x, y + r * 0.5f, r), color)
    }
  }
  // A few big, faint petals close to the camera.
  repeat(6) {
    val x = random.nextFloat() * w
    val y = h * (0.10f + 0.85f * random.nextFloat())
    val r = (60f + random.nextFloat() * 40f) * unit
    val rotation = random.nextFloat() * 360f
    rotate(rotation, Offset(x, y)) {
      drawPath(petalPath(x, y + r * 0.5f, r), Color(0xFFFFB3C7).copy(alpha = 0.26f))
    }
  }
}
