/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.framecapture

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.framecapture.FrameCapture
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.SnapshotSortType
import app.gyrolet.mpvrx.preferences.SortOrder
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.browser.LocalNavigationBarHeight
import app.gyrolet.mpvrx.ui.browser.cards.SelectionIndicator
import app.gyrolet.mpvrx.ui.browser.cards.animatedSelectionColor
import app.gyrolet.mpvrx.ui.browser.components.BrowserTopBar
import app.gyrolet.mpvrx.ui.browser.selection.SelectionState
import app.gyrolet.mpvrx.ui.browser.states.EmptyState
import app.gyrolet.mpvrx.ui.components.InlineSearchBar
import app.gyrolet.mpvrx.ui.framecapture.dialogs.SnapshotSortDialog
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.controls.components.tvFocusHighlight
import app.gyrolet.mpvrx.ui.theme.AppShapeScale
import app.gyrolet.mpvrx.ui.theme.wallpaperAwareBackgroundColor
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.navigateTo
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object SnapshotScreen : Screen {

  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val browserPreferences = koinInject<BrowserPreferences>()
    val viewModel: SnapshotViewModel =
      viewModel(factory = SnapshotViewModel.factory(context.applicationContext as Application))
    val snapshots by viewModel.snapshots.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val bottomInset = LocalNavigationBarHeight.current
    val sortType by browserPreferences.snapshotSortType.collectAsState()
    val sortOrder by browserPreferences.snapshotSortOrder.collectAsState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(SelectionState<Long>()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
      if (isSearching) focusRequester.requestFocus()
    }

    val displayItems =
      remember(snapshots, sortType, sortOrder, searchQuery) {
        snapshots
          .filter { searchQuery.isBlank() || it.videoTitle.contains(searchQuery, ignoreCase = true) }
          .sortedWith(snapshotComparator(sortType, sortOrder))
      }
    val displayIds = remember(displayItems) { displayItems.map { it.id } }

    // Deleting or filtering away the last selected row would otherwise leave the bar in selection
    // mode with nothing selected and no way back out except the system Back.
    LaunchedEffect(displayIds, selection) {
      if (selection.isInSelectionMode && displayIds.none { it in selection.selectedIds }) {
        selection = selection.clear()
      }
    }

    BackHandler(enabled = selection.isInSelectionMode) { selection = selection.clear() }

    SnapshotSortDialog(isOpen = showSortDialog, onDismiss = { showSortDialog = false })

    Scaffold(
      containerColor = wallpaperAwareBackgroundColor(),
      topBar = {
        when {
          selection.isInSelectionMode ->
            BrowserTopBar(
              title = stringResource(R.string.ui_snapshots),
              isInSelectionMode = true,
              selectedCount = selection.selectedCount,
              totalCount = displayItems.size,
              onCancelSelection = { selection = selection.clear() },
              isSingleSelection = selection.isSingleSelection,
              onDeleteClick = { confirmDelete = true },
              onSelectAll = { selection = selection.selectAll(displayIds) },
              onInvertSelection = { selection = selection.invertSelection(displayIds) },
              onDeselectAll = { selection = selection.clear() },
            )

          isSearching ->
            InlineSearchBar(
              query = searchQuery,
              onQueryChange = { searchQuery = it },
              onSearch = { },
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
              inputFieldModifier = Modifier.focusRequester(focusRequester),
              placeholder = { Text(stringResource(R.string.snapshot_search_placeholder)) },
              leadingIcon = {
                Icon(
                  Icons.RoundedFilled.Search,
                  contentDescription = stringResource(R.string.settings_search_title),
                )
              },
              trailingIcon = {
                IconButton(
                  onClick = {
                    isSearching = false
                    searchQuery = ""
                  },
                ) {
                  Icon(
                    Icons.RoundedFilled.Close,
                    contentDescription = stringResource(R.string.generic_cancel),
                  )
                }
              },
              shape = RoundedCornerShape(28.dp),
              tonalElevation = 6.dp,
            )

          else ->
            BrowserTopBar(
              title = stringResource(R.string.ui_snapshots),
              isInSelectionMode = false,
              selectedCount = 0,
              totalCount = snapshots.size,
              onCancelSelection = { },
              onSortClick = { showSortDialog = true },
              onSearchClick = { isSearching = true },
              onSettingsClick = {
                backStack.navigateTo(app.gyrolet.mpvrx.ui.preferences.PreferencesScreen)
              },
            )
        }
      },
    ) { paddingValues ->
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background),
      ) {
        when {
          snapshots.isEmpty() ->
            EmptyState(
              icon = Icons.RoundedFilled.Image,
              title = stringResource(R.string.snapshot_empty_title),
              message = stringResource(R.string.snapshot_empty_message),
            )

          displayItems.isEmpty() ->
            // A search that matches nothing is not the same as having no snapshots at all, so it
            // gets its own message instead of the "capture your first moment" guidance.
            EmptyState(
              icon = Icons.RoundedFilled.Search,
              title = stringResource(R.string.snapshot_no_results),
              message = stringResource(R.string.snapshot_search_placeholder),
            )

          else ->
            LazyVerticalGrid(
              columns = GridCells.Adaptive(minSize = 140.dp),
              modifier = Modifier.fillMaxSize(),
              contentPadding =
                PaddingValues(
                  start = 12.dp,
                  end = 12.dp,
                  top = 12.dp,
                  bottom = bottomInset + 12.dp,
                ),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(displayItems, key = { it.id }) { capture ->
                LaunchedEffect(capture.id) { viewModel.loadThumbnail(capture) }
                SnapshotGridItem(
                  capture = capture,
                  thumbnail = thumbnails[capture.id],
                  isSelected = selection.isSelected(capture.id),
                  onClick = {
                    if (selection.isInSelectionMode) {
                      selection = selection.toggle(capture.id)
                    } else {
                      // The viewer carries the whole list so it can swipe between neighbours.
                      // Navigation3 has no per-entry ViewModelStore, so the route key inside the
                      // viewer has to include this list's identity — see SnapshotDetailScreen.
                      backStack.navigateTo(
                        SnapshotDetailScreen(
                          captures = displayItems.map { it.toDetailItem() },
                          initialIndex = displayItems.indexOf(capture).coerceAtLeast(0),
                        ),
                      )
                    }
                  },
                  // Matches the browser grids: the first long press starts a selection, later ones
                  // extend it from that anchor.
                  onLongClick = {
                    selection =
                      if (selection.isInSelectionMode) {
                        selection.selectRange(capture.id, displayIds)
                      } else {
                        selection.toggle(capture.id)
                      }
                  },
                )
              }
            }
        }
      }
    }

    if (confirmDelete) {
      AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = {
          Text(
            stringResource(R.string.snapshot_delete_selected_title, selection.selectedCount),
          )
        },
        text = { Text(stringResource(R.string.snapshot_delete_message)) },
        confirmButton = {
          TextButton(
            onClick = {
              confirmDelete = false
              viewModel.delete(selection.selectedIds)
              selection = selection.clear()
            },
          ) {
            Text(stringResource(R.string.snapshot_delete))
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmDelete = false }) {
            Text(stringResource(R.string.generic_cancel))
          }
        },
      )
    }
  }
}

