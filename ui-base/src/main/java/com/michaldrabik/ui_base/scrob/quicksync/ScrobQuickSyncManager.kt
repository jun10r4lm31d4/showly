package com.michaldrabik.ui_base.scrob.quicksync

import androidx.work.WorkManager
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.ScrobPendingOp
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import timber.log.Timber
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules pushes of local watched-state changes to the user's Scrob server.
 *
 * Every change is first persisted into the durable `scrob_pending_ops` outbox
 * and only then a drain is signalled to [ScrobQuickSyncWorker]. Rows survive
 * process death and offline periods, so no watched-state is lost on crash or
 * without connectivity.
 */
@Singleton
class ScrobQuickSyncManager @Inject constructor(
  private val scrobRemoteSource: ScrobRemoteDataSource,
  private val localSource: LocalDataSource,
  private val workManager: WorkManager,
) {

  suspend fun scheduleMoviesWatched(
    moviesTmdbIds: List<Long>,
    customDate: ZonedDateTime?,
  ) {
    if (!ensureLogged()) return
    val ops = moviesTmdbIds
      .filter { it > 0 }
      .map { ScrobPendingOp(op = Op.MOVIE.slug, watched = true, tmdbId = it, watchedAtMillis = timestamp(customDate), createdAt = nowUtcMillis()) }
    enqueue(ops)
  }

  suspend fun clearMovies(moviesTmdbIds: List<Long>) {
    if (!ensureLogged()) return
    val ops = moviesTmdbIds
      .filter { it > 0 }
      .map { ScrobPendingOp(op = Op.MOVIE.slug, watched = false, tmdbId = it, watchedAtMillis = nowUtcMillis(), createdAt = nowUtcMillis()) }
    enqueue(ops)
  }

  suspend fun scheduleEpisodes(
    showTmdbId: Long,
    episodes: List<EpisodeRef>,
    customDate: ZonedDateTime? = null,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob episodes push. Missing show tmdb id.")
      return
    }
    val watchedAt = timestamp(customDate)
    val now = nowUtcMillis()
    val ops = episodes
      .filter { it.tmdbId > 0 }
      .map {
        ScrobPendingOp(
          op = Op.EPISODE.slug,
          watched = true,
          tmdbId = it.tmdbId,
          showTmdbId = showTmdbId,
          seasonNumber = it.seasonNumber,
          episodeNumber = it.episodeNumber,
          watchedAtMillis = watchedAt,
          createdAt = now,
        )
      }
    if (episodes.any { it.tmdbId <= 0 }) {
      Timber.w("Some episode refs are missing tmdb ids and were skipped. Show #$showTmdbId")
    }
    enqueue(ops)
  }

  suspend fun clearEpisodes(
    showTmdbId: Long,
    episodes: List<EpisodeRef>,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob episodes removal. Missing show tmdb id.")
      return
    }
    val now = nowUtcMillis()
    val ops = episodes
      .filter { it.tmdbId > 0 }
      .map {
        ScrobPendingOp(
          op = Op.EPISODE.slug,
          watched = false,
          tmdbId = it.tmdbId,
          showTmdbId = showTmdbId,
          seasonNumber = it.seasonNumber,
          episodeNumber = it.episodeNumber,
          watchedAtMillis = now,
          createdAt = now,
        )
      }
    enqueue(ops)
  }

  suspend fun scheduleSeason(
    showTmdbId: Long,
    seasonNumber: Int,
    customDate: ZonedDateTime? = null,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob season push. Missing show tmdb id.")
      return
    }
    enqueue(
      listOf(
        ScrobPendingOp(op = Op.SEASON.slug, watched = true, tmdbId = showTmdbId, showTmdbId = showTmdbId, seasonNumber = seasonNumber, watchedAtMillis = timestamp(customDate), createdAt = nowUtcMillis()),
      ),
    )
  }

  suspend fun clearSeason(
    showTmdbId: Long,
    seasonNumber: Int,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob season removal. Missing show tmdb id.")
      return
    }
    enqueue(
      listOf(
        ScrobPendingOp(op = Op.SEASON.slug, watched = false, tmdbId = showTmdbId, showTmdbId = showTmdbId, seasonNumber = seasonNumber, watchedAtMillis = nowUtcMillis(), createdAt = nowUtcMillis()),
      ),
    )
  }

  suspend fun scheduleShow(
    showTmdbId: Long,
    customDate: ZonedDateTime? = null,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob show push. Missing show tmdb id.")
      return
    }
    enqueue(
      listOf(
        ScrobPendingOp(op = Op.SHOW.slug, watched = true, tmdbId = showTmdbId, showTmdbId = showTmdbId, watchedAtMillis = timestamp(customDate), createdAt = nowUtcMillis()),
      ),
    )
  }

  suspend fun clearShow(showTmdbId: Long) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob show removal. Missing show tmdb id.")
      return
    }
    enqueue(
      listOf(
        ScrobPendingOp(op = Op.SHOW.slug, watched = false, tmdbId = showTmdbId, showTmdbId = showTmdbId, watchedAtMillis = nowUtcMillis(), createdAt = nowUtcMillis()),
      ),
    )
  }

  fun isLogged(): Boolean = scrobRemoteSource.isLogged()

  private fun ensureLogged(): Boolean =
    if (scrobRemoteSource.isLogged()) {
      true
    } else {
      Timber.d("Not logged into Scrob. Skipping...")
      false
    }

  private fun timestamp(customDate: ZonedDateTime?) = customDate?.toUtcZone()?.toMillis() ?: nowUtcMillis()

  private suspend fun enqueue(operations: List<ScrobPendingOp>) {
    if (operations.isEmpty()) return
    Timber.d("Queueing ${operations.size} Scrob sync operation(s).")
    localSource.scrobPendingOps.insert(operations)
    ScrobQuickSyncWorker.scheduleDrain(workManager)
  }

  internal enum class Op(
    val slug: String,
  ) {
    MOVIE("MOVIE"),
    EPISODE("EPISODE"),
    SEASON("SEASON"),
    SHOW("SHOW"),
  }

  data class EpisodeRef(
    val tmdbId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
  )
}
