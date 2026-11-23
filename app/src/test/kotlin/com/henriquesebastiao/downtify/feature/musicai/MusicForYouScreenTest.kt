package com.henriquesebastiao.downtify.feature.musicai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class MusicForYouScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeShowsFourPerSectionAndKeepsNavigationSimple() {
        val caps = MusicAiCapabilitiesDto(version = 1, features = mapOf("daily" to true, "weekly" to true))
        val cards = (1..5).flatMap { number ->
            listOf(
                MusicAiPlaylistDto("daily", "daily-$number", "2026-11-23", 40, title = "Jour $number"),
                MusicAiPlaylistDto("weekly", "weekly-$number", "2026-11-23", 25, title = "Semaine $number"),
            )
        } + listOf("rap", "rock", "pop", "jazz", "electro").mapIndexed { index, genre ->
            MusicAiPlaylistDto("weekly", genre, "2026-11-23", 25, title = "Genre " + (index + 1))
        }
        var selected = ""
        var opened = false
        compose.setContent {
            DowntifyTheme {
                LazyColumn {
                    item {
                        DailyMusicShelf(MusicAiUiState(playlists = cards), caps, { cadence, mood ->
                            selected = "$cadence:$mood"
                        }, { opened = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Jour 1").performClick()
        assertEquals("daily:daily-1", selected)
        compose.onNodeWithText("Tout voir").performClick()
        assertEquals(true, opened)
        listOf("Jour 5", "Semaine 5", "Genre 5", "Pour vous DJ", "En préparation", "Actualiser").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }
}
