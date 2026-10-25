package com.henriquesebastiao.downtify.feature.library

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.formatDuration
import com.henriquesebastiao.downtify.ui.common.PreviewData
import com.henriquesebastiao.downtify.ui.common.TrackActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A phone-sized screen: the lazy list only composes what fits.
@Config(qualifiers = "w411dp-h914dp")
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun songsState() = LibraryUiState(
        loading = false,
        tab = LibraryTab.Songs,
        songs = PreviewData.tracks,
    )

    @Test
    fun songsTabOffersShuffle() {
        var shuffled = false
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                    onShuffleSongs = { shuffled = true },
                )
            }
        }
        compose.onNodeWithText("Songs").assertIsSelected()
        compose.onNodeWithText("Shuffle").assertIsEnabled().performClick()
        assertEquals(true, shuffled)
    }

    @Test
    fun theTabsSwitchTheLibrarysSection() {
        var picked: LibraryTab? = null
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = { picked = it },
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                )
            }
        }
        compose.onNodeWithText("Albums").assertIsNotSelected().performClick()
        assertEquals(LibraryTab.Albums, picked)
    }

    @Test
    fun aSongsDurationIsShownOnItsOwnLine() {
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                )
            }
        }
        val track = PreviewData.tracks.first()
        compose.onNodeWithText(formatDuration(track.duration), useUnmergedTree = true).assertExists()
    }

    @Test
    fun trackMenuFindsSimilarTracks() {
        var similar: Pair<String, String>? = null
        compose.setContent {
            DowntifyTheme {
                LibraryScreen(
                    state = songsState(),
                    navigation = LibraryNavigation(),
                    onSelectTab = {},
                    onSort = {},
                    onToggleLayout = {},
                    onRefresh = {},
                    onPlaySong = {},
                    trackActions = TrackActions(
                        onFindSimilar = { artist, title -> similar = artist to title },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("More options for Low Tide").performClick()
        compose.onNodeWithText("Find similar").performClick()
        assertEquals("Kenji Aoki" to "Low Tide", similar)
    }
}
