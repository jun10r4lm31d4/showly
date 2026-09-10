package com.michaldrabik.ui_settings.sections.scrob

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsScrobBinding
import com.michaldrabik.ui_settings.databinding.ViewScrobInputBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsScrobFragment : BaseFragment<SettingsScrobViewModel>(R.layout.fragment_settings_scrob) {
  override val viewModel by viewModels<SettingsScrobViewModel>()

  private val binding by viewBinding(FragmentSettingsScrobBinding::bind)

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
    )

    viewModel.refresh()
  }

  private fun setupView() {
    with(binding) {
      settingsScrobInstance.onClick { showScrobDialog() }
    }
  }

  private fun render(uiState: SettingsScrobUiState) {
    with(binding) {
      settingsScrobValue.text =
        when {
          uiState.isScrobConfigured -> uiState.scrobUrl
          else -> getString(R.string.textSettingsScrobNotConfigured)
        }
    }
  }

  private fun showScrobDialog() {
    val state = viewModel.uiState.value
    val inputBinding = ViewScrobInputBinding.inflate(LayoutInflater.from(requireContext()))

    with(inputBinding) {
      scrobUrlInput.setText(state.scrobUrl)
      scrobApiInput.setText(state.scrobApiKey)
    }

    if (state.hasScrobApiKey) {
      inputBinding.scrobApiInputLayout.hint = getString(R.string.textSettingsScrobPasswordSetHint)
    }

    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setTitle(R.string.textSettingsScrobTitle)
      .setMessage(R.string.textSettingsScrobDialogMessage)
      .setView(inputBinding.root)
      .setPositiveButton(R.string.textOk) { _, _ ->
        viewModel.saveConnection(
          url =
            inputBinding.scrobUrlInput.text
              ?.toString()
              .orEmpty(),
          apikey =
            inputBinding.scrobApiInput.text
              ?.toString()
              .orEmpty(),
        )
        }.setNegativeButton(R.string.textCancel) { _, _ -> }
        .show()
  }
}
