package com.henriquesebastiao.downtify.feature.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.PodcastEpisode
import com.henriquesebastiao.downtify.core.model.PodcastShow
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.ui.common.formatDate

@Composable
fun ShowRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ShowViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(
                when (message) {
                    is ShowMessage.DownloadFailed -> resources.getString(R.string.show_download_failed, message.title)
                    ShowMessage.Unreachable -> resources.getString(R.string.load_error_unreachable)
                },
            )
        }
    }
    ShowScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        actions = EpisodeActions(viewModel::play, viewModel::download, viewModel::setPlayed),
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

/** What an episode row can do. */
data class EpisodeActions(
    val onPlay: (PodcastEpisode) -> Unit = {},
    val onDownload: (PodcastEpisode) -> Unit = {},
    val onSetPlayed: (PodcastEpisode, Boolean) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(
    state: ShowUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    actions: EpisodeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(DowntifyIcons.Back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.show == null -> EmptyState(
                    icon = DowntifyIcons.Podcasts,
                    title = stringResource(state.error?.let(::errorTitle) ?: R.string.load_error_failed),
                    body = stringResource(R.string.podcasts_pull_to_retry),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") { Header(state.show) }
                    if (state.episodes.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.show_no_episodes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.screen),
                            )
                        }
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            busy = episode.id in state.busy,
                            current = episode.id == state.currentEpisodeId,
                            playing = episode.id == state.currentEpisodeId && state.isPlaying,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(show: PodcastShow) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                url = show.artworkUrl.ifBlank { null },
                contentDescription = null,
                shape = MaterialTheme.shapes.large,
                placeholderIcon = DowntifyIcons.Podcasts,
                modifier = Modifier.size(112.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    show.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (show.author.isNotBlank()) {
                    Text(
                        show.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (show.description.isNotBlank()) {
            Text(
                show.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.md).clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    busy: Boolean,
    current: Boolean,
    playing: Boolean,
    actions: EpisodeActions,
) {
    val played = episode.played
    ListItem(
        headlineContent = {
            Text(
                episode.title.ifBlank { stringResource(R.string.show_untitled_episode) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (played) FontWeight.Normal else FontWeight.SemiBold,
                color = when {
                    current -> MaterialTheme.colorScheme.primary
                    played -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Color.Unspecified
                },
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text(meta(episode), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!played && episode.heard > 0f) {
                    LinearProgressIndicator(
                        progress = { episode.heard },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().padding(end = Spacing.sm),
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayOrDownload(episode, busy, playing, actions)
                EpisodeMenu(episode, actions)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = !busy) { actions.onPlay(episode) },
    )
}

@Composable
private fun PlayOrDownload(episode: PodcastEpisode, busy: Boolean, playing: Boolean, actions: EpisodeActions) {
    val downloading = stringResource(R.string.show_downloading)
    when {
        busy -> Box(
            Modifier.size(48.dp).semantics {
                contentDescription = downloading
            },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }

        episode.isDownloaded -> IconButton(onClick = { actions.onPlay(episode) }) {
            Icon(
                painterResource(if (playing) DowntifyIcons.Pause else DowntifyIcons.Play),
                contentDescription = stringResource(
                    if (playing) R.string.player_pause else R.string.show_play_episode,
                    episode.title,
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        else -> IconButton(onClick = { actions.onDownload(episode) }) {
            Icon(
                painterResource(DowntifyIcons.Downloads),
                contentDescription = stringResource(R.string.show_download_episode, episode.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeMenu(episode: PodcastEpisode, actions: EpisodeActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(DowntifyIcons.MoreVert),
                contentDescription = stringResource(R.string.show_episode_options, episode.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (episode.played) R.string.show_mark_unplayed else R.string.show_mark_played))
                },
                leadingIcon = { Icon(painterResource(DowntifyIcons.CheckCircle), contentDescription = null) },
                onClick = {
                    open = false
                    actions.onSetPlayed(episode, !episode.played)
                },
            )
        }
    }
}

/** `Sep 18, 2026 · 31:02 · Resume at 12:34`. */
@Composable
private fun meta(episode: PodcastEpisode): String {
    val parts = buildList {
        formatDate(episode.publishedAt)?.let(::add)
        if (episode.durationSeconds > 0) add(formatDuration(episode.durationSeconds.toDouble()))
        when {
            episode.played -> add(stringResource(R.string.show_played))

            episode.resumeAtSeconds != null ->
                add(stringResource(R.string.show_resume_at, formatDuration(episode.resumeAtSeconds!!.toDouble())))
        }
    }
    return parts.joinToString(" · ")
}

private const val DESCRIPTION_LINES = 4

private val previewShow = PodcastShow(
    1,
    "Radiolab",
    "WNYC Studios",
    "Investigations of the natural and the unnatural, told through science, philosophy and story.",
    "",
    671,
    3,
)

private fun previewEpisode(id: Long, title: String, date: String, file: String?, position: Double, played: Boolean) =
    PodcastEpisode(id, 1, title, "", "${date}T14:00:00+00:00", 1_862, null, 700 + id.toInt(), file, position, played)

@PreviewLightDark
@Composable
private fun ShowPreview() {
    DowntifyTheme {
        ShowScreen(
            state = ShowUiState(
                loading = false,
                show = previewShow,
                episodes = listOf(
                    previewEpisode(1, "The Sweetest Thing", "2026-09-18", "Podcasts/R/a.mp3", 754.0, played = false),
                    previewEpisode(2, "Zoozve", "2026-09-11", "Podcasts/R/b.mp3", 0.0, played = true),
                    previewEpisode(3, "Space to Dream", "2026-09-04", null, 0.0, played = false),
                ),
                currentEpisodeId = 1,
                isPlaying = true,
            ),
            onBack = {},
            onRefresh = {},
            actions = EpisodeActions(),
        )
    }
}
