package com.michaldrabik.ui_settings.sections.scrob

import androidx.lifecycle.ViewModel
import androidx.work.WorkManager
import com.michaldrabik.data_remote.scrob.ScrobProvider
import com.michaldrabik.ui_base.scrob.sync.ScrobSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsScrobViewModel
  @Inject
  constructor(
    private val scrobProvider: ScrobProvider,
    private val workManager: WorkManager,
  ) : ViewModel() {
    private val state = MutableStateFlow(SettingsScrobUiState())
    val uiState = state.asStateFlow()

    fun refresh() {
      state.value =
        SettingsScrobUiState(
          hasScrobApiKey = scrobProvider.hasApiKey(),
          scrobUrl = scrobProvider.getUrl(),
          scrobApiKey = scrobProvider.getApiKey(),
        )
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
  }
