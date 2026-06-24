package com.quietping.di

import com.quietping.data.datastore.SettingsRepositoryImpl
import com.quietping.data.repo.MatchRepositoryImpl
import com.quietping.data.repo.MessageRepositoryImpl
import com.quietping.data.repo.RuleRepositoryImpl
import com.quietping.data.repo.VipRepositoryImpl
import com.quietping.domain.repo.MatchRepository
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.repo.RuleRepository
import com.quietping.domain.repo.VipRepository
import com.quietping.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds each domain repository interface to its data-layer implementation.
 *
 * Every implementation has an `@Inject` constructor (its Room DAOs / DataStore come
 * from [DatabaseModule] / [AppModule]), so `@Binds` is sufficient — no factory code.
 * Repositories are [Singleton]: their reactive `Flow`s are cheap to share and the
 * impls hold no per-call state, so one instance per process avoids redundant Room
 * observers and DataStore readers across ViewModels and the capture pipeline.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindRuleRepository(impl: RuleRepositoryImpl): RuleRepository

    @Binds
    @Singleton
    abstract fun bindVipRepository(impl: VipRepositoryImpl): VipRepository

    @Binds
    @Singleton
    abstract fun bindMatchRepository(impl: MatchRepositoryImpl): MatchRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
