package com.henriquesebastiao.downtify.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** One local session. The queue payload is only rewritten when its contents change. */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0,
    val owner: String,
    val items: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val shuffleOrder: List<String>,
    val repeatMode: Int,
    val speed: Float,
    val contextType: String?,
    val contextId: String?,
    val contextTitle: String?,
)

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun read(): PlaybackStateEntity?

    @Upsert
    suspend fun write(state: PlaybackStateEntity)

    @Query("UPDATE playback_state SET currentIndex = :index, positionMs = :positionMs WHERE id = 0 AND owner = :owner")
    suspend fun checkpoint(owner: String, index: Int, positionMs: Long)

    @Query("DELETE FROM playback_state")
    suspend fun clear()
}
