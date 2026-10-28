package com.henriquesebastiao.downtify.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.model.Track
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class TrashController(
    val canManage: Boolean = false,
    val preview: (List<Track>) -> Unit = {},
    val choose: (List<Track>) -> Unit = {},
    val open: () -> Unit = {},
)

val LocalTrashController = staticCompositionLocalOf { TrashController() }

@Composable
fun LibraryTrashHost(content: @Composable () -> Unit) {
    val model: TrashViewModel = hiltViewModel()
    val canManage by model.canManage.collectAsStateWithLifecycle()
    val controller = remember(canManage, model) {
        TrashController(canManage, model::preview, model::choose, model::open)
    }
    CompositionLocalProvider(LocalTrashController provides controller) { content() }
    TrashDialogs(model)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashDialogs(model: TrashViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.open, lifecycle) {
        if (state.open) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    model.refresh()
                    delay(3000)
                }
            }
        }
    }
    val preview = state.preview
    val noOpenDialog = !state.open && state.choosing == null
    val preparing = state.busy && !state.preparationHidden
    if (preparing && noOpenDialog && preview == null) {
        AlertDialog(
            onDismissRequest = model::hidePreparation,
            title = { Text(stringResource(R.string.trash_verify)) },
            text = { Text(stringResource(R.string.trash_preparing)) },
            confirmButton = {
                TextButton(onClick = model::hidePreparation) { Text(stringResource(R.string.trash_keep_browsing)) }
            },
        )
    }
    if (preview != null) {
        AlertDialog(
            onDismissRequest = model::dismissPreview,
            title = { Text(stringResource(R.string.trash_confirm_title, preview.items.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.trash_confirm_body))
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(preview.items, key = { it.id }) { item ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(item.metadata.title.ifBlank { item.file })
                                Text(item.metadata.codec.ifBlank { item.file.substringAfterLast('.') })
                                Text(item.file, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    state.message?.let { Text(trashMessage(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !state.busy, onClick = model::confirm) {
                    Text(stringResource(R.string.trash_move))
                }
            },
            dismissButton = {
                TextButton(enabled = !state.busy, onClick = model::dismissPreview) {
                    Text(stringResource(R.string.trash_cancel))
                }
            },
        )
    }
    val noDialog = !state.open && state.choosing == null
    if (noDialog && preview == null && state.message != null) {
        AlertDialog(
            onDismissRequest = model::dismissPreview,
            text = { Text(trashMessage(state.message.orEmpty())) },
            confirmButton = {
                TextButton(onClick = model::dismissPreview) { Text(stringResource(R.string.trash_close)) }
            },
        )
    }
    val choosing = state.choosing
    if (choosing != null) {
        Dialog(
            onDismissRequest = model::dismissPreview,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(R.string.trash_choose, state.chosen.size)) }, actions = {
                    TextButton(onClick = model::dismissPreview) { Text(stringResource(R.string.trash_cancel)) }
                    TextButton(enabled = state.chosen.isNotEmpty(), onClick = model::previewChoice) {
                        Text(stringResource(R.string.trash_verify))
                    }
                })
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(choosing, key = { it.id }) { track ->
                        Row(Modifier.fillMaxWidth().padding(8.dp)) {
                            Checkbox(
                                checked = track.id in state.chosen,
                                onCheckedChange = { model.toggleChoice(track.id) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(track.displayTitle)
                                Text(track.displayArtist, style = MaterialTheme.typography.bodySmall)
                                Text(track.codec + " · " + track.file, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.open) TrashPage(state, model)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashPage(state: TrashUiState, model: TrashViewModel) {
    var allowId by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = model::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.trash_title)) }, actions = {
                TextButton(onClick = model::close) { Text(stringResource(R.string.trash_close)) }
            })
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.trash_help))
                OutlinedTextField(
                    value = state.search,
                    onValueChange = model::search,
                    label = { Text(stringResource(R.string.trash_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.trash_total, state.total, state.size / 1048576.0))
                Row {
                    TextButton(onClick = model::toggleExclusions, enabled = !state.busy) {
                        Text(stringResource(if (state.exclusions) R.string.trash_title else R.string.trash_exclusions))
                    }
                    if (!state.exclusions) {
                        TextButton(
                            enabled = state.selected.isNotEmpty() && !state.busy,
                            onClick = { model.restore(state.selected.toList()) },
                        ) { Text(stringResource(R.string.trash_restore_selection)) }
                    }
                }
                if (state.exclusions) Text(stringResource(R.string.trash_exclusions_help))
                state.message?.let { Text(trashMessage(it), color = MaterialTheme.colorScheme.error) }
                if (state.busy) CircularProgressIndicator()
                if (state.items.isEmpty()) Text(stringResource(R.string.trash_empty))
                LazyColumn(Modifier.weight(1f)) {
                    items(state.items, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            val expiration = item.expiresAt
                            val restorable = item.state in setOf("trashed", "error") &&
                                (expiration == null || expiration * 1000 > System.currentTimeMillis())
                            if (!state.exclusions) {
                                Checkbox(
                                    checked = item.id in state.selected,
                                    enabled = restorable && !state.busy,
                                    onCheckedChange = { model.toggle(item.id) },
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    item.metadata.title.ifBlank { item.file },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(item.metadata.artist)
                                Text(
                                    item.metadata.codec + " · " + item.file,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (!state.exclusions) Text(trashState(item.state))
                                item.expiresAt?.let {
                                    val date = DateFormat.getDateTimeInstance().format(Date((it * 1000).toLong()))
                                    Text(
                                        stringResource(R.string.trash_expires, date),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (item.error.isNotEmpty()) {
                                    Text(trashMessage(item.error), color = MaterialTheme.colorScheme.error)
                                }
                                Row {
                                    if (state.exclusions) {
                                        TextButton(enabled = !state.busy, onClick = { allowId = item.id }) {
                                            Text(stringResource(R.string.trash_allow))
                                        }
                                    }
                                    if (restorable && !state.exclusions) {
                                        TextButton(enabled = !state.busy, onClick = {
                                            model.restore(listOf(item.id))
                                        }) {
                                            Text(stringResource(R.string.trash_restore))
                                        }
                                    }
                                    if (item.state == "error") {
                                        TextButton(enabled = !state.busy, onClick = { model.retry(item.id) }) {
                                            Text(stringResource(R.string.trash_retry))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!state.exclusions && state.total > 50) {
                    Row {
                        TextButton(enabled = state.offset > 0, onClick = { model.page(-50) }) {
                            Text(stringResource(R.string.trash_previous))
                        }
                        TextButton(enabled = state.offset + 50 < state.total, onClick = { model.page(50) }) {
                            Text(stringResource(R.string.trash_next))
                        }
                    }
                }
            }
        }
    }
    allowId?.let { id ->
        AlertDialog(
            onDismissRequest = { allowId = null },
            title = { Text(stringResource(R.string.trash_allow)) },
            text = { Text(stringResource(R.string.trash_allow_help)) },
            confirmButton = {
                TextButton(onClick = {
                    allowId = null
                    model.allow(id)
                }) { Text(stringResource(R.string.trash_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { allowId = null }) { Text(stringResource(R.string.trash_cancel)) }
            },
        )
    }
}

@Composable
private fun trashState(state: String): String = stringResource(
    when (state) {
        "pending", "moving" -> R.string.trash_pending
        "trashed" -> R.string.trash_retained
        "restore_pending", "restoring" -> R.string.trash_restoring
        "purging", "purged" -> R.string.trash_expired
        else -> R.string.trash_error
    },
)

@Composable
private fun trashMessage(code: String): String = stringResource(
    when (code) {
        "pending" -> R.string.trash_pending

        "admin_required", "admin_access_revoked" -> R.string.trash_error_admin

        "file_changed", "trash_file_changed", "file_changed_during_move" -> R.string.trash_error_changed

        "restore_collision" -> R.string.trash_error_collision

        "track_is_playing" -> R.string.trash_error_playing

        "confirmation_expired", "confirmation_invalid" -> R.string.trash_error_confirmation

        "identity_uncertain" -> R.string.trash_error_identity

        "preview_busy" -> R.string.trash_error_busy

        "preview_unavailable" -> R.string.trash_error_confirmation

        "library_task_active" -> R.string.trash_error_upgrade

        "retention_expired" -> R.string.trash_expired

        "root_unavailable", "nas_marker_missing", "nas_marker_changed", "filesystem_unavailable" ->
            R.string.trash_error_storage

        else -> R.string.trash_error
    },
)
