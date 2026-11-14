package io.github.jnbp.nextstop.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.FavoritePlace
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.shortStopName

/**
 * The shortcuts of the launcher icon: "Departures here" is static (res/xml/shortcuts.xml);
 * home and work (when set) and up to [MAX_RECENT] recent destinations are dynamic and follow
 * the places of the app. A stop can also be pinned to the home screen. Every shortcut is a
 * link of the app (see [AppLinks]), sent to [MainActivity] of this very package.
 */
object AppShortcuts {
    private const val ID_HOME = "home"
    private const val ID_WORK = "work"
    private const val PREFIX_RECENT = "to:"
    private const val PREFIX_STOP = "stop:"
    private const val MAX_RECENT = 2

    // The static shortcut takes one place of those an activity may show.
    private const val STATIC_COUNT = 1

    private fun linkIntent(context: Context, link: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun routeShortcut(context: Context, id: String, label: String, longLabel: String, to: Stop, icon: Int, rank: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(linkIntent(context, routeLinkText(null, to)))
            .setRank(rank)
            .build()

    /**
     * Sets the dynamic shortcuts: home, work and the newest places of [history] that are
     * neither. Failures (a launcher without shortcuts, the rate limit) leave them as they were.
     */
    fun update(context: Context, favorites: List<FavoritePlace>, history: List<Stop>) {
        val home = favorites.firstOrNull { it.icon == PlaceIcon.Home }?.place
        val work = favorites.firstOrNull { it.icon == PlaceIcon.Work }?.place
        val shortcuts = mutableListOf<ShortcutInfoCompat>()
        if (home != null) {
            val label = context.getString(R.string.launcher_home)
            shortcuts += routeShortcut(context, ID_HOME, label, label, home, R.drawable.ic_shortcut_home, shortcuts.size)
        }
        if (work != null) {
            val label = context.getString(R.string.launcher_work)
            shortcuts += routeShortcut(context, ID_WORK, label, label, work, R.drawable.ic_shortcut_work, shortcuts.size)
        }
        val taken = setOfNotNull(home?.id, work?.id)
        history
            .filter { it.id !in taken && it.id != CURRENT_LOCATION_ID && (it.position != null || it.kind == PlaceKind.Stop) }
            .take(MAX_RECENT)
            .forEach { place ->
                val name = place.name.shortStopName()
                shortcuts += routeShortcut(
                    context,
                    PREFIX_RECENT + place.id,
                    name,
                    context.getString(R.string.launcher_to_place, name),
                    place,
                    R.drawable.ic_shortcut_place,
                    shortcuts.size,
                )
            }
        try {
            val room = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_COUNT).coerceAtLeast(0)
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts.take(room))
        } catch (e: Exception) {
            // No shortcuts this time; the next change of the places tries again.
        }
    }

    /** Whether the launcher can take a stop pinned to the home screen. */
    fun canPin(context: Context): Boolean = try {
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    } catch (e: Exception) {
        false
    }

    /**
     * Asks the launcher to put [stop] on the home screen: a tap opens its departures. The
     * launcher shows its own dialog; false when it cannot take the shortcut.
     */
    fun pinStop(context: Context, stop: Stop): Boolean {
        if (!canPin(context)) return false
        val name = stop.name.shortStopName()
        val shortcut = ShortcutInfoCompat.Builder(context, PREFIX_STOP + stop.id)
            .setShortLabel(name)
            .setLongLabel(context.getString(R.string.launcher_departures_of, name))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_departures))
            .setIntent(linkIntent(context, departuresLinkText(stop.copy(distanceMeters = null))))
            .build()
        return try {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        } catch (e: Exception) {
            false
        }
    }
}
