package com.michaldrabik.data_remote.scrob

/**
 * Supplies the API keys used by the remote data sources.
 *
 * Keys are entered by the user at runtime rather than compiled into the binary, so release builds ship with no key material and every install uses its own TMDB quota.
 */
interface ScrobProvider {
  fun getActivityScrobHistorySyncedAt(): Long

  fun setActivityScrobHistorySyncedAt(key: Long)

  /**
   * Wall-clock time (epoch millis) of the last FULL history snapshot import.
   * Used to throttle full reconciliations to ~1x/day while daily runs use the
   * cheap incremental path based on [getActivityScrobHistorySyncedAt].
   * 0 means no full sync has run yet.
   */
  fun getFullHistorySyncedAt(): Long

  fun setFullHistorySyncedAt(value: Long)

  fun getApiKey(): String

  fun getUrl(): String

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
