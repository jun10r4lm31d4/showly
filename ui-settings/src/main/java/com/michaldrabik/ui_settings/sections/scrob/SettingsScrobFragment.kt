package com.michaldrabik.ui_settings.sections.scrob

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsScrobBinding
import com.michaldrabik.ui_settings.databinding.ViewScrobInputBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
      { viewModel.messageFlow.collect { showSnack(it) } },
    )

    viewModel.refresh()
    viewModel.observeSyncing()
  }

  private fun setupView() {
    with(binding) {
      settingsScrobInstance.onClick { showScrobDialog() }
      settingsScrobSync.onClick { viewModel.syncNow() }
      settingsScrobWatchlist.onClick { showWatchlistDialog() }
    }
  }

  private fun render(uiState: SettingsScrobUiState) {
    with(binding) {
      settingsScrobValue.text =
        when {
          uiState.isScrobConfigured -> uiState.scrobUrl
          else -> getString(R.string.textSettingsScrobNotConfigured)
        }
      settingsScrobSync.visibleIf(uiState.isScrobConfigured)
      settingsScrobSyncProgress.visibleIf(uiState.isSyncing)
      settingsScrobWatchlist.visibleIf(uiState.isScrobConfigured)
      settingsScrobWatchlistValue.text =
        when {
          uiState.watchlistListName.isNotBlank() -> uiState.watchlistListName
          else -> getString(R.string.textSettingsScrobWatchlistNotSelected)
        }
    }
  }

  private fun showWatchlistDialog() {
    lifecycleScope.launch {
      val options =
        try {
          viewModel.loadWatchlistOptions()
        } catch (error: Throwable) {
          viewModel.onWatchlistOptionsError(error)
          return@launch
        }
      if (!isAdded) return@launch

      val selectedId = viewModel.uiState.value.watchlistListId
      val labels = listOf(getString(R.string.textSettingsScrobWatchlistNone)) + options.map { it.name }
      val checked = options.indexOfFirst { it.id == selectedId } + 1

      MaterialAlertDialogBuilder(requireContext(), com.michaldrabik.ui_base.R.style.AlertDialog)
        .setBackground(ContextCompat.getDrawable(requireContext(), com.michaldrabik.ui_base.R.drawable.bg_dialog))
        .setTitle(R.string.textSettingsScrobWatchlistDialogTitle)
        .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, index ->
          val id = options.getOrNull(index - 1)?.id ?: -1
          if (id != selectedId) {
            viewModel.saveWatchlistList(id)
          }
          dialog.dismiss()
        }.show()
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

    val dialog =
      MaterialAlertDialogBuilder(requireContext(), com.michaldrabik.ui_base.R.style.AlertDialog)
        .setView(inputBinding.root)
        .create()

    with(inputBinding) {
      scrobOkButton.onClick {
        viewModel.saveConnection(
          url = scrobUrlInput.text?.toString().orEmpty(),
          apikey = scrobApiInput.text?.toString().orEmpty(),
        )
        dialog.dismiss()
      }
      scrobCancelButton.onClick { dialog.dismiss() }
    }

    dialog.show()
  }
}
