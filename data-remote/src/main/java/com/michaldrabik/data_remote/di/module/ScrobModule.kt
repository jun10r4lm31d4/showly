package com.michaldrabik.data_remote.di.module

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import com.michaldrabik.common.security.SecretCipher
import com.michaldrabik.data_remote.scrob.PreferencesScrobProvider
import com.michaldrabik.data_remote.scrob.ScrobProvider
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.data_remote.scrob.api.ScrobApi
import com.michaldrabik.data_remote.scrob.api.service.ScrobSyncService
import com.michaldrabik.data_remote.scrob.interceptors.ScrobApiKeyInterceptor
import com.michaldrabik.data_remote.scrob.interceptors.ScrobBaseUrlInterceptor
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScrobModule {
  @Provides
  @Singleton
  fun providesScrobProvider(
    @Named("scrobPreferences") sharedPreferences: SharedPreferences,
    secretCipher: SecretCipher,
  ): ScrobProvider = PreferencesScrobProvider(sharedPreferences, secretCipher)

  @Provides
  @Singleton
  fun providesScrobApi(
    @Named("retrofitScrob") retrofit: Retrofit,
    scrobProvider: ScrobProvider
  ): ScrobRemoteDataSource = ScrobApi(retrofit.create(ScrobSyncService::class.java), scrobProvider)

  @Provides
  @Singleton
  fun providesScrobBaseUrlInterceptor(scrobProvider: ScrobProvider) =
    ScrobBaseUrlInterceptor(scrobProvider)

  @Provides
  @Singleton
  fun providesScrobApiKeyInterceptor(scrobProvider: ScrobProvider) =
    ScrobApiKeyInterceptor(scrobProvider)
}
