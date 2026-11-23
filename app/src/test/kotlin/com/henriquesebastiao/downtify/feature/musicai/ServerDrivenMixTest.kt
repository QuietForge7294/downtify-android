package com.henriquesebastiao.downtify.feature.musicai

import com.henriquesebastiao.downtify.core.network.dto.MusicAiCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiMoodMetaDto
import com.henriquesebastiao.downtify.core.network.dto.MusicAiPlaylistDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerDrivenMixTest {
    private val techno = MusicAiMoodMetaDto("techno", "Techno nouveau", "#123456", "weekly", "techno")
    private val caps = MusicAiCapabilitiesDto(
        version = 1,
        features = mapOf("daily" to true, "weekly" to true),
        moods = listOf("pop", "rock"),
        moodsMeta = listOf(techno),
    )

    @Test fun groupsOnlyPlayableCardsAndKeepsOnePerTheme() {
        val daily = MusicAiPlaylistDto("daily", "calme", "2026-11-23", 40)
        val weekly = MusicAiPlaylistDto("weekly", "calme", "2026-11-23", 25)
        val genre = MusicAiPlaylistDto("weekly", "techno", "2026-11-16", 25, state = "previous")
        val pending = genre.copy(period = "2026-11-23", count = 0, state = "preparing")
        val older = daily.copy(period = "2026-11-21")
        assertEquals(
            listOf(listOf(daily), listOf(weekly), listOf(genre)),
            mixGroups(listOf(older, pending, weekly, genre, daily), caps),
        )
    }

    @Test fun metadataComesFromTheCurrentSession() {
        assertEquals("Techno nouveau", moodName("techno", techno))
        assertEquals("Techno", moodName("techno"))
        assertNotEquals(moodColor("techno", techno), moodColor("techno"))
    }

    @Test fun oldServersUseKnownGenreNamesAndRespectDisabledFeatures() {
        val jazz = MusicAiPlaylistDto("weekly", "jazz-nat", "2026-11-23", 25)
        assertEquals(listOf(jazz), mixGroups(listOf(jazz), caps.copy(moodsMeta = emptyList()))[2])
        assertEquals(emptyList<MusicAiPlaylistDto>(), mixGroups(listOf(jazz), caps.copy(features = emptyMap()))[2])
    }
}
