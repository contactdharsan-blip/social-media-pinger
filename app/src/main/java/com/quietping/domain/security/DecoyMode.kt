package com.quietping.domain.security

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-global flag: true when the current session was unlocked with the decoy PIN
 * rather than the real biometric. While active, content surfaces (the Vault) render
 * an empty store so a coerced unlock reveals nothing.
 *
 * Deliberately not persisted — it resets when the process dies (i.e. a real relaunch
 * starts in the normal locked state). A singleton object so the lock screen, the
 * ViewModels, and the capture layer all observe the same flag without DI plumbing.
 */
object DecoyMode {
    private val active = AtomicBoolean(false)

    /** Whether the session is currently in decoy (empty-vault) mode. */
    val isActive: Boolean
        get() = active.get()

    /** Enter decoy mode (a decoy-PIN unlock). */
    fun enter() = active.set(true)

    /** Clear decoy mode (e.g. a fresh real unlock). */
    fun clear() = active.set(false)
}
