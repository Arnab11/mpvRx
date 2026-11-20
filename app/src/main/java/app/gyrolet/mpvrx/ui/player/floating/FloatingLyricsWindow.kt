/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player.floating

import android.animation.ValueAnimator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.lyrics.SyncedLine
import app.gyrolet.mpvrx.preferences.AudioPreferences
import app.gyrolet.mpvrx.preferences.LyricsTranslationDisplayMode
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.icons.AppIcon
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.PlaybackPhase
import app.gyrolet.mpvrx.ui.player.PlaybackSession
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.theme.MpvrxTheme
import app.gyrolet.mpvrx.ui.utils.performAppHapticFeedback
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class FloatingLyricsWindow(
  context: Context,
  private val preferences: AudioPreferences,
  private val playerPreferences: PlayerPreferences,
  private val scope: CoroutineScope,
  private val onDismiss: () -> Unit,
  private val onDisable: () -> Unit,
  private val onOpenPlayer: () -> Unit,
  private val onTogglePlay: () -> Unit,
  private val onPrevious: () -> Unit,
  private val onNext: () -> Unit,
  private val onSeek: (Long) -> Unit,
  private val onUnavailable: (RuntimeException) -> Unit,
) {
  private data class Metrics(
    val collapsedWidth: Dp = 280.dp,
    val collapsedHeight: Dp = 72.dp,
    val expandedWidth: Dp = 340.dp,
    val expandedHeight: Dp = 292.dp,
  )

  private val display = checkNotNull(
    context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY),
  ) { "No display is available for floating lyrics" }
  private val displayContext = context.createDisplayContext(display)
  private val windowContext =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    } else displayContext
  private val windows = checkNotNull(windowContext.getSystemService(WindowManager::class.java))
  private val owner = OverlayOwner()
  private val safeBounds = Rect()
  private var view: ComposeView? = null
  private var dropTarget: TextView? = null
  private var callbacksRegistered = false
  private var removed = false
  private var added = false
  private var model by mutableStateOf(FloatingLyricsModel())
  private var metrics by mutableStateOf(Metrics())
  private var expanded by mutableStateOf(false)
  private var dockEnd by mutableStateOf(preferences.floatingLyricsDockEnd.get())
  private var growUp by mutableStateOf(false)
  private var dragging by mutableStateOf(false)
  private var touchTick by mutableIntStateOf(0)
  private var frameExpanded = false
  private var anchor = Offset.Zero
  private var dragStart = Offset.Zero
  private var fingerStart = Offset.Zero
  private var settle: Job? = null
  private var touchCancelled = false
  private var dropArmed = false
  private val velocity = VelocityTracker()
  private val density: Float get() = windowContext.resources.displayMetrics.density
  private fun Dp.pixels(): Int = (value * density).roundToInt()
  private val shadow get() = 8.dp.pixels()
  private val coverInsetX get() = shadow + 34.dp.pixels()
  private val coverInsetY get() = shadow + metrics.collapsedHeight.pixels() / 2
  private val params = WindowManager.LayoutParams(
    1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
      WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT,
  ).apply {
    gravity = Gravity.TOP or Gravity.LEFT
    title = windowContext.getString(R.string.pref_floating_lyrics_title)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
  }
  private val configuration = object : ComponentCallbacks {
    override fun onConfigurationChanged(newConfig: Configuration) {
      if (removed) return
      settle?.cancel()
      cancelTouch()
      dragging = false
      removeDropTarget()
      refreshSafely(reset = true)
    }
    override fun onLowMemory() = Unit
  }
  private val detector = GestureDetector(windowContext, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(event: MotionEvent): Boolean = !removed
    override fun onSingleTapUp(event: MotionEvent): Boolean {
      if (!removed && !dragging) updateExpanded(!expanded)
      return true
    }
    override fun onLongPress(event: MotionEvent) {
      if (!removed && !dragging) onOpenPlayer()
    }
    override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
      if (removed || first == null) return false
      if (!dragging) {
        dragging = true
        updateExpanded(false)
        view?.performAppHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showDropTarget()
      }
      if (removed) return false
      moveAnchor(dragStart + Offset(current.rawX, current.rawY) - fingerStart)
      val armed = nearDropTarget(current.rawX, current.rawY)
      if (armed != dropArmed) {
        dropArmed = armed
        dropTarget?.animate()?.scaleX(if (armed) 1.15f else 1f)?.scaleY(if (armed) 1.15f else 1f)?.setDuration(100)?.start()
        if (armed) view?.performAppHapticFeedback(HapticFeedbackConstants.CONFIRM)
      }
      return true
    }
  })

  init {
    refreshGeometry(reset = true)
  }

  fun update(transform: (FloatingLyricsModel) -> FloatingLyricsModel) {
    if (!removed) model = transform(model)
  }

  fun show(): Boolean {
    if (removed) return false
    if (added) return true
    val content = ComposeView(windowContext).apply {
      setViewTreeLifecycleOwner(owner)
      setViewTreeSavedStateRegistryOwner(owner)
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setOnApplyWindowInsetsListener { _, insets ->
        post { if (!removed) refreshSafely(reset = false) }
        insets
      }
      setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && !removed) {
          updateExpanded(false)
          true
        } else false
      }
      setContent { MpvrxTheme(hostToasts = false) { Content() } }
    }
    view = content
    return try {
      owner.resume()
      windows.addView(content, params)
      added = true
      windowContext.registerComponentCallbacks(configuration)
      callbacksRegistered = true
      true
    } catch (error: WindowManager.BadTokenException) {
      failWindow(error)
      false
    } catch (error: WindowManager.InvalidDisplayException) {
      failWindow(error)
      false
    } catch (error: SecurityException) {
      failWindow(error)
      false
    } catch (error: IllegalStateException) {
      failWindow(error)
      false
    } catch (error: IllegalArgumentException) {
      failWindow(error)
      false
    }
  }

  fun remove() {
    if (removed) return
    removed = true
    settle?.cancel()
    settle = null
    cancelTouch()
    removeDropTarget()
    val content = view
    view = null
    if (callbacksRegistered) {
      callbacksRegistered = false
      windowContext.unregisterComponentCallbacks(configuration)
    }
    if (content != null) {
      if (added) {
        added = false
        try {
          windows.removeViewImmediate(content)
        } catch (error: IllegalArgumentException) {
          Log.w(TAG, "Floating lyrics window was already removed", error)
        } catch (error: SecurityException) {
          Log.w(TAG, "Could not remove floating lyrics window", error)
        }
      }
      content.disposeComposition()
    }
    owner.destroy()
  }

  private fun failWindow(error: RuntimeException) {
    if (removed) return
    remove()
    onUnavailable(error)
  }

  private fun refreshSafely(reset: Boolean) {
    if (removed) return
    try {
      refreshGeometry(reset)
    } catch (error: WindowManager.InvalidDisplayException) {
      failWindow(error)
    } catch (error: SecurityException) {
      failWindow(error)
    } catch (error: IllegalStateException) {
      failWindow(error)
    } catch (error: IllegalArgumentException) {
      failWindow(error)
    }
  }

  private fun refreshGeometry(reset: Boolean) {
    val width: Int
    val height: Int
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val displayMetrics = windows.maximumWindowMetrics
      val insets = displayMetrics.windowInsets.getInsetsIgnoringVisibility(
        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
      )
      width = displayMetrics.bounds.width()
      height = displayMetrics.bounds.height()
      left = insets.left
      top = insets.top
      right = insets.right
      bottom = insets.bottom
    } else {
      val displayMetrics = windowContext.resources.displayMetrics
      val insets = view?.let(ViewCompat::getRootWindowInsets)?.getInsetsIgnoringVisibility(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
      )
      width = displayMetrics.widthPixels
      height = displayMetrics.heightPixels
      left = insets?.left ?: 0
      top = insets?.top ?: 24.dp.pixels()
      right = insets?.right ?: 0
      bottom = insets?.bottom ?: 0
    }
    val margin = 8.dp.pixels()
    safeBounds.set(left + margin, top + margin, width - right - margin, height - bottom - margin)
    val availableWidth = (safeBounds.width() - shadow * 2) / density
    val availableHeight = (safeBounds.height() - shadow * 2) / density
    check(availableWidth >= 164f && availableHeight >= 128f) { "Display is too small for floating lyrics" }
    val fontScale = windowContext.resources.configuration.fontScale
    val collapsedHeight = (56f * fontScale + 16f).coerceIn(64f, availableHeight - 64f)
    metrics = Metrics(
      collapsedWidth = minOf(280f, availableWidth).dp,
      collapsedHeight = collapsedHeight.dp,
      expandedWidth = minOf(340f, availableWidth).dp,
      expandedHeight = minOf(
        availableHeight,
        maxOf(collapsedHeight + 128f, minOf(collapsedHeight + 220f * fontScale, availableHeight * 0.78f)),
      ).dp,
    )
    if (reset) {
      dockEnd = preferences.floatingLyricsDockEnd.get()
      anchor = Offset(
        (if (dockEnd) safeBounds.right - coverInsetX else safeBounds.left + coverInsetX).toFloat(),
        safeBounds.top + coverInsetY + (safeBounds.height() - coverInsetY * 2).coerceAtLeast(0) *
          (preferences.floatingLyricsY.get().coerceIn(0, 1_000) / 1_000f),
      )
    }
    anchor = clamped(anchor)
    if (!dragging) growUp = anchor.y + metrics.expandedHeight.pixels() - coverInsetY + shadow * 2 > safeBounds.bottom
    applyFrame()
  }

  private fun clamped(position: Offset): Offset = Offset(
    position.x.coerceIn(
      (safeBounds.left + coverInsetX).toFloat(),
      (safeBounds.right - coverInsetX).coerceAtLeast(safeBounds.left + coverInsetX).toFloat(),
    ),
    position.y.coerceIn(
      (safeBounds.top + coverInsetY).toFloat(),
      (safeBounds.bottom - coverInsetY).coerceAtLeast(safeBounds.top + coverInsetY).toFloat(),
    ),
  )

  private fun moveAnchor(position: Offset) {
    if (removed) return
    anchor = clamped(position)
    applyFrame()
  }

  private fun applyFrame() {
    if (removed) return
    val width = (if (frameExpanded) metrics.expandedWidth else metrics.collapsedWidth).pixels() + shadow * 2
    val height = (if (frameExpanded) metrics.expandedHeight else metrics.collapsedHeight).pixels() + shadow * 2
    var x = (anchor.x - if (dockEnd) width - coverInsetX else coverInsetX).roundToInt()
    var y = (anchor.y - if (growUp) height - coverInsetY else coverInsetY).roundToInt()
    if (frameExpanded && !dragging) {
      x = x.coerceIn(safeBounds.left, safeBounds.right - width)
      y = y.coerceIn(safeBounds.top, safeBounds.bottom - height)
      anchor = Offset(
        (x + if (dockEnd) width - coverInsetX else coverInsetX).toFloat(),
        (y + if (growUp) height - coverInsetY else coverInsetY).toFloat(),
      )
    }
    if (params.width == width && params.height == height && params.x == x && params.y == y) return
    params.width = width
    params.height = height
    params.x = x
    params.y = y
    val content = view ?: return
    if (!added) return
    try {
      windows.updateViewLayout(content, params)
    } catch (error: SecurityException) {
      failWindow(error)
    } catch (error: WindowManager.BadTokenException) {
      failWindow(error)
    } catch (error: WindowManager.InvalidDisplayException) {
      failWindow(error)
    } catch (error: IllegalArgumentException) {
      failWindow(error)
    }
  }

  private fun updateExpanded(value: Boolean) {
    if (removed) return
    touchTick++
    if (value) {
      growUp = anchor.y + metrics.expandedHeight.pixels() - coverInsetY + shadow * 2 > safeBounds.bottom
      // Reserve the native frame before Compose grows; keep it until collapse completes.
      frameExpanded = true
      applyFrame()
    }
    expanded = value
  }

  private fun fitCollapsedFrame() {
    if (!expanded && !removed) {
      frameExpanded = false
      applyFrame()
    }
  }

  private fun cancelTouch() {
    touchCancelled = true
    val now = SystemClock.uptimeMillis()
    val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
    try {
      detector.onTouchEvent(cancel)
    } finally {
      cancel.recycle()
    }
  }

  private fun headerTouch(event: MotionEvent): Boolean {
    if (removed) return false
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      touchCancelled = false
      settle?.cancel()
      dragStart = anchor
      fingerStart = Offset(event.rawX, event.rawY)
      velocity.resetTracking()
      touchTick++
    } else if (event.pointerCount > 1) {
      cancelTouch()
      if (dragging) {
        dragging = false
        dropArmed = false
        removeDropTarget()
        settleAtEdge(Velocity.Zero)
      }
    }
    if (touchCancelled) return true
    velocity.addPosition(event.eventTime, Offset(event.rawX, event.rawY))
    detector.onTouchEvent(event)
    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
      if (dragging) {
        val dismiss = event.actionMasked == MotionEvent.ACTION_UP && dropArmed
        dragging = false
        dropArmed = false
        removeDropTarget()
        if (dismiss) onDismiss()
        else settleAtEdge(if (event.actionMasked == MotionEvent.ACTION_UP) velocity.calculateVelocity() else Velocity.Zero)
      }
    }
    return true
  }

  private fun settleAtEdge(releaseVelocity: Velocity) {
    if (removed) return
    dockEnd = anchor.x + releaseVelocity.x * 0.12f >= safeBounds.exactCenterX()
    val target = clamped(
      Offset(
        (if (dockEnd) safeBounds.right - coverInsetX else safeBounds.left + coverInsetX).toFloat(),
        anchor.y + releaseVelocity.y * 0.12f,
      ),
    )
    settle?.cancel()
    settle = scope.launch {
      if (playerPreferences.reduceMotion.get() || !ValueAnimator.areAnimatorsEnabled()) {
        moveAnchor(target)
      } else {
        val position = Animatable(anchor, Offset.VectorConverter)
        position.animateTo(
          target,
          animationSpec = spring(dampingRatio = 1f, stiffness = 320f),
          initialVelocity = Offset(releaseVelocity.x, releaseVelocity.y),
        ) { moveAnchor(this.value) }
      }
      if (!removed) {
        preferences.floatingLyricsDockEnd.set(dockEnd)
        val room = (safeBounds.height() - coverInsetY * 2).coerceAtLeast(1)
        preferences.floatingLyricsY.set(
          ((anchor.y - safeBounds.top - coverInsetY) * 1_000f / room).roundToInt().coerceIn(0, 1_000),
        )
      }
    }
  }

  private fun dock(right: Boolean) {
    if (removed) return
    settle?.cancel()
    touchTick++
    dockEnd = right
    moveAnchor(Offset((if (right) safeBounds.right - coverInsetX else safeBounds.left + coverInsetX).toFloat(), anchor.y))
    preferences.floatingLyricsDockEnd.set(right)
  }

  private fun nearDropTarget(x: Float, y: Float): Boolean =
    kotlin.math.abs(x - safeBounds.exactCenterX()) < 72.dp.pixels() && y > safeBounds.bottom - 112.dp.pixels()

  private fun showDropTarget() {
    if (removed || dropTarget != null) return
    val target = TextView(windowContext).apply {
      text = "\u00D7"
      textSize = 30f
      gravity = Gravity.CENTER
      setTextColor(android.graphics.Color.WHITE)
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFFD04B43.toInt())
      }
    }
    val size = 64.dp.pixels()
    val layout = WindowManager.LayoutParams(
      size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.LEFT
      x = safeBounds.centerX() - size / 2
      y = safeBounds.bottom - size - 16.dp.pixels()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
    }
    try {
      windows.addView(target, layout)
      dropTarget = target
    } catch (error: WindowManager.BadTokenException) {
      failWindow(error)
    } catch (error: WindowManager.InvalidDisplayException) {
      failWindow(error)
    } catch (error: SecurityException) {
      failWindow(error)
    }
  }

  private fun removeDropTarget() {
    val target = dropTarget ?: return
    dropTarget = null
    target.animate().cancel()
    try {
      windows.removeViewImmediate(target)
    } catch (error: IllegalArgumentException) {
      Log.w(TAG, "Floating lyrics dismiss target was already removed", error)
    } catch (error: SecurityException) {
      Log.w(TAG, "Could not remove floating lyrics dismiss target", error)
    }
  }

  private fun canControl(generation: Long): Boolean {
    val session = PlaybackSession.state.value
    return !removed && model.canControl && model.generation == generation &&
      PlaybackSession.isCurrentGeneration(generation) &&
      (session.phase == PlaybackPhase.READY || session.phase == PlaybackPhase.BACKGROUND)
  }

  private fun transport(generation: Long, action: () -> Unit) {
    if (canControl(generation)) action()
  }

  private fun seekTo(position: Long, generation: Long) {
    if (model.canSeek && canControl(generation)) {
      onSeek(position.coerceIn(0L, model.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE))
      touchTick++
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  private fun Content() {
    val model = this.model
    val reduced = AppMotion.shouldReduceMotion()
    val expandAnimation by animateFloatAsState(
      if (expanded) 1f else 0f,
      animationSpec = if (reduced) snap() else spring(dampingRatio = 0.9f, stiffness = 380f),
      finishedListener = { if (it == 0f) fitCollapsedFrame() },
      label = "floatingLyricsExpand",
    )
    val progress = expandAnimation.coerceIn(0f, 1f)
    val tuckAnimation by animateFloatAsState(
      if (dragging) 1f else 0f,
      animationSpec = if (reduced) snap() else spring(dampingRatio = 0.85f, stiffness = 460f),
      label = "floatingLyricsTuck",
    )
    val tuck = tuckAnimation.coerceIn(0f, 1f)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val appear by animateFloatAsState(
      if (appeared) 1f else 0f,
      animationSpec = if (reduced) snap() else spring(dampingRatio = 0.9f, stiffness = 420f),
      label = "floatingLyricsAppear",
    )
    val lyricsList = rememberLazyListState()
    var following by remember(model.generation, expanded) { mutableStateOf(true) }
    var scrubbing by remember(model.generation) { mutableStateOf<Float?>(null) }
    LaunchedEffect(model.canSeek) { if (!model.canSeek) scrubbing = null }
    LaunchedEffect(model.generation, expanded, lyricsList.interactionSource) {
      lyricsList.interactionSource.interactions.collect { interaction ->
        if (interaction is DragInteraction.Start) {
          following = false
          touchTick++
        }
      }
    }
    LaunchedEffect(model.generation, model.lineIndex, expanded) {
      if (expanded && following && model.lineIndex >= 0 && !lyricsList.isScrollInProgress) {
        val index = (model.lineIndex - 1).coerceAtLeast(0)
        if (reduced) lyricsList.scrollToItem(index) else lyricsList.animateScrollToItem(index)
      }
    }
    LaunchedEffect(expanded, touchTick, scrubbing != null, lyricsList.isScrollInProgress) {
      if (expanded && scrubbing == null && !lyricsList.isScrollInProgress) {
        delay(6_000)
        updateExpanded(false)
      }
    }
    val displayMode by preferences.lyricsTranslationDisplayMode.collectAsState()
    val line = model.lines.getOrNull(model.lineIndex)
    val main = when {
      line == null -> model.title
      displayMode == LyricsTranslationDisplayMode.Replace && !line.translation.isNullOrBlank() -> line.translation.orEmpty()
      line.line.isBlank() -> "..."
      else -> line.line
    }
    val secondary = if (line == null) model.artist else {
      line.translation?.takeIf { displayMode == LyricsTranslationDisplayMode.DualLine && it.isNotBlank() }
        ?: line.romanization?.takeIf { it.isNotBlank() }
        ?: model.lines.getOrNull(model.lineIndex + 1)?.line.orEmpty()
    }
    val expandLabel = stringResource(if (expanded) R.string.generic_collapse else R.string.generic_expand)
    val openLabel = stringResource(R.string.floating_lyrics_open_player)
    val leftLabel = stringResource(R.string.floating_lyrics_dock_left)
    val rightLabel = stringResource(R.string.floating_lyrics_dock_right)
    val dismissLabel = stringResource(R.string.floating_lyrics_dismiss)
    val align = when {
      growUp && dockEnd -> AbsoluteAlignment.BottomRight
      growUp -> AbsoluteAlignment.BottomLeft
      dockEnd -> AbsoluteAlignment.TopRight
      else -> AbsoluteAlignment.TopLeft
    }
    Box(Modifier.fillMaxSize().padding(8.dp)) {
      Surface(
        modifier = Modifier.align(align)
          .width(lerp(lerp(metrics.collapsedWidth, metrics.expandedWidth, progress), 68.dp, tuck))
          .height(lerp(metrics.collapsedHeight, metrics.expandedHeight, progress))
          .graphicsLayer {
            alpha = appear.coerceIn(0f, 1f)
            scaleX = 0.94f + appear * 0.06f
            scaleY = scaleX
          },
        shape = RoundedCornerShape(lerp(32.dp, 28.dp, progress)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
      ) {
        Column {
          @Composable
          fun Header() {
            Box(
              Modifier.fillMaxWidth().height(metrics.collapsedHeight)
                .semantics(mergeDescendants = true) {
                  role = Role.Button
                  onClick(expandLabel) { updateExpanded(!expanded); true }
                  onLongClick(openLabel) { if (!removed) onOpenPlayer(); true }
                  customActions = listOf(
                    CustomAccessibilityAction(leftLabel) { dock(false); true },
                    CustomAccessibilityAction(rightLabel) { dock(true); true },
                    CustomAccessibilityAction(dismissLabel) { if (!removed) onDismiss(); true },
                  )
                }
                .pointerInteropFilter(onTouchEvent = ::headerTouch),
            ) {
              val artwork = model.artwork
              Box(
                Modifier.align(if (dockEnd) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                  .absolutePadding(left = if (dockEnd) 0.dp else 12.dp, right = if (dockEnd) 12.dp else 0.dp)
                  .size(44.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                if (artwork != null && !artwork.isRecycled) {
                  Image(
                    bitmap = remember(artwork) { artwork.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(Icons.RoundedFilled.Audiotrack, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
              }
              AnimatedContent(
                targetState = main to secondary,
                transitionSpec = {
                  if (reduced) EnterTransition.None togetherWith ExitTransition.None else {
                    (fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 5 })
                      .togetherWith(fadeOut(tween(100)) + slideOutVertically(tween(150)) { -it / 5 })
                  }
                },
                label = "floatingLyricsLine",
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                  .absolutePadding(left = if (dockEnd) 14.dp else 68.dp, right = if (dockEnd) 68.dp else 14.dp)
                  .graphicsLayer { alpha = 1f - tuck },
              ) { text ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(
                    text = highlightedLine(text.first, line, model.positionMs + model.offsetMs, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (text.second.isNotBlank()) Text(
                    text.second,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
          @Composable
          fun Extras() {
            Column(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = progress * (1f - tuck) }) {
              LazyColumn(
                state = lyricsList,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
              ) {
                when {
                  model.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                      CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                  }
                  model.errorMessage != null -> item {
                    Text(model.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                  }
                  model.lines.isNotEmpty() -> itemsIndexed(
                    model.lines,
                    key = { index, _ -> "${model.generation}:$index" },
                  ) { index, cue ->
                    TextButton(
                      onClick = { seekTo(cue.time.toLong() - model.offsetMs, model.generation) },
                      enabled = model.canSeek,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Column(Modifier.fillMaxWidth()) {
                        val text = if (displayMode == LyricsTranslationDisplayMode.Replace && !cue.translation.isNullOrBlank()) {
                          cue.translation.orEmpty()
                        } else cue.line.ifBlank { "..." }
                        Text(
                          text,
                          color = if (index == model.lineIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                          style = MaterialTheme.typography.bodyMedium,
                          maxLines = 3,
                          overflow = TextOverflow.Ellipsis,
                        )
                        if (displayMode == LyricsTranslationDisplayMode.DualLine && !cue.translation.isNullOrBlank()) {
                          Text(cue.translation.orEmpty(), style = MaterialTheme.typography.labelSmall)
                        }
                      }
                    }
                  }
                  model.plain.isNotEmpty() -> itemsIndexed(model.plain) { _, text ->
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 5.dp))
                  }
                  else -> item {
                    Text(stringResource(R.string.floating_lyrics_no_lyrics), style = MaterialTheme.typography.bodySmall)
                  }
                }
              }
              if (model.canSeek && model.durationMs > 0 && metrics.expandedHeight - metrics.collapsedHeight >= 180.dp) {
                val seekLabel = stringResource(R.string.pref_player_seeking_title)
                val played = model.positionMs.toFloat() / model.durationMs
                Slider(
                  value = scrubbing ?: played.coerceIn(0f, 1f),
                  onValueChange = { scrubbing = it },
                  onValueChangeFinished = {
                    scrubbing?.let { seekTo((it * model.durationMs).toLong(), model.generation) }
                    scrubbing = null
                    touchTick++
                  },
                  modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 14.dp)
                    .semantics { contentDescription = seekLabel },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(DateUtils.formatElapsedTime(model.positionMs / 1_000), style = MaterialTheme.typography.labelSmall)
                  Text(DateUtils.formatElapsedTime(model.durationMs / 1_000), style = MaterialTheme.typography.labelSmall)
                }
              }
              Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
              ) {
                Control(Icons.RoundedFilled.SkipPrevious, R.string.pref_gesture_media_previous, model.canPrevious) {
                  transport(model.generation, onPrevious)
                }
                Control(
                  if (model.paused) Icons.RoundedFilled.PlayArrow else Icons.RoundedFilled.Pause,
                  if (model.paused) R.string.ui_play else R.string.audiobook_pause, model.canControl,
                ) { transport(model.generation, onTogglePlay) }
                Control(Icons.RoundedFilled.SkipNext, R.string.pref_gesture_media_next, model.canNext) {
                  transport(model.generation, onNext)
                }
                Control(Icons.RoundedFilled.Close, R.string.floating_lyrics_dismiss, true, onDismiss)
                Control(Icons.RoundedFilled.Block, R.string.floating_lyrics_turn_off, true, onDisable)
              }
              Spacer(Modifier.height(4.dp))
            }
          }
          if (growUp && (expanded || progress > 0.001f)) Extras()
          Header()
          if (!growUp && (expanded || progress > 0.001f)) Extras()
        }
      }
    }
  }

  @Composable
  private fun Control(icon: AppIcon, @StringRes label: Int, enabled: Boolean, action: () -> Unit) {
    IconButton(
      onClick = { if (!removed) { touchTick++; action() } },
      enabled = enabled,
      modifier = Modifier.size(48.dp),
    ) {
      Icon(
        icon,
        contentDescription = stringResource(label),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        modifier = Modifier.size(23.dp),
      )
    }
  }

  private companion object {
    const val TAG = "FloatingLyricsWindow"
  }
}

private fun highlightedLine(text: String, line: SyncedLine?, positionMs: Long, accent: Color): AnnotatedString =
  buildAnnotatedString {
    append(text)
    if (line == null || text != line.line) return@buildAnnotatedString
    var cursor = 0
    for (word in line.words.orEmpty()) {
      val start = text.indexOf(word.word, cursor)
      if (start < 0 || word.word.isEmpty()) continue
      val end = start + word.word.length
      if (word.time <= positionMs) addStyle(SpanStyle(color = accent), start, end)
      cursor = end
    }
  }

private class OverlayOwner : SavedStateRegistryOwner {
  private val registry = LifecycleRegistry(this)
  private val savedState = SavedStateRegistryController.create(this)
  override val lifecycle: Lifecycle get() = registry
  override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

  init {
    savedState.performAttach()
    savedState.performRestore(null)
    registry.currentState = Lifecycle.State.CREATED
  }

  fun resume() { registry.currentState = Lifecycle.State.RESUMED }
  fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}
