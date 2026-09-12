package com.michaldrabik.data_remote.scrob

import com.michaldrabik.data_remote.scrob.model.ScrobHistoryEvent
import com.michaldrabik.data_remote.scrob.model.ScrobList
import com.michaldrabik.data_remote.scrob.model.ScrobListCreateRequest
import com.michaldrabik.data_remote.scrob.model.ScrobListItem
import com.michaldrabik.data_remote.scrob.model.ScrobListItemAddRequest
import com.michaldrabik.data_remote.scrob.model.ScrobSeasonWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobShowWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobWatchRequest

interface ScrobRemoteDataSource {

  /**
   * Fetches one page of the user's completed watch history, newest first.
   * @param type optionally filters to "movie" or "episode".
   */
  suspend fun fetchHistoryPage(
    page: Int,
    pageSize: Int,
    type: String? = null,
  ): List<ScrobHistoryEvent>

  /** Fetches all of the user's custom lists (without items). */
  suspend fun fetchLists(): List<ScrobList>

  /** Creates a remote list. Returns the created list. */
  suspend fun createList(request: ScrobListCreateRequest): ScrobList

  /** Renames/updates a remote list. */
  suspend fun renameList(
    listId: Long,
    request: ScrobListCreateRequest,
  )

  /**
   * Deletes a remote list. Already-deleted lists are treated
   * as success so retries stay idempotent.
   */
  suspend fun deleteList(listId: Long)

  /** Fetches a single list's items. */
  suspend fun fetchListItems(listId: Long): List<ScrobListItem>

  /** Adds an item to a list. Returns the created remote item. */
  suspend fun addListItem(
    listId: Long,
    request: ScrobListItemAddRequest,
  ): ScrobListItem

  /**
   * Removes an item from a list. Already-removed items are treated
   * as success so retries stay idempotent.
   */
  suspend fun removeListItem(
    listId: Long,
    itemId: Long,
  )

  /** Pushes a single movie or episode watch event ("mark as watched") to the Scrob server. */
  suspend fun addToHistory(request: ScrobWatchRequest)

  /** Removes a movie or episode watch event from the Scrob server history. */
  suspend fun removeFromHistory(
    mediaType: String,
    tmdbId: Long,
  )

  /** Marks a whole season as watched. */
  suspend fun addSeasonToHistory(request: ScrobSeasonWatchRequest)

  /** Marks a whole season as unwatched. */
  suspend fun removeSeasonFromHistory(
    seriesTmdbId: Long,
    seasonNumber: Int,
  )

  /** Marks every episode of a show as watched. */
  suspend fun addShowToHistory(request: ScrobShowWatchRequest)

  /** Marks every episode of a show as unwatched. */
  suspend fun removeShowFromHistory(seriesTmdbId: Long)

  /** Returns true if the user has configured the Scrob server connection. */
  //TODO: change function to test connection with apk and url
  fun isLogged(): Boolean
}

class ScrobAuthException(
  message: String,
  val requires2fa: Boolean = false,
) : Exception(message)
