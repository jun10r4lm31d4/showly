package com.michaldrabik.ui_base.scrob.quicksync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtcMillis
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_local.database.model.ScrobPendingOp
import com.michaldrabik.data_remote.scrob.ScrobAuthException
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import com.michaldrabik.data_remote.scrob.model.ScrobSeasonWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobShowWatchRequest
import com.michaldrabik.data_remote.scrob.model.ScrobWatchRequest
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Drains the durable `scrob_pending_ops` outbox, pushing local watched-state
 * changes to the Scrob server.
 *
 * Rows are only deleted after a successful push, so crashes, offline periods
 * and transient server errors never lose data: the next drain resumes where
 * it stopped. Auth failures keep rows queued until credentials are fixed and
 * a new drain is signalled.
 */
@HiltWorker
class ScrobQuickSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted workerParams: WorkerParameters,
  private val scrobRemoteSource: ScrobRemoteDataSource,
  private val localSource: LocalDataSource,
) : CoroutineWorker(context, workerParams) {

  companion object {
    private const val TAG = "SCROB_QUICK_SYNC_WORK"

    // TODO: remove in a future release. Pre-outbox payloads enqueued by older
    //  app versions carry operations inline instead of outbox rows.
    internal const val KEY_OPERATIONS = "KEY_OPERATIONS"
    internal const val KEY_TIMESTAMP = "KEY_TIMESTAMP"

    private const val MAX_RETRY_COUNT = 10
    private const val DRAIN_BATCH_SIZE = 50
    private const val OP_DELAY_MS = 300L

    fun scheduleDrain(workManager: WorkManager) {
      val request = OneTimeWorkRequestBuilder<ScrobQuickSyncWorker>()
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        ).setInitialDelay(3, SECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, SECONDS)
        .addTag(TAG)
        .build()

      // KEEP: an already queued/running drain sees rows inserted afterwards
      // because it reads the outbox in batches until empty.
      workManager.enqueueUniqueWork(TAG, KEEP, request)
      Timber.i("Scrob QuickSync drain scheduled.")
    }
  }

  override suspend fun doWork(): Result {
    Timber.d("Initialized.")

    if (!scrobRemoteSource.isLogged()) {
      Timber.d("Not logged into Scrob. Keeping queue for later...")
      return Result.failure()
    }

    drainLegacyPayload()?.let { return it }
    return drainOutbox()
  }

  private suspend fun drainOutbox(): Result {
    while (true) {
      val batch = localSource.scrobPendingOps.getOldest(DRAIN_BATCH_SIZE)
      if (batch.isEmpty()) break

      val appliedIds = mutableListOf<Long>()
      for ((index, op) in batch.withIndex()) {
        try {
          applyOp(op)
          appliedIds += op.id
          if (index != batch.lastIndex) {
            delay(OP_DELAY_MS)
          }
        } catch (error: Throwable) {
          rethrowCancellation(error)
          // Malformed rows can never succeed - drop them so they don't poison the queue.
          if (error is IllegalArgumentException || error is IndexOutOfBoundsException) {
            Timber.e("Dropping malformed Scrob pending op id=${op.id} op=${op.op}. $error")
            appliedIds += op.id
            continue
          }
          // Auth failures keep rows queued; a new drain after re-login resumes them.
          if (error is ScrobAuthException) {
            Timber.w("Scrob auth failed. Keeping ${batch.size - appliedIds.size} queued op(s).")
            localSource.scrobPendingOps.deleteByIds(appliedIds)
            return Result.failure()
          }
          Timber.w("Scrob sync operation failed. id=${op.id} op=${op.op} $error")
          localSource.scrobPendingOps.deleteByIds(appliedIds)
          return retryOrFail()
        }
      }
      localSource.scrobPendingOps.deleteByIds(appliedIds)
    }

    Timber.d("Quick Sync completed.")
    return Result.success()
  }

  private fun retryOrFail(): Result =
    when {
      runAttemptCount < MAX_RETRY_COUNT -> Result.retry()
      else -> Result.failure().also { Timber.w("Scrob QuickSync failed after $runAttemptCount attempts. Queue kept for next drain.") }
    }

  private suspend fun drainLegacyPayload(): Result? {
    val operations = inputData.getStringArray(KEY_OPERATIONS).orEmpty()
    if (operations.isEmpty()) return null

    Timber.d("Draining ${operations.size} legacy inline operation(s).")
    val timestampMillis = inputData.getLong(KEY_TIMESTAMP, nowUtcMillis())
    val watchedAt = dateIsoStringFromMillis(timestampMillis)

    var retryable = false
    operations.forEachIndexed { index, operation ->
      try {
        applyLegacyOperation(operation, watchedAt)
        if (index != operations.lastIndex) {
          delay(OP_DELAY_MS)
        }
      } catch (error: Throwable) {
        rethrowCancellation(error)
        Timber.w("Scrob sync operation failed. '$operation' $error")
        retryable = error !is ScrobAuthException
      }
    }

    return when {
      !retryable -> null
      else -> retryOrFail()
    }.also {
      Timber.d("Legacy drain completed.")
    }
  }

  private fun parseOp(slug: String): Operation =
    try {
      Operation.valueOf(slug)
    } catch (error: IllegalArgumentException) {
      throw IllegalArgumentException("Unknown Scrob op '$slug'", error)
    }

  private suspend fun applyOp(op: ScrobPendingOp) {
    val watchedAt = dateIsoStringFromMillis(op.watchedAtMillis)
    when (parseOp(op.op)) {
      Operation.MOVIE -> applyMovie(op.tmdbId, op.watched, watchedAt)
      Operation.EPISODE ->
        applyEpisode(
          showTmdbId = op.showTmdbId,
          seasonNumber = op.seasonNumber,
          episodeNumber = op.episodeNumber,
          episodeTmdbId = op.tmdbId,
          watched = op.watched,
          watchedAt = watchedAt,
        )
      Operation.SEASON -> applySeason(op.showTmdbId, op.seasonNumber, op.watched, watchedAt)
      Operation.SHOW -> applyShow(op.showTmdbId, op.watched, watchedAt)
    }
  }

  private suspend fun applyLegacyOperation(
    operation: String,
    watchedAt: String,
  ) {
    val parts = operation.split("|")
    when (Operation.valueOf(parts[0])) {
      Operation.MOVIE -> applyMovie(parts[2].toLong(), parts[1] == "1", watchedAt)
      Operation.EPISODE -> applyEpisode(parts[2].toLong(), parts[3].toInt(), parts[4].toInt(), parts[5].toLong(), parts[1] == "1", watchedAt)
      Operation.SEASON -> applySeason(parts[2].toLong(), parts[3].toInt(), parts[1] == "1", watchedAt)
      Operation.SHOW -> applyShow(parts[2].toLong(), parts[1] == "1", watchedAt)
    }
  }

  private suspend fun applyMovie(
    tmdbId: Long,
    watched: Boolean,
    watchedAt: String,
  ) {
    require(tmdbId > 0) { "Invalid movie tmdbId=$tmdbId" }
    when {
      watched -> scrobRemoteSource.addToHistory(
        ScrobWatchRequest(tmdbId = tmdbId, mediaType = MEDIA_TYPE_MOVIE, watchedAt = watchedAt),
      )
      else -> scrobRemoteSource.removeFromHistory(MEDIA_TYPE_MOVIE, tmdbId)
    }
  }

  private suspend fun applyEpisode(
    showTmdbId: Long,
    seasonNumber: Int,
    episodeNumber: Int,
    episodeTmdbId: Long,
    watched: Boolean,
    watchedAt: String,
  ) {
    require(showTmdbId > 0 && episodeTmdbId > 0) { "Invalid episode showTmdbId=$showTmdbId tmdbId=$episodeTmdbId" }
    when {
      watched -> scrobRemoteSource.addToHistory(
        ScrobWatchRequest(
          tmdbId = episodeTmdbId,
          mediaType = MEDIA_TYPE_EPISODE,
          watchedAt = watchedAt,
          seriesTmdbId = showTmdbId,
          seasonNumber = seasonNumber,
          episodeNumber = episodeNumber,
        ),
      )
      else -> scrobRemoteSource.removeFromHistory(MEDIA_TYPE_EPISODE, episodeTmdbId)
    }
  }

  private suspend fun applySeason(
    showTmdbId: Long,
    seasonNumber: Int,
    watched: Boolean,
    watchedAt: String,
  ) {
    require(showTmdbId > 0) { "Invalid season showTmdbId=$showTmdbId" }
    when {
      watched -> scrobRemoteSource.addSeasonToHistory(
        ScrobSeasonWatchRequest(seriesTmdbId = showTmdbId, seasonNumber = seasonNumber, watchedAt = watchedAt),
      )
      else -> scrobRemoteSource.removeSeasonFromHistory(showTmdbId, seasonNumber)
    }
  }

  private suspend fun applyShow(
    showTmdbId: Long,
    watched: Boolean,
    watchedAt: String,
  ) {
    require(showTmdbId > 0) { "Invalid show tmdbId=$showTmdbId" }
    when {
      watched -> scrobRemoteSource.addShowToHistory(
        ScrobShowWatchRequest(seriesTmdbId = showTmdbId, watchedAt = watchedAt),
      )
      else -> scrobRemoteSource.removeShowFromHistory(showTmdbId)
    }
  }

  private enum class Operation {
    MOVIE,
    EPISODE,
    SEASON,
    SHOW,
  }
}

private const val MEDIA_TYPE_MOVIE = "movie"
private const val MEDIA_TYPE_EPISODE = "episode"
