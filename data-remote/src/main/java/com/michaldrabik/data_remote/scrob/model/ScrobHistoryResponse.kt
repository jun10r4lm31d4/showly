package com.michaldrabik.data_remote.scrob.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrobHistoryEvent(
  @Json(name = "id") val id: Long,
  @Json(name = "media") val media: ScrobMedia,
  @Json(name = "watched_at") val watchedAt: String?,
)

@JsonClass(generateAdapter = true)
data class ScrobHistoryResponse(
  @Json(name = "page") val page: Int,
  @Json(name = "page_size") val pageSize: Int,
  @Json(name = "total_results") val totalResults: Int,
  @Json(name = "total_pages") val totalPages: Int,
  @Json(name = "results") val results: List<ScrobHistoryEvent>,
)
