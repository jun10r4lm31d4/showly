package com.michaldrabik.data_remote.scrob.interceptors

import com.michaldrabik.data_remote.token.ScrobSessionProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit requires a fixed base URL at build time, but every Scrob server is self-hosted
 * at a different address chosen by the user on the login screen. Requests are built against
 * [com.michaldrabik.data_remote.Config.SCROB_PLACEHOLDER_BASE_URL] and this interceptor swaps
 * the scheme/host/port/base-path for the one stored after login before the call goes out.
 */
@Singleton
class ScrobBaseUrlInterceptor @Inject constructor(
  private val sessionProvider: ScrobSessionProvider,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val original = chain.request()

    val storedBaseUrl = sessionProvider.getBaseUrl()
      ?: return chain.proceed(original)

    val base = storedBaseUrl.toHttpUrl()

    // Rebuild the full URL on top of the stored base so that any path segments
    // of the user-provided base URL (e.g. "https://host/scrob/") are preserved.
    val newUrl = buildString {
      append(base.toString().trimEnd('/'))
      append(original.url.encodedPath)
      original.url.encodedQuery?.let { query ->
        append('?').append(query)
      }
    }.toHttpUrl()

    val request = original
      .newBuilder()
      .url(newUrl)
      .build()

    return chain.proceed(request)
  }
}
