package com.michaldrabik.data_remote.scrob.interceptors

import com.michaldrabik.data_remote.scrob.ScrobProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class ScrobBaseUrlInterceptor (
  private val scrobProvider: ScrobProvider,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val original = chain.request()

    val storedBaseUrl = scrobProvider.getUrl()

    val base = storedBaseUrl.toHttpUrl()

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
