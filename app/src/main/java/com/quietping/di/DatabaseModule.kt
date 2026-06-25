package com.quietping.di

import android.content.Context
import com.quietping.data.db.AppDatabase
import com.quietping.data.db.DatabaseFactory
import com.quietping.data.db.dao.BreakInLogDao
import com.quietping.data.db.dao.ConversationDao
import com.quietping.data.db.dao.MatchDao
import com.quietping.data.db.dao.MessageDao
import com.quietping.data.db.dao.MessageVersionDao
import com.quietping.data.db.dao.RuleDao
import com.quietping.data.db.dao.VipDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Persistence bindings: the encrypted [AppDatabase] and every DAO it exposes.
 *
 * The database is built by [DatabaseFactory], which loads the native SQLCipher
 * library and keys the open-helper with the Keystore-wrapped passphrase from
 * [com.quietping.data.db.DbKey]. It is a [Singleton] so the SQLCipher connection
 * pool and the wrapped key are created exactly once for the process.
 *
 * Each DAO is provided from the singleton database; Room caches its own DAO
 * instances, so these providers are thin accessors that let constructor-injected
 * repositories ([com.quietping.data.repo]) depend on a single DAO without knowing
 * about the database wiring.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = DatabaseFactory.create(context)

    @Provides
    fun provideConversationDao(db: AppDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMessageVersionDao(db: AppDatabase): MessageVersionDao = db.messageVersionDao()

    @Provides
    fun provideRuleDao(db: AppDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideVipDao(db: AppDatabase): VipDao = db.vipDao()

    @Provides
    fun provideMatchDao(db: AppDatabase): MatchDao = db.matchDao()

    @Provides
    fun provideBreakInLogDao(db: AppDatabase): BreakInLogDao = db.breakInLogDao()
}
