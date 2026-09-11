package com.michaldrabik.ui_settings.sections.scrob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.michaldrabik.data_remote.scrob.ScrobProvider
import com.michaldrabik.ui_base.scrob.sync.ScrobSyncWorker
import com.michaldrabik.ui_base.utilities.events.MessageEvent
import com.michaldrabik.ui_base.viewmodel.ChannelsDelegate
import com.michaldrabik.ui_base.viewmodel.DefaultChannelsDelegate
import com.michaldrabik.ui_settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsScrobViewModel
  @Inject
  constructor(
    private val scrobProvider: ScrobProvider,
    private val workManager: WorkManager,
  ) : ViewModel(),
    ChannelsDelegate by DefaultChannelsDelegate() {
    private val state = MutableStateFlow(SettingsScrobUiState())
    val uiState = state.asStateFlow()

    fun refresh() {
      state.value =
        state.value.copy(
          hasScrobApiKey = scrobProvider.hasApiKey(),
          scrobUrl = scrobProvider.getUrl(),
          scrobApiKey = scrobProvider.getApiKey(),
        )
    }

    fun observeSyncing() {
      val isSyncingHistory =
        workManager
          .getWorkInfosForUniqueWorkFlow(ScrobSyncWorker.TAG_HISTORY)
          .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
      val isSyncingLists =
        workManager
          .getWorkInfosForUniqueWorkFlow(ScrobSyncWorker.TAG_LISTS)
          .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

      viewModelScope.launch {
        combine(isSyncingHistory, isSyncingLists) { history, lists -> history || lists }
          .collect { syncing ->
            state.value = state.value.copy(isSyncing = syncing)
          }
      }
    }

    fun saveConnection(
      url: String,
      apikey: String,
    ) {
      if (apikey.isNotBlank() && url.isNotBlank()) {
        scrobProvider.setUrl(url)
        scrobProvider.setApiKey(apikey)

        ScrobSyncWorker.scheduleHistory(workManager)
        ScrobSyncWorker.scheduleLists(workManager)
      }
      refresh()
    }

    fun syncNow() {
      if (!scrobProvider.isConfigured() || state.value.isSyncing) return
      ScrobSyncWorker.scheduleHistory(workManager)
      ScrobSyncWorker.scheduleLists(workManager)
      viewModelScope.launch {
        messageChannel.send(MessageEvent.Info(R.string.textScrobSyncStarted))
      }
    }
  }
