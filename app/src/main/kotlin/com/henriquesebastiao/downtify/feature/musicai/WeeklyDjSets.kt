package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicDjSessionDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal fun MusicAiCapabilitiesDto.weeklySetsEnabled() =
    djWeeklySets && listOf("weekly", "dj_prepared", "dj_weekly").all(::enabled)

@Composable
fun WeeklyDjSection(featured: Boolean, onSeeAll: () -> Unit = {}, viewModel: MusicDjViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, caps.weeklySetsEnabled()) {
        if (!caps.weeklySetsEnabled()) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.refreshWeekly()
                val preparing = viewModel.state.value.weekly?.cards.orEmpty().any {
                    it.session?.state in setOf("queued", "preparing", "rendering")
                }
                delay(if (preparing) 10_000 else 60_000)
            }
        }
    }
    WeeklyDjShelf(
        state,
        caps,
        featured,
        onSeeAll,
        onPlay = { viewModel.play(it) },
        onPrepare = viewModel::prepareWeekly,
        onSave = viewModel::saveCopy,
    )
}

@Composable
internal fun WeeklyDjShelf(
    state: MusicDjUiState,
    caps: MusicAiCapabilitiesDto,
    featured: Boolean,
    onSeeAll: () -> Unit = {},
    onPlay: (MusicDjSessionDto) -> Unit = {},
    onPrepare: (String) -> Unit = {},
    onSave: (MusicDjSessionDto, String) -> Unit = { _, _ -> },
) {
    if (!caps.weeklySetsEnabled()) return
    val weekly = state.weekly
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Pour vous DJ", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (featured) TextButton(onClick = onSeeAll) { Text("Tous les sets") }
        }
        Text("Des sets audio de la semaine, adaptés à vos sélections. Aucune sauvegarde automatique.")
        if (weekly == null) {
            Text("Chargement des sets…")
        } else {
            val cards = if (featured) {
                weekly.featured.mapNotNull { theme -> weekly.cards.firstOrNull { it.theme == theme } }.take(4)
            } else {
                weekly.cards
            }
            for (card in cards) {
                WeeklyDjCard(card.theme, card.session, state.busy, caps, onPlay, onPrepare, onSave)
            }
            if (!featured && weekly.copies.isNotEmpty()) {
                Text("Mes sets enregistrés", style = MaterialTheme.typography.titleMedium)
                for (copy in weekly.copies) {
                    WeeklyDjCard(
                        copy.weeklyTheme.ifBlank {
                            copy.theme
                        },
                        copy,
                        state.busy,
                        caps,
                        onPlay,
                        onPrepare,
                        onSave,
                    )
                }
            }
        }
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WeeklyDjCard(
    theme: String,
    session: MusicDjSessionDto?,
    busy: Boolean,
    caps: MusicAiCapabilitiesDto,
    onPlay: (MusicDjSessionDto) -> Unit,
    onPrepare: (String) -> Unit,
    onSave: (MusicDjSessionDto, String) -> Unit,
) {
    val ready = session?.state == "ready"
    val preparing = session?.state in setOf("queued", "preparing", "rendering")
    val title = session?.title?.ifBlank { null } ?: "Set DJ · ${moodNames[theme] ?: theme}"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MusicMoodArt(theme, "weekly", Modifier.fillMaxWidth().height(104.dp), subtitle = "SET DJ", compact = true)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (session != null) {
                val minutes = (session.duration.coerceAtLeast(0.0) / 60).toInt()
                Text("${session.trackCount ?: session.tracks.size} titres · $minutes min")
            }
            Text(weeklyStatus(session))
            if (preparing) {
                LinearProgressIndicator(
                    progress = { (session?.progress ?: 0.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ready) {
                TextButton(onClick = { onPlay(session) }, enabled = !busy) { Text("Écouter $title") }
                if (caps.djAssetCopies && !session.immutableCopy) CopySetForm(session, title, busy, onSave)
            } else if (!preparing) {
                TextButton(onClick = { onPrepare(theme) }, enabled = !busy && caps.dj.preparedReady) {
                    Text("Préparer ${moodNames[theme] ?: theme}")
                }
            }
        }
    }
}

private fun weeklyStatus(session: MusicDjSessionDto?) = when (session?.state) {
    "ready" -> if (session.immutableCopy) "Copie enregistrée · audio conservé" else "Prêt à écouter"
    "queued" -> "En attente · priorité à votre bibliothèque et à la lecture"
    "preparing", "rendering" -> "En préparation · ${(session.progress.coerceIn(0.0, 1.0) * 100).toInt()} %"
    "expired" -> "À préparer à nouveau"
    "failed" -> "La préparation a échoué. Vous pouvez réessayer."
    else -> "Pas encore préparé"
}

@Composable
private fun CopySetForm(
    session: MusicDjSessionDto,
    title: String,
    busy: Boolean,
    onSave: (MusicDjSessionDto, String) -> Unit,
) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    var name by rememberSaveable(session.id) { mutableStateOf(title) }
    if (!expanded) {
        TextButton(onClick = { expanded = true }, enabled = !busy) { Text("Enregistrer une copie") }
    } else {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 160) name = it },
            label = { Text("Nom de la copie") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(onClick = {
                onSave(session, name)
            }, enabled = !busy && name.isNotBlank()) { Text("Conserver l’audio") }
            TextButton(onClick = { expanded = false }, enabled = !busy) { Text("Annuler") }
        }
    }
}
