package com.michaldrabik.ui_model

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate

enum class AppTheme(
  val code: Int,
  @StringRes val displayName: Int,
) {
  LIGHT(AppCompatDelegate.MODE_NIGHT_NO, R.string.textThemeLight),
  DARK(AppCompatDelegate.MODE_NIGHT_YES, R.string.textThemeDark),
  SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, R.string.textThemeSystem),
  ;

  companion object {
    fun fromCode(code: Int) = values().first { it.code == code }
  }
}
