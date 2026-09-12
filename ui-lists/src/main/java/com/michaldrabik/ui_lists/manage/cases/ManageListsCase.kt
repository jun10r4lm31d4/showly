package com.michaldrabik.ui_lists.manage.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.data_remote.scrob.ScrobProvider
import com.michaldrabik.repository.ListsRepository
import com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncManager
import com.michaldrabik.ui_lists.manage.recycler.ManageListsItem
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class ManageListsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val listsRepository: ListsRepository,
  private val scrobQuickSyncManager: ScrobQuickSyncManager,
  private val scrobProvider: ScrobProvider,
) {

  suspend fun loadLists(
    itemId: IdTrakt,
    itemType: String,
  ) = withContext(dispatchers.IO) {
    val listsAsync = async { listsRepository.loadAll() }
    val listsWithItemAsync = async { listsRepository.loadListIdsForItem(itemId, itemType) }
    val (lists, listsWithItem) = Pair(listsAsync.await(), listsWithItemAsync.await())
    // The list chosen as watchlist is managed by the dedicated watchlist
    // button, so it is hidden here to avoid duplicate/conflicting actions.
    val watchlistListId = scrobProvider.getWatchlistListId()
    lists
      .filterNot { it.id == watchlistListId || it.idScrob == watchlistListId }
      .sortedBy { it.name }
      .map {
        val isChecked = listsWithItem.contains(it.id)
        ManageListsItem(it, isChecked, true)
      }
  }

  suspend fun addToList(
    itemId: IdTrakt,
    itemType: String,
    listItem: ManageListsItem,
  ) = withContext(dispatchers.IO) {
    listsRepository.addToList(listItem.list.id, itemId, itemType)
    scrobQuickSyncManager.scheduleListItemAdd(listItem.list.id, itemId.id, itemType)
  }

  suspend fun removeFromList(
    itemId: IdTrakt,
    itemType: String,
    listItem: ManageListsItem,
  ) = withContext(dispatchers.IO) {
    listsRepository.removeFromList(listItem.list.id, itemId, itemType)
    scrobQuickSyncManager.scheduleListItemRemove(listItem.list.id, itemId.id, itemType)
  }
}
