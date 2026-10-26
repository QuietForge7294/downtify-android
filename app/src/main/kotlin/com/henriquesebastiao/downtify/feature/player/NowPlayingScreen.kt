package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.designsystem.component.CircleButton
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.MarqueeText
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.component.PlaybackControls
import com.henriquesebastiao.downtify.core.designsystem.component.PlaybackLabels
import com.henriquesebastiao.downtify.core.designsystem.component.PlayerToggle
import com.henriquesebastiao.downtify.core.designsystem.component.PlayerToggles
import com.henriquesebastiao.downtify.core.designsystem.component.Tag
import com.henriquesebastiao.downtify.core.designsystem.component.TagTone
import com.henriquesebastiao.downtify.core.designsystem.component.WavySeekBar
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.PlaybackContext
import com.henriquesebastiao.downtify.core.model.PlaybackContextType
import com.henriquesebastiao.downtify.core.model.ServerJob
import com.henriquesebastiao.downtify.core.model.ServerJobStatus
import com.henriquesebastiao.downtify.core.model.StreamQuality
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.codecLabel
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.player.PlaybackError
import com.henriquesebastiao.downtify.core.player.PlayerState
import com.henriquesebastiao.downtify.core.player.PlayingEpisode
import com.henriquesebastiao.downtify.core.player.RepeatMode
import com.henriquesebastiao.downtify.ui.common.PreviewData

/** Everything Now Playing can ask for. */
data class NowPlayingActions(
    val onCollapse: () -> Unit = {},
    val onTogglePlay: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onCycleRepeat: () -> Unit = {},
    val onToggleLike: () -> Unit = {},
    val onSimilar: (artist: String, title: String) -> Unit = { _, _ -> },
    val onOpenLyrics: () -> Unit = {},
    val onOpenQueue: () -> Unit = {},
    val onOpenContext: (PlaybackContext) -> Unit = {},
    val onSkipBack: () -> Unit = {},
    val onSkipForward: () -> Unit = {},
    val onCycleSpeed: () -> Unit = {},
    val onGoToShow: (Long) -> Unit = {},
    val onGoToAlbum: (String) -> Unit = {},
    val onGoToArtist: (String) -> Unit = {},
    val onDownloadStream: () -> Unit = {},
)

/**
 * Now Playing (v3), in the cover's colors: lyrics and the queue as a pair at the top end, the
 * cover as large as the controls leave room for, the title, the wavy seek bar with the format
 * between its times, the expressive transport, and the shuffle / repeat / like toggles between
 * similar tracks and the menu (or the stream's download).
 *
 * [hasLibraryCopy] is true when the playing stream has since been downloaded (a library track
 * matches it, passed as [libraryTrack]): the stream keeps playing, but the buttons switch to
 * the library set (like, lyrics, the header menu) and the download button goes away. A finished
 * server job ([ServerJobStatus.Done]) hides the download button at once; like, lyrics and the
 * menu appear with the library sync, when there is a track id behind them to act on.
 */
