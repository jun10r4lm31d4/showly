package com.michaldrabik.ui_lists.create.cases

import com.michaldrabik.repository.ListsRepository
import com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncManager
import com.michaldrabik.ui_model.CustomList
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class CreateListCase @Inject constructor(
  private val listsRepository: ListsRepository,
  private val scrobQuickSyncManager: ScrobQuickSyncManager,
) {

  suspend fun createList(
    name: String,
    description: String?,
  ): CustomList {
    val list = listsRepository.createList(name, description, null, null)
    scrobQuickSyncManager.scheduleListCreate(list.id)
    return list
  }

  suspend fun updateList(list: CustomList): CustomList {
    val updated = listsRepository.updateList(list.id, null, null, list.name, list.description)
    scrobQuickSyncManager.scheduleListRename(list.id)
    return updated
  }
}
