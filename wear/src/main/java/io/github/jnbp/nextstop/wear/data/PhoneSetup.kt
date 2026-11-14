package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Opens Next Stop on the paired phone (deep link nextstop://open, declared by the phone
 * app), or when it is not installed there its download page. The watch app works without
 * the phone; this only helps to set the phone up.
 */
object PhoneSetup {
    // TODO: the Play Store listing of Next Stop once it is published; until then this placeholder.
    const val INSTALL_URL = "https://github.com/jnbp/next-stop"
    private const val OPEN_URI = "nextstop://open"

    /** Set once something opened on the phone, so the screen can say "Look at your phone". */
    val opened = MutableStateFlow(false)

    /** Opens the app on the phone when [installed], else (or when that fails) [INSTALL_URL]. */
    fun open(context: Context, installed: Boolean) {
        val app = context.applicationContext
        val uri = if (installed) OPEN_URI else INSTALL_URL
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(uri))
        try {
            // A null node: from a watch the activity starts on the paired phone.
            val future = RemoteActivityHelper(app).startRemoteActivity(intent, null)
            future.addListener(
                {
                    try {
                        future.get()
                        opened.value = true
                    } catch (e: Exception) {
                        Log.d(LOG_TAG, "phone setup: $uri failed: $e")
                        if (installed) open(app, installed = false)
                    }
                },
                ContextCompat.getMainExecutor(app),
            )
        } catch (e: Exception) {
            Log.d(LOG_TAG, "phone setup: cannot reach the phone: $e")
        }
    }
}
