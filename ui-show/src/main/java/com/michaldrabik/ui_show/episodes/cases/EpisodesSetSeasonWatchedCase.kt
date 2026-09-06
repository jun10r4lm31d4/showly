package com.michaldrabik.ui_show.episodes.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncManager
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.SeasonBundle
import com.michaldrabik.ui_model.Show
import dagger.hilt.android.scopes.ViewModelScoped
import java.time.ZonedDateTime
import javax.inject.Inject

@ViewModelScoped
class EpisodesSetSeasonWatchedCase @Inject constructor(
  private val episodesManager: EpisodesManager,
  private val scrobQuickSyncManager: ScrobQuickSyncManager,
) {

  suspend fun setSeasonWatched(
    show: Show,
    season: Season,
    isChecked: Boolean,
    customDate: ZonedDateTime?,
  ): Result {
    val bundle = SeasonBundle(season, show)

    when {
      isChecked -> {
        scrobQuickSyncManager.scheduleSeason(
          showTmdbId = show.ids.tmdb.id,
          seasonNumber = season.number,
          customDate = customDate,
        )
        return Result.SUCCESS
      }
      else -> {
        episodesManager.setSeasonUnwatched(bundle)
        scrobQuickSyncManager.clearSeason(
          showTmdbId = show.ids.tmdb.id,
          seasonNumber = season.number,
        )
        return Result.SUCCESS
      }
    }
  }

  enum class Result {
    SUCCESS,
  }
}
