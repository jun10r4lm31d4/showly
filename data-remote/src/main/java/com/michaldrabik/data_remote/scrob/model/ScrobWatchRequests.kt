package com.michaldrabik.data_remote.scrob.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrobWatchRequest(
  @Json(name = "tmdb_id") val tmdbId: Long,
  @Json(name = "media_type") val mediaType: String,
  @Json(name = "watched_at") val watchedAt: String? = null,
  @Json(name = "completed") val completed: Boolean = true,
  @Json(name = "series_tmdb_id") val seriesTmdbId: Long? = null,
  @Json(name = "series_tvdb_id") val seriesTvdbId: Long? = null,
  @Json(name = "season_number") val seasonNumber: Int? = null,
  @Json(name = "episode_number") val episodeNumber: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ScrobSeasonWatchRequest(
  @Json(name = "series_tmdb_id") val seriesTmdbId: Long,
  @Json(name = "season_number") val seasonNumber: Int? = null,
  @Json(name = "series_tvdb_id") val seriesTvdbId: Long? = null,
  @Json(name = "episode_order") val episodeOrder: String? = null,
  @Json(name = "watched_at") val watchedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ScrobShowWatchRequest(
  @Json(name = "series_tmdb_id") val seriesTmdbId: Long,
  @Json(name = "series_tvdb_id") val seriesTvdbId: Long? = null,
  @Json(name = "watched_at") val watchedAt: String? = null,
)
