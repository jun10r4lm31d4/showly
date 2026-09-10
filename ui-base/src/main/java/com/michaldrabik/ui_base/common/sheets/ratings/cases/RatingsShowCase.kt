package com.michaldrabik.ui_base.common.sheets.ratings.cases

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.repository.RatingsRepository
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Show
import com.michaldrabik.ui_model.TraktRating
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
class RatingsShowCase @Inject constructor(
  private val dispatchers: CoroutineDispatchers,
  private val ratingsRepository: RatingsRepository,
) {

  companion object {
    private val RATING_VALID_RANGE = 1..10
  }

  suspend fun loadRating(idTrakt: IdTrakt): TraktRating =
    withContext(dispatchers.IO) {
      val show = Show.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = idTrakt))
      try {
        val rating = ratingsRepository.shows.loadRatings(listOf(show))
        rating.firstOrNull() ?: TraktRating.EMPTY
      } catch (error: Throwable) {
        throw error
        TraktRating.EMPTY
      }
    }

  suspend fun saveRating(
    idTrakt: IdTrakt,
    rating: Int,
  ) = withContext(dispatchers.IO) {
    check(rating in RATING_VALID_RANGE)

    try {
      val show = Show.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = idTrakt))
      ratingsRepository.shows.addRating(
        show = show,
        rating = rating,
      )
    } catch (error: Throwable) {
      throw error
    }
  }

  suspend fun deleteRating(idTrakt: IdTrakt) =
    withContext(dispatchers.IO) {
      val show = Show.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = idTrakt))
      try {
        ratingsRepository.shows.deleteRating(
          show = show,
        )
      } catch (error: Throwable) {
        throw error
      }
    }
}
