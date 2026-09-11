package com.michaldrabik.ui_base.scrob.imports

import com.michaldrabik.common.Mode
import com.michaldrabik.common.dispatchers.CoroutineDispatchers
import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.CustomListItem
import com.michaldrabik.data_local.utilities.TransactionsProvider
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.data_remote.scrob.model.ScrobList
import com.michaldrabik.data_remote.scrob.model.ScrobListItem
import com.michaldrabik.data_remote.trakt.TraktRemoteDataSource
import com.michaldrabik.repository.mappers.Mappers
import com.michaldrabik.repository.settings.SettingsRepository
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.scrob.ScrobSyncRunner
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import com.michaldrabik.ui_model.CustomList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobImportListsRunner @Inject constructor(
  private val scrobRemoteSource: ScrobRemoteDataSource,
  // Public, unauthenticated Trakt catalog lookups only - see ScrobImportWatchedRunner.
  private val traktCatalogSource: TraktRemoteDataSource,
  private val localSource: LocalDataSource,
  private val mappers: Mappers,
  private val transactions: TransactionsProvider,
  private val settingsRepository: SettingsRepository,
  private val dispatchers: CoroutineDispatchers,
) : ScrobSyncRunner(scrobRemoteSource) {

  companion object {
    internal const val SCROB_LIST_SLUG_PREFIX = "scrob-"
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
      importLists()
    } catch (error: Throwable) {
      if (retryCount.getAndIncrement() < MAX_IMPORT_RETRY_COUNT) {
        Timber.w("Scrob lists import failed. Will retry in ${RETRY_DELAY_MS}ms... $error")
        delay(RETRY_DELAY_MS)
        runImport()
      } else {
        throw error
      }
    }
  }

  private suspend fun importLists() {
    Timber.d("Fetching Scrob lists...")

    val remoteLists = scrobRemoteSource.fetchLists()
    val localLists = localSource.customLists
      .getAll()
      .map { mappers.customList.fromDatabase(it) }

    remoteLists.forEach { remoteList ->
      Timber.d("Processing '${remoteList.name}'...")
      try {
        val listId = upsertList(remoteList, localLists.find { it.id == remoteList.id })
        importListItems(listId, remoteList.id)
      } catch (error: Throwable) {
        if (error !is CancellationException) {
          Timber.w("Scrob list import failed for id=${remoteList.id}. Skipping... $error")
          Logger.record(error, "ScrobImportListsRunner::importLists()")
        }
        rethrowCancellation(error)
      }
      delay(SCROB_LOOKUP_DELAY_MS)
    }

    removeMissingLists(remoteLists.map { it.id }.toSet())
  }

  /**
   * Removes local Scrob-origin lists that no longer exist on the server.
   * Only lists created by this import (idSlug "scrob-<remoteId>") are touched;
   * user-created lists are left alone.
   */
  private suspend fun removeMissingLists(remoteIds: Set<Long>) {
    val toRemove = localSource.customLists
      .getAll()
      .filter { it.idSlug.startsWith(SCROB_LIST_SLUG_PREFIX) }
      .filter { list ->
        val remoteId = list.idSlug.removePrefix(SCROB_LIST_SLUG_PREFIX).toLongOrNull()
        remoteId == null || remoteId !in remoteIds
      }
    if (toRemove.isEmpty()) {
      Timber.d("Scrob lists reconciliation: nothing to remove.")
      return
    }

    transactions.withTransaction {
      toRemove.forEach { list ->
        localSource.customListsItems.deleteByList(list.id)
        localSource.customLists.deleteById(list.id)
      }
    }
    Timber.d("Scrob lists reconciliation: removed ${toRemove.size} lists missing from server.")
  }

  private suspend fun upsertList(
    remoteList: ScrobList,
    local: CustomList?,
  ): Long {
    if (local == null) {
      Timber.d("Local list not found. Creating...")
      val list = CustomList.create().copy(
        idSlug = "$SCROB_LIST_SLUG_PREFIX${remoteList.id}",
        name = remoteList.name,
        description = remoteList.description,
        privacy = if (remoteList.privacyLevel == "public") "public" else "private",
        itemCount = remoteList.itemCount.toLong(),
        createdAt = nowUtc(),
        updatedAt = nowUtc(),
        id = remoteList.id,
      )
      return localSource.customLists.insert(listOf(mappers.customList.toDatabase(list))).first()
    }

    val updated = local.copy(
      name = remoteList.name,
      description = remoteList.description,
      privacy = if (remoteList.privacyLevel == "public") "public" else "private",
      itemCount = remoteList.itemCount.toLong(),
      updatedAt = nowUtc(),
    )
    localSource.customLists.update(listOf(mappers.customList.toDatabase(updated)))
    return local.id
  }

  private suspend fun importListItems(
    listId: Long,
    scrobListId: Long,
  ) {
    val moviesEnabled = settingsRepository.isMoviesEnabled
    val localItems = localSource.customListsItems.getItemsById(listId)
    val nowMillis = nowUtcMillis()

    val remoteItems = scrobRemoteSource.fetchListItems(scrobListId)
    val remoteKeys = mutableSetOf<Pair<Long, String>>()

    remoteItems.forEach { item ->
      try {
        when {
          item.media.isMovie() && moviesEnabled -> {
            val tmdbId = item.media.tmdbId ?: return@forEach
            val remoteMovie = resolveMovie(tmdbId) ?: return@forEach

            val movie = mappers.movie.fromNetwork(remoteMovie)
            remoteKeys += movie.traktId to Mode.MOVIES.type
            if (localItems.any { it.idTrakt == movie.traktId && it.type == Mode.MOVIES.type }) return@forEach

            transactions.withTransaction {
              localSource.movies.upsert(listOf(mappers.movie.toDatabase(movie)))
              localSource.customListsItems.insertItem(
                CustomListItem(
                  id = 0,
                  idList = listId,
                  idTrakt = movie.traktId,
                  type = Mode.MOVIES.type,
                  rank = 0,
                  listedAt = item.addedAtMillis() ?: nowMillis,
                  createdAt = nowMillis,
                  updatedAt = nowMillis,
                ),
              )
            }
          }

          item.media.isEpisode() || item.media.isShowLevel() -> {
            val showTmdbId = item.media.showTmdbId ?: item.media.tmdbId ?: return@forEach
            val remoteShow = resolveShow(showTmdbId) ?: return@forEach

            val show = mappers.show.fromNetwork(remoteShow)
            remoteKeys += show.traktId to Mode.SHOWS.type
            if (localItems.any { it.idTrakt == show.traktId && it.type == Mode.SHOWS.type }) return@forEach

            transactions.withTransaction {
              localSource.shows.upsert(listOf(mappers.show.toDatabase(show)))
              localSource.customListsItems.insertItem(
                CustomListItem(
                  id = 0,
                  idList = listId,
                  idTrakt = show.traktId,
                  type = Mode.SHOWS.type,
                  rank = 0,
                  listedAt = item.addedAtMillis() ?: nowMillis,
                  createdAt = nowMillis,
                  updatedAt = nowMillis,
                ),
              )
            }
          }
        }
      } catch (error: Throwable) {
        if (error !is CancellationException) {
          Timber.w("Scrob list item import failed (media_id=${item.media.id}). Skipping... $error")
          Logger.record(error, "ScrobImportListsRunner::importListItems()")
        }
        rethrowCancellation(error)
      }
      delay(SCROB_LOOKUP_DELAY_MS)
    }

    removeMissingListItems(listId, remoteKeys)

    if (remoteItems.isNotEmpty()) {
      localSource.customLists.updateTimestamp(listId, nowMillis)
    }
  }

  /**
   * Removes local items of a Scrob list that are no longer present on the server.
   * Items whose media could not be resolved are skipped (fail-open).
   */
  private suspend fun removeMissingListItems(
    listId: Long,
    remoteKeys: Set<Pair<Long, String>>,
  ) {
    val toRemove = localSource.customListsItems
      .getItemsById(listId)
      .filter { (it.idTrakt to it.type) !in remoteKeys }
    if (toRemove.isEmpty()) return

    transactions.withTransaction {
      toRemove.forEach { localSource.customListsItems.deleteItem(listId, it.idTrakt, it.type) }
    }
    Timber.d("Scrob list items reconciliation: removed ${toRemove.size} items missing from server (listId=$listId).")
  }

  private fun ScrobListItem.addedAtMillis(): Long? = parseTimestampMillis(addedAt)

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
