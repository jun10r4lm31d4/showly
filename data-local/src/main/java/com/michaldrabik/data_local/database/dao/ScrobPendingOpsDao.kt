package com.michaldrabik.data_local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.michaldrabik.data_local.database.model.ScrobPendingOp
import com.michaldrabik.data_local.sources.ScrobPendingOpsLocalDataSource

@Dao
interface ScrobPendingOpsDao : ScrobPendingOpsLocalDataSource {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  override suspend fun insert(ops: List<ScrobPendingOp>): List<Long>

  // Ordered by autoincrement id (insertion/causal order), not by timestamp:
  // ops enqueued in the same millisecond (e.g. LIST_CREATE + its items)
  // must drain in the order they were queued.
  @Query("SELECT * FROM scrob_pending_ops ORDER BY id ASC LIMIT :limit")
  override suspend fun getOldest(limit: Int): List<ScrobPendingOp>

  @Query("SELECT * FROM scrob_pending_ops WHERE op IN (:ops) ORDER BY created_at ASC")
  override suspend fun getByOps(ops: List<String>): List<ScrobPendingOp>

  @Query("DELETE FROM scrob_pending_ops WHERE id IN (:ids)")
  override suspend fun deleteByIds(ids: List<Long>)

  @Query("SELECT COUNT(*) FROM scrob_pending_ops")
  override suspend fun count(): Int

  @Query("DELETE FROM scrob_pending_ops")
  override suspend fun deleteAll()
}
