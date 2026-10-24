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

/** "Agora não" para a versão nova do app: a mesma versão só volta a ser oferecida no dia seguinte. */
@Singleton
class UpdateOrder @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("versao_nova", Context.MODE_PRIVATE)
    private val deferred = MutableStateFlow(-1L)   // versão do app deixada para depois nesta abertura

    internal var today: () -> String = { SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()) }

    /** "Agora não" para esta versão hoje (vale também depois de reabrir o app). */
    fun declinedToday(code: Long) = prefs.getLong(KEY_CODE, -1) == code && prefs.getString(KEY_DAY, "") == today()

    /** O usuário deixou a versão [code] do app para depois; [remember] = "Agora não" (até amanhã). */
    fun defer(code: Long, remember: Boolean) {
        if (remember) prefs.edit { putLong(KEY_CODE, code); putString(KEY_DAY, today()) }
        if (code > deferred.value) deferred.value = code
    }

    private companion object {
        const val KEY_CODE = "recusada"
        const val KEY_DAY = "recusada_em"
    }
}
