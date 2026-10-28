package com.henriquesebastiao.downtify.core.data.library

import com.henriquesebastiao.downtify.core.model.TrashItem
import com.henriquesebastiao.downtify.core.model.TrashListing
import com.henriquesebastiao.downtify.core.model.TrashMetadata
import com.henriquesebastiao.downtify.core.model.TrashPreview
import com.henriquesebastiao.downtify.core.network.ApiFactory
import com.henriquesebastiao.downtify.core.network.TrashApi
import com.henriquesebastiao.downtify.core.network.dto.TrashConfirmationDto
import com.henriquesebastiao.downtify.core.network.dto.TrashIdsDto
import com.henriquesebastiao.downtify.core.network.dto.TrashItemDto
import com.henriquesebastiao.downtify.core.network.dto.TrashListingDto
import com.henriquesebastiao.downtify.core.network.dto.TrashPreviewDto
import com.henriquesebastiao.downtify.core.network.dto.TrashSelectionDto
import com.henriquesebastiao.downtify.core.network.session.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

class TrashFailure(val code: String, cause: Throwable? = null) : Exception(code, cause)

@Singleton
class LibraryTrashRepository @Inject constructor(private val sessions: SessionStore, private val apis: ApiFactory) {
    val session get() = sessions.session

    private suspend fun <T> call(block: suspend (TrashApi) -> T): T = withContext(Dispatchers.IO) {
        val session = sessions.current ?: throw TrashFailure("unavailable")
        try {
            block(apis.createTrashPatient(session.baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val code = runCatching {
                Json.parseToJsonElement(e.response()?.errorBody()?.string().orEmpty())
                    .jsonObject["detail"]?.jsonObject?.get("code")?.jsonPrimitive?.content
            }.getOrNull()
            throw TrashFailure(code ?: if (e.code() == 403) "admin_required" else "unavailable", e)
        } catch (e: IOException) {
            throw TrashFailure("unavailable", e)
        } catch (e: SerializationException) {
            throw TrashFailure("unavailable", e)
        }
    }

    suspend fun canManage(): Boolean = try {
        call { it.trashCapabilities().canManage }
    } catch (_: TrashFailure) {
        false
    }

    suspend fun preview(trackIds: List<String>) = withTimeoutOrNull(30 * 60 * 1000L) {
        val request = call { it.trashPreview(TrashSelectionDto(trackIds)) }
        repeat(1800) {
            val result = call { it.trashPreviewStatus(request.requestId) }
            if (result.state == "failed") throw TrashFailure(result.error.ifBlank { "preview_unavailable" })
            if (result.state ==
                "ready"
            ) {
                return@withTimeoutOrNull result.preview?.toModel() ?: throw TrashFailure("preview_unavailable")
            }
            delay(1000)
        }
        null
    } ?: throw TrashFailure("preview_unavailable")
    suspend fun confirm(token: String) = call { it.trashConfirm(TrashConfirmationDto(token)) }
    suspend fun list(offset: Int, search: String) = call { it.trashList(offset, search).toModel() }
    suspend fun restore(ids: List<String>) = call { it.trashRestore(TrashIdsDto(ids)) }
    suspend fun retry(ids: List<String>) = call { it.trashRetry(TrashIdsDto(ids)) }
    suspend fun exclusions() = call { it.trashExclusions().toModel() }
    suspend fun allowDownload(ids: List<String>) = call { it.trashAllowDownload(TrashIdsDto(ids)) }
}

private fun TrashItemDto.toModel() = TrashItem(
    id,
    file,
    TrashMetadata(metadata.title, metadata.artist, metadata.codec),
    state,
    size,
    expiresAt,
    error,
)
private fun TrashPreviewDto.toModel() = TrashPreview(confirmation, items.map { it.toModel() })
private fun TrashListingDto.toModel() = TrashListing(items.map { it.toModel() }, total, size)
