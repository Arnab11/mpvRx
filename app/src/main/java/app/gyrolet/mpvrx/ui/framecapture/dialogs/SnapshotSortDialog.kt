/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.framecapture.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.SnapshotSortType
import app.gyrolet.mpvrx.preferences.SortOrder
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.browser.dialogs.SortDialog
import app.gyrolet.mpvrx.ui.icons.Icons
import org.koin.compose.koinInject

@Composable
fun SnapshotSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
) {
  if (!isOpen) return
  val preferences = koinInject<BrowserPreferences>()
  val sortType by preferences.snapshotSortType.collectAsState()
  val sortOrder by preferences.snapshotSortOrder.collectAsState()

  val labels =
    mapOf(
      SnapshotSortType.CapturedAt to stringResource(R.string.snapshot_sort_captured_at),
      SnapshotSortType.VideoTitle to stringResource(R.string.snapshot_sort_video_title),
      SnapshotSortType.Position to stringResource(R.string.snapshot_sort_position),
    )

  val ascendingLabel = stringResource(R.string.playlist_sort_ascending)
  val descendingLabel = stringResource(R.string.playlist_sort_descending)

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = stringResource(R.string.sort_view_options),
    sortType = labels.getValue(sortType),
    onSortTypeChange = { selected -> labels.entries.firstOrNull { it.value == selected }?.let { preferences.snapshotSortType.set(it.key) } },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { preferences.snapshotSortOrder.set(if (it) SortOrder.Ascending else SortOrder.Descending) },
    types = SnapshotSortType.entries.map(labels::getValue),
    icons =
      SnapshotSortType.entries.map { type ->
        when (type) {
          SnapshotSortType.CapturedAt -> Icons.RoundedFilled.CalendarToday
          SnapshotSortType.VideoTitle -> Icons.RoundedFilled.Title
          SnapshotSortType.Position -> Icons.RoundedFilled.AccessTime
        }
      },
    getLabelForType = { type, _ ->
      if (type == labels.getValue(SnapshotSortType.VideoTitle)) {
        "A-Z" to "Z-A"
      } else {
        ascendingLabel to descendingLabel
      }
    },
    showSortOptions = true,
    enableViewModeOptions = false,
    enableLayoutModeOptions = false,
  )
}
