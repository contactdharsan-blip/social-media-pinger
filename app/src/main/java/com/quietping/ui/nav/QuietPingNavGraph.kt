package com.quietping.ui.nav

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.quietping.ui.settings.PrivacyLockScreen
import com.quietping.ui.vault.VaultScreen
import com.quietping.ui.vault.VaultThreadScreen
import com.quietping.ui.theme.GlassDefaults
import com.quietping.ui.theme.LocalQuietPingTheme
import com.quietping.ui.theme.MotionTokens
import com.quietping.ui.theme.TextTertiary
import com.quietping.ui.theme.glass

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
fun QuietPingNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { GlassBottomBar(navController) }
    ) { scaffoldPadding ->
        QuietPingNavHost(
            navController = navController,
            contentPadding = scaffoldPadding
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
    contentPadding: PaddingValues
) {
    // Translates a (parameter-less) Dest into a navController call. Parameterized
    // destinations are reached via the typed helpers below, not this lambda.
    val navigate: (Dest) -> Unit = { dest -> navController.navigateTo(dest) }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Dest.Onboarding.route,
        modifier = Modifier.fillMaxSize()
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

        // ---- Parameterized / detail routes ----
        composable(
            route = Dest.VaultThread.route,
            arguments = listOf(
                navArgument(Dest.VaultThread.ARG_CONVERSATION_ID) {
                    type = NavType.LongType
                }
            )
        ) {
            VaultThreadScreen(onNavigate = navigate, onBack = back)
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
            RuleEditorScreen(onNavigate = navigate, onBack = back)
        }

        // ---- Settings sub-screens (full-screen) ----
        composable(Dest.Appearance.route) {
            AppearanceScreen(onNavigate = navigate, onBack = back)
        }
        composable(Dest.PrivacyLock.route) {
            PrivacyLockScreen(onNavigate = navigate, onBack = back)
        }
    }
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
 * The frosted glass bottom navigation bar (DESIGN.md glass surface + spring motion).
 * Shown only while the current route is one of the four [Dest.bottomNavRoots];
 * full-screen flows render without it.
 */
@Composable
private fun GlassBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val onRoot = Dest.bottomNavRoots.any { it.route == currentRoute }
    if (!onRoot) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp)
            .glass(
                intensity = LocalQuietPingTheme.current.glassIntensity,
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
