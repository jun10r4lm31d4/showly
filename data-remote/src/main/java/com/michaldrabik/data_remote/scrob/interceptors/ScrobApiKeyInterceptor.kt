package com.michaldrabik.data_remote.scrob.interceptors

import com.michaldrabik.data_remote.scrob.ScrobProvider
import okhttp3.Interceptor
import okhttp3.Response

class ScrobApiKeyInterceptor (
  private val scrobProvider: ScrobProvider,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request =
      chain
        .request()
        .newBuilder()
        .header("Content-Type", "application/json")
        .header("X-Api-Key", scrobProvider.getApiKey())
        .build()

    return chain.proceed(request)
  }
}
