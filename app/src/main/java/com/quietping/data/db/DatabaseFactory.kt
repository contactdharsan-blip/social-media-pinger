package com.quietping.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the encrypted [AppDatabase].
 *
 * Uses the net.zetetic SQLCipher [SupportOpenHelperFactory] keyed with the
 * Keystore-wrapped passphrase from [DbKey]. The native SQLCipher library is loaded
 * once on first build. Foreign-key enforcement is enabled per-connection (SQLite
 * defaults it off), so cascade deletes from retention purges actually fire.
 */
object DatabaseFactory {

    @Volatile
    private var nativeLoaded = false

    private fun ensureNativeLoaded() {
        if (nativeLoaded) return
        synchronized(this) {
            if (!nativeLoaded) {
                System.loadLibrary("sqlcipher")
                nativeLoaded = true
            }
        }
    }

    /**
     * Construct the Room database backed by SQLCipher. The [passphrase] bytes are
     * consumed by the open-helper factory; callers should not reuse the array.
     */
    fun create(
        context: Context,
        passphrase: ByteArray = DbKey.passphrase(context)
    ): AppDatabase {
        ensureNativeLoaded()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DB_NAME
        )
            .openHelperFactory(factory)
            .addCallback(ForeignKeysCallback)
            // Captured content is ephemeral (retention-purged); on a schema bump we
            // drop and recreate rather than hand-write SQLCipher ALTER migrations.
            .fallbackToDestructiveMigration()
            .build()
    }

    /** Enables PRAGMA foreign_keys for every opened connection. */
    private object ForeignKeysCallback : androidx.room.RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON;")
        }
    }
}
