package com.michaldrabik.ui_base.scrob.quicksync

import androidx.work.WorkManager
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import timber.log.Timber
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules pushes of local watched-state changes to the user's Scrob server,
 * mirroring how [com.michaldrabik.ui_base.trakt.quicksync.QuickSyncManager] works for Trakt.
 */
@Singleton
class ScrobQuickSyncManager @Inject constructor(
  private val scrobRemoteSource: ScrobRemoteDataSource,
  private val workManager: WorkManager,
) {

  fun scheduleMoviesWatched(
    moviesTmdbIds: List<Long>,
    customDate: ZonedDateTime?,
  ) {
    if (!ensureLogged()) return
    val ops = moviesTmdbIds
      .filter { it > 0 }
      .map { "${Op.MOVIE.slug}|1|$it" }
    schedule(ops, timestamp(customDate))
  }

  fun clearMovies(moviesTmdbIds: List<Long>) {
    if (!ensureLogged()) return
    val ops = moviesTmdbIds
      .filter { it > 0 }
      .map { "${Op.MOVIE.slug}|0|$it" }
    schedule(ops, nowUtcMillis())
  }

  fun scheduleEpisodes(
    showTmdbId: Long,
    episodes: List<EpisodeRef>,
    customDate: ZonedDateTime? = null,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob episodes push. Missing show tmdb id.")
      return
    }
    val ops = episodes
      .filter { it.tmdbId > 0 }
      .map { "${Op.EPISODE.slug}|1|$showTmdbId|${it.seasonNumber}|${it.episodeNumber}|${it.tmdbId}" }
    if (episodes.any { it.tmdbId <= 0 }) {
      Timber.w("Some episode refs are missing tmdb ids and were skipped. Show #$showTmdbId")
    }
    schedule(ops, timestamp(customDate))
  }

  fun clearEpisodes(
    showTmdbId: Long,
    episodes: List<EpisodeRef>,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob episodes removal. Missing show tmdb id.")
      return
    }
    val ops = episodes
      .filter { it.tmdbId > 0 }
      .map { "${Op.EPISODE.slug}|0|$showTmdbId|${it.seasonNumber}|${it.episodeNumber}|${it.tmdbId}" }
    schedule(ops, nowUtcMillis())
  }

  fun scheduleSeason(
    showTmdbId: Long,
    seasonNumber: Int,
    customDate: ZonedDateTime? = null,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob season push. Missing show tmdb id.")
      return
    }
    schedule(listOf("${Op.SEASON.slug}|1|$showTmdbId|$seasonNumber"), timestamp(customDate))
  }

  fun clearSeason(
    showTmdbId: Long,
    seasonNumber: Int,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob season removal. Missing show tmdb id.")
      return
    }
    schedule(listOf("${Op.SEASON.slug}|0|$showTmdbId|$seasonNumber"), nowUtcMillis())
  }

  fun scheduleShow(
    showTmdbId: Long,
    customDate: ZonedDateTime? = null,
  ) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob show push. Missing show tmdb id.")
      return
    }
    schedule(listOf("${Op.SHOW.slug}|1|$showTmdbId"), timestamp(customDate))
  }

  fun clearShow(showTmdbId: Long) {
    if (!ensureLogged()) return
    if (showTmdbId <= 0) {
      Timber.d("Skipped Scrob show removal. Missing show tmdb id.")
      return
    }
    schedule(listOf("${Op.SHOW.slug}|0|$showTmdbId"), nowUtcMillis())
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

  private fun schedule(
    operations: List<String>,
    timestampMillis: Long,
  ) {
    if (operations.isEmpty()) return
    Timber.d("Scheduling ${operations.size} Scrob sync operation(s).")
    ScrobQuickSyncWorker.schedule(workManager, operations.toTypedArray(), timestampMillis)
  }

  private enum class Op(
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
