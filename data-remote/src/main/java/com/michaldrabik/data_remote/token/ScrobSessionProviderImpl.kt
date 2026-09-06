package com.michaldrabik.data_remote.token

import android.annotation.SuppressLint
import android.content.SharedPreferences
import javax.inject.Named
import javax.inject.Singleton

@SuppressLint("ApplySharedPref")
@Singleton
internal class ScrobSessionProviderImpl(
  @Named("networkPreferences") private val sharedPreferences: SharedPreferences,
) : ScrobSessionProvider {

  companion object {
    private const val KEY_BASE_URL = "SCROB_BASE_URL"
    private const val KEY_USERNAME = "SCROB_USERNAME"
    private const val KEY_ACCESS_TOKEN = "SCROB_ACCESS_TOKEN"
  }

  private var token: String? = null

  override fun getToken(): String? {
    if (token == null) {
      token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }
    return token
  }

  override fun getBaseUrl(): String? = sharedPreferences.getString(KEY_BASE_URL, null)

  override fun getUsername(): String? = sharedPreferences.getString(KEY_USERNAME, null)

  override fun isLogged(): Boolean = !getToken().isNullOrBlank() && !getBaseUrl().isNullOrBlank()

  override fun saveSession(
    baseUrl: String,
    username: String,
    accessToken: String,
  ) {
    val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    sharedPreferences
      .edit()
      .putString(KEY_BASE_URL, normalizedUrl)
      .putString(KEY_USERNAME, username)
      .putString(KEY_ACCESS_TOKEN, accessToken)
      .commit()

    token = null
  }

  override fun revokeSession() {
    sharedPreferences
      .edit()
      .remove(KEY_BASE_URL)
      .remove(KEY_USERNAME)
      .remove(KEY_ACCESS_TOKEN)
      .commit()

    token = null
  }
}
