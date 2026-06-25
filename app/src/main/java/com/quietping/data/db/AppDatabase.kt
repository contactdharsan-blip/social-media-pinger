package com.quietping.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quietping.data.db.dao.BreakInLogDao
import com.quietping.data.db.dao.ConversationDao
import com.quietping.data.db.dao.MatchDao
import com.quietping.data.db.dao.MessageDao
import com.quietping.data.db.dao.MessageVersionDao
import com.quietping.data.db.dao.RuleDao
import com.quietping.data.db.dao.VipDao
import com.quietping.data.db.entities.BreakInLogEntity
import com.quietping.data.db.entities.ConversationEntity
import com.quietping.data.db.entities.MatchLogEntity
import com.quietping.data.db.entities.MessageEntity
import com.quietping.data.db.entities.MessageVersionEntity
import com.quietping.data.db.entities.RuleEntity
import com.quietping.data.db.entities.VipContactEntity

/**
 * The encrypted Room database for QuietPing's on-device vault, rules, and audit
 * log. Built via [DatabaseFactory] with a SQLCipher [net.zetetic.database.sqlcipher.SupportOpenHelperFactory]
 * so the file at rest is encrypted with a Keystore-wrapped passphrase.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MessageVersionEntity::class,
        RuleEntity::class,
        VipContactEntity::class,
        MatchLogEntity::class,
        BreakInLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun messageVersionDao(): MessageVersionDao
    abstract fun ruleDao(): RuleDao
    abstract fun vipDao(): VipDao
    abstract fun matchDao(): MatchDao
    abstract fun breakInLogDao(): BreakInLogDao

    companion object {
        const val DB_NAME = "quietping.db"
    }
}
