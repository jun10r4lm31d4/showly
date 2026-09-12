package com.michaldrabik.data_local.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable outbox for local watched-state changes that still need to be pushed
 * to the user's Scrob server. Rows survive process death and offline periods;
 * [com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncWorker] drains them.
 */
@Entity(
  tableName = "scrob_pending_ops",
  indices = [Index(value = ["created_at"], name = "index_scrob_pending_ops_created_at")],
)
data class ScrobPendingOp(
  @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
  @ColumnInfo(name = "op") val op: String,
  @ColumnInfo(name = "watched") val watched: Boolean,
  @ColumnInfo(name = "tmdb_id") val tmdbId: Long,
  @ColumnInfo(name = "show_tmdb_id", defaultValue = "-1") val showTmdbId: Long = -1,
  @ColumnInfo(name = "season_number", defaultValue = "-1") val seasonNumber: Int = -1,
  @ColumnInfo(name = "episode_number", defaultValue = "-1") val episodeNumber: Int = -1,
  @ColumnInfo(name = "watched_at", defaultValue = "-1") val watchedAtMillis: Long = -1,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "list_id", defaultValue = "-1") val listId: Long = -1,
  @ColumnInfo(name = "media_type", defaultValue = "") val mediaType: String = "",
)
