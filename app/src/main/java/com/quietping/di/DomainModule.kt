package com.quietping.di

import android.content.Context
import com.quietping.domain.alerts.AlertDispatcher
import com.quietping.domain.alerts.AlertDispatcherImpl
import com.quietping.domain.icon.IconSwitcher
import com.quietping.domain.parser.InstagramParser
import com.quietping.domain.parser.MessageParser
import com.quietping.domain.parser.MessengerParser
import com.quietping.domain.parser.SmsParser
import com.quietping.domain.parser.WhatsAppParser
import com.quietping.domain.repo.MatchRepository
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.rules.RuleEngine
import com.quietping.domain.rules.RuleEngineImpl
import com.quietping.domain.vault.VaultManager
import com.quietping.domain.vault.VaultManagerImpl
import com.quietping.icon.IconSwitcherImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * `@Binds` half of the domain layer: bind interfaces whose implementations have an
 * `@Inject` constructor.
 *
 * Only [IconSwitcher] qualifies here — [com.quietping.icon.IconSwitcherImpl] is
 * `@Inject`/`@Singleton`. The remaining domain impls (rule engine, vault, alert
 * dispatcher) have plain constructors and are built in [DomainProvidersModule].
 *
 * Kept as a distinct `abstract class` from the `object` provider module so each
 * module is homogeneous (`@Binds` vs `@Provides`) — the most robust layout across
 * Dagger versions, avoiding any companion-object multibinding ambiguity.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindIconSwitcher(impl: IconSwitcherImpl): IconSwitcher
}

/**
 * `@Provides` half of the domain layer: construct the impls with non-`@Inject`
 * constructors, and contribute the per-app parsers into a `Set<MessageParser>`.
 *
 * The engine/vault/dispatcher are `@Singleton` so their internal caches (the
 * engine's compiled-automaton cache, the dispatcher's notification channels) are
 * shared process-wide. Parsers are stateless strategies; each is contributed
 * `@IntoSet` so [com.quietping.capture.CapturePipeline] (which injects
 * `Set<@JvmSuppressWildcards MessageParser>`) receives all four.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainProvidersModule {

    @Provides
    @Singleton
    fun provideRuleEngine(): RuleEngine = RuleEngineImpl()

    @Provides
    @Singleton
    fun provideVaultManager(
        messageRepository: MessageRepository
    ): VaultManager = VaultManagerImpl(messageRepository)

    @Provides
    @Singleton
    fun provideAlertDispatcher(
        @ApplicationContext context: Context,
        matchRepository: MatchRepository,
        settingsRepository: com.quietping.domain.settings.SettingsRepository
    ): AlertDispatcher = AlertDispatcherImpl(
        context = context,
        matchRepository = matchRepository,
        settingsRepository = settingsRepository
    )

    // ---- Message parsers contributed into the Set<MessageParser> ----

    @Provides
    @IntoSet
    fun provideWhatsAppParser(): MessageParser = WhatsAppParser()

    @Provides
    @IntoSet
    fun provideInstagramParser(): MessageParser = InstagramParser()

    @Provides
    @IntoSet
    fun provideMessengerParser(): MessageParser = MessengerParser()

    @Provides
    @IntoSet
    fun provideSmsParser(): MessageParser = SmsParser()
}