@Composable
fun NowPlayingScreen(
    state: PlayerState,
    isLiked: Boolean,
    serverName: String,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
    downloadJob: ServerJob? = null,
    hasLibraryCopy: Boolean = false,
    libraryTrack: Track? = null,
) {
    val track = state.track
    val episode = state.episode
    val stream = state.stream
    if (track == null && episode == null && stream == null) return
    val downloaded = hasLibraryCopy || downloadJob?.status == ServerJobStatus.Done
    // What the header menu and the artist tap navigate to: the library copy behind a stream.
    val menuTrack = track ?: libraryTrack.takeIf { stream != null }
    val coverUrl = playingCoverUrl(state, ServerUrls.COVER_LARGE)

    CoverTheme(playingCoverUrl(state)) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            modifier = modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val compact = maxHeight < COMPACT_HEIGHT
                Column(
                    Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.sm else Spacing.md),
                ) {
                    Header(
                        context = state.context,
                        episode = episode,
                        actions = actions,
                        lyrics = episode == null && (stream == null || hasLibraryCopy),
                        queue = episode == null,
                    )
                    // The cover takes whatever the rest leaves, square.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Artwork(
                            url = coverUrl,
                            contentDescription = null,
                            shape = DowntifyShape.Artwork,
                            placeholderIcon = if (episode != null) DowntifyIcons.Podcasts else DowntifyIcons.Song,
                            modifier = Modifier
                                .widthIn(max = MAX_COVER)
                                .fillMaxWidth()
                                .aspectRatio(1f, matchHeightConstraintsFirst = true),
                        )
                    }
                    TitleBlock(
                        title = track?.displayTitle ?: stream?.title ?: episode?.title.orEmpty(),
                        artist = track?.displayArtist ?: stream?.artist ?: episode?.showName.orEmpty(),
                        onArtist = when {
                            episode != null -> ({ actions.onGoToShow(episode.showId) })
                            menuTrack?.artistId?.isNotEmpty() == true -> ({ actions.onGoToArtist(menuTrack.artistId) })
                            else -> null
                        },
                    )
                    SeekBar(state, actions.onSeek, formatOf(track, state))
                    val menu: @Composable () -> Unit = {
                        MoreMenu(menuTrack?.albumId.orEmpty(), menuTrack?.artistId.orEmpty(), episode, actions)
                    }
                    if (episode != null) {
                        EpisodeTransport(state, actions, compact, menu)
                    } else {
                        TrackTransport(
                            state = state,
                            actions = actions,
                            compact = compact,
                            isLiked = isLiked.takeIf { stream == null || hasLibraryCopy },
                            leading = { SimilarButton(state, actions) },
                            trailing = if (stream != null && !downloaded) {
                                {
                                    StreamDownloadButton(
                                        job = downloadJob,
                                        title = stream.title,
                                        onDownload = actions.onDownloadStream,
                                    )
                                }
                            } else {
                                menu
                            },
                        )
                    }
                    SourceLines(state, track, serverName)
                }
            }
        }
    }
}

/**
 * The top row: collapse, where it plays from (tap to go there), and lyrics and the queue as a pair
 * of buttons, round outside, at the end. A stream with no library copy has no lyrics: the queue
 * stands alone. An episode has neither.
 */
@Composable
private fun Header(
    context: PlaybackContext?,
    episode: PlayingEpisode?,
    actions: NowPlayingActions,
    lyrics: Boolean,
    queue: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircleButton(
            DowntifyIcons.Collapse,
            stringResource(R.string.player_close),
            actions.onCollapse,
            containerColor = playerTint(),
            contentColor = playerContent(),
        )
        Column(
            Modifier
                .weight(1f)
                .then(
                    when {
                        episode != null -> Modifier.clickable { actions.onGoToShow(episode.showId) }
                        context != null -> Modifier.clickable { actions.onOpenContext(context) }
                        else -> Modifier
                    },
                ),
        ) {
            val (overline, title) = when {
                episode != null -> stringResource(R.string.player_playing_from_podcast) to episode.showName
                context != null -> stringResource(playingFromLabel(context.type)) to context.title
                else -> null to null
            }
            if (overline != null) {
                Text(
                    overline.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(MUTED_ALPHA),
                )
            }
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (lyrics) {
                HeaderButton(
                    DowntifyIcons.Lyrics,
                    stringResource(R.string.player_lyrics),
                    actions.onOpenLyrics,
                    if (queue) DowntifyShape.PairStart else CircleShape,
                )
            }
            if (queue) {
                HeaderButton(
                    DowntifyIcons.Queue,
                    stringResource(R.string.player_queue),
                    actions.onOpenQueue,
                    if (lyrics) DowntifyShape.PairEnd else CircleShape,
                )
            }
        }
    }
}

