/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.repository.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import app.gyrolet.mpvrx.data.lyrics.LrcLibApiService
import app.gyrolet.mpvrx.data.lyrics.LrcLibResponse
import app.gyrolet.mpvrx.domain.lyrics.Lyrics
import app.gyrolet.mpvrx.domain.lyrics.LyricsSourceType
import app.gyrolet.mpvrx.utils.media.EmbeddedLyricsExtractor
import app.gyrolet.mpvrx.utils.media.LyricsUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class LyricsResult(
  val embeddedLyrics: Lyrics? = null,
  val onlineLyrics: Lyrics? = null,
  val activeLyrics: Lyrics? = null,
  val selectedSource: LyricsSourceType = LyricsSourceType.EMBEDDED,
  val availableSources: List<LyricsSourceType> = emptyList(),
)

class LyricsRepository(
  private val context: Context,
  private val lrcLibApiService: LrcLibApiService,
) {
  companion object {
    private const val TAG = "LyricsRepository"
    private val BRACKETED_REGEX = Regex("""[\(\[\{\uFF08\uFF3B\uFF5B\u3010\u300E\u300C\u3014\u3008\u300A]([^)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]*)[\)\]\}\uFF09\uFF3D\uFF5D\u3011\u300F\u300D\u3015\u3009\u300B]""")
    private val TRACK_NO_REGEX = Regex("""^\s*\d{1,3}\s*[\._-]\s+""")
    private val YOUTUBE_ID_REGEX = Regex("""\s*\[[a-zA-Z0-9_-]{6,16}\]\s*$""")
    private val MEDIA_EXT_REGEX = Regex("""\.(mp3|flac|m4a|aac|wav|ogg|opus|wma|alac|ape|mp4|mkv|webm|avi|mov|flv|wmv|m4v|3gp|ts)$""", RegexOption.IGNORE_CASE)
    private val YOUTUBE_NOISE_REGEX = Regex("""(?i)\b(official\s+(music\s+)?video|official\s+audio|official\s+lyric\s+video|lyric\s+video|lyrics\s+video|lyrical\s+video|full\s+song\s+lyrics|full\s+song|full\s+video|video\s+song|music\s+video|lyrical|lyrics|audio|visualizer|remastered|remaster|4k|hd|8k|mv)\b""")
    private val SEGMENT_DELIMITER_REGEX = Regex("""\s*[|｜¦/／]\s*""")
    private val HYPHEN_DELIMITER_REGEX = Regex("""\s*[-–—－]\s*""")
    private val UNKNOWN_ARTISTS = setOf("", "unknown", "unknown artist", "<unknown>", "various artists", "various")
    private const val MAX_ONLINE_LOOKUP_ATTEMPTS = 8
  }

  // Cache by media path -> LyricsResult
  private data class CacheKey(val mediaPath: String, val allowOnline: Boolean)
  private val cache = LruCache<CacheKey, LyricsResult>(64)

  private fun cleanTitle(title: String): String {
    return title
      .replace(MEDIA_EXT_REGEX, "")
      .replace(YOUTUBE_ID_REGEX, "")
      .replace(TRACK_NO_REGEX, "")
      .trim()
  }

  private fun superCleanTitle(title: String): String {
    val step1 = cleanTitle(title)
    val firstSeg = step1.split(SEGMENT_DELIMITER_REGEX).firstOrNull()?.trim().orEmpty().ifBlank { step1 }
    val step2 = BRACKETED_REGEX.replace(firstSeg, "").trim()
    val step3 = step2.split(" feat.", " ft.", " featuring", " Feat.", " Ft.", " feat ", " ft ").first().trim()
    val step4 = YOUTUBE_NOISE_REGEX.replace(step3, "").trim()
    return step4.ifBlank { step3.ifBlank { step1 } }
  }

  private fun cleanArtist(artist: String?): String {
    val raw = artist?.trim().orEmpty()
    if (raw.lowercase() in UNKNOWN_ARTISTS) return ""
    return raw.split(" feat.", " ft.", " featuring", " Feat.", " Ft.", " feat ", " ft ").first().trim()
  }

  private fun isLikelyChannelOrLabel(artist: String?): Boolean {
    val lower = artist?.trim().orEmpty().lowercase()
    if (lower.isBlank() || lower in UNKNOWN_ARTISTS) return true
    return lower.endsWith(" music") || lower.endsWith(" records") ||
      lower.endsWith(" official") || lower.endsWith(" entertainment") ||
      lower.endsWith(" channel") || lower.endsWith(" series") ||
      lower.endsWith(" films") || lower.endsWith(" company") ||
      lower.endsWith(" vevo") || lower.contains("t-series") ||
      lower.contains("tseries") || lower.contains("saregama") ||
      lower.contains("zee music") || lower.contains("sony music") ||
      lower.contains("tips official") || lower.contains("yrf") ||
      lower.contains("speed records") || lower.contains("geet mp3") ||
      lower.contains("desire music") || lower.contains("lofi girl") ||
      lower.contains("aditya music") || lower.contains("lahari music")
  }

  suspend fun loadLyricsForTrack(
    mediaPath: String,
    title: String?,
    artist: String?,
    durationSeconds: Int = 0,
    forceRefresh: Boolean = false,
    allowOnline: Boolean = true,
  ): LyricsResult = withContext(Dispatchers.IO) {
    val cacheKey = CacheKey(mediaPath, allowOnline)
    if (!forceRefresh) {
      cache.get(cacheKey)?.let { return@withContext it }
    }

    Log.d(TAG, "Loading lyrics for: $title by $artist ($mediaPath)")

    // 1. Check embedded lyrics first
    val embedded = EmbeddedLyricsExtractor.extractEmbeddedLyrics(context, mediaPath)

    // 2. Fetch online lyrics from LRCLIB
    val online = if (allowOnline) fetchOnlineLyrics(title, artist, durationSeconds) else null

    // 3. Determine available sources and default preference (Embedded first if available)
    val sources = mutableListOf<LyricsSourceType>()
    if (embedded != null && embedded.isValid()) {
      sources.add(if (embedded.sourceType == LyricsSourceType.LOCAL) LyricsSourceType.LOCAL else LyricsSourceType.EMBEDDED)
    }
    if (online != null && online.isValid()) {
      sources.add(LyricsSourceType.ONLINE)
    }

    val defaultSelected = when {
      embedded != null && embedded.isValid() -> {
        if (embedded.synced.isNullOrEmpty() && online != null && !online.synced.isNullOrEmpty()) {
          LyricsSourceType.ONLINE
        } else {
          embedded.sourceType
        }
      }
      online != null && online.isValid() -> LyricsSourceType.ONLINE
      else -> LyricsSourceType.EMBEDDED
    }

    val active = when (defaultSelected) {
      LyricsSourceType.EMBEDDED, LyricsSourceType.LOCAL -> embedded ?: online
      LyricsSourceType.ONLINE -> online ?: embedded
    }

    val result = LyricsResult(
      embeddedLyrics = embedded,
      onlineLyrics = online,
      activeLyrics = active,
      selectedSource = defaultSelected,
      availableSources = sources.distinct(),
    )

    cache.put(cacheKey, result)
    result
  }

  suspend fun fetchOnlineLyrics(
    rawTitle: String?,
    rawArtist: String?,
    durationSeconds: Int = 0,
  ): Lyrics? = withContext(Dispatchers.IO) {
    if (rawTitle.isNullOrBlank()) return@withContext null

    val baseTitle = cleanTitle(rawTitle)
    val segments = baseTitle.split(SEGMENT_DELIMITER_REGEX).map { it.trim() }.filter { it.isNotBlank() }
    val firstSeg = segments.firstOrNull() ?: baseTitle
    val primaryTitle = superCleanTitle(firstSeg)

    val cleanRawArtist = cleanArtist(rawArtist)
    val isChannel = isLikelyChannelOrLabel(cleanRawArtist)
    val validMetadataArtist = if (!isChannel) cleanRawArtist else ""

    val attempted = mutableSetOf<String>()
    var plainFallback: Lyrics? = null

    suspend fun tryGet(track: String, artistName: String): Lyrics? {
      val cleanT = track.trim()
      val cleanA = artistName.trim()
      if (cleanT.isBlank() || cleanA.isBlank()) return null
      val key = "get:$cleanT:$cleanA".lowercase(Locale.ROOT)
      if (attempted.size >= MAX_ONLINE_LOOKUP_ATTEMPTS || !attempted.add(key)) return null

      // Try with exact duration first if available
      val resp = lrcLibApiService.getLyrics(
        trackName = cleanT,
        artistName = cleanA,
        duration = if (durationSeconds > 0) durationSeconds else null,
      ) ?: if (durationSeconds > 0) {
        // If exact duration get failed (e.g. slight mismatch), try without duration
        lrcLibApiService.getLyrics(trackName = cleanT, artistName = cleanA, duration = null)
      } else null

      if (resp != null) {
        if (!resp.syncedLyrics.isNullOrBlank()) {
          val parsed = LyricsUtils.parseLyrics(resp.syncedLyrics, sourceType = LyricsSourceType.ONLINE)
          if (parsed.isValid() && !parsed.synced.isNullOrEmpty()) return parsed
        }
        if (!resp.plainLyrics.isNullOrBlank() && plainFallback == null) {
          val parsed = LyricsUtils.parseLyrics(resp.plainLyrics, sourceType = LyricsSourceType.ONLINE)
          if (parsed.isValid()) plainFallback = parsed
        }
      }
      return null
    }

    suspend fun trySearch(q: String? = null, track: String? = null, artistName: String? = null): Lyrics? {
      val cleanQ = q?.trim()?.takeIf { it.isNotBlank() }
      val cleanT = track?.trim()?.takeIf { it.isNotBlank() }
      val cleanA = artistName?.trim()?.takeIf { it.isNotBlank() }
      if (cleanQ == null && cleanT == null && cleanA == null) return null
      val key = "search:$cleanQ:$cleanT:$cleanA".lowercase(Locale.ROOT)
      if (attempted.size >= MAX_ONLINE_LOOKUP_ATTEMPTS || !attempted.add(key)) return null
      val res = lrcLibApiService.searchLyrics(
        query = cleanQ,
        trackName = cleanT,
        artistName = cleanA,
      )
      val best = extractBestMatch(res, durationSeconds)
      if (best != null) {
        if (!best.synced.isNullOrEmpty()) return best
        if (plainFallback == null) plainFallback = best
      }
      return null
    }

    try {
      // 1. If metadata artist is a real artist (not a YouTube channel/label)
      if (validMetadataArtist.isNotBlank()) {
        tryGet(primaryTitle, validMetadataArtist)?.let { return@withContext it }
        trySearch(track = primaryTitle, artistName = validMetadataArtist)?.let { return@withContext it }
        trySearch(q = "$validMetadataArtist $primaryTitle")?.let { return@withContext it }
      }

      // 2. If first segment contains "Artist - Title" or "Title - Soundtrack"
      if (firstSeg.contains(HYPHEN_DELIMITER_REGEX)) {
        val parts = firstSeg.split(HYPHEN_DELIMITER_REGEX, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
          val p0 = superCleanTitle(parts[0])
          val p1 = superCleanTitle(parts[1])

          // Try p0 as artist, p1 as track (e.g. A.R. Rahman - Raanjhanaa)
          tryGet(p1, p0)?.let { return@withContext it }
          trySearch(track = p1, artistName = p0)?.let { return@withContext it }
          trySearch(q = "$p0 $p1")?.let { return@withContext it }

          // Try p0 as track, p1 as artist/album (e.g. Vaaroon - Mirzapur)
          tryGet(p0, p1)?.let { return@withContext it }
          trySearch(track = p0, artistName = p1)?.let { return@withContext it }
          trySearch(q = "$p0 $p1")?.let { return@withContext it }
          trySearch(track = p0)?.let { return@withContext it }
          trySearch(q = p0)?.let { return@withContext it }
        }
      }

      // 3. Multi-segment artist/album search (e.g. Title ｜ Album ｜ Artists)
      for (otherSeg in segments.drop(1)) {
        val cleanSeg = YOUTUBE_NOISE_REGEX.replace(BRACKETED_REGEX.replace(otherSeg, "").trim(), "").trim()
        if (cleanSeg.isNotBlank() && !isLikelyChannelOrLabel(cleanSeg)) {
          val firstArtist = cleanArtist(cleanSeg)
          if (firstArtist.isNotBlank()) {
            tryGet(primaryTitle, firstArtist)?.let { return@withContext it }
            trySearch(track = primaryTitle, artistName = firstArtist)?.let { return@withContext it }
            trySearch(q = "$primaryTitle $firstArtist")?.let { return@withContext it }
          }
          trySearch(q = "$primaryTitle $cleanSeg")?.let { return@withContext it }
        }
      }

      // 4. Title-only searches (pure song title search without artist constraints)
      trySearch(track = primaryTitle)?.let { return@withContext it }
      trySearch(q = primaryTitle)?.let { return@withContext it }

      // 5. Fallback with raw first segment
      val rawFirstClean = cleanTitle(firstSeg)
      if (rawFirstClean != primaryTitle && rawFirstClean.isNotBlank()) {
        trySearch(track = rawFirstClean)?.let { return@withContext it }
        trySearch(q = rawFirstClean)?.let { return@withContext it }
      }

      // Return plainFallback if no synced lyrics were found
      return@withContext plainFallback
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (e: Exception) {
      Log.w(TAG, "Failed to fetch online lyrics: ${e.message}")
    }

    plainFallback
  }

  private fun extractBestMatch(
    responses: List<LrcLibResponse>,
    targetDurationSec: Int = 0,
  ): Lyrics? {
    if (responses.isEmpty()) return null
    val candidates = responses.filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
    if (candidates.isEmpty()) return null

    val syncedCandidates = candidates.filter { !it.syncedLyrics.isNullOrBlank() }
    val bestMatch = if (syncedCandidates.isNotEmpty()) {
      if (targetDurationSec > 0) {
        syncedCandidates.minByOrNull { item ->
          if (item.duration > 0) Math.abs(item.duration - targetDurationSec) else 20.0
        } ?: syncedCandidates.first()
      } else {
        syncedCandidates.first()
      }
    } else {
      if (targetDurationSec > 0) {
        candidates.minByOrNull { item ->
          if (item.duration > 0) Math.abs(item.duration - targetDurationSec) else 20.0
        } ?: candidates.first()
      } else {
        candidates.first()
      }
    }

    val raw = bestMatch.syncedLyrics ?: bestMatch.plainLyrics ?: return null
    val parsed = LyricsUtils.parseLyrics(raw, sourceType = LyricsSourceType.ONLINE)
    return if (parsed.isValid()) parsed else null
  }

  fun switchSource(mediaPath: String, sourceType: LyricsSourceType, allowOnline: Boolean = true): LyricsResult? {
    if (!allowOnline && sourceType == LyricsSourceType.ONLINE) return null
    val cacheKey = CacheKey(mediaPath, allowOnline)
    val existing = cache.get(cacheKey) ?: return null
    val newActive = when (sourceType) {
      LyricsSourceType.EMBEDDED, LyricsSourceType.LOCAL -> existing.embeddedLyrics ?: existing.onlineLyrics
      LyricsSourceType.ONLINE -> existing.onlineLyrics ?: existing.embeddedLyrics
    }
    val updated = existing.copy(
      selectedSource = sourceType,
      activeLyrics = newActive,
    )
    cache.put(cacheKey, updated)
    return updated
  }
}
