/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.content.Context
import android.util.LruCache
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class AmbientRenderContext(
  val scaleX: Double,
  val scaleY: Double,
)

data class AmbientSharedShaderConfig(
  val vignetteStrength: Float,
  val opacity: Float,
  val edgeBlend: Float = 0f,
)

data class AmbientShaderSpec(
  val context: AmbientRenderContext,
  val shared: AmbientSharedShaderConfig,
  val blurSamples: Int,
  val maxRadius: Float,
  val glowIntensity: Float,
  val satBoost: Float,
  val warmth: Float,
  val fadeCurve: Float,
  val style: AmbientStyle = AmbientStyle.Glow,
)

data class AmbientGlowPreset(
  val blurSamples: Int,
  val maxRadius: Float,
  val glowIntensity: Float,
  val satBoost: Float,
  val vignetteStrength: Float,
  val warmth: Float,
  val fadeCurve: Float,
  val opacity: Float,
)

object AmbientShaderPresets {
  val glowFast = AmbientGlowPreset(8, 0.15f, 1.2f, 1.0f, 0.3f, 0.0f, 1.2f, 0.8f)
  val glowBalanced = AmbientGlowPreset(18, 0.28f, 1.45f, 1.25f, 0.55f, 0.0f, 1.7f, 1.0f)
  val glowHighQuality = AmbientGlowPreset(24, 0.35f, 1.5f, 1.3f, 0.7f, 0.0f, 1.8f, 1.0f)
}

fun matchesGlowPreset(
  preset: AmbientGlowPreset,
  blurSamples: Int,
  maxRadius: Float,
  glowIntensity: Float,
  satBoost: Float,
  vignetteStrength: Float,
  warmth: Float,
  fadeCurve: Float,
  opacity: Float,
): Boolean =
  blurSamples == preset.blurSamples &&
    closeTo(maxRadius, preset.maxRadius) &&
    closeTo(glowIntensity, preset.glowIntensity) &&
    closeTo(satBoost, preset.satBoost) &&
    closeTo(vignetteStrength, preset.vignetteStrength) &&
    closeTo(warmth, preset.warmth) &&
    closeTo(fadeCurve, preset.fadeCurve) &&
    closeTo(opacity, preset.opacity)

private fun closeTo(left: Float, right: Float, tolerance: Float = 0.01f): Boolean = abs(left - right) <= tolerance

private const val GOLDEN_ANGLE = 2.399963229728653

private fun glslFloat(value: Double): String {
  val normalized = if (abs(value) < 0.0000005) 0.0 else value
  val formatted =
    String.format(Locale.US, "%.8f", normalized)
      .trimEnd('0')
      .trimEnd('.')
  return if (formatted.contains('.')) formatted else "$formatted.0"
}

private fun spiralRadiusNorm(
  index: Int,
  count: Int,
): Double = sqrt((index.toDouble() + 0.5) / count.toDouble())

/** Only the tiny pyramid seed uses the quality/sample budget; the final pass is fixed-cost. */
private val tapTableCache = LruCache<Int, String>(16)

private fun buildDownsampleTaps(samples: Int): String {
  tapTableCache.get(samples)?.let { return it }
  val taps =
    (0 until samples).joinToString(",\n") { index ->
      val radius = spiralRadiusNorm(index, samples) * 0.70710678
      val theta = (index.toDouble() + 0.5) * GOLDEN_ANGLE
      "    vec2(${glslFloat(cos(theta) * radius)}, ${glslFloat(sin(theta) * radius)})"
    }
  val table = "const vec2 FX_TAPS[$samples] = vec2[$samples](\n$taps\n);"
  tapTableCache.put(samples, table)
  return table
}

/**
 * Ports the uploaded fx_ambient.frag / fx_common.glsl spatial Glow and Mirror effects to
 * mpv OUTPUT hooks. Explicit saved levels replace Vulkan mip samplers, so both vo=gpu and
 * vo=gpu-next can use the same shader. Only the last pass remaps the visible picture.
 *
 * mpv hooks do not retain the previous frame: the native FxRenderer EMA/echo compute
 * buffers need renderer-owned persistent resources and are not part of this spatial port.
 */
object AmbientShaderBuilder {
  @Volatile private var ambientTemplate: String? = null

  fun build(
    context: Context,
    spec: AmbientShaderSpec,
  ): String {
    require(spec.style.usesShader) { "YouTube ambient uses its own frame-capture renderer" }
    val template =
      ambientTemplate ?: context.assets.open("shaders/ambient/fx_ambient.glsl")
        .bufferedReader().use { it.readText() }.also { ambientTemplate = it }
    val samples = spec.blurSamples.coerceIn(5, 64)
    val config =
      """
#define FX_SAMPLES $samples
#define FX_MODE ${if (spec.style == AmbientStyle.Mirror) 4 else 1}
#define SCALE_X ${glslFloat(spec.context.scaleX)}
#define SCALE_Y ${glslFloat(spec.context.scaleY)}
#define REACH ${glslFloat(((spec.maxRadius.toDouble() - 0.05) / 0.75).coerceIn(0.0, 1.0))}
#define INTENSITY ${glslFloat(spec.glowIntensity.toDouble())}
#define SAT_BOOST ${glslFloat(spec.satBoost.toDouble())}
#define WARMTH ${glslFloat(spec.warmth.toDouble())}
#define FADE_CURVE ${glslFloat(spec.fadeCurve.toDouble().coerceAtLeast(0.5) / 1.5)}
#define VIGNETTE_STR ${glslFloat(spec.shared.vignetteStrength.toDouble())}
#define OPACITY ${glslFloat(spec.shared.opacity.toDouble())}
#define EDGE_BLEND ${glslFloat(spec.shared.edgeBlend.toDouble())}
      """.trimIndent()

    return template.replace("// FX_CONFIG", config)
      .replace("// FX_DOWNSAMPLE_TAPS", buildDownsampleTaps(samples))
  }
}
