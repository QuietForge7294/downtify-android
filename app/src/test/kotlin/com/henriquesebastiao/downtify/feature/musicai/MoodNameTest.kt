package com.henriquesebastiao.downtify.feature.musicai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodNameTest {
    @Test
    fun `base moods and locale variants have distinct labels`() {
        assertEquals("Jazz", moodName("jazz"))
        assertEquals("Pop · national", moodName("pop-nat"))
        assertEquals("Rap & Hip-Hop · international", moodName("rap-intl"))
        assertEquals("Électro · national", moodName("electro-nat"))
        assertEquals("Pop Unknown", moodName("pop-unknown"))
        assertEquals("Inconnu", moodName("inconnu"))
    }

    @Test
    fun `genre themes cover the locale variants but not the moods`() {
        assertTrue(isGenreMood("rap"))
        assertTrue(isGenreMood("jazz"))
        assertTrue(isGenreMood("jazz-nat"))
        assertTrue(isGenreMood("rock-intl"))
        assertFalse(isGenreMood("detente"))
        assertFalse(isGenreMood("calme"))
        assertFalse(isGenreMood("inconnu-nat"))
    }

    @Test
    fun `variant colors reuse their base genre palette`() {
        assertEquals(moodColors["jazz"], moodColor("jazz-nat"))
        assertEquals(moodColors["rock"], moodColor("rock-intl"))
        assertNull(moodColor("inconnu"))
    }
}
