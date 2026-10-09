/* SPDX-License-Identifier: AGPL-3.0-or-later */

package app.gyrolet.mpvrx.ui.player

import app.gyrolet.mpvrx.preferences.MpvConfigOverridePolicy
import java.io.File
import kotlinx.serialization.json.Json

/** The built-in stats script reads its options live; never replace unrelated script options. */
class StatsZoomController(private val filesDir: File) {
  private var originalOptions: Map<String, String>? = null
  private var baseMetrics: Map<String, Float> = emptyMap()
  private var lastScale = 1f

  fun update(followVideoZoom: Boolean, zoom: Float) {
    if (MpvConfigOverridePolicy.isOwnedByMpvConf("script-opts")) return
    val scale = if (followVideoZoom) videoZoomMultiplier(zoom) else 1f
    if (scale == lastScale) return
    if (originalOptions == null) {
      val options = runCatching {
        PlaybackSession.getPropertyNode("script-opts")?.toObject<Map<String, String>>(Json)
      }.getOrNull().orEmpty()
      val config = runCatching {
        File(filesDir, "script-opts/stats.conf").readLines().mapNotNull { line ->
          val clean = line.substringBefore('#').trim()
          if ('=' !in clean) null else clean.substringBefore('=').trim() to clean.substringAfter('=').trim()
        }.toMap()
      }.getOrDefault(emptyMap())
      val defaults = mapOf("font_size" to 20f, "border_size" to 1.65f)
      originalOptions = options.filterKeys { it.removePrefix("stats-") in defaults }
      baseMetrics = defaults.mapValues { (key, default) ->
        options["stats-$key"]?.toFloatOrNull() ?: config[key]?.toFloatOrNull() ?: default
      }
    }
    if (scale == 1f) {
      baseMetrics.keys.forEach { key ->
        val name = "stats-$key"
        val original = originalOptions?.get(name)
        if (original == null) {
          PlaybackSession.command("change-list", "script-opts", "remove", name)
        } else {
          PlaybackSession.command("change-list", "script-opts", "append", "$name=$original")
        }
      }
      originalOptions = null
    } else {
      val options = baseMetrics.entries.joinToString(",") { (key, value) -> "stats-$key=${value * scale}" }
      PlaybackSession.command("change-list", "script-opts", "append", options)
    }
    lastScale = scale
  }
}
