package com.quietping.domain.icon

/**
 * Switches the launcher icon between bundled `<activity-alias>` variants via
 * PackageManager component enabling. The disguised "Stealth" alias doubles as a
 * privacy feature for the Vault.
 *
 * Supported aliases: "Default", "Mono", "Stealth".
 */
interface IconSwitcher {

    /** The aliases this device build offers, in display order. */
    fun available(): List<String>

    /** The currently enabled alias. */
    fun current(): String

    /**
     * Enable the [alias] component and disable the others. Triggers a brief
     * launcher refresh (communicate this in-app).
     */
    fun switch(alias: String)
}