/** One of the header's pair: 56 × 48 dp, in the collapse button's tint, squarer while pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderButton(icon: Int, label: String, onClick: () -> Unit, shape: Shape) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonShapes(shape = shape, pressedShape = RoundedCornerShape(12.dp)),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = playerTint(),
            contentColor = playerContent(),
        ),
        modifier = Modifier.size(width = 56.dp, height = 48.dp),
    ) {
        Icon(painterResource(icon), contentDescription = label)
    }
}

/** Go to the show, the album or the artist; nothing for a stream with no library copy. */
@Composable
private fun MoreMenu(albumId: String, artistId: String, episode: PlayingEpisode?, actions: NowPlayingActions) {
    if (episode == null && albumId.isEmpty() && artistId.isEmpty()) return
    var menu by remember { mutableStateOf(false) }
    Box {
        CircleButton(
            DowntifyIcons.MoreVert,
            stringResource(R.string.action_more),
            { menu = true },
            containerColor = playerTint(),
            contentColor = playerContent(),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (episode != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_go_to_show)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.Podcasts), contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.onGoToShow(episode.showId)
                    },
                )
            }
            if (albumId.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.track_go_to_album)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.Album), contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.onGoToAlbum(albumId)
                    },
                )
            }
            if (artistId.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.track_go_to_artist)) },
                    leadingIcon = { Icon(painterResource(DowntifyIcons.Artist), contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.onGoToArtist(artistId)
                    },
                )
            }
        }
    }
}

/** Tracks like this one; none for an episode. */
@Composable
private fun SimilarButton(state: PlayerState, actions: NowPlayingActions) {
    val artist = state.track?.displayArtist ?: state.stream?.artist.orEmpty()
    val title = state.track?.displayTitle ?: state.stream?.title.orEmpty()
    if (state.episode != null || artist.isBlank() || title.isBlank()) return
    CircleButton(
        DowntifyIcons.Explore,
        stringResource(R.string.search_similar, title),
        { actions.onSimilar(artist, title) },
        containerColor = playerTint(),
        contentColor = playerContent(),
    )
}

/**
 * The title and the artist (or the show), centred. One that doesn't fit slides, fading in and out
 * at both ends, so it stays readable.
 */
@Composable
private fun TitleBlock(title: String, artist: String, onArtist: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MarqueeText(title, style = MaterialTheme.typography.headlineMediumEmphasized)
        MarqueeText(
            artist,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .alpha(SUBTITLE_ALPHA)
                .then(if (onArtist != null) Modifier.clickable(onClick = onArtist) else Modifier),
        )
    }
}

@Composable
private fun SeekBar(state: PlayerState, onSeek: (Long) -> Unit, format: String?) {
    val duration = state.durationMs.coerceAtLeast(1)
    WavySeekBar(
        progress = state.positionMs.toFloat() / duration,
        onSeek = { onSeek((it * duration).toLong()) },
        playing = state.isPlaying,
        timeAt = { formatDuration((it * duration).toLong() / 1000.0) },
        duration = formatDuration(state.durationMs / 1000.0),
        contentDescription = stringResource(R.string.player_seek),
        between = format?.let { { Tag(it, tone = TagTone.OnColor, small = true) } },
    )
}

/** What the transport button says, for both kinds of item. */
@Composable
private fun transportLabels(previous: Int, next: Int) = PlaybackLabels(
    play = stringResource(R.string.player_play),
    pause = stringResource(R.string.player_pause),
    previous = stringResource(previous),
    next = stringResource(next),
)

/**
 * A song's transport and its toggles: shuffle, repeat and, when there is something to like
 * ([isLiked] not null: a library track), like. Now Playing and the queue share it. Now Playing
 * puts a button on either side of the toggles ([leading], [trailing]), each in a 48 dp slot so the
 * toggles stay centred when one is empty.
 */
