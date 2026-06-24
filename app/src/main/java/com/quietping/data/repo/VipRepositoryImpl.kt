package com.quietping.data.repo

import com.quietping.data.db.dao.VipDao
import com.quietping.data.db.toDomain
import com.quietping.data.db.toEntity
import com.quietping.domain.model.VipContact
import com.quietping.domain.repo.VipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [VipRepository]. Adds ignore duplicates via the (app, handle) unique
 * index, so re-starring a contact is a harmless no-op.
 */
class VipRepositoryImpl @Inject constructor(
    private val vipDao: VipDao
) : VipRepository {

    override fun vips(): Flow<List<VipContact>> =
        vipDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(vip: VipContact) {
        vipDao.insert(vip.toEntity())
    }

    override suspend fun remove(id: Long) =
        vipDao.deleteById(id)
}
