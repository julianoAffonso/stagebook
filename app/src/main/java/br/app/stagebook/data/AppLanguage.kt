package br.app.stagebook.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** App language. [SYSTEM] = the device's (falls back to English, the base, if there's no translation). */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    PT("pt-BR"),
    EN("en"),
}

/**
 * Language choice, saved on the device and applied before any screen (`attachBaseContext` of the app and the Activity):
 * kept outside the library, which only loads later. English is the base (`values`) and Brazilian Portuguese lives in
 * `values-pt-rBR`; each new translation is a `values-<tag>` and only joins [available] when complete (`LanguagesTest`
 * checks). How to translate: `docs/traduzir.md`.
 */
object Languages {
    /** The languages offered in settings. Translating = creating `values-<tag>/strings.xml` and adding it here. */
    val available: List<AppLanguage> = listOf(AppLanguage.SYSTEM, AppLanguage.PT, AppLanguage.EN)

    private const val PREFS = "lingua"
    private const val KEY = "lingua"

    fun get(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return AppLanguage.entries.firstOrNull { it.tag == tag }?.takeIf { it in available } ?: AppLanguage.SYSTEM
    }

    fun set(context: Context, language: AppLanguage) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY, language.tag) }

    /** The context with the chosen language (the same one, if it's the device's). */
    // the warning is about Play bundles split by language: Stagebook ships as a whole APK (GitHub Releases)
    @SuppressLint("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        val language = get(base)
        if (language == AppLanguage.SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
        return base.createConfigurationContext(config)
    }
}
