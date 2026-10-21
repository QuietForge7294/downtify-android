package com.henriquesebastiao.downtify.core.data.playback

import androidx.room.Room
import com.henriquesebastiao.downtify.core.data.db.DowntifyDatabase
import com.henriquesebastiao.downtify.core.model.PlaybackSnapshot
import com.henriquesebastiao.downtify.core.model.SavedPlaybackItem
import com.henriquesebastiao.downtify.core.network.session.Session
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackRepositoryTest {
    @Test
    fun `conflated checkpoints retain the newest queue and obsolete owners cannot overwrite it`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            DowntifyDatabase::class.java,
        ).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val initial = Session("http://lan.example", "server", "Test", "device", "fixture")
        val paired = MutableStateFlow<Session?>(initial)
        val sessions = mockk<SessionStore>(relaxed = true)
        every { sessions.current } answers { paired.value }
        every { sessions.session } returns paired
        val repository = PlaybackRepository(database, sessions, scope)
        try {
            val first = PlaybackSnapshot(listOf(SavedPlaybackItem("a", "One", "Artist")), 0, 0)
            repository.save(initial.playbackOwner, first)
            await { database.playback().read() != null }
            val newest = first.copy(items = listOf(SavedPlaybackItem("b", "Two", "Artist")))
            repeat(100) { repository.save(initial.playbackOwner, newest.copy(positionMs = it.toLong())) }
            await { database.playback().read()?.positionMs == 99L }
            assertEquals("b", repository.load(initial.playbackOwner)!!.items.single().mediaId)
            paired.value = initial.copy(baseUrl = "https://vpn.example")
            assertEquals(99L, repository.load(initial.playbackOwner)!!.positionMs)
            paired.value = null
            await { database.playback().read() == null }
            repository.save(initial.playbackOwner, first)
            val another = initial.copy(deviceId = "new-pairing")
            paired.value = another
            repository.save(another.playbackOwner, newest)
            await { database.playback().read()?.owner == another.playbackOwner }
            assertNull(repository.load(initial.playbackOwner))
            assertEquals("b", repository.load(another.playbackOwner)!!.items.single().mediaId)
        } finally {
            scope.cancel()
            database.close()
        }
    }

    private suspend fun await(condition: suspend () -> Boolean) {
        withTimeout(10_000) { while (!condition()) delay(10) }
    }
}
