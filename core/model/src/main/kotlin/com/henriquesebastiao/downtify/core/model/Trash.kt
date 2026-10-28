package com.henriquesebastiao.downtify.core.model

data class TrashMetadata(val title: String = "", val artist: String = "", val codec: String = "")
data class TrashItem(
    val id: String,
    val file: String = "",
    val metadata: TrashMetadata = TrashMetadata(),
    val state: String = "",
    val size: Long = 0,
    val expiresAt: Double? = null,
    val error: String = "",
)
data class TrashPreview(val confirmation: String, val items: List<TrashItem> = emptyList())
data class TrashListing(val items: List<TrashItem> = emptyList(), val total: Int = 0, val size: Long = 0)
