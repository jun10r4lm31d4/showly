package com.michaldrabik.ui_backup.features.export

import android.net.Uri
import java.time.format.DateTimeFormatter

data class BackupExportUiState(
  val isLoading: Boolean = false,
  val exportContent: ExportContentState? = null,
  val error: Throwable? = null,
  val lastBackupExportTimestamp: Long = 0L,
  val dateFormat: DateTimeFormatter? = null,
)

data class ExportContentState(
  val exportContent: String,
  val exportUri: Uri,
)
