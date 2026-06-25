package com.quietping.data.repo

import com.quietping.data.db.dao.BreakInLogDao
import com.quietping.data.db.entities.BreakInLogEntity
import com.quietping.domain.model.BreakInEvent
import com.quietping.domain.repo.BreakInRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [BreakInRepository] over [BreakInLogDao]. The timestamp is stamped at
 * record time; the clock is read here (not injected) since this is a fire-and-forget
 * audit write with no unit-tested timing contract.
 */
class BreakInRepositoryImpl @Inject constructor(
    private val dao: BreakInLogDao
) : BreakInRepository {

    override fun recent(limit: Int): Flow<List<BreakInEvent>> =
        dao.observeRecent(limit).map { rows ->
            rows.map { BreakInEvent(id = it.id, attemptedAt = it.attemptedAt, reason = it.reason) }
        }

    override suspend fun record(reason: String) {
        dao.insert(BreakInLogEntity(attemptedAt = System.currentTimeMillis(), reason = reason))
    }

    override suspend fun clear() {
        dao.clear()
    }
}
