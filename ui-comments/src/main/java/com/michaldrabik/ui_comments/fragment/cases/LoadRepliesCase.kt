package com.michaldrabik.ui_comments.fragment.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.CommentsRepository
import com.michaldrabik.ui_model.Comment
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class LoadRepliesCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val commentsRepository: CommentsRepository,
) {

  suspend fun loadReplies(comment: Comment): List<Comment> =
    withContext(dispatchers.IO) {
      commentsRepository
        .loadReplies(comment.id)
        .map {
          it.copy()
        }
    }
}
