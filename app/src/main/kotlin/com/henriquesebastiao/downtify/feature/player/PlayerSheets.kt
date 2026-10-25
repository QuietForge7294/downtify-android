package com.henriquesebastiao.downtify.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.R
import com.henriquesebastiao.downtify.core.designsystem.component.CoverTheme
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing
import com.henriquesebastiao.downtify.core.model.LrcParser
import com.henriquesebastiao.downtify.core.model.LyricLine
import com.henriquesebastiao.downtify.core.model.Lyrics

/**
 * Lyrics, in the cover's colors, synced to the playhead when the server has an LRC: the line
 * being sung in bold primary, the others faded. Tap a line to jump to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    lyrics: LyricsState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    coverUrl: String? = null,
) {
    CoverTheme(coverUrl) {
        val scheme = MaterialTheme.colorScheme
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ) {
            LyricsContent(lyrics, positionMs, onSeek)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsContent(lyrics: LyricsState, positionMs: Long, onSeek: (Long) -> Unit) {
    Column {
        SheetTitle(stringResource(R.string.player_lyrics))
        when (lyrics) {
            LyricsState.Loading -> Box(
                Modifier.fillMaxWidth().heightIn(min = 200.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            is LyricsState.Loaded -> when {
                lyrics.lyrics.isEmpty -> Text(
                    stringResource(R.string.player_lyrics_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(LinePadding).alpha(INACTIVE_ALPHA),
                )

                lyrics.lyrics.isSynced -> SyncedLyrics(lyrics.lyrics.synced, positionMs, onSeek)

                else -> Text(
                    lyrics.lyrics.plain,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(LinePadding),
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, positionMs: Long, onSeek: (Long) -> Unit) {
    val active = LrcParser.activeIndex(lines, positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.xl),
        modifier = Modifier.heightIn(max = 520.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == active
            // Same size for every line: only weight, color and opacity change, so nothing reflows.
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(horizontal = 28.dp, vertical = 9.dp)
                    .alpha(if (isActive) 1f else INACTIVE_ALPHA),
            )
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = Spacing.sm).semantics { heading() },
    )
}

private val LinePadding = PaddingValues(horizontal = 28.dp, vertical = Spacing.lg)
private const val INACTIVE_ALPHA = 0.45f

@PhonePreviews
@Composable
private fun LyricsPreview() {
    val lines = listOf(
        "The tide came in slowly",
        "And took what I left on the sand",
        "",
        "And the sky turned the sea's color",
    )
        .mapIndexed { i, text -> LyricLine(i * 4_000L, text) }
    DowntifyTheme {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            LyricsContent(LyricsState.Loaded("t", Lyrics(lines, "")), positionMs = 5_000, onSeek = {})
        }
    }
}
