/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.fonts.GoogleFontFamily
import app.gyrolet.mpvrx.domain.fonts.GoogleFontsRepository
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.components.AppPickerSheet
import app.gyrolet.mpvrx.presentation.components.PlayerSheetSearchField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun GoogleFontsSheet(
  preferences: AppearancePreferences,
  onDismiss: () -> Unit,
) {
  val repository = koinInject<GoogleFontsRepository>()
  val selectedFamily by preferences.googleFontFamily.collectAsState()
  val useSystemFont by preferences.useSystemFont.collectAsState()
  val scope = rememberCoroutineScope()
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var catalog by remember { mutableStateOf<List<GoogleFontFamily>>(emptyList()) }
  var isLoading by remember { mutableStateOf(true) }
  var catalogFailed by remember { mutableStateOf(false) }
  var downloadingFamily by remember { mutableStateOf<String?>(null) }
  var downloadFailedFamily by remember { mutableStateOf<String?>(null) }
  var refreshKey by remember { mutableIntStateOf(0) }

  LaunchedEffect(refreshKey) {
    isLoading = true
    catalogFailed = false
    repository.loadCatalog(forceRefresh = refreshKey > 0).fold(
      onSuccess = { catalog = it },
      onFailure = { catalogFailed = true },
    )
    isLoading = false
  }

  val filteredFonts =
    remember(catalog, searchQuery) {
      catalog.filter { font ->
        font.family != BUILT_IN_FONT_FAMILY &&
          (searchQuery.isBlank() ||
            font.family.contains(searchQuery, ignoreCase = true) ||
            font.category.contains(searchQuery, ignoreCase = true))
      }
    }

  AppPickerSheet(
    onDismissRequest = onDismiss,
    title = stringResource(R.string.app_font_google_fonts_title),
    subtitle = stringResource(R.string.app_font_attribution),
    scrollContent = false,
    actions = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.generic_cancel))
      }
    },
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      PlayerSheetSearchField(
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        placeholder = stringResource(R.string.generic_search),
      )

      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        item(key = "built-in") {
          AppFontRow(
            title = stringResource(R.string.app_font_google_sans_flex),
            subtitle = stringResource(R.string.app_font_built_in),
            selected = !useSystemFont && selectedFamily.isBlank(),
            onClick = {
              repository.clearActiveFont()
              preferences.googleFontFamily.set("")
              preferences.useSystemFont.set(false)
              onDismiss()
            },
          )
        }
        item(key = "system") {
          AppFontRow(
            title = stringResource(R.string.pref_appearance_system_font_title),
            subtitle = stringResource(R.string.pref_appearance_system_font_summary),
            selected = useSystemFont,
            onClick = {
              preferences.useSystemFont.set(true)
              onDismiss()
            },
          )
        }

        when {
          isLoading -> {
            item(key = "loading") {
              Box(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
              }
            }
          }
          catalogFailed -> {
            item(key = "catalog-error") {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  text = stringResource(R.string.app_font_catalog_error),
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { refreshKey++ }) {
                  Text(stringResource(R.string.ui_retry))
                }
              }
            }
          }
          filteredFonts.isEmpty() -> {
            item(key = "empty") {
              Text(
                text = stringResource(R.string.ui_no_results_found),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
              )
            }
          }
          else -> {
            items(filteredFonts, key = GoogleFontFamily::family) { font ->
              AppFontRow(
                title = font.family,
                subtitle = font.category,
                selected = !useSystemFont && selectedFamily == font.family,
                downloading = downloadingFamily == font.family,
                enabled = downloadingFamily == null,
                onClick = {
                  downloadingFamily = font.family
                  downloadFailedFamily = null
                  scope.launch {
                    repository.install(font.family).fold(
                      onSuccess = {
                        preferences.googleFontFamily.set(font.family)
                        preferences.googleFontRevision.set(preferences.googleFontRevision.get() + 1)
                        preferences.useSystemFont.set(false)
                        onDismiss()
                      },
                      onFailure = { downloadFailedFamily = font.family },
                    )
                    downloadingFamily = null
                  }
                },
              )
            }
          }
        }
      }

      downloadFailedFamily?.let { family ->
        Text(
          text = stringResource(R.string.app_font_download_error, family),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun AppFontRow(
  title: String,
  subtitle: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  downloading: Boolean = false,
  enabled: Boolean = true,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(10.dp),
    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (downloading) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
      } else {
        RadioButton(selected = selected, onClick = null)
      }
      Spacer(Modifier.width(4.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

private const val BUILT_IN_FONT_FAMILY = "Google Sans Flex"