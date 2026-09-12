package com.michaldrabik.ui_base.scrob.quicksync

import androidx.work.WorkManager
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.common.extensions.toMillis
import com.michaldrabik.common.extensions.toUtcZone
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.ScrobPendingOp
import com.michaldrabik.data_remote.scrob.ScrobProvider
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.ui_base.scrob.imports.ScrobImportListsRunner
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
  private val scrobProvider: ScrobProvider,
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

  suspend fun scheduleWatchlistMovie(traktId: Long) {
    enqueueWatchlistOp(traktId, mediaType = MEDIA_TYPE_MOVIE, watched = true)
  }

  suspend fun clearWatchlistMovie(traktId: Long) {
    enqueueWatchlistOp(traktId, mediaType = MEDIA_TYPE_MOVIE, watched = false)
  }

  suspend fun scheduleWatchlistShow(traktId: Long) {
    enqueueWatchlistOp(traktId, mediaType = MEDIA_TYPE_SERIES, watched = true)
  }

  suspend fun clearWatchlistShow(traktId: Long) {
    enqueueWatchlistOp(traktId, mediaType = MEDIA_TYPE_SERIES, watched = false)
  }

  suspend fun scheduleListCreate(localListId: Long) {
    if (!ensureLogged()) return
    enqueue(
      listOf(
        ScrobPendingOp(
          op = Op.LIST_CREATE.slug,
          watched = true,
          tmdbId = -1,
          watchedAtMillis = nowUtcMillis(),
          createdAt = nowUtcMillis(),
          listId = localListId,
        ),
      ),
    )
  }

  suspend fun scheduleListRename(localListId: Long) {
    if (!ensureLogged()) return
    if (remoteListId(localListId) == null) {
      Timber.d("Skipped Scrob list rename. No remote counterpart for list id=$localListId.")
      return
    }
    enqueue(
      listOf(
        ScrobPendingOp(
          op = Op.LIST_RENAME.slug,
          watched = true,
          tmdbId = -1,
          watchedAtMillis = nowUtcMillis(),
          createdAt = nowUtcMillis(),
          listId = localListId,
        ),
      ),
    )
  }

  suspend fun scheduleListDelete(remoteListId: Long) {
    if (!ensureLogged()) return
    if (remoteListId <= 0) return
    enqueue(
      listOf(
        ScrobPendingOp(
          op = Op.LIST_DELETE.slug,
          watched = false,
          tmdbId = -1,
          watchedAtMillis = nowUtcMillis(),
          createdAt = nowUtcMillis(),
          // No local row exists by drain time: this carries the REMOTE id.
          listId = remoteListId,
        ),
      ),
    )
  }

  suspend fun scheduleListItemAdd(
    localListId: Long,
    traktId: Long,
    type: String,
  ) {
    enqueueListItemOp(localListId, traktId, type, watched = true)
  }

  suspend fun scheduleListItemRemove(
    localListId: Long,
    traktId: Long,
    type: String,
  ) {
    enqueueListItemOp(localListId, traktId, type, watched = false)
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

  private suspend fun enqueueWatchlistOp(
    traktId: Long,
    mediaType: String,
    watched: Boolean,
  ) {
    if (!ensureLogged()) return
    val watchlistListId = scrobProvider.getWatchlistListId()
    if (watchlistListId <= 0) {
      Timber.d("No Scrob watchlist list selected. Skipping list push...")
      return
    }
    // Watchlist ops carry the REMOTE list id; the row is looked up at drain time
    // and falls back to treating it as remote when missing locally.
    val tmdbId = resolveTmdbId(traktId, mediaType) ?: -1
    if (tmdbId <= 0) {
      Timber.d("Skipped Scrob list push. Missing tmdb id for trakt id=$traktId.")
      return
    }
    val op = if (watched) Op.LIST_ADD else Op.LIST_REMOVE
    enqueue(
      listOf(
        ScrobPendingOp(
          op = op.slug,
          watched = watched,
          tmdbId = tmdbId,
          watchedAtMillis = nowUtcMillis(),
          createdAt = nowUtcMillis(),
          listId = watchlistListId,
          mediaType = mediaType,
        ),
      ),
    )
  }

  /**
   * Guarantees the list exists remotely, lazily queueing its creation when
   * needed. This heals pre-existing user lists that were created before the
   * Scrob push existed: touching such a list (add/remove item) first queues
   * its LIST_CREATE, and FIFO order drains creation before the item ops.
   * Returns false when there is nothing to push to.
   */
  private suspend fun ensureListPushed(localListId: Long): Boolean {
    if (!ensureLogged()) return false
    if (localSource.customLists.getById(localListId) == null) return false
    if (remoteListId(localListId) != null) return true
    val createQueued = localSource.scrobPendingOps
      .getByOps(listOf(Op.LIST_CREATE.slug))
      .any { it.listId == localListId }
    if (!createQueued) {
      enqueue(
        listOf(
          ScrobPendingOp(
            op = Op.LIST_CREATE.slug,
            watched = true,
            tmdbId = -1,
            watchedAtMillis = nowUtcMillis(),
            createdAt = nowUtcMillis(),
            listId = localListId,
          ),
        ),
      )
    }
    return true
  }

  private suspend fun enqueueListItemOp(
    localListId: Long,
    traktId: Long,
    type: String,
    watched: Boolean,
  ) {
    if (!ensureListPushed(localListId)) return
    val mediaType =
      when (type) {
        "movie" -> MEDIA_TYPE_MOVIE
        "show" -> MEDIA_TYPE_SERIES
        else -> {
          Timber.d("Skipped Scrob list push. Unknown type '$type'.")
          return
        }
      }
    val tmdbId = resolveTmdbId(traktId, mediaType) ?: -1
    if (tmdbId <= 0) {
      Timber.d("Skipped Scrob list push. Missing tmdb id for trakt id=$traktId.")
      return
    }
    val op = if (watched) Op.LIST_ADD else Op.LIST_REMOVE
    enqueue(
      listOf(
        // LOCAL list id: resolved to the remote id at drain time, so items added
        // before a pending LIST_CREATE drains still land on the right list.
        ScrobPendingOp(
          op = op.slug,
          watched = watched,
          tmdbId = tmdbId,
          watchedAtMillis = nowUtcMillis(),
          createdAt = nowUtcMillis(),
          listId = localListId,
          mediaType = mediaType,
        ),
      ),
    )
  }

  private suspend fun resolveTmdbId(
    traktId: Long,
    mediaType: String,
  ): Long? =
    when (mediaType) {
      MEDIA_TYPE_MOVIE -> localSource.movies.getById(traktId)?.idTmdb
      MEDIA_TYPE_SERIES -> localSource.shows.getById(traktId)?.idTmdb
      else -> null
    }

  /**
   * Remote id of a local list, or null when it has no server counterpart.
   * Imported lists use the remote id as primary key; pushed lists carry it
   * in [ScrobPendingOp]-adjacent `idScrob`.
   */
  suspend fun remoteListId(localListId: Long): Long? {
    val row = localSource.customLists.getById(localListId) ?: return null
    row.idScrob?.let { return it }
    if (row.idSlug.startsWith(ScrobImportListsRunner.SCROB_LIST_SLUG_PREFIX)) return row.id
    return null
  }

  internal enum class Op(
    val slug: String,
  ) {
    MOVIE("MOVIE"),
    EPISODE("EPISODE"),
    SEASON("SEASON"),
    SHOW("SHOW"),
    LIST_ADD("LIST_ADD"),
    LIST_REMOVE("LIST_REMOVE"),
    LIST_CREATE("LIST_CREATE"),
    LIST_RENAME("LIST_RENAME"),
    LIST_DELETE("LIST_DELETE"),
  }

  data class EpisodeRef(
    val tmdbId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
  )

  companion object {
    internal const val MEDIA_TYPE_MOVIE = "movie"
    internal const val MEDIA_TYPE_SERIES = "series"
  }
}
