package com.henriquesebastiao.downtify.feature.musicai

import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiMoodMetaDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A mix added on the server must show up without shipping a new APK. */
class ServerDrivenMixTest {
    @After
    fun reset() = MoodCatalog.install(emptyList())

    @Test
    fun `a new server mix has a readable label and its own colour`() {
        MoodCatalog.install(
            listOf(
                MusicAiMoodMetaDto("techno", "Techno", "#0F172A", "weekly", "techno"),
                MusicAiMoodMetaDto("techno-nat", "Techno · national", "#0F172A", "weekly", "techno"),
            ),
        )
        assertEquals("Techno", moodName("techno"))
        assertEquals("Techno · national", moodName("techno-nat"))
        assertTrue(isGenreMood("techno"))
        assertTrue(isGenreMood("techno-nat"))
        assertNotNull(moodColor("techno"))
        assertEquals(moodColor("techno"), moodColor("techno-nat"))
    }

    @Test
    fun `an unknown theme still renders a readable label`() {
        MoodCatalog.install(emptyList())
        assertEquals("Dubstep", moodName("dubstep"))
        assertEquals("Dubstep · international", moodName("dubstep-intl"))
        assertFalse(isGenreMood("inconnu"))
    }

    @Test
    fun `the shelf lists mixes created by the server first`() {
        val caps = MusicAiCapabilitiesDto(moods = listOf("calme", "pop", "rock"))
        val playlists = listOf(
            MusicAiPlaylistDto(cadence = "weekly", mood = "techno", period = "2026-W47", count = 25),
            MusicAiPlaylistDto(cadence = "daily", mood = "calme", period = "2026-11-23", count = 40),
        )
        assertEquals(listOf("techno", "pop", "rock"), weeklyMoods(playlists, caps))
    }

    @Test
    fun `the shelf falls back to advertised genres before the first generation`() {
        val caps = MusicAiCapabilitiesDto(moods = listOf("detente", "jazz-nat", "jazz-intl"))
        assertEquals(listOf("jazz-nat", "jazz-intl"), weeklyMoods(emptyList(), caps))
    }
}
