package com.michaldrabik.data_remote.di.module

import android.content.SharedPreferences
import com.michaldrabik.data_remote.scrob.api.ScrobApi
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.data_remote.scrob.api.service.ScrobSyncService
import com.michaldrabik.data_remote.token.ScrobSessionProvider
import com.michaldrabik.data_remote.token.ScrobSessionProviderImpl
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScrobModule {

  @Provides
  @Singleton
  fun providesScrobSessionProvider(
    @Named("networkPreferences") sharedPreferences: SharedPreferences,
  ): ScrobSessionProvider = ScrobSessionProviderImpl(sharedPreferences)

  @Provides
  @Singleton
  fun providesScrobSyncService(
    @Named("retrofitScrob") retrofit: Retrofit,
  ): ScrobSyncService = retrofit.create(ScrobSyncService::class.java)

  @Provides
  @Singleton
  fun providesScrobApi(
    @Named("okHttpBase") okHttpClient: OkHttpClient,
    syncService: ScrobSyncService,
    sessionProvider: ScrobSessionProvider,
    moshi: Moshi,
  ): ScrobRemoteDataSource = ScrobApi(okHttpClient, syncService, sessionProvider, moshi)
}
