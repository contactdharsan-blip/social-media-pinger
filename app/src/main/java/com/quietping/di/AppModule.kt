package com.quietping.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.quietping.data.datastore.quietPingSettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Application-wide infrastructure bindings that are neither persistence (see
 * [DatabaseModule]) nor a domain implementation (see [DomainModule] /
 * [RepositoryModule]).
 *
 * Provides:
 *  - the single Preferences [DataStore] backing [com.quietping.data.datastore.SettingsRepositoryImpl];
 *  - an application-scoped [CoroutineScope] (qualified [ApplicationScope]) for
 *    fire-and-forget startup work (channel creation, work scheduling) that must
 *    outlive any one screen but is owned by the process.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * The process-wide settings store. The `by preferencesDataStore` delegate in
     * [com.quietping.data.datastore.quietPingSettingsDataStore] guarantees a single
     * instance per file, so exposing it here as `@Singleton` keeps DI and the
     * delegate in agreement.
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.quietPingSettingsDataStore

    /**
     * A [SupervisorJob]-backed scope on [Dispatchers.Default] for application-level
     * background tasks. A failing child never cancels the scope, so one bad startup
     * task cannot tear down the others.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The background [CoroutineContext] for [com.quietping.capture.CapturePipeline]'s
     * ingestion loop.
     *
     * The pipeline's `@Inject` constructor declares this parameter with a Kotlin
     * default of [Dispatchers.IO], but Dagger ignores Kotlin default values and
     * requires an explicit binding — so we provide exactly that intended default
     * here. IO is correct: every pipeline step (parse -> Room read/write -> rule
     * eval -> notify) is I/O-bound, off the binder/callback threads that produced
     * the event.
     */
    @Provides
    fun provideCapturePipelineDispatcher(): CoroutineContext = Dispatchers.IO
}

/** Qualifies the long-lived, process-scoped [CoroutineScope] from [AppModule]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
