package com.michaldrabik.data_remote.scrob.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrobLoginResponse(
  @Json(name = "access_token") val accessToken: String?,
  @Json(name = "token_type") val tokenType: String?,
  @Json(name = "requires_2fa") val requires2fa: Boolean = false,
  @Json(name = "temp_token") val tempToken: String?,
)
