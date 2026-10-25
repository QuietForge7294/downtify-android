package com.henriquesebastiao.downtify.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.data.offline.DownloadActivity
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.OfflinePlanner
import com.henriquesebastiao.downtify.core.model.OfflineProgress
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.model.ServerQueueSummary
import com.henriquesebastiao.downtify.core.model.formatBytes
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls

@Composable
fun DownloadsRoute(
    onOpen: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DownloadsScreen(
        state = state,
        onKeepLiked = viewModel::setKeepLiked,
        onOpen = onOpen,
        onRemove = viewModel::remove,
        onTab = viewModel::selectTab,
        modifier = modifier,
    )
}

/** What's kept on the phone: the storage it takes, Liked songs, and each album or playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onKeepLiked: (Boolean) -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    onTab: (DownloadsTab) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.downloads_title), style = MaterialTheme.typography.headlineMedium)
            })
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(bottom = Spacing.xl),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "tabs") { Tabs(state.tab, onTab) }
            if (state.tab == DownloadsTab.Server) {
                serverTab(state)
                return@LazyColumn
            }
            item(key = "storage") {
                StorageCard(state, Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs))
            }
            item(key = "liked") { KeepLikedRow(state, onKeepLiked) }
            val collections = state.items
            if (collections.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = DowntifyIcons.Downloads,
                        title = stringResource(R.string.downloads_empty_title),
                        body = stringResource(R.string.downloads_empty_body),
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    )
                }
            } else {
                item(key = "header") {
                    Text(
                        stringResource(R.string.downloads_on_phone),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs),
                    )
                }
                items(collections, key = { "${it.type}:${it.refId}" }) { item ->
                    DownloadRow(item, state, onOpen = { onOpen(item) }, onRemove = { onRemove(item) })
                }
            }
            if (state.serverSummary.active > 0) {
                item(key = "server-card") { ServerCard(state, onClick = { onTab(DownloadsTab.Server) }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tabs(selected: DownloadsTab, onTab: (DownloadsTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        DownloadsTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onTab(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, DownloadsTab.entries.size),
            ) {
                Text(
                    stringResource(
                        if (tab == DownloadsTab.Phone) R.string.downloads_tab_phone else R.string.downloads_tab_server,
                    ),
                )
            }
        }
    }
}

/** "The server is downloading 13 songs · Harbor Nights · 4 of 9" — opens the server's side. */
@Composable
private fun ServerCard(state: DownloadsUiState, onClick: () -> Unit) {
    val summary = state.serverSummary
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = Spacing.lg, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                painterResource(DowntifyIcons.Server),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(vertical = Spacing.md)) {
                Text(
                    pluralStringResource(R.plurals.downloads_server_card, summary.active, summary.active),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (summary.currentAlbum.isNotBlank() && summary.currentAlbumTotal > 1) {
                    Text(
                        stringResource(
                            R.string.downloads_server_card_detail,
                            summary.currentAlbum,
                            summary.currentAlbumDone,
                            summary.currentAlbumTotal,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(painterResource(DowntifyIcons.ChevronRight), contentDescription = null)
        }
    }
}

private fun LazyListScope.serverTab(state: DownloadsUiState) {
    if (state.serverJobs.isEmpty()) {
        item(key = "server-empty") {
            EmptyState(
                icon = DowntifyIcons.Server,
                title = stringResource(R.string.downloads_server_empty_title),
                body = stringResource(R.string.downloads_server_empty_body),
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
            )
        }
        return
    }
    if (state.serverSummary.active > 0) {
        item(key = "server-summary") {
            Text(
                pluralStringResource(
                    R.plurals.downloads_server_card,
                    state.serverSummary.active,
                    state.serverSummary.active,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
            )
        }
    }
    items(state.serverJobs, key = { "job:${it.songId}" }) { job -> ServerJobRow(job) }
}

@Composable
private fun ServerJobRow(job: ServerJob) {
    ListItem(
        headlineContent = { Text(job.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (job.status == ServerJobStatus.Downloading) {
                    LinearProgressIndicator(
                        progress = { job.progress / PERCENT },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().padding(end = Spacing.sm),
                    )
                }
                Text(
                    listOf(job.artist, jobStatus(job)).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (job.status ==
                        ServerJobStatus.Error
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                )
            }
        },
        leadingContent = {
            Artwork(url = job.coverUrl.ifBlank { null }, contentDescription = null, modifier = Modifier.size(56.dp))
        },
        trailingContent = when {
            job.status == ServerJobStatus.Done -> {
                {
                    Icon(
                        painterResource(DowntifyIcons.CheckCircle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            job.status.isActive && job.position != null -> {
                {
                    Text(
                        "#${job.position}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun jobStatus(job: ServerJob): String = when (job.status) {
    ServerJobStatus.Queued -> stringResource(R.string.downloads_server_job_queued)

    ServerJobStatus.Downloading -> stringResource(R.string.downloads_server_job_downloading, job.progress.toInt())

    ServerJobStatus.Done -> stringResource(R.string.downloads_server_job_done)

    ServerJobStatus.Error -> if (job.message.isBlank()) {
        stringResource(R.string.downloads_server_job_error)
    } else {
        stringResource(R.string.downloads_server_job_error_detail, job.message)
    }
}

private const val PERCENT = 100f

@Composable
private fun StorageCard(state: DownloadsUiState, modifier: Modifier = Modifier) {
    val unlimited = state.limitBytes == OfflinePlanner.UNLIMITED
    val capacity = if (unlimited) state.usedBytes + state.freeBytes else state.limitBytes
    val fraction = if (capacity > 0) (state.usedBytes.toFloat() / capacity).coerceIn(0f, 1f) else 0f
    val a11y = stringResource(R.string.downloads_storage_a11y)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.downloads_used, formatBytes(state.usedBytes)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (unlimited) {
                        stringResource(R.string.downloads_no_limit)
                    } else {
                        stringResource(R.string.downloads_of_limit, formatBytes(state.limitBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = a11y },
            )
            Text(
                pluralStringResource(
                    R.plurals.downloads_storage_body,
                    state.songCount,
                    state.songCount,
                    formatBytes(state.freeBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeepLikedRow(state: DownloadsUiState, onKeepLiked: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.downloads_keep_liked)) },
        supportingContent = {
            Text(
                stringResource(
                    if (state.wifiOnly) R.string.downloads_keep_liked_body_wifi else R.string.downloads_keep_liked_body,
                ),
            )
        },
        trailingContent = { Switch(checked = state.keepLiked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = state.keepLiked, role = Role.Switch, onValueChange = onKeepLiked),
    )
}

@Composable
private fun DownloadRow(item: DownloadItem, state: DownloadsUiState, onOpen: () -> Unit, onRemove: () -> Unit) {
    val title = if (item.type == PlaybackContextType.Liked) stringResource(R.string.liked_songs) else item.title
    val p = item.progress
    val inProgress = p.total > 0 && !p.isComplete && p.overLimit == 0
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (inProgress) {
                    LinearProgressIndicator(
                        progress = { p.downloaded.toFloat() / p.total },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().padding(end = Spacing.sm),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.isComplete) {
                        Icon(
                            painterResource(DowntifyIcons.CheckCircle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp).size(14.dp),
                        )
                    }
                    Text(statusText(item, state), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        leadingContent = {
            Artwork(
                url = LocalCoverUrls.current.track(item.coverTrackId),
                contentDescription = null,
                placeholderIcon = when (item.type) {
                    PlaybackContextType.Liked -> DowntifyIcons.FavoriteFilled
                    PlaybackContextType.Playlist -> DowntifyIcons.Playlist
                    else -> DowntifyIcons.Album
                },
                modifier = Modifier.size(56.dp),
            )
        },
        trailingContent = { RowMenu(title, onRemove) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

@Composable
private fun RowMenu(title: String, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(DowntifyIcons.MoreVert),
                contentDescription = stringResource(R.string.downloads_options, title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.downloads_remove)) },
                leadingIcon = { Icon(painterResource(DowntifyIcons.Delete), contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun statusText(item: DownloadItem, state: DownloadsUiState): String {
    val p = item.progress
    return when {
        p.total == 0 -> stringResource(R.string.downloads_status_missing)

        p.overLimit > 0 -> pluralStringResource(
            R.plurals.downloads_status_over_limit,
            p.overLimit,
            p.downloaded,
            p.total,
            p.overLimit,
        )

        p.isComplete -> {
            val done = pluralStringResource(R.plurals.downloads_status_done, p.total, p.total, formatBytes(p.bytes))
            if (item.type ==
                PlaybackContextType.Liked
            ) {
                stringResource(R.string.downloads_status_done_synced, done)
            } else {
                done
            }
        }

        else -> {
            val progress = stringResource(R.string.downloads_status_progress, p.downloaded, p.total)
            when (state.activity) {
                DownloadActivity.Downloading -> progress

                else -> {
                    val waiting = if (state.wifiOnly) {
                        R.string.downloads_status_waiting_wifi
                    } else {
                        R.string.downloads_status_waiting_network
                    }
                    stringResource(waiting, progress)
                }
            }
        }
    }
}

private val previewState = DownloadsUiState(
    loading = false,
    usedBytes = 2_400_000_000,
    limitBytes = 8_000_000_000,
    freeBytes = 5_600_000_000,
    songCount = 212,
    keepLiked = true,
    activity = DownloadActivity.WaitingForNetwork,
    items = listOf(
        DownloadItem(PlaybackContextType.Liked, "", "", null, OfflineProgress(118, 118, 1_100_000_000, 0)),
        DownloadItem(PlaybackContextType.Album, "a", "Glass Harbor", null, OfflineProgress(10, 10, 412_000_000, 0)),
        DownloadItem(PlaybackContextType.Playlist, "Deep Focus", "Deep Focus", null, OfflineProgress(6, 4, 0, 0)),
    ),
)

@PreviewLightDark
@Composable
private fun DownloadsPreview() {
    DowntifyTheme {
        DownloadsScreen(state = previewState, onKeepLiked = {}, onOpen = {}, onRemove = {})
    }
}

@PreviewLightDark
@Composable
private fun DownloadsServerPreview() {
    val jobs = listOf(
        ServerJob("1", "Harbor Song", "Nora Vale", "Harbor Nights", "", ServerJobStatus.Downloading, 42f, ""),
        ServerJob("2", "Low Lights", "Nora Vale", "Harbor Nights", "", ServerJobStatus.Queued, 0f, ""),
        ServerJob("3", "Tides", "Lumen Field", "Tides", "", ServerJobStatus.Done, 100f, ""),
    )
    DowntifyTheme {
        DownloadsScreen(
            state = previewState.copy(
                tab = DownloadsTab.Server,
                serverJobs = jobs,
                serverSummary = ServerQueueSummary.of(jobs),
            ),
            onKeepLiked = {},
            onOpen = {},
            onRemove = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DownloadsEmptyPreview() {
    DowntifyTheme {
        DownloadsScreen(
            state = DownloadsUiState(loading = false, limitBytes = 8_000_000_000, freeBytes = 50_000_000_000),
            onKeepLiked = {},
            onOpen = {},
            onRemove = {},
        )
    }
}
