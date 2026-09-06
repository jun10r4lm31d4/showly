package com.michaldrabik.ui_show.episodes.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncManager
import com.michaldrabik.ui_model.EpisodeBundle
import dagger.hilt.android.scopes.ViewModelScoped
import java.time.ZonedDateTime
import javax.inject.Inject

@ViewModelScoped
class EpisodesSetEpisodeWatchedCase @Inject constructor(
  private val episodesManager: EpisodesManager,
  private val scrobQuickSyncManager: ScrobQuickSyncManager,
) {

  suspend fun setEpisodeWatched(
    episodeBundle: EpisodeBundle,
    isChecked: Boolean,
    customDate: ZonedDateTime?,
  ): Result {
    val (episode, season, show) = episodeBundle

    val episodeRef = ScrobQuickSyncManager.EpisodeRef(
      tmdbId = episode.ids.tmdb.id,
      seasonNumber = season.number,
      episodeNumber = episode.number,
    )

    when {
      isChecked -> {
        episodesManager.setEpisodeWatched(episodeBundle, customDate)
        scrobQuickSyncManager.scheduleEpisodes(
          showTmdbId = show.ids.tmdb.id,
          episodes = listOf(episodeRef),
          customDate = customDate,
        )
        return Result.SUCCESS
      }
      else -> {
        episodesManager.setEpisodeUnwatched(episodeBundle)
        scrobQuickSyncManager.clearEpisodes(
          showTmdbId = show.ids.tmdb.id,
          episodes = listOf(episodeRef),
        )
        return Result.SUCCESS
      }
    }
  }

  enum class Result {
    SUCCESS,
  }
}
