package com.michaldrabik.data_remote.scrob.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrobList(
  @Json(name = "id") val id: Long,
  @Json(name = "name") val name: String,
  @Json(name = "description") val description: String?,
  @Json(name = "privacy_level") val privacyLevel: String?,
  @Json(name = "item_count") val itemCount: Int,
  @Json(name = "created_at") val createdAt: String?,
  @Json(name = "updated_at") val updatedAt: String?,
)

@JsonClass(generateAdapter = true)
data class ScrobListsResponse(
  @Json(name = "lists") val lists: List<ScrobList>,
)

@JsonClass(generateAdapter = true)
data class ScrobListItem(
  @Json(name = "id") val id: Long,
  @Json(name = "list_id") val listId: Long,
  @Json(name = "added_at") val addedAt: String?,
  @Json(name = "sort_order") val sortOrder: Int?,
  @Json(name = "media") val media: ScrobMedia,
)

@JsonClass(generateAdapter = true)
data class ScrobListDetailsResponse(
  @Json(name = "id") val id: Long,
  @Json(name = "name") val name: String,
  @Json(name = "description") val description: String?,
  @Json(name = "privacy_level") val privacyLevel: String?,
  @Json(name = "items") val items: List<ScrobListItem>,
)
