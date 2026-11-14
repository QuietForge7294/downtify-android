package com.henriquesebastiao.downtify.core.data.library

import com.henriquesebastiao.downtify.core.model.CatalogSource
import com.henriquesebastiao.downtify.core.model.RemoteSong
import com.henriquesebastiao.downtify.core.model.Track
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryFileIdentityTest {
    @Test fun `library file takes precedence over title and remote id`() {
        val first = track("a", "a.flac")
        val selected = track("b", "b.flac")
        val snapshot = LibrarySnapshot(listOf(first, selected))
        assertSame(selected, snapshot.findSong(song("a", "b.flac")))
    }

    @Test fun `missing exact library file cannot fall back to another recording`() {
        val snapshot = LibrarySnapshot(listOf(track("a", "a.flac")))
        assertNull(snapshot.findSong(song("a", "missing.flac")))
    }

    @Test fun `ordinary AI and catalog rows retain existing local id lookup`() {
        val selected = track("a", "a.flac")
        assertSame(selected, LibrarySnapshot(listOf(selected)).findSong(song("a", "").copy(title = "Other")))
    }

    @Test fun `duplicate file identity preserves the first matching row`() {
        val selected = track("a", "same.flac")
        val snapshot = LibrarySnapshot(listOf(selected, track("b", "same.flac")))
        assertSame(selected, snapshot.findSong(song("remote", "same.flac")))
    }

    private fun song(id: String, file: String) = RemoteSong(
        id = id, title = "Title", artists = listOf("Artist"), album = "Album", coverUrl = "",
        durationSeconds = 1, url = "", source = CatalogSource.Other, previewUrl = "", year = "",
        raw = "{}", libraryFile = file,
    )

    private fun track(id: String, file: String) = Track(
        id = id, file = file, title = "Title", artist = "Artist", artists = listOf("Artist"),
        album = "Album", albumArtist = "Artist", albumId = "album", artistId = "artist",
        trackNumber = 1, year = "", duration = 1.0, codec = "flac", bitrate = 1,
        sampleRate = 1, channels = 1, size = 1, added = 1, hasCover = false, playlists = emptyList(),
    )
}
