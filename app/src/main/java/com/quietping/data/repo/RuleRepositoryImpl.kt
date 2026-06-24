package com.quietping.data.repo

import com.quietping.data.db.dao.RuleDao
import com.quietping.data.db.toDomain
import com.quietping.data.db.toEntity
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Rule
import com.quietping.domain.repo.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [RuleRepository]. [upsert] inserts (REPLACE on id conflict) and
 * returns the affected row id so callers can immediately reference a new rule.
 */
class RuleRepositoryImpl @Inject constructor(
    private val ruleDao: RuleDao
) : RuleRepository {

    override fun rules(): Flow<List<Rule>> =
        ruleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun rulesFor(app: AppPackage): Flow<List<Rule>> =
        ruleDao.observeForApp(app).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsert(rule: Rule): Long =
        ruleDao.insert(rule.toEntity())

    override suspend fun delete(id: Long) =
        ruleDao.deleteById(id)
}
