package com.henriquesebastiao.downtify.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrashCapabilitiesDto(@SerialName("can_manage") val canManage: Boolean = false)

@Serializable
data class TrashSelectionDto(@SerialName("track_ids") val trackIds: List<String>)

@Serializable
data class TrashConfirmationDto(val confirmation: String)

@Serializable
data class TrashIdsDto(val ids: List<String>)

@Serializable
data class TrashMetadataDto(val title: String = "", val artist: String = "", val codec: String = "")

@Serializable
data class TrashItemDto(
    val id: String,
    val file: String = "",
    val metadata: TrashMetadataDto = TrashMetadataDto(),
    val state: String = "",
    val size: Long = 0,
    @SerialName("expires_at") val expiresAt: Double? = null,
    val error: String = "",
)

@Serializable
data class TrashPreviewDto(val confirmation: String, val items: List<TrashItemDto> = emptyList())

@Serializable
data class TrashPreviewStartDto(@SerialName("request_id") val requestId: String, val state: String = "preparing")

@Serializable
data class TrashPreviewStatusDto(
    val state: String = "preparing",
    val preview: TrashPreviewDto? = null,
    val error: String = "",
)

@Serializable
data class TrashListingDto(val items: List<TrashItemDto> = emptyList(), val total: Int = 0, val size: Long = 0)
