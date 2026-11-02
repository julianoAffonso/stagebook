package br.app.stagebook.data

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

/** "Not now" for the new app version: the same version is only offered again the next day. */
@Singleton
class UpdateOrder @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("versao_nova", Context.MODE_PRIVATE)
    private val deferred = MutableStateFlow(-1L)   // app version put off in this launch

    internal var today: () -> String = { SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()) }

    /** "Not now" for this version today (also holds after reopening the app). */
    fun declinedToday(code: Long) = prefs.getLong(KEY_CODE, -1) == code && prefs.getString(KEY_DAY, "") == today()

    /** The user put off app version [code]; [remember] = "Not now" (until tomorrow). */
    fun defer(code: Long, remember: Boolean) {
        if (remember) prefs.edit { putLong(KEY_CODE, code); putString(KEY_DAY, today()) }
        if (code > deferred.value) deferred.value = code
    }

    private companion object {
        const val KEY_CODE = "recusada"
        const val KEY_DAY = "recusada_em"
    }
}
