package com.damagdpixl.svita.ui.wardrobe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.data.SvitaRepositories
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.Marigold
import com.damagdpixl.svita.core.model.Subtype

/**
 * Loads the full subtype taxonomy grouped by category (sort order of the seed),
 * dropping empty groups. One read per category — a handful of fast local
 * queries on a local SQLite file.
 */
suspend fun loadSubtypeGroups(repos: SvitaRepositories): List<SubtypeGroup> =
    repos.taxonomy.categories().mapNotNull { category ->
        val subtypes = repos.taxonomy.subtypesByCategory(category.id)
        if (subtypes.isEmpty()) null else SubtypeGroup(category, subtypes)
    }

/**
 * System Photo Picker launcher (no permission needed): returns a click callback
 * that opens the picker and forwards the picked URIs to [onPicked].
 *
 * [maxItems]: an explicit cap for the picker window; `null` = the platform
 * maximum — `PickMultipleVisualMedia()` configures itself with
 * `MediaStore.getPickImagesMaxLimit()` (100 on system-picker devices) and falls
 * back to unlimited `ACTION_OPEN_DOCUMENT` multi-select where the system picker
 * is unavailable (pre-T). The bulk-import screen uses `null`, the single-item
 * editor keeps a small explicit cap.
 */
@Composable
fun rememberPhotoPicker(
    onPicked: (List<Uri>) -> Unit,
    maxItems: Int? = null,
): () -> Unit {
    val contract = if (maxItems == null) {
        ActivityResultContracts.PickMultipleVisualMedia()
    } else {
        ActivityResultContracts.PickMultipleVisualMedia(maxItems)
    }
    val launcher = rememberLauncherForActivityResult(contract) { uris -> onPicked(uris) }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

/**
 * Subtype picker grouped by category, shared by the item editor and the bulk
 * import screen. Test tags are parameterized so both screens keep stable,
 * non-colliding tags ([optionTagPrefix] + "_" + subtype key).
 */
@Composable
fun SubtypePickerDialog(
    groups: List<SubtypeGroup>,
    selectedId: Long?,
    localize: (Subtype) -> String,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    listTag: String = "subtype_picker_list",
    optionTagPrefix: String = "editor_subtype",
) {
    EditorialDialog(onDismiss = onDismiss, scrimTag = "subtype_scrim") {
        Text(
            text = stringResource(R.string.editor_subtype_pick_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Cream,
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .testTag(listTag),
        ) {
            groups.forEach { group ->
                item(key = "group_${group.category.id}") {
                    Text(
                        text = if (LocalConfiguration.current.locales[0].language == "uk") {
                            group.category.nameUk
                        } else {
                            group.category.nameEn
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Cream.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(
                    items = group.subtypes,
                    key = { _, subtype -> "pick_${subtype.id}" },
                ) { _, subtype ->
                    val selected = subtype.id == selectedId
                    Text(
                        text = localize(subtype),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) Marigold else Cream,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) Marigold.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onSelect(subtype.id) }
                            .padding(horizontal = 8.dp, vertical = 9.dp)
                            .testTag("${optionTagPrefix}_${subtype.key}"),
                    )
                }
            }
        }
    }
}
