package com.michaldrabik.ui_comments.fragment.cases

import com.michaldrabik.common.Mode
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.CommentsRepository
import com.michaldrabik.ui_model.Comment
import com.michaldrabik.ui_model.IdTrakt
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class LoadCommentsCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val commentsRepository: CommentsRepository,
) {

  suspend fun loadComments(
    id: IdTrakt,
    mode: Mode,
  ): List<Comment> =
    withContext(dispatchers.IO) {
      val comments = commentsRepository
        .loadComments(id, mode)
        .map {
          it.copy()
        }.partition { it.isMe }

      comments.first + comments.second
    }
}
