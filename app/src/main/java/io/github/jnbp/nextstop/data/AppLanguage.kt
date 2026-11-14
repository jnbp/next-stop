package io.github.jnbp.nextstop.data

import android.content.Context
import io.github.jnbp.nextstop.R
import java.util.Locale

/**
 * The language the app shows its texts in, "de" or "en", for the providers to answer in the
 * same one (notices, names of places, directions).
 *
 * Not `Locale.getDefault()`: that is the first language of the system list, while Android
 * picks the app's resources from the whole list. With "English, Deutsch" in the system
 * settings the app has no English-only folder to match the first entry better than its
 * default, falls through to the second and shows German, while the default locale stays
 * English, so the APIs were asked in English. The resources themselves say which language
 * they are in ([R.string.api_language]), so the answer always matches what the app shows.
 *
 * Read through the application context, whose resources Android updates on a change of
 * the configuration; [init] is called where the app starts and where a source is built.
 */
object AppLanguage {
    @Volatile
    private var appContext: Context? = null

    /** Remembers the application context of [context]; cheap to call again. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext ?: context
    }

    /** "de" or "en": the language of the resources, the default locale only before [init]. */
    val code: String
        get() {
            val fromResources = try {
                appContext?.getString(R.string.api_language)
            } catch (e: Exception) {
                null
            }
            return when (fromResources) {
                "de", "en" -> fromResources
                else -> if (Locale.getDefault().language == "de") "de" else "en"
            }
        }

    val german: Boolean get() = code == "de"
}

/** The language of the app's texts, "de" or "en" (see [AppLanguage]). */
fun appLanguage(context: Context): String {
    AppLanguage.init(context)
    return AppLanguage.code
}
