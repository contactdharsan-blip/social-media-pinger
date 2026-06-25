package com.quietping.xposed

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Reusable, version-DURABLE storage-layer hook shared by every chat-app block strategy
 * (WhatsApp revoke, Instagram unsend, Facebook/Messenger remove-for-everyone).
 *
 * It hooks `android.database.sqlite.SQLiteDatabase` — an ANDROID FRAMEWORK class loaded by the boot
 * classloader, so a single [XposedHelpers.findAndHookMethod] attaches process-wide and is NEVER
 * obfuscated by the host app. That is the whole point: the hook attaches on every app version with
 * no per-version jadx pass. The only per-app/per-version unknown is which write is a delete-for-
 * everyone, which each caller supplies as a [Detector] (the documented tuning knob).
 *
 * ## Two independent capabilities
 *  1. **Recover (read-before-write)** — the instant before the host clears/removes the row, SELECT
 *     the original row read-only and forward it to QuietPing's encrypted vault via [VaultBridge].
 *     This needs NO blocking and NO DB mutation, so it is the safe default and works the moment the
 *     hook attaches. Field extraction is heuristic (schema-agnostic) and logs the column names so
 *     the per-version schema can be confirmed from logcat.
 *  2. **Block (validated only)** — neutralise the write so the message also stays in the host's own
 *     UI. Gated behind the caller's `validated` flag because a wrong guess could corrupt the user's
 *     real chat DB; OFF until the write shape is confirmed on a device.
 *
 * Every inspection failure lets the original write proceed untouched — never a crash, never a
 * corrupted DB.
 */
object SqliteWriteHook {

    /** The SQLite write being intercepted. */
    enum class Op { UPDATE, DELETE }

    /** Per-app classifier: is this write the delete-for-everyone mutation? */
    fun interface Detector {
        fun isRevoke(op: Op, table: String, values: ContentValues?, where: String?): Boolean
    }

