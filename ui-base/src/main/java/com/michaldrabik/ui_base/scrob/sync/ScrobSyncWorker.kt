package com.michaldrabik.ui_base.scrob.sync

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.michaldrabik.data_local.LocalDataSource
import com.michaldrabik.data_remote.scrob.ScrobAuthException
import com.michaldrabik.ui_base.Logger
import com.michaldrabik.ui_base.R
import com.michaldrabik.ui_base.scrob.imports.ScrobImportListsRunner
import com.michaldrabik.ui_base.scrob.imports.ScrobImportWatchedRunner
import com.michaldrabik.ui_base.scrob.quicksync.ScrobQuickSyncWorker
import com.michaldrabik.ui_base.utilities.extensions.notificationManager
import com.michaldrabik.ui_base.utilities.extensions.rethrowCancellation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@SuppressLint("MissingPermission")
@HiltWorker
class ScrobSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted workerParams: WorkerParameters,
  private val importWatchedRunner: ScrobImportWatchedRunner,
  private val importListsRunner: ScrobImportListsRunner,
  private val localSource: LocalDataSource,
) : CoroutineWorker(context, workerParams) {

  companion object {
    internal const val KEY_IMPORT_HISTORY = "KEY_IMPORT_HISTORY"
    internal const val KEY_IMPORT_LISTS = "KEY_IMPORT_LISTS"
    internal const val KEY_FORCE_FULL = "KEY_FORCE_FULL"
    internal const val KEY_SILENT = "KEY_SILENT"

    const val TAG_HISTORY = "SCROB_SYNC_WORK_HISTORY"
    const val TAG_LISTS = "SCROB_SYNC_WORK_LISTS"

    private const val MAX_DEFER_COUNT = 3

    private const val NOTIFICATION_PROGRESS_ID = 840
    private const val NOTIFICATION_HISTORY_ID = 841
    private const val NOTIFICATION_LISTS_ID = 842
    private const val NOTIFICATION_ERROR_ID = 843

    private const val NOTIFICATION_CHANNEL_ID = "Showly Scrob Sync Service"

    fun scheduleHistory(
      workManager: WorkManager,
      forceFull: Boolean = false,
    ) {
      val request = OneTimeWorkRequestBuilder<ScrobSyncWorker>()
        .setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).setInputData(workDataOf(KEY_IMPORT_HISTORY to true, KEY_FORCE_FULL to forceFull))
        .addTag(TAG_HISTORY)
        .build()

      workManager.enqueueUniqueWork(TAG_HISTORY, ExistingWorkPolicy.REPLACE, request)
      Timber.i("Scrob history sync scheduled. forceFull=$forceFull")
    }

    fun scheduleLists(workManager: WorkManager) {
      val request = OneTimeWorkRequestBuilder<ScrobSyncWorker>()
        .setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).setInputData(workDataOf(KEY_IMPORT_LISTS to true))
        .addTag(TAG_LISTS)
        .build()

      workManager.enqueueUniqueWork(TAG_LISTS, ExistingWorkPolicy.REPLACE, request)
      Timber.i("Scrob lists sync scheduled.")
    }

    /**
     * Silent auto-sync for app open. Uses KEEP so it never cancels an already
     * running manual sync, and posts no progress/success notifications.
     * History runs incremental inside the runner (full 1x/day or when forced).
     */
    fun scheduleHistoryAuto(workManager: WorkManager) {
      val request = OneTimeWorkRequestBuilder<ScrobSyncWorker>()
        .setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).setInputData(workDataOf(KEY_IMPORT_HISTORY to true, KEY_FORCE_FULL to false, KEY_SILENT to true))
        .addTag(TAG_HISTORY)
        .build()

      workManager.enqueueUniqueWork(TAG_HISTORY, ExistingWorkPolicy.KEEP, request)
      Timber.i("Scrob history auto-sync scheduled.")
    }

    fun scheduleListsAuto(workManager: WorkManager) {
      val request = OneTimeWorkRequestBuilder<ScrobSyncWorker>()
        .setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        ).setInputData(workDataOf(KEY_IMPORT_LISTS to true, KEY_SILENT to true))
        .addTag(TAG_LISTS)
        .build()

      workManager.enqueueUniqueWork(TAG_LISTS, ExistingWorkPolicy.KEEP, request)
      Timber.i("Scrob lists auto-sync scheduled.")
    }

    fun cancelPending(workManager: WorkManager) {
      workManager.cancelUniqueWork(TAG_HISTORY)
      workManager.cancelUniqueWork(TAG_LISTS)
    }
  }

  override suspend fun doWork(): Result {
    Timber.d("Initialized.")

    val isImportHistory = inputData.getBoolean(KEY_IMPORT_HISTORY, false)
    val isImportLists = inputData.getBoolean(KEY_IMPORT_LISTS, false)
    val isSilent = inputData.getBoolean(KEY_SILENT, false)

    // No export sync exists, so the history import must never run while local
    // watched-state pushes are still queued: the remote snapshot would miss them
    // and reconciliation would wipe freshly watched items locally.
    if (isImportHistory && hasPendingPushes()) {
      if (runAttemptCount >= MAX_DEFER_COUNT) {
        Timber.w("Scrob history import deferred too many times. Failing without importing.")
        return Result.failure()
      }
      Timber.i("Scrob history import deferred: QuickSync outbox not empty. Signalling drain.")
      ScrobQuickSyncWorker.scheduleDrain(WorkManager.getInstance(applicationContext))
      return Result.retry()
    }

    try {
      if (isImportHistory) runImportWatched(isSilent)
      if (isImportLists) runImportLists(isSilent)

      val notifId = when {
        isImportHistory -> NOTIFICATION_HISTORY_ID
        isImportLists -> NOTIFICATION_LISTS_ID
        else -> return Result.success()
      }
      if (!isSilent) {
        notificationManager().notify(notifId, createSuccessNotification())
      }
    } catch (error: Throwable) {
      if (error !is ScrobAuthException) rethrowCancellation(error)

      Logger.record(error, "ScrobSyncWorker::doWork()")
      notificationManager().notify(NOTIFICATION_ERROR_ID, createErrorNotification())

      return when {
        error is ScrobAuthException || runAttemptCount >= 3 -> Result.failure()
        else -> Result.retry()
      }
    } finally {
      notificationManager().cancel(NOTIFICATION_PROGRESS_ID)
    }

    return Result.success()
  }

  private suspend fun hasPendingPushes(): Boolean = localSource.scrobPendingOps.count() > 0

  private suspend fun runImportWatched(silent: Boolean = false) {
    if (!silent) setProgressNotification("Syncing progress…")
    val forceFull = inputData.getBoolean(KEY_FORCE_FULL, false)
    importWatchedRunner.run(forceFull)
  }

  private suspend fun runImportLists(silent: Boolean = false) {
    if (!silent) setProgressNotification("Syncing lists…")
    importListsRunner.run()
  }

  private fun setProgressNotification(content: String?) {
    notificationManager().notify(NOTIFICATION_PROGRESS_ID, createProgressNotification(content))
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Showly Scrob Sync",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        setSound(null, null)
      }
      applicationContext.notificationManager().createNotificationChannel(channel)
    }
  }

  private fun createBaseNotification(): NotificationCompat.Builder {
    createNotificationChannel()
    return NotificationCompat
      .Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
      .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
      .setContentTitle(applicationContext.getString(R.string.textScrobSync))
      .setSmallIcon(R.drawable.ic_notification)
      .setAutoCancel(true)
      .setColor(ContextCompat.getColor(applicationContext, R.color.colorNotificationDark))
  }

  private fun createProgressNotification(content: String?): Notification =
    createBaseNotification()
      .setContentText(content ?: applicationContext.getString(R.string.textScrobSyncRunning))
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setOngoing(true)
      .setAutoCancel(false)
      .setProgress(0, 0, true)
      .build()

  private fun createSuccessNotification(): Notification =
    createBaseNotification()
      .setContentText(applicationContext.getString(R.string.textScrobSyncComplete))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build()

  private fun createErrorNotification(): Notification =
    createBaseNotification()
      .setContentText(applicationContext.getString(R.string.errorScrobSyncFailed))
      .setStyle(
        NotificationCompat
          .BigTextStyle()
          .bigText(applicationContext.getString(R.string.errorScrobSyncFailed)),
      ).setPriority(NotificationCompat.PRIORITY_HIGH)
      .build()
}
