package com.michaldrabik.ui_comments.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.michaldrabik.common.Mode
import com.michaldrabik.ui_base.BaseFragment
import com.michaldrabik.ui_base.utilities.extensions.addDivider
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.fadeIn
import com.michaldrabik.ui_base.utilities.extensions.fadeOut
import com.michaldrabik.ui_base.utilities.extensions.gone
import com.michaldrabik.ui_base.utilities.extensions.launchAndRepeatStarted
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.updateTopMargin
import com.michaldrabik.ui_base.utilities.extensions.visibleIf
import com.michaldrabik.ui_base.utilities.viewBinding
import com.michaldrabik.ui_comments.R
import com.michaldrabik.ui_comments.databinding.FragmentCommentsBinding
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_navigation.java.NavigationArgs.ARG_OPTIONS
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize

@SuppressLint("SetTextI18n", "DefaultLocale", "SourceLockedOrientationActivity")
@AndroidEntryPoint
class CommentsFragment : BaseFragment<CommentsViewModel>(R.layout.fragment_comments) {

  companion object {
    const val BACK_UP_BUTTON_THRESHOLD = 25

    fun createBundle(movie: Movie): Bundle = bundleOf(ARG_OPTIONS to Options(movie.ids.trakt, Mode.MOVIES))

    fun createBundle(show: Show): Bundle = bundleOf(ARG_OPTIONS to Options(show.ids.trakt, Mode.SHOWS))
  }

  override val navigationId = R.id.commentsFragment
  override val viewModel by viewModels<CommentsViewModel>()
  private val binding by viewBinding(FragmentCommentsBinding::bind)

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)
    setupView()
    setupRecycler()
    setupInsets()

    launchAndRepeatStarted(
      { viewModel.uiState.collect { render(it) } },
      { viewModel.messageFlow.collect { showSnack(it) } },
    )
  }

  private fun setupView() {
    hideNavigation()
    with(binding) {
      commentsBackArrow.onClick { requireActivity().onBackPressed() }
      commentsTitle.onClick { requireActivity().onBackPressed() }
      commentsUpButton.onClick {
        commentsUpButton.fadeOut(150)
        resetScroll()
      }
    }
  }

  private fun setupInsets() {
    with(binding) {
      commentsRecycler.doOnApplyWindowInsets { view, insets, padding, _ ->
        val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
          top = padding.top + inset.top,
          bottom = padding.bottom + inset.bottom,
        )
        commentsTitle.updateTopMargin(inset.top)
        commentsBackArrow.updateTopMargin(inset.top)
        commentsUpButton.updateLayoutParams<MarginLayoutParams> {
          updateMargins(bottom = inset.bottom + dimenToPx(R.dimen.fabButtonPadding))
        }
      }
    }
  }

  private fun setupRecycler() {
    binding.commentsRecycler.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
      itemAnimator = null
      addDivider(R.drawable.divider_comments_list)
      addOnScrollListener(recyclerScrollListener)
    }
  }

  private fun resetScroll() {
    with(binding) {
      commentsRecycler.smoothScrollToPosition(0)
      commentsBackArrow.animate().translationY(0F).start()
      commentsTitle.animate().translationY(0F).start()
    }
  }

  private fun render(uiState: CommentsUiState) {
    with(uiState) {
      comments?.let {
        with(binding) {
          commentsProgress.gone()
          commentsEmpty.visibleIf(comments.isEmpty())
        }
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
  }

  private val recyclerScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(
      recyclerView: RecyclerView,
      newState: Int,
    ) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE) {
        return
      }
      val layoutManager = (binding.commentsRecycler.layoutManager as? LinearLayoutManager)
      if ((layoutManager?.findFirstVisibleItemPosition() ?: 0) >= BACK_UP_BUTTON_THRESHOLD) {
        binding.commentsUpButton.fadeIn(150)
      } else {
        binding.commentsUpButton.fadeOut(150)
      }
    }
  }

  @Parcelize
  data class Options(
    val id: IdTrakt,
    val mode: Mode,
  ) : Parcelable
}
