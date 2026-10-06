package app.gyrolet.mpvrx.ui.preferences

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.media.model.Video
import app.gyrolet.mpvrx.domain.thumbnail.ThumbnailRepository
import app.gyrolet.mpvrx.presentation.components.RemoteImage
import app.gyrolet.mpvrx.repository.WatchMediaStats
import app.gyrolet.mpvrx.repository.WatchStatsRepository
import app.gyrolet.mpvrx.repository.WatchStatsSnapshot
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ProfileWatchStatistics() {
  val repository = koinInject<WatchStatsRepository>()
  val scope = rememberCoroutineScope()
  val revision by repository.revision.collectAsStateWithLifecycle()
  val stats by produceState(WatchStatsSnapshot(), repository, revision) { value = repository.snapshot() }
  var confirmReset by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
  var mediaFilter by rememberSaveable { androidx.compose.runtime.mutableStateOf(0) }

  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = stringResource(R.string.watch_stats_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        enabled = stats.totalSeconds > 0 || stats.sessions > 0 || stats.media.isNotEmpty(),
        onClick = { confirmReset = true },
      ) {
        Text(stringResource(R.string.watch_stats_reset))
      }
    }

    WatchTimeHero(stats)
    WatchSplit(stats)
    WeeklyActivity(stats.days)

    Text(
      text = stringResource(R.string.watch_stats_top_media),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 2.dp),
    )

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      listOf(
        R.string.pref_all_sources,
        R.string.watch_stats_video,
        R.string.watch_stats_audio,
      ).forEachIndexed { index, label ->
        FilterChip(
          selected = mediaFilter == index,
          onClick = { mediaFilter = index },
          label = { Text(stringResource(label)) },
        )
      }
    }

    val topMedia =
      stats.media.entries
        .filter { it.value.seconds > 0 && (mediaFilter == 0 || it.value.isAudio == (mediaFilter == 2)) }
        .sortedByDescending { it.value.seconds }
        .take(8)

    if (topMedia.isEmpty()) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = stringResource(R.string.watch_stats_empty),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(20.dp),
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        topMedia.forEach { (_, media) -> WatchMediaRow(media) }
      }
    }
  }

  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text(stringResource(R.string.watch_stats_reset)) },
      text = { Text(stringResource(R.string.watch_stats_reset_confirm)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmReset = false
            scope.launch { repository.clear() }
          },
        ) {
          Text(
            stringResource(R.string.watch_stats_reset),
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmReset = false }) {
          Text(stringResource(R.string.generic_cancel))
        }
      },
    )
  }
}

