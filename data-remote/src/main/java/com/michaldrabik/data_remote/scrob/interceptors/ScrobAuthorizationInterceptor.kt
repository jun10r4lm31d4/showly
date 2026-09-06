package com.michaldrabik.data_remote.scrob.interceptors

import com.michaldrabik.data_remote.token.ScrobSessionProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobAuthorizationInterceptor @Inject constructor(
  private val sessionProvider: ScrobSessionProvider,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request().newBuilder()
    sessionProvider.getToken()?.let {
      request.addHeader("Authorization", "Bearer $it")
    }
    return chain.proceed(request.build())
  }
}
