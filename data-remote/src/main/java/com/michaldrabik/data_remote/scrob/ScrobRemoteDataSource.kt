package com.michaldrabik.data_remote.scrob

import com.michaldrabik.data_remote.scrob.model.ScrobHistoryEvent
import com.michaldrabik.data_remote.scrob.model.ScrobList
import com.michaldrabik.data_remote.scrob.model.ScrobListItem
import com.michaldrabik.data_remote.scrob.model.ScrobSeasonWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobShowWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobWatchRequest

interface ScrobRemoteDataSource {

  /**
   * Logs in against the given self-hosted Scrob server and persists the session on success.
   * @throws ScrobAuthException if credentials are invalid, 2FA is required, or the server
   * could not be reached.
   */
  suspend fun login(
    baseUrl: String,
    username: String,
    password: String,
  )

  fun isLogged(): Boolean

  fun logout()

  fun getSessionUsername(): String?

  fun getSessionBaseUrl(): String?

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

  /** Fetches a single list's items. */
  suspend fun fetchListItems(listId: Long): List<ScrobListItem>

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
}

class ScrobAuthException(
  message: String,
  val requires2fa: Boolean = false,
) : Exception(message)
