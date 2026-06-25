package com.quietping.ui.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietping.ui.components.GlassButton
import com.quietping.ui.components.GlassButtonStyle
import com.quietping.ui.nav.Dest
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.StatusSuccess
import com.quietping.ui.theme.TextSecondary
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.glass
import com.quietping.ui.theme.motionEnter
import com.quietping.ui.theme.motionExit

/**
 * Stepped permission onboarding (PRD §7 / §9.1). Each step explains *why* the
 * permission is needed in plain language and deep-links to the relevant system
 * screen (or fires the READ_SMS runtime request). Optional steps are skippable;
 * the app degrades to whatever the user grants. "Done" navigates to [Dest.Home].
 *
 * This composable is CONTENT ONLY — the hosting Scaffold lives in the nav graph.
 */
@Composable
fun OnboardingScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val motion = LocalQuietPingTheme.current.motionEnabled

    // READ_SMS runtime request: report the result straight back to the VM.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateGrant(OnboardingStep.SMS_ACCESS, granted)
        if (granted) viewModel.next()
    }

    // Re-check every grant whenever we return to the foreground (e.g. back from a
    // system settings screen) so each step reflects the live OS state.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshGrants(
            mapOf(
                OnboardingStep.NOTIFICATION_ACCESS to context.isNotificationListenerEnabled(),
                OnboardingStep.SMS_ACCESS to context.isReadSmsGranted(),
                OnboardingStep.DND_ACCESS to context.isDndPolicyGranted(),
                OnboardingStep.ACCESSIBILITY_ACCESS to context.isAccessibilityServiceEnabled()
            )
        )
        onPauseOrDispose { }
    }

    val step = uiState.currentStep
    val content = remember(step) { step.copyFor() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))

            // Top bar: back affordance + step progress dots.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (uiState.stepIndex > 0) {
                    GlassIconAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = viewModel::back
                    )
                } else {
                    Spacer(Modifier.size(44.dp))
                }
                StepProgress(
                    current = uiState.stepIndex,
                    total = uiState.stepCount
                )
                Spacer(Modifier.size(44.dp))
            }

            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = uiState.stepIndex,
                transitionSpec = {
                    val duration = if (motion) 320 else 0
                    val forward = targetState >= initialState
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(duration)) { full -> dir * full } + fadeIn(tween(duration)))
                        .togetherWith(
                            slideOutHorizontally(tween(duration)) { full -> -dir * full } + fadeOut(tween(duration))
                        )
                },
                label = "onboarding-step",
                modifier = Modifier.weight(1f)
            ) { _ ->
                StepBody(
                    icon = content.icon,
                    title = content.title,
                    rationale = content.rationale,
                    optional = step.optional,
                    granted = uiState.isCurrentGranted
                )
            }

            // Action area: primary grant CTA + skip/continue.
            OnboardingActions(
                step = step,
                granted = uiState.isCurrentGranted,
                isLast = uiState.isLastStep,
                onGrant = {
                    when (step) {
                        OnboardingStep.NOTIFICATION_ACCESS ->
                            context.launchSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

                        OnboardingStep.SMS_ACCESS ->
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)

                        OnboardingStep.DND_ACCESS ->
                            context.launchSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

                        OnboardingStep.ACCESSIBILITY_ACCESS ->
                            context.launchSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    }
                },
                onContinue = {
                    if (uiState.isLastStep) {
                        viewModel.complete()
                        onNavigate(Dest.Home)
                    } else viewModel.next()
                },
                onSkip = {
                    if (uiState.isLastStep) {
                        viewModel.complete()
                        onNavigate(Dest.Home)
                    } else viewModel.skip()
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The body of a single onboarding step: icon badge, title, rationale, status. */
@Composable
private fun StepBody(
    icon: ImageVector,
    title: String,
    rationale: String,
    optional: Boolean,
    granted: Boolean
) {
    val accent = LocalQuietPingTheme.current.accent
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .glass(cornerRadius = GlassDefaults.CornerRadius),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else icon,
                // The badge icon flips to a check to convey grant state; announce it.
                contentDescription = if (granted) "Permission granted" else null,
                tint = if (granted) StatusSuccess else accent,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        if (optional) {
            PillLabel(text = "Optional")
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = rationale,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f)
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = granted,
            enter = motionEnter(),
            exit = motionExit()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = StatusSuccess,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusSuccess
                )
            }
        }
    }
}

/** Primary grant button plus the secondary continue/skip control. */
@Composable
private fun OnboardingActions(
    step: OnboardingStep,
    granted: Boolean,
    isLast: Boolean,
    onGrant: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (granted) {
            GlassButton(
                text = if (isLast) "Done" else "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                style = GlassButtonStyle.Primary
            )
        } else {
            GlassButton(
                text = grantCta(step),
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth(),
                style = GlassButtonStyle.Primary
            )
            Spacer(Modifier.height(12.dp))
            GlassButton(
                text = if (isLast) {
                    if (step.optional) "Skip & finish" else "Finish for now"
                } else {
                    if (step.optional) "Skip this step" else "Not now"
                },
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                style = GlassButtonStyle.Secondary
            )
        }
    }
}

