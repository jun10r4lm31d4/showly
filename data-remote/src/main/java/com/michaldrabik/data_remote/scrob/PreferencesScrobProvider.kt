package com.michaldrabik.data_remote.scrob

import android.content.SharedPreferences
import androidx.core.content.edit
import com.michaldrabik.common.security.SecretCipher
import com.michaldrabik.data_remote.BuildConfig
import javax.inject.Singleton

@Singleton
internal class PreferencesScrobProvider(
  private val sharedPreferences: SharedPreferences,
  private val secretCipher: SecretCipher,
) : ScrobProvider {
  companion object {
    private const val URL = "SCROB_URL"
    private const val API_KEY = "SCROB_API_KEY"
    private const val ACTIVITY_SCROB_HISTORY_SYNCED_AT = "ACTIVITY_SCROB_HISTORY_SYNCED_AT"
    private const val WATCHLIST_LIST_ID = "SCROB_WATCHLIST_LIST_ID"
  }

  private var url: String? = null

  private var apiKey: String? = null

  private var activityScrobHistorySyncedAt: Long? = null

  private var watchlistListId: Long? = null

  override fun getWatchlistListId(): Long {
    watchlistListId?.let { return it }
    return sharedPreferences.getLong(WATCHLIST_LIST_ID, -1).also { watchlistListId = it }
  }

  override fun setWatchlistListId(id: Long) {
    watchlistListId = id
    sharedPreferences.edit { putLong(WATCHLIST_LIST_ID, id) }
  }

  override fun getActivityScrobHistorySyncedAt(): Long {
    activityScrobHistorySyncedAt?.let { return it }
    return sharedPreferences.getLong(ACTIVITY_SCROB_HISTORY_SYNCED_AT, 0)
  }

  override fun getApiKey(): String {
    apiKey?.let { return it }
    return read(API_KEY, BuildConfig.SCROB_API_KEY).also { apiKey = it }
  }

  override fun getUrl(): String {
    url?.let { return it }
    return read(URL, BuildConfig.SCROB_URL).also { url = it }
  }

  override fun setActivityScrobHistorySyncedAt(key: Long) {
    activityScrobHistorySyncedAt = key
    sharedPreferences.edit { putLong(ACTIVITY_SCROB_HISTORY_SYNCED_AT, key) }
  }

  override fun setApiKey(key: String) {
    apiKey = key.trim()
    write(API_KEY, key.trim())
  }

  override fun setUrl(key: String) {
    url = key.trim()
    write(URL, key.trim())
  }

  override fun hasApiKey(): Boolean = getApiKey().isNotBlank()

  override fun hasUrl(): Boolean = getUrl().isNotBlank()

  override fun isConfigured(): Boolean = hasApiKey() && hasUrl()

  private fun read(
    key: String,
    fallback: String,
  ): String {
    val stored = sharedPreferences.getString(key, null) ?: return fallback
    return secretCipher.decrypt(stored) ?: fallback
  }

  private fun write(
    key: String,
    value: String,
  ) {
    sharedPreferences.edit {
      if (value.isBlank()) {
        remove(key)
      } else {
        putString(key, secretCipher.encrypt(value))
      }
    }
  }
}
