/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.gyrolet.mpvrx.ui.player.HdrScreenMode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val GLASS_MAX_DIMENSION = 640
private const val CAPTURE_INTERVAL_PLAYING_MS = 50L // ~20 fps live stream while controls are visible
private const val CAPTURE_INTERVAL_PAUSED_MS = 300L
private const val FAILURES_BEFORE_FALLBACK = 2

private const val CAPTURE_OK = 0
private const val CAPTURE_RETRY = 1
private const val CAPTURE_UNSUPPORTED = 2

data class VideoGlassFrame(
  val frame: ImageBitmap? = null,
  val supported: Boolean = true,
)

@Composable
fun rememberVideoGlassFrame(
  surfaceView: SurfaceView,
  active: Boolean,
  playbackGeneration: Long,
  hdrScreenMode: HdrScreenMode,
  orientation: Int,
  isSurfaceReadyProvider: () -> Boolean,
  isPlayingProvider: () -> Boolean,
  retainFrameWhenInactive: Boolean = true,
  fallbackFrameProvider: suspend (Int) -> Bitmap?,
): VideoGlassFrame {
  var state by remember(surfaceView, playbackGeneration, hdrScreenMode, orientation) {
    mutableStateOf(VideoGlassFrame())
  }
  val currentIsSurfaceReadyProvider by rememberUpdatedState(isSurfaceReadyProvider)
  val currentIsPlayingProvider by rememberUpdatedState(isPlayingProvider)
  val currentFallbackFrameProvider by rememberUpdatedState(fallbackFrameProvider)
  val lifecycleOwner = LocalLifecycleOwner.current

  LaunchedEffect(
    active,
    surfaceView,
    lifecycleOwner,
    playbackGeneration,
    hdrScreenMode,
    orientation,
    retainFrameWhenInactive,
  ) {
    if (!retainFrameWhenInactive) state = VideoGlassFrame()
    if (!active) {
      return@LaunchedEffect
    }

    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      val pipeline = VideoGlassPipeline()
      var unsupported = false
      try {
        pipeline.runCapture(
          surfaceView = surfaceView,
          isSurfaceReady = currentIsSurfaceReadyProvider,
          isPlaying = currentIsPlayingProvider,
          fallbackFrame = { currentFallbackFrameProvider(GLASS_MAX_DIMENSION) },
          onFrame = { state = it },
          onUnsupported = {
            unsupported = true
            state = VideoGlassFrame(supported = false)
          },
        )
      } finally {
        pipeline.close()
        if (!unsupported && !retainFrameWhenInactive) state = VideoGlassFrame()
      }
    }
  }

  return state
}

private class VideoGlassPipeline : AutoCloseable {
  private val thread = HandlerThread("VideoGlassPixelCopy").apply { start() }
  private val pixelCopyHandler = Handler(thread.looper)

  private var outputBitmaps: Array<Bitmap>? = null
  private var currentWidth = 0
  private var currentHeight = 0
  private var writeIndex = 0

  private var fallbackCanvas: Canvas? = null
  private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

  private var supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
  private var consecutiveFailures = 0
  @Volatile private var closed = false

  suspend fun runCapture(
    surfaceView: SurfaceView,
    isSurfaceReady: () -> Boolean,
    isPlaying: () -> Boolean,
    fallbackFrame: suspend () -> Bitmap?,
    onFrame: (VideoGlassFrame) -> Unit,
    onUnsupported: () -> Unit,
  ) {
    if (!supported) {
      onUnsupported()
      return
    }

    while (currentCoroutineContext().isActive && supported && !closed) {
      var cadence = CAPTURE_INTERVAL_PAUSED_MS
      if (isSurfaceReady() && surfaceView.width > 0 && surfaceView.height > 0) {
        val target = obtainTargetBitmap(surfaceView.width, surfaceView.height)
        when (captureSurface(surfaceView, target, pixelCopyHandler)) {
          CAPTURE_OK -> {
            consecutiveFailures = 0
            val captured = target
            writeIndex = writeIndex xor 1
            onFrame(VideoGlassFrame(frame = captured.asImageBitmap(), supported = true))
            cadence = if (isPlaying()) CAPTURE_INTERVAL_PLAYING_MS else CAPTURE_INTERVAL_PAUSED_MS
          }

          CAPTURE_UNSUPPORTED -> {
            supported = false
            onUnsupported()
          }

          else -> {
            consecutiveFailures++
            if (consecutiveFailures >= FAILURES_BEFORE_FALLBACK) {
              val fb = fallbackFrame()
              if (fb != null && !fb.isRecycled && !closed) {
                val canvas = obtainFallbackCanvas(target)
                canvas.drawBitmap(
                  fb,
                  null,
                  android.graphics.Rect(0, 0, target.width, target.height),
                  fallbackPaint,
                )
                if (fb !== target && !fb.isRecycled) fb.recycle()
                val captured = target
                writeIndex = writeIndex xor 1
                onFrame(VideoGlassFrame(frame = captured.asImageBitmap(), supported = true))
                consecutiveFailures = 0
              }
            }
            cadence = if (isPlaying()) CAPTURE_INTERVAL_PLAYING_MS * 2 else CAPTURE_INTERVAL_PAUSED_MS
          }
        }
      }
      if (supported && !closed) delay(cadence)
    }
  }

  private fun obtainTargetBitmap(sw: Int, sh: Int): Bitmap {
    val tw: Int
    val th: Int
    if (sw >= sh) {
      tw = GLASS_MAX_DIMENSION
      th = ((GLASS_MAX_DIMENSION.toLong() * sh) / sw).toInt().coerceAtLeast(1)
    } else {
      th = GLASS_MAX_DIMENSION
      tw = ((GLASS_MAX_DIMENSION.toLong() * sw) / sh).toInt().coerceAtLeast(1)
    }

    var bitmaps = outputBitmaps
    if (bitmaps == null || currentWidth != tw || currentHeight != th) {
      bitmaps?.forEach { runCatching { it.recycle() } }
      currentWidth = tw
      currentHeight = th
      bitmaps = arrayOf(
        Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888),
      )
      outputBitmaps = bitmaps
      writeIndex = 0
      fallbackCanvas = Canvas(bitmaps[0])
    }
    return bitmaps[writeIndex]
  }

  private fun obtainFallbackCanvas(target: Bitmap): Canvas {
    val existing = fallbackCanvas
    if (existing != null) {
      existing.setBitmap(target)
      return existing
    }
    val newCanvas = Canvas(target)
    fallbackCanvas = newCanvas
    return newCanvas
  }

  override fun close() {
    closed = true
    supported = false
    thread.quitSafely()
    outputBitmaps?.forEach { runCatching { it.recycle() } }
    outputBitmaps = null
  }
}

private suspend fun captureSurface(
  surfaceView: SurfaceView,
  destination: Bitmap,
  handler: Handler,
): Int =
  suspendCancellableCoroutine { continuation ->
    val surface = surfaceView.holder.surface
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      continuation.resume(CAPTURE_UNSUPPORTED)
      return@suspendCancellableCoroutine
    }
    if (!surface.isValid) {
      continuation.resume(CAPTURE_RETRY)
      return@suspendCancellableCoroutine
    }

    try {
      PixelCopy.request(
        surfaceView,
        destination,
        { result ->
          if (continuation.isActive) {
            continuation.resume(
              when (result) {
                PixelCopy.SUCCESS -> CAPTURE_OK
                else -> CAPTURE_RETRY
              },
            )
          }
        },
        handler,
      )
    } catch (_: Throwable) {
      if (continuation.isActive) continuation.resume(CAPTURE_RETRY)
    }
  }
