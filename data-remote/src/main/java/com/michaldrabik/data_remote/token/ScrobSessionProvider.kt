package com.michaldrabik.data_remote.token

interface ScrobSessionProvider {

  /**
   * Returns the JWT access token if the user is logged in, or null.
   */
  fun getToken(): String?

  /**
   * Returns the base URL of the user's Scrob server (with trailing slash), or null.
   */
  fun getBaseUrl(): String?

  /**
   * Returns the username used to log in, or null.
   */
  fun getUsername(): String?

  fun isLogged(): Boolean

  /**
   * Persists the server URL, username and JWT token after a successful login.
   */
  fun saveSession(
    baseUrl: String,
    username: String,
    accessToken: String,
  )

  /**
   * Clears the stored session (logout).
   */
  fun revokeSession()
}
