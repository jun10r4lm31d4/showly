package com.michaldrabik.repository

import com.michaldrabik.common.Mode
import com.michaldrabik.data_remote.trakt.TraktRemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.ui_model.Comment
import com.michaldrabik.ui_model.IdTrakt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentsRepository @Inject constructor(
  private val remoteSource: TraktRemoteDataSource,
  private val mappers: Mappers,
) {

  suspend fun loadComments(
    id: IdTrakt,
    mode: Mode,
    limit: Int = 100,
  ): List<Comment> {
    val comments = when (mode) {
      Mode.SHOWS -> remoteSource.fetchShowComments(id.id, limit)
      Mode.MOVIES -> remoteSource.fetchMovieComments(id.id, limit)
    }
    return comments
      .map { mappers.comment.fromNetwork(it) }
      .filter { it.parentId <= 0 }
  }

  suspend fun loadEpisodeComments(
    idTrakt: IdTrakt,
    season: Int,
    episode: Int,
  ) = remoteSource
    .fetchEpisodeComments(idTrakt.id, season, episode)
    .map { mappers.comment.fromNetwork(it) }
    .filter { it.parentId <= 0 }

  suspend fun loadReplies(commentId: Long) =
    remoteSource
      .fetchCommentReplies(commentId)
      .map { mappers.comment.fromNetwork(it).copy(replies = 0) }
      .sortedBy { it.createdAt?.toEpochSecond() }
}
