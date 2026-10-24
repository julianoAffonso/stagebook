package com.juliano.stagebook.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordem entre as duas atualizações: a versão nova do app vem antes das músicas (o app novo junta as
 * músicas com o código mais novo, e o usuário não revisa duas vezes). As músicas esperam até o usuário
 * deixar a versão do app para depois ("Agora não", ou o download falhar); instalar encerra o app e a
 * versão nova faz a das músicas. E instalar espera uma gravação das músicas em andamento terminar.
 */
@Singleton
class UpdateOrder @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("versao_nova", Context.MODE_PRIVATE)
    private val deferred = MutableStateFlow(-1L)   // versão do app deixada para depois nesta abertura
    private val syncBusy = MutableStateFlow(false)

    internal var today: () -> String = { SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()) }

    /** "Agora não" para esta versão hoje (vale também depois de reabrir o app). */
    fun declinedToday(code: Long) = prefs.getLong(KEY_CODE, -1) == code && prefs.getString(KEY_DAY, "") == today()

    /** O usuário deixou a versão [code] do app para depois; [remember] = "Agora não" (até amanhã). */
    fun defer(code: Long, remember: Boolean) {
        if (remember) prefs.edit { putLong(KEY_CODE, code); putString(KEY_DAY, today()) }
        if (code > deferred.value) deferred.value = code
    }

    /** As músicas esperam a decisão sobre a versão [code] do app (já recusada hoje: segue). */
    suspend fun awaitAppDecision(code: Long) {
        if (declinedToday(code)) return
        deferred.first { it >= code }
    }

    fun syncBusy(busy: Boolean) { syncBusy.value = busy }

    suspend fun awaitSyncIdle() { syncBusy.first { !it } }

    private companion object {
        const val KEY_CODE = "recusada"
        const val KEY_DAY = "recusada_em"
    }
}
