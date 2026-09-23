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
import app.gyrolet.mpvrx.domain.framecapture.FrameCapture
import app.gyrolet.mpvrx.domain.framecapture.FrameCaptureRepository
import app.gyrolet.mpvrx.domain.framecapture.SnapshotImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SnapshotViewModel(
  application: Application,
) : AndroidViewModel(application), KoinComponent {

  private val repository: FrameCaptureRepository by inject()

  val snapshots: StateFlow<List<FrameCapture>> =
    repository
      .observeAll()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  /** Thumbnails keyed by capture id. Cached for the lifetime of the ViewModel. */
  private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
  val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

  private val inFlight = mutableSetOf<Long>()

  fun loadThumbnail(capture: FrameCapture) {
    if (_thumbnails.value.containsKey(capture.id) || !inFlight.add(capture.id)) return
    viewModelScope.launch {
      try {
        val bitmap =
          withContext(Dispatchers.IO) {
            SnapshotImageLoader.load(
              context = getApplication(),
              imageUri = capture.imageUri,
              imagePath = capture.imagePath,
              targetMaxPx = SnapshotImageLoader.THUMBNAIL_MAX_PX,
            )
          }
        if (bitmap != null) {
          _thumbnails.value = _thumbnails.value + (capture.id to bitmap)
        }
      } finally {
        inFlight.remove(capture.id)
      }
    }
  }

  fun delete(ids: Collection<Long>) {
    if (ids.isEmpty()) return
    viewModelScope.launch {
      repository.deleteAll(ids)
      // The grid is driven by the DAO flow, so the removed rows disappear on their own.
      _thumbnails.value = _thumbnails.value - ids.toSet()
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      viewModelFactory {
        initializer { SnapshotViewModel(application) }
      }
  }}
