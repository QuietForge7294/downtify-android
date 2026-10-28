package com.henriquesebastiao.downtify.core.network

import com.henriquesebastiao.downtify.core.network.dto.TrashCapabilitiesDto
import com.henriquesebastiao.downtify.core.network.dto.TrashItemDto
import com.henriquesebastiao.downtify.core.network.dto.TrashPreviewDto
import com.henriquesebastiao.downtify.core.network.dto.TrashSelectionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashContractTest {
    @Test
    fun `missing capability never grants administration`() {
        assertFalse(NetworkJson.decodeFromString<TrashCapabilitiesDto>("{}").canManage)
        assertTrue(
            NetworkJson.decodeFromString<TrashCapabilitiesDto>("""{"can_manage":true,"retention_days":30}""").canManage,
        )
    }

    @Test
    fun `preview retains concrete file format and title`() {
        val result = NetworkJson.decodeFromString<TrashPreviewDto>(
            """
            {"confirmation":"one-use-token","expires_at":123,"items":[
            {"id":"i1","file":"Artist/Song.flac",
            "metadata":{"title":"Song","artist":"Artist","codec":"flac","unknown":"allowed"},
            "state":"preview","size":12345,"expires_at":null}]}
            """.trimIndent(),
        )
        assertEquals("one-use-token", result.confirmation)
        assertEquals("flac", result.items.single().metadata.codec)
        assertEquals("Artist/Song.flac", result.items.single().file)
        assertNull(result.items.single().expiresAt)
    }

    @Test
    fun `selection sends stable ids rather than filesystem paths`() {
        val encoded = NetworkJson.encodeToString(TrashSelectionDto.serializer(), TrashSelectionDto(listOf("t1", "t2")))
        assertEquals("""{"track_ids":["t1","t2"]}""", encoded)
    }

    @Test
    fun `unknown states and fractional expiration do not crash decoding`() {
        val item = NetworkJson.decodeFromString<TrashItemDto>(
            """{"id":"i","state":"future-state","expires_at":123.5,"error":"new_code"}""",
        )
        assertEquals("future-state", item.state)
        assertEquals(123.5, item.expiresAt!!, 0.0)
    }
}
