package com.michaldrabik.ui_show.episodes.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_model.EpisodeBundle
import dagger.hilt.android.scopes.ViewModelScoped
import java.time.ZonedDateTime
import javax.inject.Inject

@ViewModelScoped
class EpisodesSetEpisodeWatchedCase @Inject constructor(
  private val episodesManager: EpisodesManager,
) {

  suspend fun setEpisodeWatched(
    episodeBundle: EpisodeBundle,
    isChecked: Boolean,
    customDate: ZonedDateTime?,
  ): Result {
    when {
      isChecked -> {
        episodesManager.setEpisodeWatched(episodeBundle, customDate)
        return Result.SUCCESS
      }
      else -> {
        episodesManager.setEpisodeUnwatched(episodeBundle)
        return Result.SUCCESS
      }
    }
  }

  enum class Result {
    SUCCESS,
  }
}
