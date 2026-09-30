/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.domain.fonts

import android.content.Context
import android.graphics.Typeface
import android.util.AtomicFile
import app.gyrolet.mpvrx.network.awaitResponse
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GoogleFontFamily(
  val family: String,
  val category: String = "",
  val subsets: List<String> = emptyList(),
  val popularity: Int = Int.MAX_VALUE,
  val isOpenSource: Boolean = true,
)

@Serializable
private data class GoogleFontsCatalog(
  val familyMetadataList: List<GoogleFontFamily> = emptyList(),
)

class GoogleFontsRepository(
  context: Context,
  private val httpClient: OkHttpClient,
) {
  private val appContext = context.applicationContext
  private val directory = File(appContext.filesDir, DIRECTORY_NAME)
  private val catalogFile = File(directory, CATALOG_FILE_NAME)
  private val activeFontFile = File(directory, ACTIVE_FONT_FILE_NAME)
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun loadCatalog(forceRefresh: Boolean = false): Result<List<GoogleFontFamily>> =
    withContext(Dispatchers.IO) {
      val cached = readCatalog()
      if (!forceRefresh && cached != null && catalogFile.isFresh()) {
        return@withContext Result.success(cached)
      }

      try {
        val request =
          Request.Builder()
            .url(CATALOG_URL)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val payload =
          httpClient.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("Google Fonts catalog returned HTTP ${response.code}")
            response.body.string()
          }
        val catalog = parseCatalog(payload)
        writeAtomically(catalogFile, payload.toByteArray(Charsets.UTF_8))
        Result.success(catalog)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        cached?.let(Result.Companion::success) ?: Result.failure(error)
      }
    }

  suspend fun install(family: String): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val fontUrl = resolveTtfUrl(family)
        val request = Request.Builder().url(fontUrl).header("User-Agent", LEGACY_ANDROID_USER_AGENT).get().build()
        val fontBytes =
          httpClient.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) throw IOException("Font download returned HTTP ${response.code}")
            val contentLength = response.body.contentLength()
            if (contentLength > MAX_FONT_BYTES) throw IOException("Font file is too large")
            response.body.bytes().also { bytes ->
              if (bytes.isEmpty() || bytes.size > MAX_FONT_BYTES) throw IOException("Invalid font file size")
            }
          }

        directory.mkdirs()
        val validationFile = File.createTempFile("font-", ".ttf", directory)
        try {
          validationFile.writeBytes(fontBytes)
          runCatching { Typeface.createFromFile(validationFile) }
            .getOrElse { throw IOException("Downloaded font for $family could not be loaded", it) }
        } finally {
          validationFile.delete()
        }
        writeAtomically(activeFontFile, fontBytes)
        Result.success(Unit)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        Result.failure(error)
      }
    }

  fun activeFontFile(): File = activeFontFile

  fun clearActiveFont() {
    activeFontFile.delete()
    File(activeFontFile.path + ".bak").delete()
  }

  private suspend fun resolveTtfUrl(family: String): String {
    val cssUrl =
      CSS_URL
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("family", "$family:400")
        .build()
    val request = Request.Builder().url(cssUrl).header("User-Agent", LEGACY_ANDROID_USER_AGENT).get().build()
    val css =
      httpClient.newCall(request).awaitResponse().use { response ->
        if (!response.isSuccessful) throw IOException("Font stylesheet returned HTTP ${response.code}")
        response.body.string()
      }
    return TTF_URL_REGEX.find(css)?.groupValues?.get(1)
      ?: throw IOException("Google Fonts did not provide a compatible TTF")
  }

  private fun readCatalog(): List<GoogleFontFamily>? =
    runCatching { parseCatalog(AtomicFile(catalogFile).openRead().bufferedReader().use { it.readText() }) }.getOrNull()

  private fun parseCatalog(payload: String): List<GoogleFontFamily> =
    json
      .decodeFromString<GoogleFontsCatalog>(payload)
      .familyMetadataList
      .asSequence()
      .filter { it.family.isNotBlank() && it.isOpenSource }
      .distinctBy { it.family }
      .sortedWith(compareBy<GoogleFontFamily> { it.popularity }.thenBy { it.family })
      .toList()
      .also { if (it.isEmpty()) throw IOException("Google Fonts catalog is empty") }

  private fun File.isFresh(): Boolean =
    isFile && System.currentTimeMillis() - lastModified() < TimeUnit.DAYS.toMillis(CATALOG_MAX_AGE_DAYS)

  private fun writeAtomically(
    target: File,
    bytes: ByteArray,
  ) {
    target.parentFile?.mkdirs()
    val atomicFile = AtomicFile(target)
    val output = atomicFile.startWrite()
    try {
      output.write(bytes)
      atomicFile.finishWrite(output)
    } catch (error: Throwable) {
      atomicFile.failWrite(output)
      throw error
    }
  }

  private companion object {
    const val CATALOG_URL = "https://fonts.google.com/metadata/fonts"
    const val CSS_URL = "https://fonts.googleapis.com/css"
    const val USER_AGENT = "mpvRx Google Fonts"
    const val LEGACY_ANDROID_USER_AGENT =
      "Mozilla/5.0 (Linux; U; Android 4.4; en-us) AppleWebKit/534.30 Version/4.0 Mobile Safari/534.30"
    const val DIRECTORY_NAME = "app-fonts"
    const val CATALOG_FILE_NAME = "catalog.json"
    const val ACTIVE_FONT_FILE_NAME = "active.ttf"
    const val CATALOG_MAX_AGE_DAYS = 7L
    const val MAX_FONT_BYTES = 10 * 1024 * 1024
    val TTF_URL_REGEX = Regex("url\\((https://fonts\\.gstatic\\.com/[^)]+\\.ttf)\\)")
  }
}