package com.michaldrabik.ui_settings.sections.scrob

data class SettingsScrobUiState(
  val scrobUrl: String = "",
  val scrobApiKey: String = "",
  val hasScrobApiKey: Boolean = false,
) {
  val isScrobConfigured: Boolean
    get() = scrobUrl.isNotBlank() and hasScrobApiKey
}
