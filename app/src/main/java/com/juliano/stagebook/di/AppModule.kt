package com.juliano.stagebook.di

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

/** Escopo que vive o app inteiro: gravações e buscas que não podem morrer quando a tela fecha. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Uma linha só, de baixa prioridade: trabalho de fundo (sincronia, procurar versão) não rouba o palco do Fire. */
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
            // erro inesperado numa gravação ou na sincronia em segundo plano não pode derrubar o app no palco
            CoroutineExceptionHandler { _, e -> Log.e("stagebook", "erro em segundo plano", e) },
    )

    @Provides
    @Singleton
    @LowPriority
    fun lowPriority(): CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "stagebook-fundo")
    }.asCoroutineDispatcher()
}
