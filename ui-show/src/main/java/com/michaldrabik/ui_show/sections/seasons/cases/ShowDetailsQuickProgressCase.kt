package com.michaldrabik.ui_show.sections.seasons.cases

import com.michaldrabik.repository.EpisodesManager
import com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncManager
import com.michaldrabik.ui_model.Episode
import com.michaldrabik.ui_model.EpisodeBundle
import com.michaldrabik.ui_model.SeasonBundle
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_show.quicksetup.QuickSetupListItem
import com.michaldrabik.ui_show.sections.seasons.recycler.SeasonListItem
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.coroutineScope
import java.time.ZonedDateTime
import javax.inject.Inject

@ViewModelScoped
class ShowDetailsQuickProgressCase @Inject constructor(
  private val episodesManager: EpisodesManager,
  private val scrobQuickSyncManager: ScrobQuickSyncManager,
) {

  suspend fun setQuickProgress(
    selectedItem: QuickSetupListItem,
    seasonsItems: List<SeasonListItem>,
    show: Show,
    customDate: ZonedDateTime?,
  ) = coroutineScope {
    val episodesAdded = mutableListOf<Episode>()

    episodesManager.setAllUnwatched(show.ids.trakt, skipSpecials = true)
    val seasons = seasonsItems.map { it.season }
    seasons
      .filter { !it.isSpecial() && it.number < selectedItem.season.number }
      .forEach { season ->
        val bundle = SeasonBundle(season, show)
        episodesManager.setSeasonWatched(bundle, customDate).apply {
          episodesAdded.addAll(this)
        }
      }

    val season = seasons.find { it.number == selectedItem.season.number }
    season
      ?.episodes
      ?.filter { it.number <= selectedItem.episode.number }
      ?.forEach { episode ->
        val bundle = EpisodeBundle(episode, season, show)
        episodesManager.setEpisodeWatched(bundle, customDate)
        episodesAdded.add(episode)
      }

    // Local state was reset and re-marked up to the selected episode - mirror it on Scrob.
    scrobQuickSyncManager.clearShow(show.ids.tmdb.id)
    scrobQuickSyncManager.scheduleEpisodes(
      showTmdbId = show.ids.tmdb.id,
      episodes = episodesAdded.map { episode ->
        ScrobQuickSyncManager.EpisodeRef(
          tmdbId = episode.ids.tmdb.id,
          seasonNumber = episode.season,
          episodeNumber = episode.number,
        )
      },
      customDate = customDate,
    )
  }
}
