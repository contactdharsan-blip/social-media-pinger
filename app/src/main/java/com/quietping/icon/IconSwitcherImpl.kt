package com.quietping.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.quietping.domain.icon.IconSwitcher
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [IconSwitcher] backed by `PackageManager.setComponentEnabledSetting` over the
 * launcher `<activity-alias>` entries declared in the manifest (PRD 6E).
 *
 * Exactly one alias is enabled at a time; switching enables the chosen alias and
 * disables the others with [PackageManager.DONT_KILL_APP] so the process survives
 * the launcher refresh. The "Stealth" alias is the disguised privacy variant.
 *
 * The active alias is mirrored into [SettingsRepository] so the rest of the app
 * (and the next launch) can read the user's choice without querying PackageManager.
 */
@Singleton
class IconSwitcherImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : IconSwitcher {

    /** Detached scope for the fire-and-forget persistence from a non-suspend API. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val packageManager: PackageManager get() = context.packageManager
    private val appPackage: String get() = context.packageName

    /** Map of alias display name -> fully-qualified manifest component name. */
    private val aliasComponents: Map<String, String> = linkedMapOf(
        ALIAS_DEFAULT to "$appPackage.alias.Default",
        ALIAS_MONO to "$appPackage.alias.Mono",
        ALIAS_STEALTH to "$appPackage.alias.Stealth"
    )

    override fun available(): List<String> = aliasComponents.keys.toList()

    /**
     * The currently enabled alias. The Default alias ships enabled in the manifest,
     * so a component reporting [PackageManager.COMPONENT_ENABLED_STATE_DEFAULT] for
     * Default still counts as enabled; the non-default aliases must be explicitly
     * enabled to count. Falls back to [ALIAS_DEFAULT] when nothing else matches.
     */
    override fun current(): String {
        // An explicitly-enabled non-default alias wins.
        aliasComponents.forEach { (alias, component) ->
            if (alias == ALIAS_DEFAULT) return@forEach
            if (isExplicitlyEnabled(component)) return alias
        }
        // Otherwise: Default is active unless it was explicitly disabled.
        val defaultComponent = aliasComponents.getValue(ALIAS_DEFAULT)
        return if (isExplicitlyDisabled(defaultComponent)) {
            // Default disabled but no other enabled -> first non-disabled, else Default.
            aliasComponents.keys.firstOrNull { alias ->
                !isExplicitlyDisabled(aliasComponents.getValue(alias))
            } ?: ALIAS_DEFAULT
        } else {
            ALIAS_DEFAULT
        }
    }

    /**
     * Enable [alias] and disable the rest. Unknown aliases are ignored (no-op) so a
     * stale/persisted value can never crash the launcher swap.
     */
    override fun switch(alias: String) {
        val target = aliasComponents[alias] ?: return
        aliasComponents.forEach { (name, component) ->
            val newState =
                if (component == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            setState(component, newState)
            // Persisted name is the display alias, not the component path.
            if (component == target) persistActive(name)
        }
    }

    private fun setState(component: String, newState: Int) {
        packageManager.setComponentEnabledSetting(
            ComponentName(appPackage, component),
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun isExplicitlyEnabled(component: String): Boolean =
        runCatching {
            packageManager.getComponentEnabledSetting(ComponentName(appPackage, component))
        }.getOrNull() == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun isExplicitlyDisabled(component: String): Boolean =
        runCatching {
            packageManager.getComponentEnabledSetting(ComponentName(appPackage, component))
        }.getOrNull() == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun persistActive(alias: String) {
        scope.launch { settingsRepository.setActiveIconAlias(alias) }
    }

    companion object {
        const val ALIAS_DEFAULT: String = "Default"
        const val ALIAS_MONO: String = "Mono"
        const val ALIAS_STEALTH: String = "Stealth"
    }
}
