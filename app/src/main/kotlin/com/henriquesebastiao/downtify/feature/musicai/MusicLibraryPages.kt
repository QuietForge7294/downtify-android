package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.henriquesebastiao.downtify.core.designsystem.component.Artwork
import com.henriquesebastiao.downtify.core.network.ServerUrls
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiMoodMetaDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import com.henriquesebastiao.downtify.core.player.LocalDjMode
import com.henriquesebastiao.downtify.feature.player.PlaybackMixMode
import com.henriquesebastiao.downtify.ui.common.LocalCoverUrls
import com.henriquesebastiao.downtify.ui.common.plusBottomBars
import kotlinx.coroutines.awaitCancellation

internal fun mixGroups(
    playlists: List<MusicAiPlaylistDto>,
    caps: MusicAiCapabilitiesDto,
): List<List<MusicAiPlaylistDto>> {
    val metadata = caps.moodsMeta.associateBy { it.name }
    val available = playlists
        .filter { it.count > 0 && it.state in setOf("ready", "previous") && caps.enabled(it.cadence) }
        .sortedWith(compareByDescending<MusicAiPlaylistDto> { it.period }.thenByDescending { it.created })
        .distinctBy { it.cadence to it.mood }
    val (genres, weekly) = available.filter { it.cadence == "weekly" }.partition {
        isGenreMood(it.mood, metadata[it.mood])
    }
    return listOf(available.filter { it.cadence == "daily" }, weekly, genres)
}

private val mixSectionTitles = listOf("Pour vous aujourd’hui", "Pour vous cette semaine", "Vos genres")

@Composable
fun DailyMusicShelf(
    state: MusicAiUiState,
    caps: MusicAiCapabilitiesDto,
    onOpen: (String, String) -> Unit,
    onSeeAll: () -> Unit,
) {
    val groups = remember(state.playlists, caps) { mixGroups(state.playlists, caps) }
    if (groups.all { it.isEmpty() }) return
    val metadata = remember(caps.moodsMeta) { caps.moodsMeta.associateBy { it.name } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pour vous", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSeeAll) { Text("Tout voir") }
        }
        groups.forEachIndexed { index, cards ->
            if (cards.isNotEmpty()) {
                Text(mixSectionTitles[index], style = MaterialTheme.typography.titleSmall)
                cards.take(4).chunked(2).forEach { MixRow(it, metadata, onOpen) }
            }
        }
    }
}

