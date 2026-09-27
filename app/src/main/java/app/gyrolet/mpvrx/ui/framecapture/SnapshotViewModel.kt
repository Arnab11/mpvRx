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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.gyrolet.mpvrx.domain.framecapture.FolderWriteResult
import app.gyrolet.mpvrx.domain.framecapture.FrameCapture
import app.gyrolet.mpvrx.domain.framecapture.FrameCaptureRepository
import app.gyrolet.mpvrx.domain.framecapture.SnapshotFolder
import app.gyrolet.mpvrx.domain.framecapture.SnapshotThumbnailStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** One folder's contents, plus the folder list the move dialog needs as targets. */
data class SnapshotFolderContent(
  val folder: SnapshotFolder?,
  val snapshots: List<FrameCapture>,
  val moveTargets: List<SnapshotFolderRow>,
)

class SnapshotViewModel(
  application: Application,
  private val folderId: Long,
) : AndroidViewModel(application), KoinComponent {

  private val repository: FrameCaptureRepository by inject()

  private val thumbnails =
    SnapshotThumbnailStore(context = application, scope = viewModelScope)

  val content: StateFlow<SnapshotFolderContent> =
    combine(repository.observeFolders(), repository.observeAll()) { folders, captures ->
        // Counts come from the shared derivation so the number shown against a folder in the move
        // dialog matches the row count here.
        val library = SnapshotLibraryData.of(folders, captures)
        SnapshotFolderContent(
          folder = folders.firstOrNull { it.id == folderId },
          snapshots = captures.filter { it.folderId == folderId },
          moveTargets = library.folders,
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        SnapshotFolderContent(folder = null, snapshots = emptyList(), moveTargets = emptyList()),
      )

  val thumbnailCache: StateFlow<Map<Long, Bitmap>> = thumbnails.thumbnails

  fun loadThumbnail(capture: FrameCapture) = thumbnails.load(capture)

  fun delete(ids: Collection<Long>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
      repository.deleteAll(ids)
      thumbnails.forget(ids)
    }
  }

  fun move(
    ids: Collection<Long>,
    folderId: Long?,
  ) {
    if (ids.isEmpty()) return
    viewModelScope.launch { repository.moveCaptures(ids, folderId) }
  }

  /**
   * Creating a folder from this page exists for the move dialog's "New folder" entry: the user is
   * filing snapshots, so making the destination on the spot beats backing out to the library page.
   */
  fun createFolder(
    name: String,
    onResult: (FolderWriteResult) -> Unit,
  ) {
    viewModelScope.launch { onResult(repository.createFolder(name)) }
  }

  companion object {
    fun factory(
      application: Application,
      folderId: Long,
    ): ViewModelProvider.Factory =
      viewModelFactory { initializer { SnapshotViewModel(application, folderId) } }
  }
}