/** Newest capture first by default (FR-09); the other fields sort on what the grid caption shows. */
private fun snapshotComparator(
  sortType: SnapshotSortType,
  sortOrder: SortOrder,
): Comparator<FrameCapture> {
  val base =
    when (sortType) {
      SnapshotSortType.CapturedAt -> compareBy<FrameCapture> { it.capturedAt }.thenBy { it.id }
      SnapshotSortType.VideoTitle ->
        compareBy<FrameCapture, String>(String.CASE_INSENSITIVE_ORDER) { it.videoTitle }.thenBy { it.capturedAt }
      SnapshotSortType.Position -> compareBy<FrameCapture> { it.positionMs }.thenBy { it.capturedAt }
    }
  return if (sortOrder == SortOrder.Descending) base.reversed() else base
}

@Composable
private fun SnapshotGridItem(
  capture: FrameCapture,
  thumbnail: Bitmap?,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val selectionTint = animatedSelectionColor(isSelected)

  Card(
    modifier =
      Modifier
        .fillMaxWidth()
        .tvFocusHighlight(AppShapeScale.large, focusedScale = 1.03f)
        .semantics { selected = isSelected }
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    shape = AppShapeScale.large,
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(AppShapeScale.medium)
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
          // Also the state a snapshot whose gallery file the user deleted lands in, which §6 asks
          // for: the record stays visible with a placeholder instead of vanishing.
          Icon(
            Icons.RoundedFilled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
          )
        }
        Box(modifier = Modifier.matchParentSize().background(selectionTint))
        SelectionIndicator(isSelected, Modifier.align(Alignment.TopEnd).padding(6.dp))
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = capture.videoTitle,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = capture.formattedPosition,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}
