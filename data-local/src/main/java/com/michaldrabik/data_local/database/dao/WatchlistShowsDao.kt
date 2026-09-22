@file:Suppress("ktlint:standard:max-line-length")

package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.michaldrabik.data_local.database.model.Show
import com.michaldrabik.data_local.database.model.WatchlistShow
import com.michaldrabik.data_local.sources.WatchlistShowsLocalDataSource

@Dao
interface WatchlistShowsDao : WatchlistShowsLocalDataSource {

  @Query(
    "SELECT " +
      "shows.id_trakt, " +
      "shows.id_tvdb, " +
      "shows.id_tmdb, " +
      "shows.id_imdb, " +
      "shows.id_slug, " +
      "shows.id_tvrage, " +
      "shows.title, " +
      "shows.year, " +
      "shows.overview, " +
      "shows.first_aired, " +
      "shows.runtime, " +
      "shows.airtime_day, " +
      "shows.airtime_time, " +
      "shows.airtime_timezone, " +
      "shows.certification, " +
      "shows.network, " +
      "shows.country, " +
      "shows.trailer, " +
      "shows.homepage, " +
      "shows.status, " +
      "shows.rating, " +
      "shows.votes, " +
      "shows.genres, " +
      "shows.aired_episodes, " +
      "shows_see_later.updated_at, " +
      "shows_see_later.created_at " +
      "FROM shows " +
      "INNER JOIN shows_see_later USING(id_trakt)",
  )
  override suspend fun getAll(): List<Show>

  @Query("SELECT shows.id_trakt FROM shows INNER JOIN shows_see_later USING(id_trakt)")
  override suspend fun getAllTraktIds(): List<Long>

  @Query(
    "SELECT shows.* FROM shows INNER JOIN shows_see_later ON shows_see_later.id_trakt == shows.id_trakt WHERE shows.id_trakt == :traktId",
  )
  override suspend fun getById(traktId: Long): Show?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  override suspend fun insert(show: WatchlistShow)

  @Query("DELETE FROM shows_see_later WHERE id_trakt == :traktId")
  override suspend fun deleteById(traktId: Long)

  @Query("SELECT EXISTS(SELECT 1 FROM shows_see_later WHERE id_trakt = :traktId LIMIT 1);")
  override suspend fun checkExists(traktId: Long): Boolean
}