@Composable
internal fun TrackTransport(
    state: PlayerState,
    actions: NowPlayingActions,
    compact: Boolean,
    isLiked: Boolean?,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    PlaybackControls(
        playing = state.isPlaying,
        onTogglePlay = actions.onTogglePlay,
        onPrevious = actions.onPrevious,
        onNext = actions.onNext,
        labels = transportLabels(R.string.player_previous, R.string.player_next),
        compact = compact,
    )
    val toggles = buildList {
        add(
            PlayerToggle(
                icon = DowntifyIcons.Shuffle,
                label = stringResource(if (state.shuffle) R.string.player_shuffle_on else R.string.player_shuffle_off),
                checked = state.shuffle,
                onClick = actions.onToggleShuffle,
            ),
        )
        add(
            PlayerToggle(
                icon = if (state.repeat == RepeatMode.One) DowntifyIcons.RepeatOne else DowntifyIcons.Repeat,
                label = stringResource(
                    when (state.repeat) {
                        RepeatMode.Off -> R.string.player_repeat_off
                        RepeatMode.All -> R.string.player_repeat_all
                        RepeatMode.One -> R.string.player_repeat_one
                    },
                ),
                checked = state.repeat != RepeatMode.Off,
                onClick = actions.onCycleRepeat,
            ),
        )
        // Nothing to like on a stream the library doesn't have yet.
        if (isLiked != null) {
            add(
                PlayerToggle(
                    icon = if (isLiked) DowntifyIcons.FavoriteFilled else DowntifyIcons.Favorite,
                    label = stringResource(if (isLiked) R.string.player_unlike else R.string.player_like),
                    checked = isLiked,
                    onClick = actions.onToggleLike,
                ),
            )
        }
    }
    if (leading == null && trailing == null) {
        PlayerToggles(toggles, Modifier.fillMaxWidth(TOGGLES_WIDTH))
    } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { leading?.invoke() }
            PlayerToggles(toggles, Modifier.weight(1f))
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
        }
    }
}

/** An episode: 10 s back, play, 30 s forward, and its speed with the [menu] beside it — no shuffle, repeat or like. */
@Composable
private fun EpisodeTransport(
    state: PlayerState,
    actions: NowPlayingActions,
    compact: Boolean,
    menu: @Composable () -> Unit,
) {
    PlaybackControls(
        playing = state.isPlaying,
        onTogglePlay = actions.onTogglePlay,
        onPrevious = actions.onSkipBack,
        onNext = actions.onSkipForward,
        labels = transportLabels(R.string.player_skip_back, R.string.player_skip_forward),
        compact = compact,
        previousIcon = DowntifyIcons.SkipBack,
        nextIcon = DowntifyIcons.SkipForward,
    )
    val speedText = speedLabel(state.speed)
    val speedDescription = stringResource(R.string.player_speed, speedText)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // An empty slot as wide as the menu's keeps the speed in the middle.
        Spacer(Modifier.size(48.dp))
        FilledTonalButton(
            onClick = actions.onCycleSpeed,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = playerTint(),
                contentColor = playerContent(),
            ),
            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = speedDescription },
        ) {
            Text(speedText, style = DowntifyType.dataMedium)
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { menu() }
    }
}

/** `1×`, `1.25×`: no trailing zeros. */
private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

/** Download the playing stream to the server, then what the server is doing with it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreamDownloadButton(job: ServerJob?, title: String, onDownload: () -> Unit) {
    val tint = playerTint()
    val content = playerContent()
    when (job?.status) {
        null -> CircleButton(
            DowntifyIcons.Downloads,
            stringResource(R.string.search_download_to_server, title),
            onDownload,
            containerColor = tint,
            contentColor = content,
        )

        ServerJobStatus.Queued, ServerJobStatus.Downloading -> {
            val label = stringResource(
                if (job.status ==
                    ServerJobStatus.Queued
                ) {
                    R.string.search_job_queued
                } else {
                    R.string.search_job_downloading
                },
            )
            Box(Modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                LoadingIndicator(color = content, modifier = Modifier.size(40.dp))
            }
        }

        ServerJobStatus.Done -> Icon(
            painterResource(DowntifyIcons.CheckCircle),
            contentDescription = stringResource(R.string.search_job_done),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp).padding(12.dp),
        )

        ServerJobStatus.Error -> CircleButton(
            DowntifyIcons.Error,
            stringResource(R.string.search_job_error),
            onDownload,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** Where it plays from, the offline buffer and the last error, under everything else. */
