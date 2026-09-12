package com.michaldrabik.data_remote.scrob.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrobListItemAddRequest(
  @Json(name = "tmdb_id") val tmdbId: Long,
  @Json(name = "media_type") val mediaType: String,
  @Json(name = "season_number") val seasonNumber: Int? = null,
)
