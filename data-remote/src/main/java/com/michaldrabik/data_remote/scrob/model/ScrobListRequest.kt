package com.michaldrabik.data_remote.scrob.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrobListCreateRequest(
  @Json(name = "name") val name: String,
  @Json(name = "description") val description: String?,
  @Json(name = "privacy_level") val privacyLevel: String,
)
