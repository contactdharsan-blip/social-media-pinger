package com.quietping.ui.nav

/**
 * Navigation destinations for the single-Activity Compose nav graph (PRD §9.1).
 *
 * Each [Dest] exposes a [route] pattern for `NavHost` registration. Destinations
 * with arguments additionally provide a builder (`createRoute`) that fills a
 * concrete route, plus public arg-key constants for `navArgument` wiring.
 */
sealed interface Dest {

    /** The route pattern this destination is registered under in the nav graph. */
    val route: String

    /** Stepped permission onboarding. */
    data object Onboarding : Dest {
        override val route: String = "onboarding"
    }

    /** Biometric gate shown before content when app lock is enabled. */
    data object AppLock : Dest {
        override val route: String = "app_lock"
    }

    /** Home dashboard: per-app toggle cards + live match feed (bottom-nav root). */
    data object Home : Dest {
        override val route: String = "home"
    }

    /** Message Vault: conversation list (bottom-nav root). */
    data object Vault : Dest {
        override val route: String = "vault"
    }

    /** Captured-media gallery: a flat grid of files held in the on-device media vault. */
    data object VaultMedia : Dest {
        override val route: String = "vault_media"
    }

    /** A single Vault conversation thread, keyed by [ARG_CONVERSATION_ID]. */
    data object VaultThread : Dest {
        const val ARG_CONVERSATION_ID: String = "conversationId"
        override val route: String = "vault_thread/{$ARG_CONVERSATION_ID}"

        /** Build a concrete route to the thread for [conversationId]. */
        fun createRoute(conversationId: Long): String = "vault_thread/$conversationId"
    }

    /** Per-app rule list (bottom-nav root). */
    data object Rules : Dest {
        override val route: String = "rules"
    }

    /**
     * Rule editor. [ARG_RULE_ID] is optional: a positive id edits an existing
     * rule; [NEW_RULE_ID] (the absence sentinel) creates a new rule.
     */
    data object RuleEditor : Dest {
        const val ARG_RULE_ID: String = "ruleId"

        /** Sentinel passed when creating a new rule (no existing id). */
        const val NEW_RULE_ID: Long = -1L

        override val route: String = "rule_editor?$ARG_RULE_ID={$ARG_RULE_ID}"

        /**
         * Build a concrete route. Pass an existing [ruleId] to edit, or null to
         * create a new rule (encoded as [NEW_RULE_ID]).
         */
        fun createRoute(ruleId: Long? = null): String =
            "rule_editor?$ARG_RULE_ID=${ruleId ?: NEW_RULE_ID}"
    }

    /** Per-condition alert settings: channel, sound preset, vibration, DND. */
    data object AlertSettings : Dest {
        override val route: String = "alert_settings"
    }

    /** Appearance settings: app-icon grid + live-preview theme controls. */
    data object Appearance : Dest {
        override val route: String = "appearance"
    }

    /** Privacy & lock settings: biometric lock, retention window, purge now. */
    data object PrivacyLock : Dest {
        override val route: String = "privacy_lock"
    }

    /** Deep capture (root/LSPosed): per-app in-process hook toggles. */
    data object DeepCapture : Dest {
        override val route: String = "deep_capture"
    }

    companion object {
        /** The bottom-navigation root destinations, in display order. */
        val bottomNavRoots: List<Dest> = listOf(Home, Vault, Rules, AlertSettings)
    }
}
