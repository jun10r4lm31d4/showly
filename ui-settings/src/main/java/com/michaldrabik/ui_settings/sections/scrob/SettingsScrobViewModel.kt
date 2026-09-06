package com.michaldrabik.ui_settings.sections.scrob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.scrob.sync.ScrobSyncWorker
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.utilities.extensions.SUBSCRIBE_STOP_TIMEOUT
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.sections.scrob.cases.SettingsScrobCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsScrobViewModel @Inject constructor(
  private val scrobCase: SettingsScrobCase,
  private val workManager: WorkManager,
) : ViewModel(),
  ChannelsDelegate by DefaultChannelsDelegate() {

  private val signedInState = MutableStateFlow(false)
  private val signingInState = MutableStateFlow(false)
  private val usernameState = MutableStateFlow("")

  private val isSyncingHistoryFlow = workManager
    .getWorkInfosForUniqueWorkFlow(ScrobSyncWorker.TAG_HISTORY)
    .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

  private val isSyncingListsFlow = workManager
    .getWorkInfosForUniqueWorkFlow(ScrobSyncWorker.TAG_LISTS)
    .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

  fun loadSettings() {
    refreshSession()
    viewModelScope.launch { isSyncingHistoryFlow.collect { syncingHistoryState.value = it } }
    viewModelScope.launch { isSyncingListsFlow.collect { syncingListsState.value = it } }
  }

  private val syncingHistoryState = MutableStateFlow(false)
  private val syncingListsState = MutableStateFlow(false)

  private fun refreshSession() {
    signedInState.value = scrobCase.isScrobLogged()
    usernameState.value = scrobCase.getScrobUsername()
  }

  fun login(
    baseUrl: String,
    username: String,
    password: String,
  ) {
    if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
      viewModelScope.launch {
        messageChannel.send(MessageEvent.Error(R.string.errorScrobFieldsRequired))
      }
      return
    }
    viewModelScope.launch {
      try {
        signingInState.value = true
        scrobCase.loginScrob(baseUrl, username, password)
        refreshSession()
        messageChannel.send(MessageEvent.Info(R.string.textScrobLoginSuccess))
        scheduleSyncAfterLogin()
      } catch (error: Throwable) {
        Logger.record(error, "SettingsScrobViewModel::login()")
        rethrowCancellation(error)
        messageChannel.send(MessageEvent.Error(R.string.errorScrobLoginFailed))
      } finally {
        signingInState.value = false
      }
    }
  }

  private fun scheduleSyncAfterLogin() {
    ScrobSyncWorker.scheduleHistory(workManager)
    ScrobSyncWorker.scheduleLists(workManager)
    messageChannel.trySend(MessageEvent.Info(R.string.textScrobSyncStarted))
  }

  fun logout() {
    ScrobSyncWorker.cancelPending(workManager)
    scrobCase.logoutScrob()
    refreshSession()
    viewModelScope.launch {
      messageChannel.send(MessageEvent.Info(R.string.textScrobLogoutSuccess))
    }
  }

  fun syncHistory() {
    if (syncingHistoryState.value) return
    ScrobSyncWorker.scheduleHistory(workManager)
    viewModelScope.launch {
      messageChannel.send(MessageEvent.Info(R.string.textScrobSyncStarted))
    }
  }

  fun syncLists() {
    if (syncingListsState.value) return
    ScrobSyncWorker.scheduleLists(workManager)
    viewModelScope.launch {
      messageChannel.send(MessageEvent.Info(R.string.textScrobSyncStarted))
    }
  }

  val uiState = combine(
    signedInState,
    signingInState,
    syncingHistoryState,
    syncingListsState,
    usernameState,
  ) { s1, s2, s3, s4, s5 ->
    SettingsScrobUiState(
      isSignedInScrob = s1,
      isSigningIn = s2,
      isSyncingHistory = s3,
      isSyncingLists = s4,
      scrobUsername = s5,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT),
    initialValue = SettingsScrobUiState(),
  )
}
