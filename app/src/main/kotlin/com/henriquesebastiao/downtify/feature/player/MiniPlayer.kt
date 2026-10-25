package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.MiniPlayerButton
import com.henriquesebastiao.downtify.core.designsystem.component.MiniPlayerCard
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingStream
import com.henriquesebastiao.downtify.ui.common.PreviewData

/**
 * Sits above the navigation bar while something is loaded, in the cover's colors; tapping it
 * opens Now Playing.
 *
 * [hasLibraryCopy] is true when the playing stream has since been downloaded: the stream keeps
 * playing, but the download button goes away so the mini player looks exactly like a library
 * track's (no download, no spinner, no check).
 */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    onSimilar: (artist: String, title: String) -> Unit = { _, _ -> },
    downloadJob: ServerJob? = null,
    onDownloadStream: () -> Unit = {},
    hasLibraryCopy: Boolean = false,
) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
    val similarArtist = track?.displayArtist ?: stream?.artist.orEmpty()
    val similarTitle = track?.displayTitle ?: stream?.title.orEmpty()
    val canSimilar = episode == null && similarArtist.isNotBlank() && similarTitle.isNotBlank()
    val coverUrl = playingCoverUrl(state)
    CoverTheme(coverUrl) {
        MiniPlayerCard(
            title = track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty(),
            subtitle = track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty(),
            coverUrl = coverUrl,
            progress = { state.progress },
            onOpen = onOpen,
            openLabel = stringResource(R.string.player_open),
            modifier = modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        ) {
            if (canSimilar) {
                MiniPlayerButton(
                    DowntifyIcons.Explore,
                    stringResource(R.string.search_similar, similarTitle),
                    { onSimilar(similarArtist, similarTitle) },
                )
            }
            if (stream != null && !hasLibraryCopy && downloadJob?.status != ServerJobStatus.Done) {
                MiniStreamDownloadButton(job = downloadJob, title = stream.title, onDownload = onDownloadStream)
            }
            MiniPlayerButton(
                if (state.isPlaying) DowntifyIcons.Pause else DowntifyIcons.Play,
                stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                onTogglePlay,
                main = true,
            )
        }
    }
}

/**
 * Download the playing stream, the loading indicator while the server works on it.
 * Once the server finished ([ServerJobStatus.Done]) there is nothing to show: the mini player
 * looks exactly like a library track's. Never called in that case (see the caller).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniStreamDownloadButton(job: ServerJob?, title: String, onDownload: () -> Unit) {
    val active = job?.status == ServerJobStatus.Queued || job?.status == ServerJobStatus.Downloading
    if (active) {
        val label = stringResource(R.string.search_job_downloading)
        Box(Modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            LoadingIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
    } else if (job?.status != ServerJobStatus.Done) {
        MiniPlayerButton(
            DowntifyIcons.Downloads,
            stringResource(R.string.search_download_to_server, title),
            onDownload,
        )
    }
}

@PhonePreviews
@Composable
private fun MiniPlayerPreview() {
    DowntifyTheme {
        Surface {
            MiniPlayer(
                state = PlayerState(
                    track = PreviewData.tracks[1],
                    isPlaying = true,
                    positionMs = 70_000,
                    durationMs = 239_000,
                ),
                onOpen = {},
                onTogglePlay = {},
            )
        }
    }
}

@PhonePreviews
@Composable
private fun MiniPlayerStreamPreview() {
    DowntifyTheme {
        Surface {
            MiniPlayer(
                state = PlayerState(stream = PlayingStream("id", "A stream with a long title", "Artist", "", "{}")),
                onOpen = {},
                onTogglePlay = {},
            )
        }
    }
}
