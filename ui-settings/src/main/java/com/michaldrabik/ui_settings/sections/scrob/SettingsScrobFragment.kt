package com.michaldrabik.ui_settings.sections.scrob

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_settings.R
import com.michaldrabik.ui_settings.databinding.FragmentSettingsScrobBinding
import com.michaldrabik.ui_settings.sections.scrob.views.ScrobLoginView
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
      { viewModel.messageFlow.collect { showSnack(it) } },
      doAfterLaunch = { viewModel.loadSettings() },
    )
  }

  private fun setupView() {
    with(binding) {
      settingsScrobSyncHistory.onClick { viewModel.syncHistory() }
      settingsScrobSyncLists.onClick { viewModel.syncLists() }
    }
  }

  private fun render(uiState: SettingsScrobUiState) {
    uiState.run {
      with(binding) {
        settingsScrobAuthorizeProgress.visibleIf(isSigningIn)
        settingsScrobAuthorizeIcon.visibleIf(isSignedInScrob && !isSigningIn)
        settingsScrobSyncHistory.visibleIf(isSignedInScrob)
        settingsScrobSyncLists.visibleIf(isSignedInScrob)

        settingsScrobSyncHistoryProgress.visibleIf(isSyncingHistory)
        settingsScrobSyncListsProgress.visibleIf(isSyncingLists)

        settingsScrobAuthorizeSummary.text = when {
          isSignedInScrob -> when {
            scrobUsername.isNotEmpty() -> getString(
              R.string.textSettingsScrobAuthorizeSummarySignOutUser,
              scrobUsername,
            )
            else -> getString(R.string.textSettingsScrobAuthorizeSummarySignOut)
          }
          else -> getString(R.string.textSettingsScrobAuthorizeSummarySignIn)
        }

        settingsScrobAuthorize.onClick {
          when {
            isSigningIn -> Unit
            isSignedInScrob -> showLogoutDialog()
            else -> showLoginDialog()
          }
        }
      }
    }
  }

  private fun showLoginDialog() {
    val view = ScrobLoginView(requireContext())
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textScrobLoginTitle)
      .setView(view)
      .setPositiveButton(R.string.textYes) { _, _ ->
        viewModel.login(view.serverUrl, view.username, view.password)
      }.setNegativeButton(R.string.textCancel) { _, _ -> }
      .show()
  }

  private fun showLogoutDialog() {
    MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog)
      .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_dialog))
      .setTitle(R.string.textSettingsLogoutTitle)
      .setMessage(R.string.textSettingsLogoutMessage)
      .setPositiveButton(R.string.textYes) { _, _ ->
        viewModel.logout()
      }.setNegativeButton(R.string.textCancel) { _, _ -> }
      .show()
  }
}
