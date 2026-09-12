package com.michaldrabik.data_remote.scrob.api

import com.michaldrabik.data_remote.scrob.ScrobAuthException
import com.michaldrabik.data_remote.scrob.ScrobProvider
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.data_remote.scrob.api.service.ScrobSyncService
import com.michaldrabik.data_remote.scrob.model.ScrobHistoryEvent
import com.michaldrabik.data_remote.scrob.model.ScrobList
import com.michaldrabik.data_remote.scrob.model.ScrobListCreateRequest
import com.michaldrabik.data_remote.scrob.model.ScrobListItem
import com.michaldrabik.data_remote.scrob.model.ScrobListItemAddRequest
import com.michaldrabik.data_remote.scrob.model.ScrobSeasonWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobShowWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobWatchRequest
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ScrobApi @Inject constructor(
  private val syncService: ScrobSyncService,
  private val scrobProvider: ScrobProvider,
) : ScrobRemoteDataSource {

  override fun isLogged(): Boolean = scrobProvider.isConfigured()

  override suspend fun fetchHistoryPage(
    page: Int,
    pageSize: Int,
    type: String?,
  ): List<ScrobHistoryEvent> =
    runCatchingSession {
      syncService.fetchHistory(page = page, pageSize = pageSize, type = type).results
    }

  override suspend fun fetchLists(): List<ScrobList> =
    runCatchingSession {
      syncService.fetchLists().lists
    }

  override suspend fun createList(request: ScrobListCreateRequest): ScrobList =
    runCatchingSession {
      syncService.createList(request)
    }

  override suspend fun renameList(
    listId: Long,
    request: ScrobListCreateRequest,
  ) {
    runCatchingSession {
      syncService.renameList(listId, request)
    }
  }

  override suspend fun deleteList(listId: Long) {
    if (!isLogged()) {
      throw ScrobAuthException("Not logged in to Scrob.")
    }
    try {
      syncService.deleteList(listId)
    } catch (e: HttpException) {
      // Already gone remotely counts as applied, keeping retries idempotent.
      if (e.code() == 404) return
      if (e.code() == 401) {
        throw ScrobAuthException("Scrob session expired. Please log in again.")
      }
      throw ScrobAuthException("Scrob request failed (${e.code()}).")
    } catch (e: IOException) {
      throw ScrobAuthException("Could not reach the Scrob server. $e")
    }
  }

  override suspend fun fetchListItems(listId: Long): List<ScrobListItem> =
    runCatchingSession {
      syncService.fetchListDetails(listId).items
    }

  override suspend fun addListItem(
    listId: Long,
    request: ScrobListItemAddRequest,
  ): ScrobListItem =
    runCatchingSession {
      syncService.addListItem(listId, request)
    }

  override suspend fun removeListItem(
    listId: Long,
    itemId: Long,
  ) {
    if (!isLogged()) {
      throw ScrobAuthException("Not logged in to Scrob.")
    }
    try {
      syncService.removeListItem(listId, itemId)
    } catch (e: HttpException) {
      // Already gone remotely counts as applied, keeping retries idempotent.
      if (e.code() == 404) return
      if (e.code() == 401) {
        throw ScrobAuthException("Scrob session expired. Please log in again.")
      }
      throw ScrobAuthException("Scrob request failed (${e.code()}).")
    } catch (e: IOException) {
      throw ScrobAuthException("Could not reach the Scrob server. $e")
    }
  }

  override suspend fun addToHistory(request: ScrobWatchRequest) =
    runCatchingSession {
      syncService.addToHistory(request)
    }

  override suspend fun removeFromHistory(
    mediaType: String,
    tmdbId: Long,
  ) = runCatchingSession {
    syncService.removeItemFromHistory(mediaType, tmdbId)
  }

  override suspend fun addSeasonToHistory(request: ScrobSeasonWatchRequest) =
    runCatchingSession {
      syncService.addSeasonToHistory(request)
    }

  override suspend fun removeSeasonFromHistory(
    seriesTmdbId: Long,
    seasonNumber: Int,
  ) = runCatchingSession {
    syncService.removeSeasonFromHistory(seriesTmdbId, seasonNumber)
  }

  override suspend fun addShowToHistory(request: ScrobShowWatchRequest) =
    runCatchingSession {
      syncService.addShowToHistory(request)
    }

  override suspend fun removeShowFromHistory(seriesTmdbId: Long) =
    runCatchingSession {
      syncService.removeShowFromHistory(seriesTmdbId)
    }

  private suspend fun <T> runCatchingSession(block: suspend () -> T): T {
    if (!isLogged()) {
      throw ScrobAuthException("Not logged in to Scrob.")
    }
    return try {
      block()
    } catch (e: HttpException) {
      if (e.code() == 401) {
        throw ScrobAuthException("Scrob session expired. Please log in again.")
      }
      throw ScrobAuthException("Scrob request failed (${e.code()}).")
    } catch (e: IOException) {
      throw ScrobAuthException("Could not reach the Scrob server. $e")
    }
  }
}
