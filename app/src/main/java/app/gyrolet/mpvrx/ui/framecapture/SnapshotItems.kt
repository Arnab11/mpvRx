/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.framecapture

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.framecapture.FrameCapture
import app.gyrolet.mpvrx.preferences.SnapshotSortType
import app.gyrolet.mpvrx.preferences.SortOrder
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.browser.cards.SelectionIndicator
import app.gyrolet.mpvrx.ui.browser.cards.animatedSelectionColor
import app.gyrolet.mpvrx.ui.browser.selection.SelectionState
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.controls.components.tvFocusHighlight
import app.gyrolet.mpvrx.ui.theme.AppShapeScale
import app.gyrolet.mpvrx.ui.utils.navigateTo
import java.text.DateFormat
import java.util.Date

/**
 * Newest capture first by default; the other fields sort on what the list caption shows.
 *
 * Shared with the folder page, which sorts the same records with the same controls.
 */
internal fun snapshotComparator(
  sortType: SnapshotSortType,
  sortOrder: SortOrder,
): Comparator<FrameCapture> {
  val base =
    when (sortType) {
      SnapshotSortType.CapturedAt -> compareBy<FrameCapture> { it.capturedAt }.thenBy { it.id }
      SnapshotSortType.VideoTitle ->
        compareBy<FrameCapture, String>(String.CASE_INSENSITIVE_ORDER) { it.videoTitle }
          .thenBy { it.capturedAt }
      SnapshotSortType.Position -> compareBy<FrameCapture> { it.positionMs }.thenBy { it.capturedAt }
    }
  return if (sortOrder == SortOrder.Descending) base.reversed() else base
}

/** Localised, because a capture date is read by a person, not parsed by anything. */
internal val FrameCapture.formattedCaptureDate: String
  get() = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(capturedAt))

/** A photo grid that drops below three across stops reading as a grid at all. */
internal const val MIN_SNAPSHOT_GRID_COLUMNS = 3

/** Cells are square, so this is the width at which three still fit; wider screens get more. */
internal val SNAPSHOT_GRID_TARGET_CELL = 128.dp

internal val SNAPSHOT_GRID_SPACING = 2.dp

/** The folder glyph's box, matching the browser's folder cards so both pages read the same. */
private const val FOLDER_GLYPH_ASPECT = 20f / 17f

/**
 * Columns for a snapshot grid. [manualColumns] is whatever the user picked in the sort dialog and is
 * taken as given — asking for two and being handed three would be worse than a wide grid.
 */
internal fun snapshotGridColumns(
  availableWidth: Dp,
  manualColumns: Int,
): Int =
  if (manualColumns > 0) {
    manualColumns
  } else {
    maxOf(MIN_SNAPSHOT_GRID_COLUMNS, (availableWidth / SNAPSHOT_GRID_TARGET_CELL).toInt())
  }

/**
 * Tap behaviour shared by both snapshot screens: toggle the row while a selection is active, open the
 * full-screen viewer otherwise. Returns the selection unchanged when it opened the viewer.
 */
internal fun snapshotItemClick(
  selection: SelectionState<Long>,
  items: List<FrameCapture>,
  capture: FrameCapture,
  backStack: NavBackStack<Screen>,
): SelectionState<Long> {
  if (selection.isInSelectionMode) return selection.toggle(capture.id)
  // The viewer carries the whole list so it can swipe between neighbours, and its route key has to
  // include that list's identity — see SnapshotDetailScreen.
  backStack.navigateTo(
    SnapshotDetailScreen(
      captures = items.map { it.toDetailItem() },
      initialIndex = items.indexOf(capture).coerceAtLeast(0),
    ),
  )
  return selection
}

/**
 * The square snapshot cell.
 *
 * No caption: a photo grid reads as photos, and the title and timestamp are what the list layout and
 * the full-screen viewer are for.
 *
 * No rounded corners either — the grid is the pictures, and rounding every tile turns a photo wall
 * into a row of cards. This is a deliberate exception to the app's card-radius tokens; the folder
 * cards and list rows around it do follow them.
 */
@Composable
fun SnapshotGridItem(
  capture: FrameCapture,
  thumbnail: Bitmap?,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val selectionTint = animatedSelectionColor(isSelected)

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .tvFocusHighlight(AppShapeScale.none, focusedScale = 1.03f)
        .semantics { selected = isSelected }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    contentAlignment = Alignment.Center,
  ) {
    if (thumbnail != null) {
      Image(
        bitmap = thumbnail.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
    } else {
      // Also the state a snapshot whose gallery file the user deleted lands in: the record stays
      // visible with a placeholder instead of vanishing.
      Icon(
        Icons.RoundedFilled.Image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(40.dp),
      )
    }
    Box(modifier = Modifier.matchParentSize().background(selectionTint))
    SelectionIndicator(
      selected = isSelected,
      modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
    )
  }
}

/**
 * A folder in the grid, drawn to the same recipe as the browser's own folder cards —
 * `ui/browser/cards/FolderCard.kt` and `NetworkFolderCard.kt` — so a folder looks the same here as
 * anywhere else in the app: the filled folder glyph in a 20:17 box, name centred underneath.
 *
 * Deliberately no snapshot count: no other folder card shows one, and matching them is the point.
 */
@Composable
fun SnapshotFolderGridItem(
  name: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectionTint = animatedSelectionColor(isSelected)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .tvFocusHighlight(AppShapeScale.medium, focusedScale = 1.03f)
        .semantics { selected = isSelected }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 4.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().aspectRatio(FOLDER_GLYPH_ASPECT),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.RoundedFilled.Folder,
        contentDescription = stringResource(R.string.ui_folder),
        modifier = Modifier.fillMaxWidth().aspectRatio(FOLDER_GLYPH_ASPECT),
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
      )
      Box(modifier = Modifier.matchParentSize().background(selectionTint))
      SelectionIndicator(
        selected = isSelected,
        modifier = Modifier.align(Alignment.TopEnd),
      )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = name,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** The folder row form, matching the browser folder cards' list layout: 72dp glyph, name beside it. */
@Composable
fun SnapshotFolderListItem(
  name: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectionTint = animatedSelectionColor(isSelected)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .tvFocusHighlight(AppShapeScale.medium, focusedScale = 1.01f)
        .semantics { selected = isSelected }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
      Icon(
        Icons.RoundedFilled.Folder,
        contentDescription = stringResource(R.string.ui_folder),
        modifier = Modifier.matchParentSize(),
        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
      )
      Box(modifier = Modifier.matchParentSize().background(selectionTint))
    }

    Spacer(modifier = Modifier.width(12.dp))

    Text(
      text = name,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )

    SelectionIndicator(selected = isSelected)
  }
}

/** The compact one-per-row form: a square thumbnail beside the title, timestamp and capture date. */
@Composable
fun SnapshotListItem(
  capture: FrameCapture,
  thumbnail: Bitmap?,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val selectionTint = animatedSelectionColor(isSelected)

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .tvFocusHighlight(AppShapeScale.medium, focusedScale = 1.01f)
        .semantics { selected = isSelected }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(56.dp)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      if (thumbnail != null) {
        Image(
          bitmap = thumbnail.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
        )
      } else {
        Icon(
          Icons.RoundedFilled.Image,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier.size(24.dp),
        )
      }
      Box(modifier = Modifier.matchParentSize().background(selectionTint))
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = capture.videoTitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = capture.formattedPosition,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
      Text(
        text = capture.formattedCaptureDate,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }

    SelectionIndicator(selected = isSelected)
  }
}
