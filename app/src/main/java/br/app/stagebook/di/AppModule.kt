package br.app.stagebook.di

import android.os.Process
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope that lives as long as the app: saves and fetches that can't die when the screen closes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** A single low-priority thread: background work (sync, version check) doesn't steal the stage from the Fire. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LowPriority

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            // an unexpected error while saving or syncing in the background can't crash the app on stage
            CoroutineExceptionHandler { _, e -> Log.e("stagebook", "background error", e) },
    )

    @Provides
    @Singleton
    @LowPriority
    fun lowPriority(): CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "stagebook-fundo")
    }.asCoroutineDispatcher()
}
