package com.damagdpixl.svita.ui.wardrobe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.damagdpixl.svita.core.data.SvitaRepositories

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
 */
@Composable
fun rememberPhotoPicker(
    maxItems: Int = 8,
    onPicked: (List<Uri>) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems),
    ) { uris -> onPicked(uris) }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}
