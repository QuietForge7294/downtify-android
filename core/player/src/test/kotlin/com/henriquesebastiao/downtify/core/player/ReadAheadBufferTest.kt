package com.henriquesebastiao.downtify.core.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.henriquesebastiao.downtify.core.data.NetworkMonitor
import com.henriquesebastiao.downtify.core.data.session.ServerRepository
import com.henriquesebastiao.downtify.core.data.settings.SettingsRepository
import com.henriquesebastiao.downtify.core.data.settings.UserSettings
import com.henriquesebastiao.downtify.core.model.ReadAheadPolicy
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
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadAheadBufferTest {
    @Test
    fun `mobile policy and shuffled order control fetches and offline readiness`() = runBlocking {
        MockWebServer().apply { start() }.use { web ->
            repeat(4) { web.enqueue(MockResponse.Builder().body("synthetic-audio").build()) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val paired = Session(web.url("/").toString(), "server", "Test", "device", "fixture")
            val sessions = mockk<SessionStore>(relaxed = true)
            every { sessions.current } returns paired
            every { sessions.session } returns MutableStateFlow(paired)
            val preferences = MutableStateFlow(UserSettings())
            val settings = mockk<SettingsRepository>()
            every { settings.settings } returns preferences
            val status = MutableStateFlow(NetworkMonitor.Status(true, true))
            val network = mockk<NetworkMonitor>()
            every { network.status } returns status
            val server = mockk<ServerRepository>(relaxed = true)
            every { server.serverInfo } returns MutableStateFlow(null)
            val store = AudioCacheStore(RuntimeEnvironment.getApplication(), sessions, settings, scope)
            val resolver = mockk<StreamResolver>()
            every { resolver.preloadSpec(any(), any()) } answers {
                val id = firstArg<android.net.Uri>().lastPathSegment!!
                DataSpec.Builder().setUri(web.url("/$id").toString()).setKey(store.key(id, "original")).build()
            }
            val timeline = mockk<Timeline>()
            every { timeline.isEmpty } returns false
            every { timeline.getNextWindowIndex(any(), any(), any()) } answers {
                when (firstArg<Int>()) {
                    2 -> 0
                    0 -> 3
                    3 -> 1
                    else -> C.INDEX_UNSET
                }
            }
            val player = mockk<Player>(relaxed = true)
            every { player.currentTimeline } returns timeline
            every { player.currentMediaItemIndex } returns 2
            every { player.isPlaying } returns true
            every { player.playbackState } returns Player.STATE_READY
            every { player.shuffleModeEnabled } returns true
            every { player.getMediaItemAt(any()) } answers {
                val id = firstArg<Int>().toString()
                MediaItem.Builder().setMediaId(id).setUri(MediaItems.uriFor(id)).build()
            }
            val buffer = ReadAheadBuffer(
                store,
                resolver,
                OkHttpClient(),
                settings,
                network,
                server,
                sessions,
            )
            try {
                buffer.start(player, scope)
                assertEquals(0, web.requestCount)
                preferences.value = preferences.value.copy(readAhead = ReadAheadPolicy(3, 100, false))
                await { buffer.enabled && buffer.state.value.target == 3 }
                assertEquals(0, web.requestCount)
                preferences.value = preferences.value.copy(readAhead = ReadAheadPolicy(3, 100, true))
                await { buffer.state.value.ready == 3 }
                assertEquals(listOf("/2", "/0", "/3", "/1"), (0..3).map { web.takeRequest().url.encodedPath })
                status.value = NetworkMonitor.Status(false, true)
                await { buffer.state.value.ready == 3 }
                assertEquals(4, web.requestCount)
                store.cache.removeResource(store.key("0", "original"))
                await { buffer.state.value.ready == 2 }
                assertEquals(4, web.requestCount)
            } finally {
                buffer.stop()
                scope.cancel()
                store.cache.release()
            }
        }
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(10_000) { while (!condition()) delay(10) }
    }
}