@Composable
private fun WatchTimeHero(stats: WatchStatsSnapshot) {
  Surface(
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.watch_stats_total_time),
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          text = formatWatchDuration(stats.totalSeconds),
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = stringResource(R.string.watch_stats_sessions, stats.sessions),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        )
      }

      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(58.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.RoundedFilled.History,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun WatchSplit(stats: WatchStatsSnapshot) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StatTile(
      label = stringResource(R.string.watch_stats_video),
      seconds = stats.videoSeconds,
      containerColor = MaterialTheme.colorScheme.tertiaryContainer,
      contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
      modifier = Modifier.weight(1f),
    )
    StatTile(
      label = stringResource(R.string.watch_stats_audio),
      seconds = stats.audioSeconds,
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun StatTile(
  label: String,
  seconds: Long,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(22.dp),
    color = containerColor,
    contentColor = contentColor,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor.copy(alpha = 0.78f),
      )
      Text(
        text = formatWatchDuration(seconds),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun WeeklyActivity(days: Map<String, Long>) {
  val today = LocalDate.now()
  val dates = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
  val values = dates.map { days[it.toString()] ?: 0L }
  val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
  val color = MaterialTheme.colorScheme.primary

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(R.string.watch_stats_last_seven_days),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      val chartDescription =
        dates.zip(values).joinToString { (date, seconds) ->
          "$date: ${formatWatchDuration(seconds)}"
        }

      Canvas(
        Modifier
          .fillMaxWidth()
          .height(104.dp)
          .semantics { contentDescription = chartDescription },
      ) {
        val gap = 8.dp.toPx()
        val width = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, value ->
          val barHeight =
            (size.height * (value.toFloat() / max.toFloat()))
              .coerceAtLeast(3.dp.toPx())
          drawRoundRect(
            color = if (value > 0) color else color.copy(alpha = 0.14f),
            topLeft = Offset(index * (width + gap), size.height - barHeight),
            size = Size(width, barHeight),
            cornerRadius = CornerRadius(width / 2f, width / 2f),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        dates.forEach { date ->
          Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
              text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WatchMediaRow(media: WatchMediaStats) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      WatchMediaArtwork(media)

      Column(Modifier.weight(1f)) {
        Text(
          text = media.title,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = stringResource(if (media.isAudio) R.string.watch_stats_audio else R.string.watch_stats_video),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = formatWatchDuration(media.seconds),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun WatchMediaArtwork(media: WatchMediaStats) {
  val context = LocalContext.current
  val thumbnailRepository = koinInject<ThumbnailRepository>()
  val density = LocalDensity.current
  val widthPx = with(density) { 72.dp.roundToPx() }
  val heightPx = with(density) { 48.dp.roundToPx() }

  val generatedThumbnail by
    produceState<Bitmap?>(
      initialValue = null,
      media.sourceUri,
      media.title,
      media.isAudio,
      media.artworkUri,
      widthPx,
      heightPx,
    ) {
      if (media.isAudio || !media.artworkUri.isNullOrBlank()) return@produceState
      val video =
        withContext(Dispatchers.IO) {
          resolveWatchStatsVideo(context, media)
        } ?: return@produceState
      value = thumbnailRepository.getThumbnail(video, widthPx, heightPx)
    }

  Box(
    modifier =
      Modifier
        .width(72.dp)
        .height(48.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
  ) {
    when {
      !media.artworkUri.isNullOrBlank() -> {
        RemoteImage(
          url = media.artworkUri,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
      }

      generatedThumbnail != null -> {
        Image(
          bitmap = generatedThumbnail!!.asImageBitmap(),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
      }

      else -> {
        Icon(
          imageVector =
            if (media.isAudio) {
              Icons.RoundedFilled.Audiotrack
            } else {
              Icons.RoundedFilled.PlayArrow
            },
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(24.dp),
        )
      }
    }
  }
}

private fun resolveWatchStatsVideo(
  context: Context,
  media: WatchMediaStats,
): Video? {
  val source =
    media.sourceUri
      ?.takeIf(String::isNotBlank)
      ?: findLegacyVideoUri(context, media.title)?.toString()
      ?: return null

  val parsed = runCatching { Uri.parse(source) }.getOrNull()
  val uri =
    if (parsed == null || parsed.scheme.isNullOrBlank()) {
      Uri.fromFile(File(source))
    } else {
      parsed
    }

  return Video(
    id = source.hashCode().toLong(),
    title = media.title,
    displayName = media.title,
    path = source,
    uri = uri,
    duration = 0L,
    durationFormatted = "",
    size = 0L,
    sizeFormatted = "",
    dateModified = 0L,
    dateAdded = 0L,
    mimeType = "video/*",
    bucketId = "",
    bucketDisplayName = "",
    width = 0,
    height = 0,
    fps = 0f,
    resolution = "",
    isAudio = false,
  )
}

/**
 * Older watch_stats.json entries predate sourceUri. Recover a local video by its display name so
 * those rows can get thumbnails immediately instead of waiting for the item to be played again.
 */
private fun findLegacyVideoUri(
  context: Context,
  title: String,
): Uri? {
  val displayName = title.substringAfterLast('/').substringAfterLast('\\').trim()
  if (displayName.isBlank()) return null

  val resolver = context.contentResolver
  val projection =
    arrayOf(
      MediaStore.Video.Media._ID,
      MediaStore.Video.Media.DISPLAY_NAME,
    )

  fun query(selection: String, argument: String): Uri? =
    runCatching {
      resolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        arrayOf(argument),
        "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
      )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
      }
    }.getOrNull()

  query("${MediaStore.Video.Media.DISPLAY_NAME} = ?", displayName)?.let { return it }

  val stem = displayName.substringBeforeLast('.', displayName)
  if (stem.isNotBlank()) {
    query("${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?", "$stem.%")?.let { return it }
  }
  return null
}

private fun formatWatchDuration(seconds: Long): String =
  DateUtils.formatElapsedTime(seconds.coerceAtLeast(0))
