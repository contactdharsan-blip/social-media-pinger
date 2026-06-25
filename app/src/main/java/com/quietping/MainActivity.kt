package com.quietping

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quietping.domain.alerts.AlertDispatcherImpl
import com.quietping.domain.settings.SettingsRepository
import com.quietping.domain.settings.ThemeSettings
import com.quietping.ui.lock.AppLockScreen
import com.quietping.ui.nav.Dest
import com.quietping.ui.nav.QuietPingNavGraph
import com.quietping.ui.theme.BgPrimary
import com.quietping.ui.theme.QuietPingTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single Activity hosting the entire Jetpack Compose UI (Clean Architecture
 * + MVVM, unidirectional data flow). It is the `targetActivity` for every launcher
 * `<activity-alias>`; the disguised/mono/default icons all resolve here.
 *
 * It extends [FragmentActivity] (not the bare `ComponentActivity`) because the
 * AndroidX [androidx.biometric.BiometricPrompt] used by [AppLockScreen] requires a
 * `FragmentActivity` host — `AppLockScreen` resolves its host from the `Context`,
 * and a plain `ComponentActivity` would make biometric unlock permanently degrade.
 * `enableEdgeToEdge()` / `setContent {}` still apply since `FragmentActivity` is
 * itself an `androidx.activity.ComponentActivity`.
 *
 * [AndroidEntryPoint] enables injection. The theme and nav graph are owned by the
 * UI layer ([QuietPingTheme] / [QuietPingNavGraph]); this Activity wires them and
 * adds the biometric app-lock gate.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /**
     * The thread an alert tap wants to open, or null. Backed by Compose snapshot
     * state so a tap arriving via [onNewIntent] while the app is running recomposes
     * the nav graph and re-fires its deep-link effect. Read once and cleared by the
     * graph (see [QuietPingNavGraph]'s `onDeepLinkConsumed`).
     */
    private var deepLinkThreadId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLinkThreadId = threadIdFrom(intent)
        applyScreenshotBlock()
        setContent {
            // Live theme so the Appearance screen's persisted accent / glass /
            // motion choices drive the whole app (null until loaded -> defaults).
            val themeSettings by produceState<ThemeSettings?>(
                initialValue = null,
                settingsRepository
            ) {
                settingsRepository.theme.collect { value = it }
            }
            QuietPingTheme(themeSettings = themeSettings) {
                AppLockGate(settingsRepository) {
                    QuietPingNavGraph(
                        deepLinkThreadId = deepLinkThreadId,
                        onDeepLinkConsumed = { deepLinkThreadId = null }
                    )
                }
            }
        }
    }

    /**
     * Tap on an alert while the process is already alive: singleTop delivers it here
     * instead of recreating the Activity. Replace the stored intent and re-read the
     * target so the running nav graph navigates to the new thread.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkThreadId = threadIdFrom(intent)
    }

    /**
     * Keep the window's FLAG_SECURE in sync with the screenshot-block privacy setting
     * (Bundle 3). FLAG_SECURE blocks screenshots, screen recording, and hides the app
     * from the recents thumbnail — applied app-wide since this is a single-Activity app.
     */
    private fun applyScreenshotBlock() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.privacy
                    .map { it.screenshotBlock }
                    .distinctUntilChanged()
                    .collect { secure ->
                        if (secure) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
            }
        }
    }

    /** The conversation id an alert PendingIntent encoded, or null for a plain launch. */
    private fun threadIdFrom(intent: Intent?): Long? =
        intent
            ?.takeIf { it.hasExtra(AlertDispatcherImpl.EXTRA_CONVERSATION_ID) }
            ?.getLongExtra(AlertDispatcherImpl.EXTRA_CONVERSATION_ID, -1L)
            ?.takeIf { it >= 0L }
}

/**
 * Biometric app-lock gate (PRD §9.1, §11).
 *
 * The nav graph starts at onboarding and has no lock route, so gating is applied
 * here instead of inside the graph. The lock-enabled flag is read as a tri-state
 * via [produceState] (null = still loading) so protected content is **never**
 * flashed before the setting is known — a fresh process stays on the app
 * background for the one frame it takes to resolve [SettingsRepository.privacy].
 *
 * When lock is enabled and the process has not yet been unlocked, [AppLockScreen]
 * is shown over everything. The screen drives its own [androidx.biometric.BiometricPrompt]
 * and, on success, calls `onNavigate(Dest.Home)`, which we treat purely as an
 * "unlocked" signal; the real [content] then renders. The unlocked flag survives
 * configuration changes ([rememberSaveable], so a rotation does not re-prompt) but
 * is not persisted, so relaunching the process re-locks.
 */
@Composable
private fun AppLockGate(
    settingsRepository: SettingsRepository,
    content: @Composable () -> Unit
) {
    val lockEnabled by produceState<Boolean?>(initialValue = null, settingsRepository) {
        settingsRepository.privacy
            .map { it.biometricLock }
            .collect { value = it }
    }
    var unlocked by rememberSaveable { mutableStateOf(false) }

    when {
        // Lock state not resolved yet: hold on the app background (no content flash).
        lockEnabled == null -> {
            Box(Modifier.fillMaxSize().background(BgPrimary))
        }
        // Lock enabled and not yet unlocked this session: show the biometric gate.
        lockEnabled == true && !unlocked -> {
            AppLockScreen(
                onNavigate = { dest -> if (dest == Dest.Home) unlocked = true },
                onBack = { /* gate is the root; nothing to pop back to */ }
            )
        }
        else -> content()
    }
}