    /**
     * Attach the hook for one app.
     *
     * @param appLabel human label for logs (e.g. "WhatsApp").
     * @param appPackage host package id, forwarded to [VaultBridge] with recovered messages.
     * @param tables message-table name anchors; writes to other tables pass untouched.
     * @param validated when true a detected write is BLOCKED (`result = 0`); when false, observe-only.
     * @param recover when true, read the row before the write and forward it to the vault.
     * @param detector the per-app/per-version revoke classifier.
     */
    fun install(
        appLabel: String,
        appPackage: String,
        tables: Set<String>,
        validated: Boolean,
        recover: Boolean,
        detector: Detector,
    ): Boolean = try {
        val str = String::class.java
        val cv = ContentValues::class.java
        val strArr = Array<String>::class.java

        val updateHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                handle(Op.UPDATE, param, valuesIdx = 1, whereIdx = 2, whereArgsIdx = 3,
                    appLabel, appPackage, tables, validated, recover, detector)
            }
        }
        val deleteHook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                handle(Op.DELETE, param, valuesIdx = -1, whereIdx = 1, whereArgsIdx = 2,
                    appLabel, appPackage, tables, validated, recover, detector)
            }
        }

        // update(table, values, where, whereArgs) and its onConflict overload route UPDATEs.
        XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "update", str, cv, str, strArr, updateHook)
        XposedHelpers.findAndHookMethod(
            SQLiteDatabase::class.java, "updateWithOnConflict",
            str, cv, str, strArr, Int::class.javaPrimitiveType, updateHook,
        )
        // delete(table, where, whereArgs) routes row removals (unsend / remove-for-everyone).
        XposedHelpers.findAndHookMethod(SQLiteDatabase::class.java, "delete", str, str, strArr, deleteHook)

        XposedBridge.log("QuietPing: $appLabel SQLite write hook attached (validated=$validated, recover=$recover).")
        true
    } catch (t: Throwable) {
        XposedBridge.log("QuietPing: $appLabel SQLite write hook failed to attach: $t")
        false
    }

    private fun handle(
        op: Op,
        param: XC_MethodHook.MethodHookParam,
        valuesIdx: Int,
        whereIdx: Int,
        whereArgsIdx: Int,
        appLabel: String,
        appPackage: String,
        tables: Set<String>,
        validated: Boolean,
        recover: Boolean,
        detector: Detector,
    ) {
        try {
            val table = param.args.getOrNull(0) as? String ?: return
            if (tables.none { table.equals(it, ignoreCase = true) }) return
            val values = if (valuesIdx >= 0) param.args.getOrNull(valuesIdx) as? ContentValues else null
            val where = param.args.getOrNull(whereIdx) as? String

            if (!detector.isRevoke(op, table, values, where)) return

            // 1. Recover the original row BEFORE the write erases it (read-only; no mutation).
            if (recover) {
                @Suppress("UNCHECKED_CAST")
                val whereArgs = param.args.getOrNull(whereArgsIdx) as? Array<String>
                recoverRow(param.thisObject as? SQLiteDatabase, table, where, whereArgs, appLabel, appPackage)
            }

            // 2. Optionally block so the message also stays in the host's own UI.
            if (validated) {
                param.result = 0 // rows affected; the host treats this as a no-op write.
                XposedBridge.log("QuietPing: BLOCKED $appLabel $op on '$table'.")
            } else {
                XposedBridge.log(
                    "QuietPing: OBSERVE $appLabel candidate $op table='$table' " +
                        "cols=${values?.keySet()} where='$where'",
                )
            }
        } catch (t: Throwable) {
            // Any inspection failure => let the original write proceed untouched. Never corrupt the DB.
        }
    }

    /**
     * Read the row(s) the impending write targets and forward the recovered message to the vault.
     * Read-only SELECT on the same [db] (queries are not hooked, so no re-entrancy). Heuristic,
     * schema-agnostic field extraction: it logs the column names (so the exact schema can be
     * confirmed from logcat) and best-effort maps body/sender/thread/time. Sends only when a
     * non-blank body is found; any failure is swallowed so the host write proceeds normally.
     */
    private fun recoverRow(
        db: SQLiteDatabase?,
        table: String,
        where: String?,
        whereArgs: Array<String>?,
        appLabel: String,
        appPackage: String,
    ) {
        if (db == null) return
        var cursor: android.database.Cursor? = null
        try {
            cursor = db.query(table, null, where, whereArgs, null, null, null, RECOVER_LIMIT)
            val cols = cursor.columnNames
            XposedBridge.log("QuietPing: RECOVER $appLabel reading '$table' cols=${cols.toList()}")
            while (cursor.moveToNext()) {
                val row = readRow(cursor)
                val body = pick(row, BODY_ANCHORS) ?: longestText(row) ?: continue
                if (body.isBlank()) continue
                val sender = pick(row, SENDER_ANCHORS).orEmpty()
                val convKey = pick(row, THREAD_ANCHORS) ?: where ?: table
                val postedAt = pick(row, TIME_ANCHORS)?.toLongOrNull() ?: 0L
                VaultBridge.send(appPackage, convKey, sender, body, postedAt)
            }
        } catch (t: Throwable) {
            // best-effort recovery; never disrupt the host
        } finally {
            try { cursor?.close() } catch (_: Throwable) {}
        }
    }

    /** Column-name → string-value map for the current cursor row (string-coerced, blob-safe). */
    private fun readRow(c: android.database.Cursor): Map<String, String> {
        val out = LinkedHashMap<String, String>(c.columnCount)
        for (i in 0 until c.columnCount) {
            val v = try { c.getString(i) } catch (_: Throwable) { null }
            if (v != null) out[c.getColumnName(i).lowercase()] = v
        }
        return out
    }

    /** First value whose column name contains any anchor substring. */
    private fun pick(row: Map<String, String>, anchors: List<String>): String? =
        row.entries.firstOrNull { e -> anchors.any { e.key.contains(it) } && e.value.isNotBlank() }?.value

    /** Fallback body: the longest non-numeric text value in the row. */
    private fun longestText(row: Map<String, String>): String? =
        row.values.filter { it.length > 1 && it.toLongOrNull() == null }.maxByOrNull { it.length }

    // Schema-agnostic anchors — tune from the RECOVER logcat for a target version if needed.
    private val BODY_ANCHORS = listOf("text_data", "text", "body", "content", "caption", "data")
    private val SENDER_ANCHORS = listOf("sender", "author", "from", "remote", "jid", "user")
    private val THREAD_ANCHORS = listOf("chat", "thread", "conversation", "key_remote", "jid", "key")
    private val TIME_ANCHORS = listOf("timestamp", "received_timestamp", "time", "date")
    private const val RECOVER_LIMIT = "5"
}
