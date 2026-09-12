package com.michaldrabik.data_remote.scrob.api.service

import com.michaldrabik.data_remote.scrob.model.ScrobHistoryResponse
import com.michaldrabik.data_remote.scrob.model.ScrobList
import com.michaldrabik.data_remote.scrob.model.ScrobListCreateRequest
import com.michaldrabik.data_remote.scrob.model.ScrobListDetailsResponse
import com.michaldrabik.data_remote.scrob.model.ScrobListItem
import com.michaldrabik.data_remote.scrob.model.ScrobListItemAddRequest
import com.michaldrabik.data_remote.scrob.model.ScrobListsResponse
import com.michaldrabik.data_remote.scrob.model.ScrobSeasonWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobShowWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobWatchRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ScrobSyncService {

  @GET("api/proxy/history")
  suspend fun fetchHistory(
    @Query("page") page: Int,
    @Query("page_size") pageSize: Int,
    @Query("type") type: String? = null, // "movie" | "episode"
  ): ScrobHistoryResponse

  @GET("api/proxy/lists")
  suspend fun fetchLists(): ScrobListsResponse

  @POST("api/proxy/lists")
  suspend fun createList(
    @Body request: ScrobListCreateRequest,
  ): ScrobList

  @PATCH("api/proxy/lists/{listId}")
  suspend fun renameList(
    @Path("listId") listId: Long,
    @Body request: ScrobListCreateRequest,
  ): ScrobList

  @DELETE("api/proxy/lists/{listId}")
  suspend fun deleteList(
    @Path("listId") listId: Long,
  )

  @GET("api/proxy/lists/{listId}")
  suspend fun fetchListDetails(
    @Path("listId") listId: Long,
  ): ScrobListDetailsResponse

  @POST("api/proxy/lists/{listId}/items")
  suspend fun addListItem(
    @Path("listId") listId: Long,
    @Body request: ScrobListItemAddRequest,
  ): ScrobListItem

  @DELETE("api/proxy/lists/{listId}/items/{itemId}")
  suspend fun removeListItem(
    @Path("listId") listId: Long,
    @Path("itemId") itemId: Long,
  )

  @POST("api/proxy/history")
  suspend fun addToHistory(
    @Body request: ScrobWatchRequest,
  )

  @DELETE("api/proxy/history/item")
  suspend fun removeItemFromHistory(
    @Query("media_type") mediaType: String,
    @Query("tmdb_id") tmdbId: Long,
  )

  @POST("api/proxy/history/season")
  suspend fun addSeasonToHistory(
    @Body request: ScrobSeasonWatchRequest,
  )

  @DELETE("api/proxy/history/season")
  suspend fun removeSeasonFromHistory(
    @Query("series_tmdb_id") seriesTmdbId: Long,
    @Query("season_number") seasonNumber: Int,
  )

  @POST("api/proxy/history/show-all")
  suspend fun addShowToHistory(
    @Body request: ScrobShowWatchRequest,
  )

  @DELETE("api/proxy/history/show-all")
  suspend fun removeShowFromHistory(
    @Query("series_tmdb_id") seriesTmdbId: Long,
  )
}
