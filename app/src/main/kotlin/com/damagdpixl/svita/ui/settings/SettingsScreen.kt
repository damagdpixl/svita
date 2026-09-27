package com.damagdpixl.svita.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Charcoal
import com.damagdpixl.svita.core.designsystem.CharcoalPanel
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.CreamMenuBar
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.data.DebugLogExporter
import com.damagdpixl.svita.ui.wardrobe.EditorialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext

/**
 * Settings home — the hub of the customization USP: categories/fields, the tag
 * manager, the data-export stub, the MANUAL debug-log export (nothing is ever
 * collected or sent by itself) and the about panel.
 */
@Composable
fun SettingsScreen(
    onOpenCustomization: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { DebugLogExporter(context) }
    var logStatus by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .testTag("screen_settings"),
    ) {
        Text(
            text = stringResource(R.string.settings_headline),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
        )

        CreamMenuBar(
            label = stringResource(R.string.settings_menu_customization),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenCustomization)
                .testTag("settings_row_customization"),
            trailing = {
                Text(
                    text = stringResource(R.string.settings_menu_customization_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Charcoal.copy(alpha = 0.45f),
                )
                Text(text = "›", style = MaterialTheme.typography.titleMedium)
            },
        )
        CreamMenuBar(
            label = stringResource(R.string.settings_menu_avatar),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAvatar)
                .testTag("settings_row_avatar"),
            trailing = {
                Text(
                    text = stringResource(R.string.settings_menu_avatar_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Charcoal.copy(alpha = 0.45f),
                )
                Text(text = "›", style = MaterialTheme.typography.titleMedium)
            },
        )
        CreamMenuBar(
            label = stringResource(R.string.settings_menu_tags),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenTags)
                .testTag("settings_row_tags"),
            trailing = {
                Text(
                    text = stringResource(R.string.settings_menu_tags_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Charcoal.copy(alpha = 0.45f),
                )
                Text(text = "›", style = MaterialTheme.typography.titleMedium)
            },
        )
        // Data export lands with the P1 T6 wiring — a visible, inert stub.
        CreamMenuBar(
            label = stringResource(R.string.settings_menu_export),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_row_export"),
            trailing = {
                Text(
                    text = stringResource(R.string.settings_menu_export_stub),
                    style = MaterialTheme.typography.labelSmall,
                    color = Charcoal.copy(alpha = 0.45f),
                )
            },
        )
        CreamMenuBar(
            label = stringResource(R.string.settings_menu_debug_log),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        val exported = withContext(Dispatchers.IO) { exporter.export() }
                        exported.fold(
                            onSuccess = { file ->
                                logStatus = context.getString(
                                    R.string.settings_debug_log_ready,
                                    file.name,
                                )
                                // Share glue is best-effort: the file's existence
                                // is the user-facing fact, the chooser may fail
                                // on hosts without a share target.
                                runCatching {
                                    shareDebugLog(context, exporter.shareUri(file))
                                }
                            },
                            onFailure = {
                                logStatus = context.getString(R.string.settings_debug_log_failed)
                            },
                        )
                    }
                }
                .testTag("settings_row_debug_log"),
            trailing = {
                Text(text = "›", style = MaterialTheme.typography.titleMedium)
            },
        )
        Text(
            text = stringResource(R.string.settings_debug_log_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(start = 16.dp, top = 4.dp)
                .testTag("settings_debug_log_note"),
        )
        CreamMenuBar(
            label = stringResource(R.string.settings_menu_about),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAbout = true }
                .testTag("settings_row_about"),
            trailing = {
                Text(text = "›", style = MaterialTheme.typography.titleMedium)
            },
        )

        logStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("settings_debug_log_status"),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        CharcoalPanel {
            Text(
                text = stringResource(R.string.manifesto_line),
                style = MaterialTheme.typography.labelSmall,
                color = Cream,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showAbout) {
        EditorialDialog(onDismiss = { showAbout = false }, scrimTag = "about_scrim") {
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Cream,
                modifier = Modifier.testTag("about_title"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            val version = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty()
            }
            Text(
                text = stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream,
            )
            Text(
                text = stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            PillButton(
                text = stringResource(R.string.action_back),
                onClick = { showAbout = false },
                modifier = Modifier.testTag("about_close"),
            )
        }
    }
}

/** Opens the system share sheet for the exported log. Manual only. */
private fun shareDebugLog(context: Context, uri: android.net.Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }
}
