package com.michaldrabik.data_local.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
  @PrimaryKey @ColumnInfo(name = "id") val id: Long = 1,
  @ColumnInfo(name = "tvdb_token", defaultValue = "") val tvdbToken: String,
  @ColumnInfo(name = "tvdb_token_timestamp", defaultValue = "0") val tvdbTokenTimestamp: Long,
  @ColumnInfo(name = "reddit_token", defaultValue = "") val redditToken: String,
  @ColumnInfo(name = "reddit_token_timestamp", defaultValue = "0") val redditTokenTimestamp: Long,
)