@Composable
private fun MixRow(
    cards: List<MusicAiPlaylistDto>,
    metadata: Map<String, MusicAiMoodMetaDto>,
    onOpen: (String, String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (card in cards) {
            val theme = metadata[card.mood]
            val color = moodColor(card.mood, theme) ?: MaterialTheme.colorScheme.surfaceVariant
            val title = card.title.ifBlank { moodName(card.mood, theme).orEmpty() }
            val cover = LocalCoverUrls.current.track(card.coverTrackIds.firstOrNull(), ServerUrls.COVER_SMALL)
            Surface(
                onClick = { onOpen(card.cadence, card.mood) },
                shape = MaterialTheme.shapes.medium,
                color = color,
                modifier = Modifier.weight(1f),
            ) {
                Row(Modifier.heightIn(min = 64.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (cover != null) {
                        Artwork(cover, null, Modifier.size(48.dp))
                    }
                    Text(
                        title,
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (color.luminance() > 0.179f) Color.Black else Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (cards.size == 1) Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlaylistsRoute(
    onBack: () -> Unit,
    cadence: String = "daily",
    mood: String = "",
    viewModel: MusicAiViewModel = hiltViewModel(),
    onOpenPlaylist: (String, String) -> Unit = { c, m -> viewModel.openPlaylist(c, m) },
    onArtist: (String) -> Unit = {},
    onAlbum: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(cadence, mood, lifecycle, viewModel) {
        if (mood.isNotBlank()) {
            viewModel.openPlaylist(cadence, mood)
        } else {
            lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshPlaylists()
                awaitCancellation()
            }
        }
    }
    if (mood.isNotBlank()) {
        TemporaryMusicPlaylist(
            viewModel,
            state,
            onBack,
            onArtist,
            onAlbum,
            caps.moodsMeta.firstOrNull { it.name == mood },
        )
        return
    }
    val groups = remember(state.playlists, caps) { mixGroups(state.playlists, caps) }
    val metadata = remember(caps.moodsMeta) { caps.moodsMeta.associateBy { it.name } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Pour vous") }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp).plusBottomBars(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEachIndexed { index, cards ->
                if (cards.isNotEmpty()) {
                    item("header-$index") {
                        Text(mixSectionTitles[index], style = MaterialTheme.typography.titleMedium)
                    }
                    items(cards.chunked(2), key = { "$index:${it.first().mood}" }) {
                        MixRow(it, metadata, onOpenPlaylist)
                    }
                }
            }
            if (groups.all { it.isEmpty() } && !state.busy) {
                item("empty-mixes") { Text(state.message.ifBlank { "Aucun mix disponible pour le moment." }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicStudioRoute(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    seed: String = "",
    viewModel: MusicDjViewModel = hiltViewModel(),
    onOpenWeeklyDj: (String, String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val queue: com.henriquesebastiao.downtify.feature.player.QueueDjViewModel = hiltViewModel()
    val queueMessage by queue.message.collectAsStateWithLifecycle()
    val localDevice by queue.localDevice.collectAsStateWithLifecycle()
    val mode by queue.mixMode.collectAsStateWithLifecycle()
    val localMode by queue.localMode.collectAsStateWithLifecycle()
    val localReadiness by queue.localReadiness.collectAsStateWithLifecycle()
    val queueChecking by queue.checking.collectAsStateWithLifecycle()
    val queueArmed by queue.armed.collectAsStateWithLifecycle()
    val queueBusy by queue.busy.collectAsStateWithLifecycle()
    val queueAvailability by queue.availability.collectAsStateWithLifecycle()
    val queuePhase by queue.phase.collectAsStateWithLifecycle()
    LaunchedEffect(queue) { queue.onQueueShown() }
    LaunchedEffect(seed) {
        viewModel.refresh()
        if (seed.isNotBlank()) viewModel.seed(seed)
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("DJ · Bêta") }, navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp).plusBottomBars(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (caps.weeklySetsEnabled()) {
                item("weekly-dj-sets") { WeeklyDjSection(featured = false, onOpenSet = onOpenWeeklyDj) }
            }
            item {
                Text(
                    "Créez un mix préparé ou continu à partir de votre musique. " +
                        "Les transitions et les titres restent dans votre lecteur habituel.",
                )
                TextButton(onClick = onSettings) { Text("Paramètres IA") }
                com.henriquesebastiao.downtify.feature.player.PlaybackMixSelector(mode, queue::selectMode)
                com.henriquesebastiao.downtify.feature.player.RhythmicDjEngine(localDevice, queue::chooseDevice)
                if (localDevice && mode == PlaybackMixMode.Dj) {
                    com.henriquesebastiao.downtify.feature.player.LocalDjFeedback(
                        localReadiness,
                        queue::retryLocalPlans,
                        queue::useServerFallback,
                    )
                }
                Text(
                    androidx.compose.ui.res.stringResource(
                        if (localDevice) {
                            com.henriquesebastiao.downtify.R.string.music_dj_local_description
                        } else {
                            com.henriquesebastiao.downtify.R.string.music_dj_server_description
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (localMode == LocalDjMode.Transitions || localDevice) {
                    val seconds by queue.transitionSeconds.collectAsStateWithLifecycle()
                    Column {
                        Text(
                            "Durée du fondu : $seconds s",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        androidx.compose.material3.Slider(
                            value = seconds.toFloat(),
                            onValueChange = { queue.setTransitionSeconds(it.toInt()) },
                            valueRange = 1f..12f,
                            steps = 10,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Un fondu plus long adoucit l'enchaînement, un fondu court reste proche du " +
                                "titre suivant. Aucun effet si les deux titres ne sont pas dans votre " +
                                "bibliothèque locale.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(
                    onClick = queue::toggle,
                    enabled = !queueChecking || queueArmed || queueBusy,
                ) {
                    Text(
                        when {
                            queuePhase == com.henriquesebastiao.downtify.core.player.DjPhase.Preparing ->
                                "Annuler la préparation DJ"

                            queuePhase == com.henriquesebastiao.downtify.core.player.DjPhase.Ready ->
                                "Annuler le relais DJ"

                            mode == PlaybackMixMode.Transitions && queueArmed -> "Désactiver les transitions"

                            queueArmed || queueBusy ||
                                queuePhase == com.henriquesebastiao.downtify.core.player.DjPhase.Active ->
                                "Désactiver le mode DJ"

                            queueChecking && localMode == LocalDjMode.Transitions -> "Vérification des transitions…"

                            queueChecking -> "Vérification du DJ…"

                            queueAvailability.ready && localMode == LocalDjMode.Transitions ->
                                "Activer les transitions"

                            queueAvailability.ready -> "Activer le DJ sur la file"

                            else -> "Vérifier le DJ"
                        },
                    )
                }
                if (queueChecking || queueBusy) {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                val queueDetail = queueMessage.ifBlank { queueAvailability.reason }
                if (queueDetail.isNotBlank()) Text(queueDetail)
                if (!caps.enabled("dj_prepared") &&
                    !caps.enabled("dj_interactive")
                ) {
                    Text("Activez un mode DJ dans Paramètres → IA, puis autorisez-le sur le serveur.")
                }
            }
            if (!localDevice) musicDjThemeItems(caps, state, viewModel::continuous)
            item { MusicDjPanel() }
        }
    }
}