/** Step progress dots: filled with the live accent up to [current], subtle beyond. */
@Composable
private fun StepProgress(current: Int, total: Int) {
    val accent = LocalQuietPingTheme.current.accent
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // The decorative dots convey progress; announce it as a single label.
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Step ${current + 1} of $total"
        }
    ) {
        repeat(total) { i ->
            val active = i <= current
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (i == current) 24.dp else 8.dp)
                    .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
                    .background(if (active) accent else MaterialTheme.colorScheme.outline)
            )
        }
    }
}

/** A small frosted pill used for the "Optional" marker. */
@Composable
private fun PillLabel(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
            .glass(cornerRadius = GlassDefaults.CornerRadiusFull)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary
        )
    }
}

/** A 44dp frosted, tappable icon button (back affordance). */
@Composable
private fun GlassIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(GlassDefaults.CornerRadiusFull),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.size(44.dp)
    ) {
        Box(
            modifier = Modifier.glass(cornerRadius = GlassDefaults.CornerRadiusFull),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// --- Step copy ---------------------------------------------------------------

private data class StepContent(
    val icon: ImageVector,
    val title: String,
    val rationale: String
)

private fun OnboardingStep.copyFor(): StepContent = when (this) {
    OnboardingStep.NOTIFICATION_ACCESS -> StepContent(
        icon = Icons.Filled.NotificationsActive,
        title = "Allow notification access",
        rationale = "QuietPing reads incoming chat notifications on this device to " +
            "decide when one of your conditions fires. This is the core permission — " +
            "nothing leaves your phone, and the app has no internet access."
    )

    OnboardingStep.SMS_ACCESS -> StepContent(
        icon = Icons.Filled.Sms,
        title = "Read SMS & MMS",
        rationale = "Granting SMS access lets QuietPing watch your local Messages " +
            "store for keywords and VIP senders, and recover texts that get deleted. " +
            "You can skip this and still use chat-app alerts."
    )

    OnboardingStep.DND_ACCESS -> StepContent(
        icon = Icons.Filled.DoNotDisturbOn,
        title = "Override Do Not Disturb",
        rationale = "So a VIP message or your name being mentioned can still ping you " +
            "while Do Not Disturb is on. Only the alerts you mark as urgent will break " +
            "through — everything else stays silent."
    )

    OnboardingStep.ACCESSIBILITY_ACCESS -> StepContent(
        icon = Icons.Filled.Accessibility,
        title = "Wider capture (optional)",
        rationale = "Turning on the QuietPing accessibility service extends coverage to " +
            "messages that were silenced or never notified — scoped only to your chat " +
            "apps. Entirely optional; skip it if you prefer the lighter setup."
    )
}

private fun grantCta(step: OnboardingStep): String = when (step) {
    OnboardingStep.NOTIFICATION_ACCESS -> "Open notification settings"
    OnboardingStep.SMS_ACCESS -> "Grant SMS access"
    OnboardingStep.DND_ACCESS -> "Open Do Not Disturb access"
    OnboardingStep.ACCESSIBILITY_ACCESS -> "Open accessibility settings"
}

// --- System permission checks & deep-links -----------------------------------

/** Launch a system settings screen, scoped to this app where the action allows it. */
private fun Context.launchSettings(action: String) {
    val intent = Intent(action).apply {
        // For policy-access screens, target our package directly where supported.
        if (action == Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) {
            data = Uri.fromParts("package", packageName, null)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
        .onFailure {
            // Fall back to the generic action with no package data.
            runCatching {
                startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
}

/** True if QuietPing's NotificationListenerService is enabled for this user. */
private fun Context.isNotificationListenerEnabled(): Boolean {
    val enabled = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabled.split(':').any { flat ->
        ComponentName.unflattenFromString(flat)?.packageName == packageName
    }
}

/** True if READ_SMS is currently granted. */
private fun Context.isReadSmsGranted(): Boolean =
    checkSelfPermissionCompat(Manifest.permission.READ_SMS)

/** True if the app holds Do Not Disturb (notification policy) access. */
private fun Context.isDndPolicyGranted(): Boolean {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false
    return nm.isNotificationPolicyAccessGranted
}

/**
 * True if any QuietPing accessibility service is enabled. Matches on package name
 * so it stays correct regardless of the concrete service class the capture agent
 * registers.
 */
private fun Context.isAccessibilityServiceEnabled(): Boolean {
    val enabled = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { flat ->
        ComponentName.unflattenFromString(flat)?.packageName == packageName
    }
}

/** minSdk 26 already exposes Context.checkSelfPermission; centralized for clarity. */
private fun Context.checkSelfPermissionCompat(permission: String): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
