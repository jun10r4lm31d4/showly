package com.michaldrabik.ui_base.scrob.quicksync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.michaldrabik.common.extensions.dateIsoStringFromMillis
import com.michaldrabik.common.extensions.nowUtcMillis
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

@HiltWorker
class ScrobQuickSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted workerParams: WorkerParameters,
  private val scrobRemoteSource: ScrobRemoteDataSource,
) : CoroutineWorker(context, workerParams) {

  companion object {
    private const val TAG = "SCROB_QUICK_SYNC_WORK"
    internal const val KEY_OPERATIONS = "KEY_OPERATIONS"
    internal const val KEY_TIMESTAMP = "KEY_TIMESTAMP"

    private const val MAX_RETRY_COUNT = 3
    private const val OP_DELAY_MS = 300L

    fun schedule(
      workManager: WorkManager,
      operations: Array<String>,
      timestampMillis: Long,
    ) {
      val request = OneTimeWorkRequestBuilder<ScrobQuickSyncWorker>()
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        ).setInitialDelay(3, SECONDS)
        .setInputData(
          workDataOf(
            KEY_OPERATIONS to operations,
            KEY_TIMESTAMP to timestampMillis,
          ),
        ).addTag(TAG)
        .build()

      workManager.enqueueUniqueWork(TAG, APPEND_OR_REPLACE, request)
      Timber.i("Scrob QuickSync scheduled.")
    }
  }

  override suspend fun doWork(): Result {
    Timber.d("Initialized.")

    if (!scrobRemoteSource.isLogged()) {
      Timber.d("Not logged into Scrob. Skipping...")
      return Result.success()
    }

    val operations = inputData.getStringArray(KEY_OPERATIONS).orEmpty()
    val timestampMillis = inputData.getLong(KEY_TIMESTAMP, nowUtcMillis())
    val watchedAt = dateIsoStringFromMillis(timestampMillis)

    var retryable = false

    operations.forEachIndexed { index, operation ->
      try {
        applyOperation(operation, watchedAt)
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
      !retryable -> Result.success()
      runAttemptCount < MAX_RETRY_COUNT -> Result.retry()
      else -> Result.failure().also { Timber.w("Scrob QuickSync failed after $runAttemptCount attempts.") }
    }.also {
      Timber.d("Quick Sync completed.")
    }
  }

  private suspend fun applyOperation(
    operation: String,
    watchedAt: String,
  ) {
    val parts = operation.split("|")
    when (Operation.valueOf(parts[0])) {
      Operation.MOVIE -> applyMovie(parts[2].toLong(), parts[1] == "1", watchedAt)
      Operation.EPISODE -> applyEpisode(parts, watchedAt)
      Operation.SEASON -> applySeason(parts[2].toLong(), parts[3].toInt(), parts[1] == "1", watchedAt)
      Operation.SHOW -> applyShow(parts[2].toLong(), parts[1] == "1", watchedAt)
    }
  }

  private suspend fun applyMovie(
    tmdbId: Long,
    watched: Boolean,
    watchedAt: String,
  ) {
    when {
      watched -> scrobRemoteSource.addToHistory(
        ScrobWatchRequest(tmdbId = tmdbId, mediaType = MEDIA_TYPE_MOVIE, watchedAt = watchedAt),
      )
      else -> scrobRemoteSource.removeFromHistory(MEDIA_TYPE_MOVIE, tmdbId)
    }
  }

  private suspend fun applyEpisode(
    parts: List<String>,
    watchedAt: String,
  ) {
    val showTmdbId = parts[2].toLong()
    val seasonNumber = parts[3].toInt()
    val episodeNumber = parts[4].toInt()
    val episodeTmdbId = parts[5].toLong()
    val watched = parts[1] == "1"
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
