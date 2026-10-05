/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.theme.AppMotion
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

typealias LiquidGlassBackdrop = HazeState

private val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LiquidGlassBackdrop?> { null }

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdrop = rememberHazeState()

fun Modifier.captureLiquidGlassBackdrop(
  backdrop: LiquidGlassBackdrop?,
  enabled: Boolean = true,
): Modifier =
  if (enabled && backdrop != null) hazeSource(backdrop) else this

@Composable
fun ProvideLiquidGlassBackdrop(
  backdrop: LiquidGlassBackdrop,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalLiquidGlassBackdrop provides backdrop.takeIf { enabled },
    content = content,
  )
}

enum class LiquidGlassStyle {
  MiniPlayer,
  Navigation,
}

/**
 * Haze-backed liquid glass surface.
 *
 * The caller-provided source state keeps the effect portable on Android versions where advanced
 * refraction is unavailable; Haze automatically simplifies unsupported optical features.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun LiquidGlassSurface(
  shape: Shape,
  modifier: Modifier = Modifier,
  style: LiquidGlassStyle = LiquidGlassStyle.Navigation,
  glassColor: Color,
  fallbackColor: Color,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  backdrop: LiquidGlassBackdrop? = LocalLiquidGlassBackdrop.current,
  content: @Composable BoxScope.() -> Unit,
) {
  val reducedMotion = AppMotion.shouldReduceMotion()
  // Apple-style frosted glass: diffusion does most of the work, while refraction stays subtle.
  val blurRadius = if (style == LiquidGlassStyle.MiniPlayer) 14.dp else 12.dp
  val refractionHeightFraction = if (style == LiquidGlassStyle.MiniPlayer) 0.16f else 0.14f
  val refractionAmount = if (style == LiquidGlassStyle.MiniPlayer) 7.dp else 5.dp
  val shadowElevation: Dp = if (style == LiquidGlassStyle.MiniPlayer) 10.dp else 8.dp
  val roundedShape = shape as? RoundedCornerShape

  val glassStyle =
    remember(roundedShape, style, glassColor, fallbackColor, reducedMotion) {
      roundedShape?.let { resolvedShape ->
        GlassStyle {
          shape(resolvedShape)
          tint(glassColor)
          // A soft milky backing tint produces the frosted iOS-style diffusion without making
          // the surface look opaque.
          backgroundColor(fallbackColor.copy(alpha = 0.16f))
          optics(
            refractionStrength = if (reducedMotion) 0f else 0.22f,
            refractionHeightFraction = if (reducedMotion) 0f else refractionHeightFraction,
            refractionDisplacement = if (reducedMotion) 0.dp else refractionAmount,
            depth = if (reducedMotion) 0f else 0.24f,
            blurRadius = blurRadius,
            refractionDetailIntensity = if (reducedMotion) 0f else 0.14f,
          )
          // Keep the material glossy enough to read as glass, but avoid a watery/lens look.
          chromaMultiplier(1.02f)
          contrast(0.015f)
          specularIntensity(if (reducedMotion) 0.18f else 0.24f)
          ambientResponse(0.28f)
          edgeSoftness(1.5.dp)
          edgeShadow(Color.Black.copy(alpha = 0.08f))
          chromaticAberrationStrength(if (reducedMotion) 0f else 0.015f)
        }
      }
    }

  val surfaceModifier =
    if (backdrop != null && roundedShape != null && glassStyle != null) {
      modifier
        .shadow(shadowElevation, shape)
        .clip(shape)
        .hazeGlass(
          input = HazeInput.Sources(backdrop),
          style = glassStyle,
        )
    } else {
      modifier
        .shadow(shadowElevation, shape)
        .clip(shape)
        .background(fallbackColor)
    }

  CompositionLocalProvider(LocalContentColor provides contentColor) {
    Box(modifier = surfaceModifier, content = content)
  }
}
