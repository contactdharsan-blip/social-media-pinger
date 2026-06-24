package com.quietping.data.repo

import com.quietping.data.db.dao.MatchDao
import com.quietping.data.db.toDomain
import com.quietping.data.db.toEntity
import com.quietping.domain.model.MatchLog
import com.quietping.domain.repo.MatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [MatchRepository] powering Home's match feed.
 */
class MatchRepositoryImpl @Inject constructor(
    private val matchDao: MatchDao
) : MatchRepository {

    override fun recent(limit: Int): Flow<List<MatchLog>> =
        matchDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun log(match: MatchLog) {
        matchDao.insert(match.toEntity())
    }
}
