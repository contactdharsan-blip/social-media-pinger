package com.quietping.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quietping.ui.home.HomeScreen
import com.quietping.ui.lock.AppLockScreen
import com.quietping.ui.onboarding.OnboardingScreen
import com.quietping.ui.rules.RuleEditorScreen
import com.quietping.ui.rules.RulesScreen
import com.quietping.ui.settings.AlertSettingsScreen
import com.quietping.ui.settings.AppearanceScreen
import com.quietping.ui.settings.DeepCaptureScreen
import com.quietping.ui.settings.PrivacyLockScreen
import com.quietping.ui.vault.VaultMediaScreen
import com.quietping.ui.vault.VaultScreen
import com.quietping.ui.vault.VaultThreadScreen
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.glass
import com.quietping.ui.theme.BgPrimary
import com.quietping.ui.theme.GlassFill
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect

/**
 * Single source of truth for in-app navigation (PRD §9.1). One [NavHost] registers
 * every [Dest] route; the four bottom-nav roots ([Dest.bottomNavRoots]) are wrapped
 * in a [Scaffold] with a frosted glass bottom bar, while full-screen flows
 * (onboarding, app-lock, thread, rule editor, the settings sub-screens) render
 * edge-to-edge without the bar.
 *
 * Zero-arg by contract: [com.quietping.MainActivity] calls this directly inside
 * [com.quietping.ui.theme.QuietPingTheme].
 *
 * Screen composables are owned by other modules and bound here by their exact
 * names; each follows the contract
 * `fun <Name>Screen(onNavigate: (Dest) -> Unit, onBack: () -> Unit = {}, viewModel = hiltViewModel())`.
 */
@Composable
fun QuietPingNavGraph(
    deepLinkThreadId: Long? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    // Shared Haze state: the NavHost content is the blur SOURCE; the glass bottom bar
    // samples it (real backdrop blur of whatever scrolls beneath the bar).
    val hazeState = remember { HazeState() }

    // Alert tap → open the originating thread. The effect keys on the id, so a fresh
    // tap (onCreate or onNewIntent) re-runs it; we clear the id via onDeepLinkConsumed
    // so a recomposition or config-change doesn't re-navigate. Because this composes
    // only after the app-lock gate, the jump naturally happens post-unlock.
    val consume by rememberUpdatedState(onDeepLinkConsumed)
    LaunchedEffect(deepLinkThreadId) {
        val threadId = deepLinkThreadId ?: return@LaunchedEffect
        navController.openThreadFromAlert(threadId)
        consume()
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { GlassBottomBar(navController, hazeState) }
    ) { scaffoldPadding ->
        QuietPingNavHost(
            navController = navController,
            contentPadding = scaffoldPadding,
            hazeState = hazeState
        )
    }
}

/**
 * The route table. Split out so the [Scaffold] padding can be threaded into the
 * root destinations' content while modal/full-screen routes ignore the bottom bar.
 */
