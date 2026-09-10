package com.michaldrabik.ui_show.sections.seasons.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.repository.shows.ShowsRepository
import com.michaldrabik.ui_model.Season
import com.michaldrabik.ui_model.SeasonBundle
import com.michaldrabik.ui_model.Show
import dagger.hilt.android.scopes.ViewModelScoped
import java.time.ZonedDateTime
import javax.inject.Inject

@ViewModelScoped
class ShowDetailsWatchedSeasonCase @Inject constructor(
  private val showsRepository: ShowsRepository,
  private val episodesManager: EpisodesManager,
) {

  suspend fun setSeasonWatched(
    show: Show,
    season: Season,
    isChecked: Boolean,
    isLocal: Boolean,
    customDate: ZonedDateTime?,
  ): Result {
    val bundle = SeasonBundle(season, show)

    when {
      isChecked -> {
        episodesManager.setSeasonWatched(bundle, customDate)
        return Result.SUCCESS
      }
      else -> {
        episodesManager.setSeasonUnwatched(bundle)
        return Result.SUCCESS
      }
    }
  }

  enum class Result {
    SUCCESS,
  }
}
