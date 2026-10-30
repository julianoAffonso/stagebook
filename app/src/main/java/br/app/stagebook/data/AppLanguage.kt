package br.app.stagebook.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** Língua do app. [SYSTEM] = a do aparelho (cai no português, a base, se não houver tradução). */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    PT("pt"),
    ES("es"),
    IT("it"),
    EN("en"),
}

/**
 * Escolha da língua, guardada no aparelho e aplicada antes de qualquer tela (`attachBaseContext` do app e da
 * Activity): fica fora da biblioteca, que só carrega depois. Português é a base (`values`); cada tradução é um
 * `values-<tag>` e só entra em [available] quando estiver completa (o `LanguagesTest` confere).
 */
object Languages {
    /** As línguas oferecidas nos ajustes. Traduzir = criar `values-<tag>/strings.xml` e acrescentar aqui. */
    val available: List<AppLanguage> = listOf(AppLanguage.SYSTEM, AppLanguage.PT)

    private const val PREFS = "lingua"
    private const val KEY = "lingua"

    fun get(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return AppLanguage.entries.firstOrNull { it.tag == tag }?.takeIf { it in available } ?: AppLanguage.SYSTEM
    }

    fun set(context: Context, language: AppLanguage) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY, language.tag) }

    /** O contexto com a língua escolhida (o mesmo, se for a do aparelho). */
    // o aviso é sobre pacotes da Play divididos por língua: o Stagebook sai como APK inteiro (GitHub Releases)
    @SuppressLint("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        val language = get(base)
        if (language == AppLanguage.SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
        return base.createConfigurationContext(config)
    }
}