@Composable
private fun QuietPingNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    hazeState: HazeState
) {
    // Translates a (parameter-less) Dest into a navController call. Parameterized
    // destinations are reached via the typed helpers below, not this lambda.
    val navigate: (Dest) -> Unit = { dest -> navController.navigateTo(dest) }
    val back: () -> Unit = { navController.popBackStack() }

    // Reduced-motion gate (ThemeSettings.motionEnabled). When off, screen changes
    // cross-fade instead of translating — no vestibular motion, cheaper on low-end.
    val motionEnabled = LocalQuietPingTheme.current.motionEnabled

    NavHost(
        navController = navController,
        startDestination = Dest.Onboarding.route,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState),
        enterTransition = { resolveEnter(motionEnabled) },
        exitTransition = { resolveExit(motionEnabled) },
        popEnterTransition = { resolvePopEnter(motionEnabled) },
        popExitTransition = { resolvePopExit(motionEnabled) }
    ) {
        // ---- Full-screen flows (no bottom bar) ----
        composable(Dest.Onboarding.route) {
            OnboardingScreen(onNavigate = navigate, onBack = back)
        }
        composable(Dest.AppLock.route) {
            AppLockScreen(onNavigate = navigate, onBack = back)
        }

        // ---- Bottom-nav roots (padded for the glass bar) ----
        composable(Dest.Home.route) {
            Box(Modifier.padding(contentPadding)) {
                HomeScreen(onNavigate = navigate, onBack = back)
            }
        }
        composable(Dest.Vault.route) {
            Box(Modifier.padding(contentPadding)) {
                VaultScreen(onNavigate = navigate, onBack = back)
            }
        }
        composable(Dest.Rules.route) {
            Box(Modifier.padding(contentPadding)) {
                RulesScreen(onNavigate = navigate, onBack = back)
            }
        }
        composable(Dest.AlertSettings.route) {
            Box(Modifier.padding(contentPadding)) {
                AlertSettingsScreen(onNavigate = navigate, onBack = back)
            }
        }

        // ---- Vault media gallery (full-screen; owns its status-bar inset so the
        // zoom viewer's image can draw full-bleed while the header clears the bar) ----
        composable(Dest.VaultMedia.route) {
            VaultMediaScreen(onNavigate = navigate, onBack = back)
        }

        // ---- Parameterized / detail routes ----
        composable(
            route = Dest.VaultThread.route,
            arguments = listOf(
                navArgument(Dest.VaultThread.ARG_CONVERSATION_ID) {
                    type = NavType.LongType
                }
            )
        ) {
            // Full-screen routes draw edge-to-edge (no Scaffold contentPadding), so
            // they must consume the status-bar inset themselves — otherwise their
            // inline back-header lands under the system status bar, which swallows
            // its touches and makes the back button untappable.
            Box(Modifier.statusBarsPadding()) {
                VaultThreadScreen(onNavigate = navigate, onBack = back)
            }
        }
        composable(
            route = Dest.RuleEditor.route,
            arguments = listOf(
                navArgument(Dest.RuleEditor.ARG_RULE_ID) {
                    type = NavType.LongType
                    defaultValue = Dest.RuleEditor.NEW_RULE_ID
                }
            )
        ) {
            Box(Modifier.statusBarsPadding()) {
                RuleEditorScreen(onNavigate = navigate, onBack = back)
            }
        }

        // ---- Settings sub-screens (full-screen) ----
        composable(Dest.Appearance.route) {
            Box(Modifier.statusBarsPadding()) {
                AppearanceScreen(onNavigate = navigate, onBack = back)
            }
        }
        composable(Dest.PrivacyLock.route) {
            Box(Modifier.statusBarsPadding()) {
                PrivacyLockScreen(onNavigate = navigate, onBack = back)
            }
        }
        composable(Dest.DeepCapture.route) {
            Box(Modifier.statusBarsPadding()) {
                DeepCaptureScreen(onNavigate = navigate, onBack = back)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen transitions (PRD §9.1, DESIGN.md §5 motion). Forward navigation is a
// full-width horizontal SWIPE: the incoming screen slides in from the right edge
// while the outgoing screen slides off to the left in lockstep, like a page being
// pushed across. Back navigation reverses the direction. No cross-fade on the
// swipe — the screens are opaque sheets, so fading would muddy the gesture; both
// move on the same decisive [SwipeSpec] ease (no spring overshoot) so they read as
// one continuous swipe. Lateral moves between the four bottom-nav roots cross-fade
// instead (a sideways slide direction would be ambiguous between tabs). All motion
// is gated by [QuietPingThemeState.motionEnabled] — reduced motion degrades to a
// short fade with no translation.
// ---------------------------------------------------------------------------

/** A short fade used as the reduced-motion fallback and for lateral tab swaps. */
private val FadeSpec = tween<Float>(durationMillis = 150)

/**
 * The swipe travel curve: a decisive, non-overshoot ease over ~300ms. Both the
 * entering and exiting screen use it so they translate the full container width in
 * perfect lockstep — the hallmark of a "swipe" rather than a springy push.
 */
private val SwipeSpec = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)

/** True when [this] back-stack entry is one of the four bottom-nav roots. */
private fun NavBackStackEntry.isBottomRoot(): Boolean =
    Dest.bottomNavRoots.any { it.route == destination.route }

/** Both ends of the transition are bottom-nav roots → a lateral tab swap. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwap(): Boolean =
    initialState.isBottomRoot() && targetState.isBottomRoot()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolveEnter(
    motion: Boolean
): EnterTransition = when {
    !motion || isTabSwap() -> fadeIn(FadeSpec)
    else -> slideIntoContainer(SlideDirection.Start, SwipeSpec)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolveExit(
    motion: Boolean
): ExitTransition = when {
    !motion || isTabSwap() -> fadeOut(FadeSpec)
    else -> slideOutOfContainer(SlideDirection.Start, SwipeSpec)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolvePopEnter(
    motion: Boolean
): EnterTransition = when {
    !motion || isTabSwap() -> fadeIn(FadeSpec)
    else -> slideIntoContainer(SlideDirection.End, SwipeSpec)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.resolvePopExit(
    motion: Boolean
): ExitTransition = when {
    !motion || isTabSwap() -> fadeOut(FadeSpec)
    else -> slideOutOfContainer(SlideDirection.End, SwipeSpec)
}

/**
 * Navigate to [dest]. Parameter-less destinations go straight to their route.
 * The parameterized destinations carry no id at the singleton level, so a bare
 * navigation degrades to a safe variant: [Dest.RuleEditor] opens a *new* rule and
 * [Dest.VaultThread] falls back to the Vault list (callers with a concrete id use
 * [navigateToThread] / [navigateToRuleEditor] instead). Switching to a bottom-nav
 * root pops to the graph start and restores prior state (standard tab behavior).
 */
private fun NavHostController.navigateTo(dest: Dest) {
    when (dest) {
        is Dest.VaultThread -> navigate(Dest.Vault.route)
        is Dest.RuleEditor -> navigateToRuleEditor(null)
        in Dest.bottomNavRoots -> navigate(dest.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        else -> navigate(dest.route)
    }
}

/** Open a specific Vault conversation thread. */
fun NavHostController.navigateToThread(conversationId: Long) {
    navigate(Dest.VaultThread.createRoute(conversationId))
}

/**
 * Deep-link entry from an alert tap. Unlike [navigateToThread] (an in-app push from
 * the Vault list, which already sits beneath it), a notification can land here from
 * any state — including the Onboarding start destination. We first seat the Vault
 * root, then push the thread, so pressing Back exits to the Vault list rather than
 * dumping the user back on onboarding. launchSingleTop avoids stacking duplicate
 * Vault/Thread entries when several alerts are tapped in a row.
 */
fun NavHostController.openThreadFromAlert(conversationId: Long) {
    navigate(Dest.Vault.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    navigate(Dest.VaultThread.createRoute(conversationId)) {
        launchSingleTop = true
    }
}

/** Open the rule editor for [ruleId] (null = create a new rule). */
fun NavHostController.navigateToRuleEditor(ruleId: Long?) {
    navigate(Dest.RuleEditor.createRoute(ruleId))
}

/** A bottom-nav tab descriptor: its root [dest], [icon], and [label]. */
private data class BottomTab(val dest: Dest, val icon: ImageVector, val label: String)

private val bottomTabs: List<BottomTab> = listOf(
    BottomTab(Dest.Home, Icons.Filled.Home, "Home"),
    BottomTab(Dest.Vault, Icons.Filled.Inventory2, "Vault"),
    BottomTab(Dest.Rules, Icons.AutoMirrored.Filled.List, "Rules"),
    BottomTab(Dest.AlertSettings, Icons.Filled.Settings, "Settings")
)

/**
 * The Haze blur style for glass surfaces, derived from the LiquidGlass tokens: the
 * near-black canvas as the blur's base color and the same faint white [GlassFill] as a
 * tint, so the real backdrop blur reads as the existing frosted glass — just live. These
 * four values (base color, tint, blur radius, grain) are the design knob to tune the
 * frost strength. Plain (non-composable) factory: no composition state needed.
 */
private fun liquidGlassHazeStyle(): HazeStyle =
    HazeDefaults.style(
        backgroundColor = BgPrimary,
        tint = HazeTint(GlassFill),
        blurRadius = 24.dp,
        noiseFactor = 0.04f
    )

/**
 * The frosted glass bottom navigation bar (DESIGN.md glass surface + spring motion).
 * Shown only while the current route is one of the four [Dest.bottomNavRoots];
 * full-screen flows render without it.
 */
@Composable
private fun GlassBottomBar(navController: NavHostController, hazeState: HazeState) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val onRoot = Dest.bottomNavRoots.any { it.route == currentRoute }
    if (!onRoot) return

    val glassIntensity = LocalQuietPingTheme.current.glassIntensity
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp)
            // Real backdrop blur of the content scrolling beneath the bar (Haze), rounded
            // to the pill by the preceding clip; the glass fill + lit edge draw on top.
            // blurEnabled rides the user's glass intensity (0 → scrim only; also the low-end
            // degrade path). Haze auto-falls back to a translucent scrim below API 31.
            .clip(RoundedCornerShape(GlassDefaults.CornerRadiusFull))
            .hazeEffect(state = hazeState, style = liquidGlassHazeStyle()) {
                blurEnabled = glassIntensity > 0.01f
            }
            .glass(
                intensity = glassIntensity,
                cornerRadius = GlassDefaults.CornerRadiusFull
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bottomTabs.forEach { tab ->
                val selected = currentDestination?.hierarchy?.any { it.route == tab.dest.route } == true
                BottomTabItem(
                    tab = tab,
                    selected = selected,
                    onClick = {
                        if (!selected) navController.navigateTo(tab.dest)
                    }
                )
            }
        }
    }
}

/** A single bottom-bar tab: accent-tinted, spring-colored selection state. */
@Composable
private fun BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalQuietPingTheme.current.accent
    val tint by animateColorAsState(
        targetValue = if (selected) accent else TextTertiary,
        animationSpec = MotionTokens.signatureSpring(),
        label = "bottomTabTint"
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = MotionTokens.signatureSpring(),
        label = "bottomTabPill"
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 28.dp)
                .background(pillColor, RoundedCornerShape(GlassDefaults.CornerRadiusFull)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
