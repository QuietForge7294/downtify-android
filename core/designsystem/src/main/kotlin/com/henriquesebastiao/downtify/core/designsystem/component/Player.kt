package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyType
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

// The player's pieces assume the content-tinted background of a CoverTheme: primaryContainer.

/**
 * The collapsed player above the navigation bar: a primaryContainer card with 24 dp corners, the
 * cover in a circle, title and artist, then [actions] ([MiniPlayerButton]s), and the song's
 * [progress] as a thin line along the bottom. Tapping the cover or the text opens Now Playing.
 */
@Composable
fun MiniPlayerCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    progress: () -> Float,
    onOpen: () -> Unit,
    openLabel: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = DowntifyShape.MiniPlayer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Row(
                Modifier.heightIn(min = MINI_PLAYER_HEIGHT).padding(start = Spacing.md, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Artwork(
                        url = coverUrl,
                        contentDescription = null,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onPrimaryContainer.copy(alpha = SUBTITLE_ALPHA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
            LinearProgressIndicator(
                progress = progress,
                color = scheme.primary,
                trackColor = scheme.onPrimaryContainer.copy(alpha = TRACK_ALPHA),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xs)
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
    }
}

/** A 48 dp round button of the [MiniPlayerCard]; [main] (play/pause) is filled with primary. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayerButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    main: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonShapes(shape = CircleShape, pressedShape = RoundedCornerShape(16.dp)),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (main) scheme.primary else scheme.onPrimaryContainer.copy(alpha = MINI_BUTTON_ALPHA),
            contentColor = if (main) scheme.onPrimary else scheme.onPrimaryContainer,
        ),
        modifier = modifier.size(48.dp),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription)
    }
}

/** What the transport buttons say to a screen reader. */
@Immutable
data class PlaybackLabels(val play: String, val pause: String, val previous: String, val next: String)

/**
 * Now Playing's transport (Material 3 Expressive button group): wide previous/next pills around
 * play/pause, which is a circle while paused and a 28 dp squircle while playing. The pressed
 * button widens and its neighbours make room. [compact] is 64 dp tall instead of 88.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackControls(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    labels: PlaybackLabels,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val height = if (compact) 64.dp else 88.dp
    val scheme = MaterialTheme.colorScheme
    val skipColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = scheme.onPrimaryContainer.copy(alpha = SKIP_ALPHA),
        contentColor = scheme.onPrimaryContainer,
    )
    val playColors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
    val playCorner by animateDpAsState(
        if (playing) PLAYING_CORNER else height / 2,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playCorner",
    )
    val skipShapes = ButtonShapes(shape = RoundedCornerShape(height / 2), pressedShape = RoundedCornerShape(28.dp))
    val playShapes = ButtonShapes(shape = RoundedCornerShape(playCorner), pressedShape = RoundedCornerShape(20.dp))
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                TransportButton(
                    DowntifyIcons.Previous,
                    labels.previous,
                    onPrevious,
                    skipShapes,
                    skipColors,
                    interaction,
                    Modifier.weight(1f).animateWidth(interaction).height(height),
                    SKIP_ICON,
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                TransportButton(
                    if (playing) DowntifyIcons.Pause else DowntifyIcons.Play,
                    if (playing) labels.pause else labels.play,
                    onTogglePlay, playShapes, playColors, interaction,
                    Modifier.weight(PLAY_WEIGHT).animateWidth(interaction).height(height), PLAY_ICON,
                    filled = true,
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                TransportButton(
                    DowntifyIcons.Next,
                    labels.next,
                    onNext,
                    skipShapes,
                    skipColors,
                    interaction,
                    Modifier.weight(1f).animateWidth(interaction).height(height),
                    SKIP_ICON,
                )
            },
            menuContent = {},
        )
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    shapes: ButtonShapes,
    colors: ButtonColors,
    interaction: MutableInteractionSource,
    modifier: Modifier,
    iconSize: Dp,
    filled: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(iconSize))
    }
    if (filled) {
        Button(
            onClick = onClick,
            shapes = shapes,
            colors = colors,
            contentPadding = PaddingValues(0.dp),
            interactionSource = interaction,
            modifier = modifier,
            content = content,
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = shapes,
            colors = colors,
            contentPadding = PaddingValues(0.dp),
            interactionSource = interaction,
            modifier = modifier,
            content = content,
        )
    }
}

/** One of Now Playing's toggles (shuffle, repeat, like): [label] says its state to a screen reader. */
@Immutable
data class PlayerToggle(val icon: Int, val label: String, val checked: Boolean, val onClick: () -> Unit)

/**
 * The row of toggles under the transport, in one pill: each 56 dp tall, rounder and tinted with
 * primary while on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerToggles(toggles: List<PlayerToggle>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.onPrimaryContainer.copy(alpha = TOGGLE_ALPHA),
        contentColor = scheme.onPrimaryContainer,
        checkedContainerColor = scheme.primary.copy(alpha = TOGGLE_CHECKED_ALPHA),
        checkedContentColor = scheme.onPrimaryContainer,
    )
    val shapes = ToggleButtonShapes(
        shape = RoundedCornerShape(20.dp),
        pressedShape = RoundedCornerShape(16.dp),
        checkedShape = CircleShape,
    )
    Row(
        modifier
            .background(scheme.surfaceContainerLowest.copy(alpha = GROUP_ALPHA), CircleShape)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        toggles.forEach { toggle ->
            ToggleButton(
                checked = toggle.checked,
                onCheckedChange = { toggle.onClick() },
                shapes = shapes,
                colors = colors,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(painterResource(toggle.icon), contentDescription = toggle.label)
            }
        }
    }
}

/**
 * The song's position as Material's slider over a wavy track that flows while [playing] and lies
 * flat when paused. [progress] follows the player except while the finger is on it; [onSeek]
 * gets the fraction where it lets go. [timeAt] spells a fraction as a time for the monospaced
 * labels below, which follow the finger.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    playing: Boolean,
    timeAt: (Float) -> String,
    duration: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: progress.coerceIn(0f, 1f)
    val position = timeAt(value)
    val scheme = MaterialTheme.colorScheme
    val stroke = with(LocalDensity.current) { Stroke(width = SEEK_STROKE.toPx(), cap = StrokeCap.Round) }
    // The slider's own state follows the player's position — except while the finger is on it.
    val state = rememberSliderState()
    LaunchedEffect(value) { state.value = value }
    Column(modifier.fillMaxWidth()) {
        Slider(
            state = state,
            enabled = enabled,
            onValueChange = {
                state.value = it
                dragging = it
            },
            onValueChangeFinished = {
                dragging?.let(onSeek)
                dragging = null
            },
            thumb = {
                Box(Modifier.size(SEEK_THUMB).background(scheme.primary, CircleShape))
            },
            track = {
                LinearWavyProgressIndicator(
                    progress = { state.value },
                    color = scheme.primary,
                    trackColor = scheme.primary.copy(alpha = SEEK_TRACK_ALPHA),
                    stroke = stroke,
                    trackStroke = stroke,
                    amplitude = { if (playing) 1f else 0f },
                    wavelength = SEEK_WAVELENGTH,
                    modifier = Modifier.fillMaxWidth().height(SEEK_WAVE_HEIGHT),
                )
            },
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
                stateDescription = "$position / $duration"
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(position, style = DowntifyType.dataSmall, color = scheme.onPrimaryContainer)
            Text(duration, style = DowntifyType.dataSmall, color = scheme.onPrimaryContainer)
        }
    }
}

private val MINI_PLAYER_HEIGHT = 72.dp
private val PLAYING_CORNER = 28.dp
private val SKIP_ICON = 32.dp
private val PLAY_ICON = 40.dp
private const val PLAY_WEIGHT = 1.15f
private val SEEK_STROKE = 6.dp
private val SEEK_THUMB = 24.dp
private val SEEK_WAVELENGTH = 36.dp
private val SEEK_WAVE_HEIGHT = 16.dp
private const val SUBTITLE_ALPHA = 0.8f
private const val TRACK_ALPHA = 0.18f
private const val MINI_BUTTON_ALPHA = 0.12f
private const val SKIP_ALPHA = 0.16f
private const val TOGGLE_ALPHA = 0.09f
private const val TOGGLE_CHECKED_ALPHA = 0.28f
private const val GROUP_ALPHA = 0.38f
private const val SEEK_TRACK_ALPHA = 0.26f

@PhonePreviews
@Composable
private fun PlayerPreview() {
    DowntifyTheme {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                MiniPlayerCard(
                    title = "A song with a long title",
                    subtitle = "The Artist",
                    coverUrl = null,
                    progress = { 0.3f },
                    onOpen = {},
                    openLabel = "Open player",
                ) {
                    MiniPlayerButton(DowntifyIcons.Explore, "Similar", {})
                    MiniPlayerButton(DowntifyIcons.Pause, "Pause", {}, main = true)
                }
                WavySeekBar(
                    progress = 0.35f,
                    onSeek = {},
                    playing = true,
                    timeAt = { "1:24" },
                    duration = "3:59",
                    contentDescription = "Position",
                )
                PlaybackControls(
                    playing = true,
                    onTogglePlay = {},
                    onPrevious = {},
                    onNext = {},
                    labels = PlaybackLabels("Play", "Pause", "Previous", "Next"),
                )
                PlayerToggles(
                    listOf(
                        PlayerToggle(DowntifyIcons.Shuffle, "Shuffle off", checked = false) {},
                        PlayerToggle(DowntifyIcons.Repeat, "Repeat all", checked = true) {},
                        PlayerToggle(DowntifyIcons.FavoriteFilled, "Liked", checked = true) {},
                    ),
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