@Composable
private fun SourceLines(state: PlayerState, track: Track?, serverName: String) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            when {
                track != null -> sourceLine(serverName, state.quality, state.fromPhone)
                state.stream != null -> stringResource(R.string.player_source_stream, serverName)
                else -> stringResource(R.string.player_source_unknown, serverName)
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(MUTED_ALPHA),
        )
        if (state.readAheadTarget > 0) {
            Text(
                stringResource(R.string.player_buffer_ready, state.readAheadReady, state.readAheadTarget),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(MUTED_ALPHA),
            )
        }
        state.error?.let { error ->
            Text(
                errorText(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun playingFromLabel(type: PlaybackContextType): Int = when (type) {
    PlaybackContextType.Album -> R.string.player_playing_from_album
    PlaybackContextType.Artist -> R.string.player_playing_from_artist
    PlaybackContextType.Playlist, PlaybackContextType.Liked -> R.string.player_playing_from_playlist
    PlaybackContextType.Songs -> R.string.player_playing_from_library
}

@Composable
private fun sourceLine(server: String, quality: StreamQuality?, fromPhone: Boolean): String = when {
    fromPhone -> stringResource(R.string.player_source_this_phone)
    quality?.isOriginal == true -> stringResource(R.string.player_source_original_quality, server)
    else -> stringResource(R.string.player_source_unknown, server)
}

/**
 * The format as the server sends it, for the tag between the times: the file's codec, or the
 * codec and bitrate it's converted to. Unknown (a stream, an episode, a codec the server didn't
 * name): no tag.
 */
@Composable
private fun formatOf(track: Track?, state: PlayerState): String? {
    if (track == null) return null
    val quality = state.quality
    return when {
        quality != null && !quality.isOriginal && !state.fromPhone ->
            stringResource(R.string.player_format_bitrate, codecLabel(quality.format.wireName), quality.bitrate)

        track.codec.isNotBlank() && (state.fromPhone || quality != null) -> codecLabel(track.codec)

        else -> null
    }
}

@Composable
private fun errorText(error: PlaybackError): String = stringResource(
    when (error) {
        PlaybackError.Network, PlaybackError.Unauthorized -> R.string.player_error_network
        PlaybackError.NotFound -> R.string.player_error_not_found
        PlaybackError.OldServer -> R.string.player_error_old_server
        PlaybackError.Other -> R.string.player_error_other
    },
)

/** The player's round buttons: a wash of the content color on the cover's background. */
@Composable
internal fun playerTint(): Color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = TINT_ALPHA)

/** The player's text and icons on the cover's background. */
@Composable
internal fun playerContent(): Color = MaterialTheme.colorScheme.onPrimaryContainer

private val COMPACT_HEIGHT = 720.dp
private val MAX_COVER = 420.dp
private const val TOGGLES_WIDTH = 0.82f
private const val TINT_ALPHA = 0.12f
private const val MUTED_ALPHA = 0.72f
private const val SUBTITLE_ALPHA = 0.8f

@PhonePreviews
@Composable
private fun NowPlayingPreview() {
    DowntifyTheme {
        NowPlayingScreen(
            state = PlayerState(
                track = PreviewData.tracks[1],
                isPlaying = true,
                positionMs = 70_000,
                durationMs = 239_000,
                context = PlaybackContext(PlaybackContextType.Album, "alb-Glass Harbor", "Glass Harbor"),
                quality = StreamQuality.Original,
            ),
            isLiked = true,
            serverName = "nas.local",
            actions = NowPlayingActions(),
        )
    }
}

@PhonePreviews
@Composable
private fun NowPlayingEpisodePreview() {
    DowntifyTheme {
        NowPlayingScreen(
            state = PlayerState(
                episode = PlayingEpisode(7, 1, "The Sweetest Thing", "Radiolab", "", "Podcasts/R/e.mp3"),
                isPlaying = false,
                positionMs = 83_000,
                durationMs = 1_862_000,
                speed = 1.25f,
            ),
            isLiked = false,
            serverName = "nas.local",
            actions = NowPlayingActions(),
        )
    }
}
