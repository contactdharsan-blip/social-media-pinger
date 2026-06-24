package com.quietping.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.Emerald400
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.StatusError
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.glass

/**
 * Biometric app-lock gate (PRD §9.1, §11). Shown first when
 * [com.quietping.domain.settings.PrivacySettings.biometricLock] is on. On a
 * successful biometric (or device-credential) check it calls
 * `onNavigate(Dest.Home)`.
 *
 * The AndroidX [BiometricPrompt] requires a [FragmentActivity] host. This screen
 * resolves one from the local [Context]; if the host is not a FragmentActivity
 * (or biometrics are unavailable) it degrades to a clear, explanatory state with
 * a manual retry rather than crashing. Content-only — Scaffold lives in nav.
 */
@Composable
fun AppLockScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    // Whether this device can actually perform a biometric / credential check.
    val canAuthenticate = remember(activity) {
        activity != null && context.canAuthenticate()
    }

    // A reusable launcher that shows the system prompt.
    val showPrompt: () -> Unit = remember(activity) {
        {
            val host = activity
            if (host == null || !context.canAuthenticate()) {
                viewModel.onAuthError("Biometric unlock is unavailable on this device.")
            } else {
                viewModel.onPromptShown()
                host.showBiometricPrompt(
                    onSuccess = { viewModel.onAuthSucceeded() },
                    onError = { msg -> viewModel.onAuthError(msg) },
                    onFailed = { /* a single bad read; keep the prompt open */ }
                )
            }
        }
    }

    // Auto-prompt once when the gate appears and auth is possible.
    LaunchedEffect(canAuthenticate) {
        if (canAuthenticate && uiState.phase == AuthPhase.IDLE) {
            showPrompt()
        }
    }

    // Navigate onward exactly once on success.
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AuthPhase.SUCCESS) {
            onNavigate(Dest.Home)
        }
    }

    LockContent(
        phase = uiState.phase,
        errorText = uiState.errorText,
        canAuthenticate = canAuthenticate,
        onUnlock = showPrompt
    )
}

@Composable
private fun LockContent(
    phase: AuthPhase,
    errorText: String?,
    canAuthenticate: Boolean,
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App-lock emblem.
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .glass(cornerRadius = GlassDefaults.CornerRadiusFull),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "QuietPing is locked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (canAuthenticate) {
                    "Verify it's you to open your private vault and rules."
                } else {
                    "Biometric unlock isn't set up on this device. Enroll a fingerprint, " +
                        "face, or screen lock in system settings, or disable app lock in " +
                        "QuietPing privacy settings."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.95f)
            )

            if (phase == AuthPhase.ERROR && errorText != null) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = StatusError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusError,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            if (canAuthenticate) {
                UnlockButton(
                    text = if (phase == AuthPhase.PROMPTING) "Waiting…" else "Unlock",
                    enabled = phase != AuthPhase.PROMPTING,
                    onClick = onUnlock
                )
            }
        }
    }
}

/** Primary gradient "Unlock" button with a fingerprint glyph. */
@Composable
private fun UnlockButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalQuietPingTheme.current.accent
    val secondary = LocalQuietPingTheme.current.secondary
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(GlassDefaults.CornerRadiusLg),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(GlassDefaults.CornerRadiusLg))
                .background(Brush.linearGradient(listOf(accent, secondary))),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// --- Biometric helpers --------------------------------------------------------

/** Strong or weak biometric, OR device credential (PIN/pattern/password). */
private const val ALLOWED_AUTHENTICATORS =
    Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL

/** True if this device currently has a usable biometric/credential method. */
private fun Context.canAuthenticate(): Boolean =
    BiometricManager.from(this)
        .canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Build and show the system biometric prompt on this [FragmentActivity]. Routes
 * the AndroidX callbacks to the supplied lambdas. Device-credential fallback is
 * enabled, so [setNegativeButtonText] must NOT be set (the two are mutually
 * exclusive in the BiometricPrompt API).
 */
private fun FragmentActivity.showBiometricPrompt(
    onSuccess: () -> Unit,
    onError: (String?) -> Unit,
    onFailed: () -> Unit
) {
    val executor = ContextCompat.getMainExecutor(this)
    val prompt = BiometricPrompt(
        this,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onFailed()
            }
        }
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock QuietPing")
        .setSubtitle("Confirm it's you to continue")
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .setConfirmationRequired(false)
        .build()

    prompt.authenticate(info)
}

/** Walk the [ContextWrapper] chain to find the hosting [FragmentActivity], if any. */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
