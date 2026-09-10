package com.michaldrabik.ui_lists.create.cases

import com.michaldrabik.repository.ListsRepository
import com.michaldrabik.ui_model.CustomList
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class CreateListCase @Inject constructor(
  private val listsRepository: ListsRepository,
) {

  suspend fun createList(
    name: String,
    description: String?,
  ): CustomList = listsRepository.createList(name, description, null, null)

  suspend fun updateList(list: CustomList): CustomList =
    listsRepository.updateList(list.id, null, null, list.name, list.description)
}
