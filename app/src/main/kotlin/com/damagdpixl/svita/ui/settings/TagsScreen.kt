package com.damagdpixl.svita.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.damagdpixl.svita.R
import com.damagdpixl.svita.core.designsystem.Cream
import com.damagdpixl.svita.core.designsystem.GreenCta
import com.damagdpixl.svita.core.designsystem.PillButton
import com.damagdpixl.svita.core.model.Tag
import com.damagdpixl.svita.data.SvitaGraph
import com.damagdpixl.svita.ui.wardrobe.EditorialDialog
import com.damagdpixl.svita.ui.wardrobe.OutlinedInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Rename outcome surfaced as an inline Ukrainian error in the dialog. */
sealed interface TagsEvent {
    data object RenameDuplicate : TagsEvent
}

class TagsViewModel(private val graph: SvitaGraph.Graph) : ViewModel() {

    private val repos = graph.repos

    val tags: StateFlow<List<Tag>> = repos.wardrobe.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _event = MutableStateFlow<TagsEvent?>(null)
    val event: StateFlow<TagsEvent?> = _event

    /**
     * In-place rename: item links survive. [onResult] is false on a duplicate
     * name (UNIQUE) — the dialog stays open with the Ukrainian error.
     */
    fun renameTag(id: Long, newName: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = runCatching { repos.wardrobe.renameTag(id, newName.trim()) }.isSuccess
            _event.value = if (success) null else TagsEvent.RenameDuplicate
            onResult(success)
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            repos.wardrobe.deleteTag(id)
        }
    }

    fun consumeEvent() {
        _event.value = null
    }
}

/**
 * Теги й сезони: rename (in place, links survive) and delete for user tags;
 * seasons are product-fixed and stated as such.
 */
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TagsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TagsViewModel(SvitaGraph.get()) }
        },
    )
    val tags by viewModel.tags.collectAsState()
    val event by viewModel.event.collectAsState()

    var renaming by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("tags_root"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("tags_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.settings_menu_tags),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.tags_seasons_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 12.dp)
                    .testTag("tags_seasons_note"),
            )
        }

        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.tags_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 20.dp).testTag("tags_empty"),
            )
        } else {
            LazyColumn(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("tags_list")) {
                itemsIndexed(tags, key = { _, tag -> tag.id }) { _, tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tag_row_${tag.id}"),
                        )
                        Text(
                            text = stringResource(R.string.tag_rename),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .clickable { renaming = tag }
                                .padding(6.dp)
                                .testTag("tag_rename_${tag.id}"),
                        )
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable { deleting = tag }
                                .padding(6.dp)
                                .testTag("tag_delete_${tag.id}"),
                        )
                    }
                }
            }
        }
    }

    renaming?.let { tag ->
        var newName by remember(tag.id) { mutableStateOf(tag.name) }
        EditorialDialog(onDismiss = { renaming = null }, scrimTag = "tag_rename_scrim") {
            Text(
                text = stringResource(R.string.tag_rename),
                style = MaterialTheme.typography.headlineSmall,
                color = Cream,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedInput(
                value = newName,
                onValueChange = {
                    newName = it
                    viewModel.consumeEvent()
                },
                label = stringResource(R.string.tag_new_name),
                errorRes = if (event is TagsEvent.RenameDuplicate) R.string.tag_rename_duplicate else null,
                testTagValue = "tag_rename_input",
                onDark = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Spacer(modifier = Modifier.weight(1f))
                PillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { renaming = null },
                    modifier = Modifier.testTag("tag_rename_cancel"),
                )
                Spacer(modifier = Modifier.padding(4.dp))
                GreenCta(
                    text = stringResource(R.string.action_save),
                    enabled = newName.isNotBlank(),
                    onClick = {
                        viewModel.renameTag(tag.id, newName) { success ->
                            if (success) renaming = null
                        }
                    },
                    modifier = Modifier.testTag("tag_rename_save"),
                )
            }
        }
    }

    deleting?.let { tag ->
        EditorialDialog(onDismiss = { deleting = null }, scrimTag = "tag_delete_scrim") {
            Text(
                text = stringResource(R.string.tag_delete_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Cream,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tag_delete_confirm_text, tag.name),
                style = MaterialTheme.typography.bodyMedium,
                color = Cream.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                PillButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { deleting = null },
                    modifier = Modifier.testTag("tag_delete_cancel"),
                )
                Spacer(modifier = Modifier.padding(4.dp))
                GreenCta(
                    text = stringResource(R.string.action_delete),
                    onClick = {
                        viewModel.deleteTag(tag.id)
                        deleting = null
                    },
                    modifier = Modifier.testTag("tag_delete_confirm"),
                )
            }
        }
    }
}
