package com.michaldrabik.data_remote.scrob

/**
 * Supplies the API keys used by the remote data sources.
 *
 * Keys are entered by the user at runtime rather than compiled into the binary, so release builds ship with no key material and every install uses its own TMDB quota.
 */
interface ScrobProvider {
  fun getActivityScrobHistorySyncedAt(): Long

  fun getApiKey(): String

  fun getUrl(): String

  fun setActivityScrobHistorySyncedAt(key: Long)

  fun setApiKey(key: String)

  fun setUrl(key: String)

  fun hasApiKey(): Boolean

  fun hasUrl(): Boolean

  fun isConfigured(): Boolean

  /**
   * Remote id of the Scrob list used as the watchlist source.
   * -1 means no list selected.
   */
  fun getWatchlistListId(): Long

  fun setWatchlistListId(id: Long)
}
