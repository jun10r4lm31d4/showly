package com.michaldrabik.data_remote.scrob.api

import com.michaldrabik.data_remote.Config
import com.michaldrabik.data_remote.scrob.ScrobAuthException
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.data_remote.scrob.api.service.ScrobSyncService
import com.michaldrabik.data_remote.scrob.model.ScrobHistoryEvent
import com.michaldrabik.data_remote.scrob.model.ScrobList
import com.michaldrabik.data_remote.scrob.model.ScrobListItem
import com.michaldrabik.data_remote.scrob.model.ScrobLoginResponse
import com.michaldrabik.data_remote.scrob.model.ScrobSeasonWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobShowWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobWatchRequest
import com.michaldrabik.data_remote.token.ScrobSessionProvider
import com.squareup.moshi.Moshi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
internal class ScrobApi @Inject constructor(
  @Named("okHttpBase") private val okHttpClient: OkHttpClient,
  private val syncService: ScrobSyncService,
  private val sessionProvider: ScrobSessionProvider,
  private val moshi: Moshi,
) : ScrobRemoteDataSource {

  override suspend fun login(
    baseUrl: String,
    username: String,
    password: String,
  ) {
    val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    val loginUrl = "${normalizedUrl}api/proxy/auth/login"

    val formBody = FormBody
      .Builder()
      .add("username", username)
      .add("password", password)
      .build()

    val requestBuilder = Request
      .Builder()
      .url(loginUrl)
      .post(formBody)

    if (Config.SCROB_API_KEY.isNotBlank()) {
      requestBuilder.addHeader("X-Api-Key", Config.SCROB_API_KEY)
    }

    val response = suspendCancellableCoroutine<ScrobLoginResponse> { continuation ->
      val call = okHttpClient.newCall(requestBuilder.build())
      continuation.invokeOnCancellation { call.cancel() }
      call.enqueue(object : Callback {
        override fun onFailure(
          call: Call,
          e: IOException,
        ) {
          continuation.resumeWithException(
            ScrobAuthException("Could not reach the Scrob server. $e"),
          )
        }

        override fun onResponse(
          call: Call,
          response: Response,
        ) {
          try {
            val bodySource = response.body?.source()
            val parsed = bodySource?.let {
              moshi.adapter(ScrobLoginResponse::class.java).fromJson(it)
            }

            when {
              response.isSuccessful && parsed?.accessToken != null ->
                continuation.resume(parsed)

              response.isSuccessful && parsed?.requires2fa == true ->
                continuation.resumeWithException(
                  ScrobAuthException(
                    "Two-factor authentication is not supported yet.",
                    requires2fa = true,
                  ),
                )

              response.code == 401 ->
                continuation.resumeWithException(ScrobAuthException("Incorrect username or password."))

              else ->
                continuation.resumeWithException(ScrobAuthException("Login failed (${response.code})."))
            }
          } catch (e: Exception) {
            continuation.resumeWithException(ScrobAuthException("Unexpected response from server. $e"))
          } finally {
            response.closeQuietly()
          }
        }
      })
    }

    sessionProvider.saveSession(
      baseUrl = normalizedUrl,
      username = username,
      accessToken = requireNotNull(response.accessToken),
    )
  }

  override fun isLogged(): Boolean = sessionProvider.isLogged()

  override fun logout() = sessionProvider.revokeSession()

  override fun getSessionUsername(): String? = sessionProvider.getUsername()

  override fun getSessionBaseUrl(): String? = sessionProvider.getBaseUrl()

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

  override suspend fun fetchListItems(listId: Long): List<ScrobListItem> =
    runCatchingSession {
      syncService.fetchListDetails(listId).items
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
