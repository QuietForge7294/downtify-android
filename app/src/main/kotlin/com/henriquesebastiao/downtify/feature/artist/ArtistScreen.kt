package com.henriquesebastiao.downtify.feature.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.EmptyState
import com.henriquesebastiao.downtify.core.designsystem.component.SectionHeader
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.ui.common.CoverCard
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import com.henriquesebastiao.downtify.ui.common.TrackRow
import com.henriquesebastiao.downtify.ui.common.artistSubtitle

@Composable
fun ArtistRoute(
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistScreen(
        state = state,
        onBack = onBack,
        onAlbum = onAlbum,
        onPlay = viewModel::play,
        onPlayOrPause = viewModel::playOrPause,
        onShuffle = viewModel::shuffle,
        trackActions = TrackActions(
            onToggleLike = viewModel::toggleLike,
            onAddToQueue = viewModel::addToQueue,
            onGoToAlbum = { onAlbum(it.albumId) },
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onPlayOrPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    trackActions: TrackActions = TrackActions(),
) {
    val covers = LocalCoverUrls.current
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
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
        val artist = state.artist
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            artist == null -> EmptyState(
                icon = DowntifyIcons.Artist,
                title = stringResource(R.string.collection_not_found),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Artwork(
                            url = covers.track(artist.coverTrackId, ServerUrls.COVER_LARGE),
                            contentDescription = null,
                            shape = CircleShape,
                            placeholderIcon = DowntifyIcons.Artist,
                            modifier = Modifier.size(184.dp),
                        )
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            artistSubtitle(artist),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    val playing = state.isCurrentContext && state.isPlaying
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onShuffle) {
                            Icon(
                                painterResource(DowntifyIcons.Shuffle),
                                contentDescription = stringResource(R.string.collection_shuffle),
                            )
                        }
                        Spacer(Modifier.size(Spacing.sm))
                        FilledIconButton(
                            onClick = onPlayOrPause,
                            shape = DowntifyShape.Fab,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                painterResource(if (playing) DowntifyIcons.Pause else DowntifyIcons.Play),
                                contentDescription = stringResource(
                                    if (playing) R.string.collection_pause else R.string.collection_play,
                                    artist.name,
                                ),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                if (artist.albums.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.artist_albums)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.screen),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(artist.albums, key = { it.id }) { album ->
                                CoverCard(
                                    title = album.title,
                                    subtitle = album.year.ifBlank { null },
                                    coverUrl = covers.track(album.coverTrackId),
                                    onClick = { onAlbum(album.id) },
                                    modifier = Modifier.width(148.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    SectionHeader(stringResource(R.string.artist_songs), modifier = Modifier.padding(top = Spacing.lg))
                }
                itemsIndexed(state.songs, key = { i, t -> "$i:${t.id}" }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(index) },
                        coverUrl = covers.track(track.id.takeIf { track.hasCover }, ServerUrls.COVER_SMALL),
                        isCurrent = track.id == state.currentTrackId,
                        isPlaying = state.isPlaying,
                        isLiked = track.id in state.likedIds,
                        actions = trackActions,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ArtistPreview() {
    val artist = PreviewData.artists.first()
    DowntifyTheme {
        ArtistScreen(
            state = ArtistUiState(loading = false, artist = artist, songs = artist.tracks),
            onBack = {},
            onAlbum = {},
            onPlay = {},
            onPlayOrPause = {},
            onShuffle = {},
        )
    }
}
