package com.michaldrabik.ui_progress_movies.main.cases

import com.michaldrabik.repository.PinnedItemsRepository
import com.michaldrabik.repository.movies.MoviesRepository
import com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncManager
import com.michaldrabik.ui_base.trakt.quicksync.QuickSyncManager
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.Ids
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_progress_movies.BaseMockTest
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@Suppress("EXPERIMENTAL_API_USAGE")
class ProgressMoviesMainCaseTest : BaseMockTest() {

  @RelaxedMockK lateinit var moviesRepository: MoviesRepository
  @RelaxedMockK lateinit var pinnedItemsRepository: PinnedItemsRepository
  @RelaxedMockK lateinit var quickSyncManager: QuickSyncManager
  @RelaxedMockK lateinit var scrobQuickSyncManager: ScrobQuickSyncManager

  private lateinit var SUT: ProgressMoviesMainCase

  @Before
  override fun setUp() {
    super.setUp()
    SUT = ProgressMoviesMainCase(
      moviesRepository,
      pinnedItemsRepository,
      quickSyncManager,
      scrobQuickSyncManager,
    )
  }

  @After
  fun tearDown() {
    clearAllMocks()
  }

  @Test
  fun `Should add movie to movies history properly`() =
    runTest {
      val movie = Movie.EMPTY.copy(ids = Ids.EMPTY.copy(trakt = IdTrakt(123)))

      SUT.addToMyMovies(movie, null)

      coVerify { moviesRepository.myMovies.insert(IdTrakt(123), null) }
      coVerify { pinnedItemsRepository.removePinnedItem(movie) }
    }

  @Test
  fun `Should add movie to movies history properly using only ID`() =
    runTest {
      SUT.addToMyMovies(IdTrakt(123))

      coVerify { moviesRepository.myMovies.insert(IdTrakt(123), null) }
      coVerify { pinnedItemsRepository.removePinnedItem(any<Movie>()) }
    }
}
