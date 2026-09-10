package com.michaldrabik.ui_settings.sections.widgets

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsWidgetsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsWidgetsFragment : BaseFragment<SettingsWidgetsViewModel>(R.layout.fragment_settings_widgets) {

  override val viewModel by viewModels<SettingsWidgetsViewModel>()
  private val binding by viewBinding(FragmentSettingsWidgetsBinding::bind)

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      doAfterLaunch = { viewModel.loadSettings() },
    )
  }

  private fun setupView() {
    with(binding) {
      settingsWidgetsLabels.onClick {
        viewModel.enableWidgetsTitles(!settingsWidgetsLabelsSwitch.isChecked, requireAppContext())
      }
    }
  }

  private fun render(uiState: SettingsWidgetsUiState) {
    uiState.run {
      with(binding) {
        settings?.let {
          settingsWidgetsLabelsSwitch.isChecked = it.widgetsShowLabel
        }
        themeWidgets?.let {
          settingsWidgetsThemeValue.setText(it.displayName)
        }
        widgetsTransparency.let {
          settingsWidgetsTransparencyValue.setText(it.displayName)
        }
        val alpha = 1F
        settingsWidgetsTheme.alpha = alpha
        settingsWidgetsThemeValue.alpha = alpha
        settingsWidgetsTransparency.alpha = alpha
        settingsWidgetsThemeTitle.setCompoundDrawables(null, null, null, null)
        settingsWidgetsTransparencyTitle.setCompoundDrawables(null, null, null, null)
      }
    }
  }
}
