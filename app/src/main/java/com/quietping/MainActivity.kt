package com.quietping

import android.os.Bundle
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
import com.quietping.domain.settings.SettingsRepository
import com.quietping.domain.settings.ThemeSettings
import com.quietping.ui.lock.AppLockScreen
import com.quietping.ui.nav.Dest
import com.quietping.ui.nav.QuietPingNavGraph
import com.quietping.ui.theme.BgPrimary
import com.quietping.ui.theme.QuietPingTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                    QuietPingNavGraph()
                }
            }
        }
    }
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
