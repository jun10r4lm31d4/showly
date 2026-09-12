package com.michaldrabik.data_local.sources

import com.michaldrabik.data_local.database.model.ScrobPendingOp

interface ScrobPendingOpsLocalDataSource {

  suspend fun insert(ops: List<ScrobPendingOp>): List<Long>

  suspend fun getOldest(limit: Int): List<ScrobPendingOp>

  suspend fun getByOps(ops: List<String>): List<ScrobPendingOp>

  suspend fun deleteByIds(ids: List<Long>)

  suspend fun count(): Int

  suspend fun deleteAll()
}
