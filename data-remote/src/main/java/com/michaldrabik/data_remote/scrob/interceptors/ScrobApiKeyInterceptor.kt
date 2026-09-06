package com.michaldrabik.data_remote.scrob.interceptors

import com.michaldrabik.data_remote.Config
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobApiKeyInterceptor @Inject constructor() : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain
      .request()
      .newBuilder()
      .header("Content-Type", "application/json")
      .apply {
        if (Config.SCROB_API_KEY.isNotBlank()) {
          header("X-Api-Key", Config.SCROB_API_KEY)
        }
      }.build()

    return chain.proceed(request)
  }
}
