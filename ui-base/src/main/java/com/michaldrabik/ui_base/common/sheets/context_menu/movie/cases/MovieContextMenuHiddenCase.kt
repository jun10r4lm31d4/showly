package com.michaldrabik.ui_base.common.sheets.context_menu.movie.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_base.common.sheets.context_menu.events.RemoveTraktUiEvent
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class MovieContextMenuHiddenCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val moviesRepository: MoviesRepository,
  private val pinnedItemsRepository: PinnedItemsRepository,
) {

  suspend fun moveToHidden(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(traktId))

      val (isMyMovie, isWatchlist) = awaitAll(
        async { moviesRepository.myMovies.exists(traktId) },
        async { moviesRepository.watchlistMovies.exists(traktId) },
      )

      moviesRepository.hiddenMovies.insert(movie.ids.trakt)
      pinnedItemsRepository.removePinnedItem(movie)

      RemoveTraktUiEvent(removeProgress = isMyMovie, removeWatchlist = isWatchlist)
    }

  suspend fun removeFromHidden(traktId: IdTrakt) =
    withContext(dispatchers.IO) {
      moviesRepository.hiddenMovies.delete(traktId)
    }
}
