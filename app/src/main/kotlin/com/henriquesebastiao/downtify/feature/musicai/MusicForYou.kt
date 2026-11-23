package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiMoodMetaDto

internal val moodNames = mapOf(
    "calme" to "Calme",
    "concentration" to "Concentration",
    "energie" to "Motivation",
    "fete" to "Fête",
    "melancolie" to "Mélancolie",
    "detente" to "Détente",
    "rap" to "Rap & Hip-Hop",
    "rock" to "Rock",
    "electro" to "Électro",
    "pop" to "Pop",
    "jazz" to "Jazz",
)

/** Weekly genre themes known to this build when the server sends no descriptors. */
internal val genreMoodNames = listOf("rap", "rock", "electro", "pop", "jazz")

internal fun hexColor(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

/**
 * Label of any mix theme: the server label first, a known name second, then a
 * readable form of the slug. A brand-new theme is never rendered blank.
 */
internal fun moodName(mood: String, meta: MusicAiMoodMetaDto? = null): String? {
    meta?.label?.takeIf { it.isNotBlank() }?.let { return it }
    moodNames[mood]?.let { return it }
    val suffix = when {
        mood.endsWith("-nat") -> "national"
        mood.endsWith("-intl") -> "international"
        else -> null
    }
    val base = if (suffix == null) mood else mood.substringBeforeLast('-')
    if (base.isBlank()) return null
    val name = moodNames[base] ?: readableSlug(base)
    return if (suffix == null) name else "$name · $suffix"
}

private fun readableSlug(slug: String): String =
    slug.split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase() } }

internal fun isGenreMood(mood: String, meta: MusicAiMoodMetaDto? = null): Boolean =
    meta?.group?.let { it == "weekly" } ?: (mood.substringBeforeLast("-nat").substringBeforeLast("-intl") in genreMoodNames)

internal fun moodColor(mood: String, meta: MusicAiMoodMetaDto? = null): Color? {
    hexColor(meta?.color)?.let { return it }
    moodColors[mood]?.let { return it }
    moodColors[mood.substringBefore('-')]?.let { return it }
    return if (meta != null) fallbackMoodColor(mood) else null
}

/** Stable colour for a theme the server introduced after this build. */
private fun fallbackMoodColor(mood: String): Color {
    val palette = listOf(
        Color(0xFF0F766E),
        Color(0xFF7C3AED),
        Color(0xFFB45309),
        Color(0xFF1D4ED8),
    )
    return palette[(mood.hashCode() and Int.MAX_VALUE) % palette.size]
}

internal val moodColors = mapOf(
    "calme" to Color(0xFF4F46E5),
    "concentration" to Color(0xFF334155),
    "energie" to Color(0xFFC2410C),
    "fete" to Color(0xFFA21CAF),
    "melancolie" to Color(0xFF1D4ED8),
    "detente" to Color(0xFF0F766E),
    "rap" to Color(0xFF854D0E),
    "rock" to Color(0xFFB91C1C),
    "electro" to Color(0xFF0369A1),
    "pop" to Color(0xFFBE185D),
    "jazz" to Color(0xFF7C3AED),
)
internal fun mixTitle(cadence: String, mood: String) =
    "${if (cadence == "daily") "Mix du jour" else "Mix de la semaine"} · ${moodName(mood).orEmpty()}"
fun LazyListScope.musicDjThemeItems(
    caps: MusicAiCapabilitiesDto,
    state: MusicDjUiState,
    onStart: (String, String) -> Unit,
) {
    if (!caps.enabled("dj_interactive") || !caps.dj.continuousSupported) return
    item("dj-themes") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vos mixes DJ", style = MaterialTheme.typography.titleLarge)
            Text(
                "Une sélection locale qui se prolonge avec des transitions adaptées.",
                style = MaterialTheme.typography.bodySmall,
            )
            val recipes = listOf(
                Triple("Fête", "fete", "library"), Triple("Rap", "energie", "rap"), Triple("Rock", "energie", "rock"),
                Triple(
                    "Électro",
                    "fete",
                    "electro",
                ),
                Triple("Calme", "calme", "library"), Triple("Concentration", "concentration", "library"),
                Triple(
                    "Énergie",
                    "energie",
                    "library",
                ),
                Triple("Détente", "detente", "library"), Triple("Mélancolie", "melancolie", "library"),
            )
            recipes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, theme, style) ->
                        Card(Modifier.weight(1f)) {
                            TextButton(
                                onClick = { onStart(theme, style) },
                                enabled =
                                    !state.busy && caps.interactiveAvailable(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    Text(label, maxLines = 1)
                                    Text("DJ continu", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            if (!caps.interactiveAvailable()) {
                Text(
                    "Activez un moteur qualifié ou l’essai bêta volontaire dans Paramètres → IA.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
