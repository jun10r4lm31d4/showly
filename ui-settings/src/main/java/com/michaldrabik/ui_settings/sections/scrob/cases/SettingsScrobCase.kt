package com.michaldrabik.ui_settings.sections.scrob.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class SettingsScrobCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val scrobRemoteSource: ScrobRemoteDataSource,
) {

  fun isScrobLogged() = scrobRemoteSource.isLogged()

  fun getScrobUsername() = scrobRemoteSource.getSessionUsername().orEmpty()

  suspend fun loginScrob(
    baseUrl: String,
    username: String,
    password: String,
  ) = withContext(dispatchers.IO) {
    scrobRemoteSource.login(baseUrl.trim(), username.trim(), password)
  }

  fun logoutScrob() = scrobRemoteSource.logout()
}
