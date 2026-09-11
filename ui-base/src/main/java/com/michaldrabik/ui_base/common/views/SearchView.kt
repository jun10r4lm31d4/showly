package com.michaldrabik.ui_base.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.common.behaviour.SearchViewBehaviour
import com.michaldrabik.ui_base.databinding.ViewSearchBinding
import com.michaldrabik.ui_base.scrob.sync.ScrobSyncWorker
import com.michaldrabik.ui_base.utilities.extensions.dimenToPx
import com.michaldrabik.ui_base.utilities.extensions.doOnApplyWindowInsets
import com.michaldrabik.ui_base.utilities.extensions.expandTouch
import com.michaldrabik.ui_base.utilities.extensions.onClick
import com.michaldrabik.ui_base.utilities.extensions.visibleIf

class SearchView :
  FrameLayout,
  CoordinatorLayout.AttachedBehavior {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  val binding = ViewSearchBinding.inflate(LayoutInflater.from(context), this, true)

  var onSettingsClickListener: (() -> Unit)? = null
  var onStatsClickListener: (() -> Unit)? = null

  private var defaultHint: CharSequence? = null
  private var isScrobSyncing = false
  private var isObservingSync = false
  private var settingsIconDesired = true

  private var isHistorySyncing = false
  private var isListsSyncing = false

  private val historyObserver = Observer<List<WorkInfo>> { infos ->
    isHistorySyncing = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    updateSyncDisplay()
  }

  private val listsObserver = Observer<List<WorkInfo>> { infos ->
    isListsSyncing = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    updateSyncDisplay()
  }

  init {
    with(binding) {
      searchSettingsIcon.expandTouch()
      searchSettingsIcon.onClick { onSettingsClickListener?.invoke() }
      searchStatsIcon.onClick { onStatsClickListener?.invoke() }
      defaultHint = searchViewText.text
    }
  }

  var hint: String
    get() = defaultHint?.toString() ?: binding.searchViewInput.hint.toString()
    set(value) {
      defaultHint = value
      with(binding) {
        searchViewInput.hint = value
        if (!isScrobSyncing) {
          searchViewText.text = value
        }
      }
    }

  var settingsIconVisible
    get() = settingsIconDesired
    set(value) {
      settingsIconDesired = value
      binding.searchSettingsIcon.visibleIf(value && !isScrobSyncing)
    }

  var statsIconVisible
    get() = binding.searchStatsIcon.isVisible
    set(value) {
      binding.searchStatsIcon.visibleIf(value)
    }

  var isSearching = false

  override fun onAttachedToWindow() {
    doOnApplyWindowInsets { _, insets, _, _ ->
      val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
      applyWindowInsetBehaviour(context.dimenToPx(R.dimen.spaceNormal) + inset)
    }
    super.onAttachedToWindow()
    startObservingSync()
  }

  override fun onDetachedFromWindow() {
    stopObservingSync()
    super.onDetachedFromWindow()
  }

  private fun startObservingSync() {
    if (isObservingSync) return
    val owner = findViewTreeLifecycleOwner() ?: run {
      // View ainda sem LifecycleOwner (ex: inflada antes do attach completo).
      // Tenta de novo no próximo frame.
      post { startObservingSync() }
      return
    }
    val workManager = WorkManager.getInstance(context)
    workManager
      .getWorkInfosByTagLiveData(ScrobSyncWorker.TAG_HISTORY)
      .observe(owner, historyObserver)
    workManager
      .getWorkInfosByTagLiveData(ScrobSyncWorker.TAG_LISTS)
      .observe(owner, listsObserver)
    isObservingSync = true
  }

  private fun stopObservingSync() {
    if (!isObservingSync) return
    val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull()
    workManager?.getWorkInfosByTagLiveData(ScrobSyncWorker.TAG_HISTORY)?.removeObserver(historyObserver)
    workManager?.getWorkInfosByTagLiveData(ScrobSyncWorker.TAG_LISTS)?.removeObserver(listsObserver)
    isObservingSync = false
    isHistorySyncing = false
    isListsSyncing = false
    if (isScrobSyncing) {
      isScrobSyncing = false
      defaultHint?.let { binding.searchViewText.text = it }
      binding.searchSyncProgress.visibleIf(false)
      binding.searchSettingsIcon.visibleIf(settingsIconDesired)
    }
  }

  private fun updateSyncDisplay() {
    val syncing = isHistorySyncing || isListsSyncing
    if (syncing == isScrobSyncing) return
    isScrobSyncing = syncing
    with(binding) {
      if (syncing) {
        // Só o texto fantasma (telas home). O input real da tela de busca não é alterado.
        searchViewText.text = context.getString(R.string.textScrobSyncRunning)
      } else {
        defaultHint?.let { searchViewText.text = it }
      }
      // Mesma animação da janela de configuração (ProgressBar.Accent girando).
      searchSyncProgress.visibleIf(syncing)
      searchSettingsIcon.visibleIf(settingsIconDesired && !syncing)
    }
  }

  fun applyWindowInsetBehaviour(newInset: Int) {
    updateLayoutParams {
      (layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = SearchViewBehaviour(newInset)
    }
  }

  override fun getBehavior() = SearchViewBehaviour(context.dimenToPx(R.dimen.spaceNormal))

  override fun setEnabled(enabled: Boolean) {
    binding.searchViewInput.isEnabled = enabled
    super.setEnabled(enabled)
  }
}
