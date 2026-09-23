package com.michaldrabik.ui_base.scrob.imports

import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.MyMovie
import com.michaldrabik.data_local.database.model.MyShow
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.scrob.ScrobProvider
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.data_remote.scrob.model.ScrobHistoryEvent
import com.michaldrabik.data_remote.trakt.TraktRemoteDataSource
import com.michaldrabik.repository.images.MovieImagesProvider
import com.michaldrabik.repository.images.ShowImagesProvider
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.scrob.ScrobSyncRunner
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.IdTrakt
import com.michaldrabik.ui_model.ImageType.FANART
import com.michaldrabik.ui_model.Movie
import com.michaldrabik.ui_model.Show
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobImportWatchedRunner @Inject constructor(
  private val scrobRemoteSource: ScrobRemoteDataSource,
  // Public, unauthenticated Trakt catalog lookups only - used purely to resolve a tmdb_id
  // into the Trakt-keyed show/movie metadata that Showly's local DB requires. This does not
  // need (and does not use) a Trakt account.
  private val scrobProvider: ScrobProvider,
  private val traktCatalogSource: TraktRemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val transactions: TransactionsProvider,
  private val showImagesProvider: ShowImagesProvider,
  private val movieImagesProvider: MovieImagesProvider,
  private val settingsRepository: SettingsRepository,
  private val dispatchers: CoroutineDispatchers,
) : ScrobSyncRunner(scrobRemoteSource) {

  companion object {
    private const val PAGE_SIZE = 100
  }

  override suspend fun run(): Int {
    Timber.d("Initialized.")
    checkAuthorization()

    withContext(dispatchers.IO) {
      resetRetries()
      runImport()
    }

    Timber.d("Finished with success.")
    return 0
  }

  private suspend fun runImport() {
    try {
      importHistory()
    } catch (error: Throwable) {
      if (retryCount.getAndIncrement() < MAX_IMPORT_RETRY_COUNT) {
        Timber.w("Scrob history import failed. Will retry in ${RETRY_DELAY_MS}ms... $error")
        delay(RETRY_DELAY_MS)
        runImport()
      } else {
        throw error
      }
    }
  }

  private suspend fun importHistory() {
    val movieEvents = mutableListOf<ScrobHistoryEvent>()
    val episodeEvents = mutableListOf<ScrobHistoryEvent>()
    var newestWatchedAtMillis = -1L

    // The history endpoint is treated as a FULL SNAPSHOT of the remote watch state.
    // Items absent from it are considered un-watched remotely and are removed locally
    // during the reconciliation pass below.
    Timber.d("Fetching full Scrob history...")

    var page = 1
    while (true) {
      val events = scrobRemoteSource.fetchHistoryPage(page = page, pageSize = PAGE_SIZE)
      if (events.isEmpty()) break

      for (event in events) {
        when {
          event.media.isMovie() -> movieEvents += event
          event.media.isEpisode() || event.media.isShowLevel() -> episodeEvents += event
        }
        newestWatchedAtMillis = maxOf(newestWatchedAtMillis, event.watchedAtMillis() ?: -1L)
      }
      page++
    }

    val remoteMovieTmdbIds = movieEvents.mapNotNullTo(mutableSetOf()) { it.media.tmdbId }
    val showLevelWatchedIds = episodeEvents.filter { it.media.isShowLevel() }.mapNotNull { it.media.showTmdbId ?: it.media.tmdbId }.toSet()
    val remoteEpisodes = sortedMapOf<Long, MutableSet<Pair<Int, Int>>>()
    episodeEvents.forEach { event ->
      val showTmdbId = event.media.showTmdbId ?: event.media.tmdbId ?: return@forEach
      val seasonNumber = event.media.seasonNumber ?: return@forEach
      val episodeNumber = event.media.episodeNumber ?: return@forEach
      remoteEpisodes.getOrPut(showTmdbId) { mutableSetOf() } += seasonNumber to episodeNumber
    }

    Timber.d("Found ${movieEvents.size} movie events, ${episodeEvents.size} episode events.")

    coroutineScope {
      val moviesJob = async { importMovies(movieEvents) }
      val episodesJob = async { importEpisodes(episodeEvents) }
      moviesJob.await()
      episodesJob.await()
    }

    removeMissingMovies(remoteMovieTmdbIds)
    removeMissingEpisodes(remoteEpisodes, showLevelWatchedIds)

    if (newestWatchedAtMillis > 0) {
      scrobProvider.setActivityScrobHistorySyncedAt(newestWatchedAtMillis)
    }
  }

  private suspend fun importMovies(events: List<ScrobHistoryEvent>) {
    if (!settingsRepository.isMoviesEnabled || events.isEmpty()) return

    val byTmdbId = events
      .filter { it.media.tmdbId != null }
      .groupBy { it.media.tmdbId!! }

    val myMovies = localSource.myMovies.getAll()
    val myMoviesTmdbIds = myMovies.map { it.idTmdb }.toSet()
    val myMoviesIds = myMovies.map { it.idTrakt }.toSet()

    byTmdbId.forEach { (tmdbId, movieEvents) ->
      try {
        if (tmdbId in myMoviesTmdbIds) return@forEach

        val remoteMovie = resolveMovie(tmdbId)
        if (remoteMovie == null) {
          Timber.w("Could not resolve Scrob movie tmdb_id=$tmdbId on Trakt. Skipping.")
          return@forEach
        }

        val movie = mappers.movie.fromNetwork(remoteMovie)
        if (movie.traktId !in myMoviesIds) {
          val watchedAt = movieEvents.mapNotNull { it.watchedAtMillis() }.maxOrNull() ?: nowUtcMillis()
          transactions.withTransaction {
            localSource.movies.upsert(listOf(mappers.movie.toDatabase(movie)))
            localSource.myMovies.insert(listOf(MyMovie.fromTraktId(movie.traktId, watchedAt)))
          }
          loadImage(movie)
        }
      } catch (error: Throwable) {
        if (error !is CancellationException) {
          Timber.w("Scrob movie import failed for tmdb_id=$tmdbId. Skipping... $error")
          Logger.record(error, "ScrobImportWatchedRunner::importMovies()")
        }
        rethrowCancellation(error)
      }
      delay(SCROB_LOOKUP_DELAY_MS)
    }
  }

  private suspend fun importEpisodes(events: List<ScrobHistoryEvent>) {
    if (events.isEmpty()) return

    val byShowTmdbId = events
      .filter { it.media.showTmdbId != null || it.media.tmdbId != null }
      .groupBy { it.media.showTmdbId ?: it.media.tmdbId!! }

    val myShowsIds = localSource.myShows.getAllTraktIds().toSet()

    byShowTmdbId.forEach { (showTmdbId, showEvents) ->
      try {
        val remoteShow = resolveShow(showTmdbId)
        if (remoteShow == null) {
          Timber.w("Could not resolve Scrob show tmdb_id=$showTmdbId on Trakt. Skipping.")
          return@forEach
        }

        val show = mappers.show.fromNetwork(remoteShow)
        val showTraktId = show.traktId

        val watchedByEpisode = showEvents
          .filter { it.media.seasonNumber != null && it.media.episodeNumber != null }
          .associateBy(
            keySelector = { it.media.seasonNumber!! to it.media.episodeNumber!! },
            valueTransform = { it.watchedAtMillis() },
          )

        val hasShowLevelWatch = showEvents.any { it.media.isShowLevel() }
        val newestWatchedMillis = showEvents.mapNotNull { it.watchedAtMillis() }.maxOrNull() ?: nowUtcMillis()

        val showExists = showTraktId in myShowsIds
        val localEpisodes = if (showExists) localSource.episodes.getAllByShowId(showTraktId) else emptyList()

        val needsMetadata = !showExists || hasShowLevelWatch || watchedByEpisode.keys.any { key ->
          val local = localEpisodes.find { it.seasonNumber == key.first && it.episodeNumber == key.second }
          local == null || !local.isWatched
        }

        if (!needsMetadata) {
          localSource.myShows.updateWatchedAt(showTraktId, newestWatchedMillis)
          return@forEach
        }

        val remoteSeasons = traktCatalogSource.fetchSeasons(showTraktId)
        val seasons = remoteSeasons.map { mappers.season.fromNetwork(it) }

        val episodesDb = remoteSeasons.flatMap { remoteSeason ->
          val season = seasons.first { it.number == remoteSeason.number }
          remoteSeason.episodes.orEmpty().mapNotNull { remoteEpisode ->
            val key = remoteSeason.number to remoteEpisode.number
            val isWatched = hasShowLevelWatch || watchedByEpisode.containsKey(key)
            if (!isWatched) return@mapNotNull null

            val watchedAtMillis = watchedByEpisode[key] ?: newestWatchedMillis
            val episode = mappers.episode.fromNetwork(remoteEpisode)
            mappers.episode.toDatabase(
              episode = episode,
              season = season,
              showId = IdTrakt(showTraktId),
              isWatched = true,
              lastExportedAt = null,
              lastWatchedAt = watchedAtMillis.let { millisToZonedDateTime(it) },
            )
          }
        }

        if (episodesDb.isEmpty()) return@forEach

        val seasonsDb = remoteSeasons.map { remoteSeason ->
          val season = seasons.first { it.number == remoteSeason.number }
          val totalEpisodes = remoteSeason.episodes?.size ?: 0
          val watchedEpisodes = remoteSeason.episodes.orEmpty().count {
            hasShowLevelWatch || watchedByEpisode.containsKey(remoteSeason.number to it.number)
          }
          mappers.season.toDatabase(
            season,
            IdTrakt(showTraktId),
            isWatched = totalEpisodes > 0 && watchedEpisodes == totalEpisodes,
          )
        }

        transactions.withTransaction {
          localSource.shows.upsert(listOf(mappers.show.toDatabase(show)))
          localSource.seasons.upsert(seasonsDb)
          localSource.episodes.upsert(episodesDb)

          if (!showExists) {
            localSource.myShows.insert(
              listOf(MyShow.fromTraktId(showTraktId, newestWatchedMillis, newestWatchedMillis, newestWatchedMillis)),
            )
          } else {
            localSource.myShows.updateWatchedAt(showTraktId, newestWatchedMillis)
          }
        }
        loadImage(show)
      } catch (error: Throwable) {
        if (error !is CancellationException) {
          Timber.w("Scrob episode import failed for show tmdb_id=$showTmdbId. Skipping... $error")
          Logger.record(error, "ScrobImportWatchedRunner::importEpisodes()")
        }
        rethrowCancellation(error)
      }
      delay(SCROB_LOOKUP_DELAY_MS)
    }
  }



  /**
   * Removes movies from the local "My Movies" collection when they are no longer present
   * in the remote Scrob history (i.e. they were un-watched on the server). Movies without
   * a valid TMDB id are left untouched, as they cannot be matched reliably.
   */
  private suspend fun removeMissingMovies(remoteMovieTmdbIds: Set<Long>) {
    if (!settingsRepository.isMoviesEnabled) return

    val localWatched = localSource.myMovies.getAll().filter { it.idTmdb > 0 }
    val toRemove = localWatched.filter { it.idTmdb !in remoteMovieTmdbIds }
    if (toRemove.isEmpty()) {
      Timber.d("Scrob movie reconciliation: nothing to remove.")
      return
    }

    transactions.withTransaction {
      toRemove.forEach { localSource.myMovies.deleteById(it.idTrakt) }
    }
    Timber.d("Scrob movie reconciliation: removed ${toRemove.size} movies missing from history.")
  }

  /**
   * Clears local watched flags for episodes that are no longer present in the remote Scrob
   * history (i.e. they were un-watched on the server). Reconciliation is scoped to shows in
   * the user's collection and matched by TMDB show id + season/episode numbers. Season 0
   * (specials) is never touched, as the server does not track them.
   */
  private suspend fun removeMissingEpisodes(
    remoteEpisodesByShowTmdbId: Map<Long, Set<Pair<Int, Int>>>,
    showLevelWatchedIds: Set<Long> = emptySet(),
  ) {
    val myShows = localSource.myShows.getAll()

    myShows.forEach { show ->
      try {
        if (show.idTmdb <= 0 || show.idTmdb in showLevelWatchedIds) return@forEach

        val watchedEpisodes = localSource.episodes
          .getAllByShowId(show.idTrakt)
          .filter { it.isWatched && it.seasonNumber != 0 }
        if (watchedEpisodes.isEmpty()) return@forEach

        val remoteKeys = remoteEpisodesByShowTmdbId[show.idTmdb]
        val toUnwatch = watchedEpisodes.filter { episode ->
          val key = episode.seasonNumber to episode.episodeNumber
          remoteKeys == null || key !in remoteKeys
        }
        if (toUnwatch.isEmpty()) return@forEach

        val affectedSeasonsIds = toUnwatch.map { it.idSeason }.toSet()

        transactions.withTransaction {
          localSource.episodes.upsert(
            toUnwatch.map { it.copy(isWatched = false, lastWatchedAt = null) },
          )

          // Clear season flags for seasons that no longer have any watched episodes left.
          val seasonsToUnwatch = localSource.seasons
            .getAllByShowId(show.idTrakt)
            .filter { it.isWatched && it.idTrakt in affectedSeasonsIds }
            .filter { season -> localSource.episodes.getAllForSeason(season.idTrakt).none { it.isWatched } }
          if (seasonsToUnwatch.isNotEmpty()) {
            localSource.seasons.update(seasonsToUnwatch.map { it.copy(isWatched = false) })
          }

          // Keep the show's "last watched" bookkeeping consistent with remaining episodes.
          localSource.episodes.getLastWatched(show.idTrakt)?.lastWatchedAt?.let { lastWatchedAt ->
            localSource.myShows.updateWatchedAt(show.idTrakt, lastWatchedAt.toInstant().toEpochMilli())
          }
        }

        Timber.d(
          "Scrob episode reconciliation: un-watched ${toUnwatch.size} episodes of '${show.title}' missing from history.",
        )
      } catch (error: Throwable) {
        if (error !is CancellationException) {
          Timber.w("Scrob episode reconciliation failed for show id=${show.idTrakt}. Skipping... $error")
          Logger.record(error, "ScrobImportWatchedRunner::removeMissingEpisodes()")
        }
        rethrowCancellation(error)
      }
    }
  }

  private fun millisToZonedDateTime(millis: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneOffset.UTC)

  private fun ScrobHistoryEvent.watchedAtMillis(): Long? = parseTimestampMillis(watchedAt)

  private suspend fun loadImage(show: Show) {
    try {
      showImagesProvider.loadRemoteImage(show, FANART)
    } catch (error: Throwable) {
      Timber.w(error)
      rethrowCancellation(error)
    }
  }

  private suspend fun loadImage(movie: Movie) {
    try {
      movieImagesProvider.loadRemoteImage(movie, FANART)
    } catch (error: Throwable) {
      Timber.w(error)
      rethrowCancellation(error)
    }
  }

  private suspend fun resolveMovie(tmdbId: Long): com.michaldrabik.data_remote.trakt.model.Movie? {
    val local = localSource.movies.getByTmdbId(tmdbId)
    if (local != null) return mappers.movie.toNetwork(mappers.movie.fromDatabase(local))

    return traktCatalogSource
      .fetchSearchId("tmdb", tmdbId.toString())
      .firstOrNull { it.movie != null }
      ?.movie
  }

  private suspend fun resolveShow(tmdbId: Long): com.michaldrabik.data_remote.trakt.model.Show? {
    val local = localSource.shows.getByTmdbId(tmdbId)
    if (local != null) return mappers.show.toNetwork(mappers.show.fromDatabase(local))

    return traktCatalogSource
      .fetchSearchId("tmdb", tmdbId.toString())
      .firstOrNull { it.show != null }
      ?.show
  }
}
