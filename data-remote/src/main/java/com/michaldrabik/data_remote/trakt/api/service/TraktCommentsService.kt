package com.michaldrabik.data_remote.trakt.api.service

import com.michaldrabik.data_remote.trakt.model.Comment
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TraktCommentsService {

  @GET("comments/{id}/replies")
  suspend fun fetchCommentReplies(
    @Path("id") commentId: Long,
    @Query("timestamp") timestamp: Long,
  ): List<Comment>
}
