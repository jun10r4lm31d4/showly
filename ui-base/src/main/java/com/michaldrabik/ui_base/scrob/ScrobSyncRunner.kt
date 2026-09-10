package com.michaldrabik.ui_base.scrob

import com.michaldrabik.common.errors.ShowlyError
import com.michaldrabik.data_remote.scrob.ScrobRemoteDataSource
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

abstract class ScrobSyncRunner(
  private val scrobRemoteSource: ScrobRemoteDataSource,
) {

  companion object {
    const val RETRY_DELAY_MS = 5000L
    const val MAX_IMPORT_RETRY_COUNT = 3

    // A self-hosted server, so no need to be as conservative as with Trakt's public API,
    // but still throttled to avoid hammering it with one lookup per unique title.
    const val SCROB_LOOKUP_DELAY_MS = 300L

    /**
     * Scrob servers may return timestamps without any timezone information
     * (e.g. "2026-08-24T22:34:00"), which [ZonedDateTime.parse] cannot handle.
     * Falls back to [OffsetDateTime] and finally treats naive values as UTC.
     */
    fun parseTimestampMillis(value: String?): Long? =
      value?.let { timestamp ->
        if (timestamp.isBlank()) return@let null
        runCatching { ZonedDateTime.parse(timestamp).toInstant().toEpochMilli() }
          .recoverCatching { OffsetDateTime.parse(timestamp).toInstant().toEpochMilli() }
          .recoverCatching { LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC).toEpochMilli() }
          .recoverCatching { Instant.parse(timestamp).toEpochMilli() }
          .getOrNull()
      }
  }

  val retryCount = AtomicInteger(0)
  var progressListener: (suspend (String) -> Unit)? = null

  abstract suspend fun run(): Int

  protected fun checkAuthorization() {
    if (!scrobRemoteSource.isLogged()) {
      throw ShowlyError.UnauthorizedError("Not logged in to Scrob.")
    }
  }

  protected fun resetRetries() {
    retryCount.set(0)
  }
}
