package com.michaldrabik.ui_settings.sections.scrob

data class SettingsScrobUiState(
  val isSignedInScrob: Boolean = false,
  val isSigningIn: Boolean = false,
  val isSyncingHistory: Boolean = false,
  val isSyncingLists: Boolean = false,
  val scrobUsername: String = "",
)
