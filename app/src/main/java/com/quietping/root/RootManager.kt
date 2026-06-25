package com.quietping.root

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of probing the device for the Face 2 deep-capture prerequisites
 * (FACE2_XPOSED_RD.md §2).
 *
 * @property rooted        a usable `su` is present and grants uid 0.
 * @property lsposedActive the LSPosed framework directory exists (implies rooted).
 */
data class RootStatus(
    val rooted: Boolean,
    val lsposedActive: Boolean,
)

/**
 * Detects whether the device can host Face 2 — the in-process Xposed/LSPosed hook that
 * renders deleted chat messages inside the target app's own UI.
 *
 * Face 2 cannot create its prerequisites; it only rides on an existing rooted +
 * LSPosed-flashed device. This manager is Face 1's gate for that capability: when
 * [status] reports not-rooted (or LSPosed inactive), the deep-capture toggle stays
 * greyed out and capture falls back to the normal sandboxed observers.
 *
 * Everything here is best-effort and **fails safe**: any binary, process, or permission
 * failure is swallowed and reported as the absence of the capability — it never crashes
 * and never assumes root it cannot prove.
 */
@Singleton
class RootManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Probe root + LSPosed off the main thread. [RootStatus.lsposedActive] is always
     * `false` when not rooted (the LSPosed dirs live under root-only `/data/adb`).
     */
    suspend fun status(): RootStatus = withContext(Dispatchers.IO) {
        val rooted = isRooted()
        val lsposed = rooted && isLsposedActive()
        RootStatus(rooted = rooted, lsposedActive = lsposed)
    }

    /**
     * True only if a `su` binary exists on a common PATH dir **and** `su -c id` actually
     * grants uid 0. Presence of the binary alone is not enough — an unrooted/denied device
     * can still ship a stub `su`, so we require a real uid-0 grant.
     */
    private fun isRooted(): Boolean {
        if (!suBinaryExists()) return false
        return runSu("id")?.let { out ->
            out.contains("uid=0") || out.contains("uid=0(root)")
        } ?: false
    }

    /** Looks for a `su` binary in the usual root install locations. */
    private fun suBinaryExists(): Boolean = SU_PATHS.any { dir ->
        try {
            File(dir, "su").exists()
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * True if the LSPosed framework is installed (its module dir exists under root-only
     * storage). Checked via `su` since the paths are unreadable without root. Only meaningful
     * when already [isRooted]; the caller guarantees that.
     */
    private fun isLsposedActive(): Boolean {
        // LSPosed (Zygisk) keeps its daemon dir here; fall back to the Magisk modules dir.
        return dirExistsViaSu(LSPOSED_DIR) || dirExistsViaSu(MAGISK_MODULES_DIR)
    }

    private fun dirExistsViaSu(path: String): Boolean =
        runSu("ls -d $path")?.let { out ->
            out.contains(path) && !out.contains("No such file")
        } ?: false

    /**
     * Run `su -c <cmd>` with a short timeout and return combined stdout, or `null` if `su`
     * is unavailable, denied, or times out. Catches everything — a failed probe is just an
     * absent capability, never a crash.
     */
    private fun runSu(cmd: String): String? = try {
        val process = ProcessBuilder("su", "-c", cmd)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(SU_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            null
        } else if (process.exitValue() != 0) {
            null
        } else {
            process.inputStream.bufferedReader().use { it.readText() }
        }
    } catch (t: Throwable) {
        null
    }

    private companion object {
        /** Common locations a `su` binary is installed by root solutions. */
        val SU_PATHS = listOf(
            "/system/bin",
            "/system/xbin",
            "/sbin",
            "/su/bin",
            "/system/sbin",
            "/vendor/bin",
            "/data/local/xbin",
            "/data/local/bin",
        )

        /** LSPosed (Zygisk) daemon directory — root-only. */
        const val LSPOSED_DIR = "/data/adb/lspd"

        /** Magisk modules directory — LSPosed installs as a Magisk module here. */
        const val MAGISK_MODULES_DIR = "/data/adb/modules"

        /** Keep root probes snappy so [status] never hangs the caller. */
        const val SU_TIMEOUT_SECONDS = 2L
    }
}
