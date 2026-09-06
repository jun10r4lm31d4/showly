package com.michaldrabik.data_remote.scrob.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrobMedia(
  @Json(name = "id") val id: Long,
  @Json(name = "tmdb_id") val tmdbId: Long?,
  @Json(name = "type") val type: String, // "movie" | "episode" | "series" | "season"
  @Json(name = "title") val title: String?,
  @Json(name = "overview") val overview: String?,
  @Json(name = "poster_path") val posterPath: String?,
  @Json(name = "backdrop_path") val backdropPath: String?,
  @Json(name = "release_date") val releaseDate: String?,
  @Json(name = "season_number") val seasonNumber: Int?,
  @Json(name = "episode_number") val episodeNumber: Int?,
  @Json(name = "show_title") val showTitle: String?,
  @Json(name = "show_poster_path") val showPosterPath: String?,
  @Json(name = "show_tmdb_id") val showTmdbId: Long?,
) {
  fun isMovie() = type == "movie"

  fun isEpisode() = type == "episode"

  fun isShowLevel() = type == "series" || type == "season"
}
