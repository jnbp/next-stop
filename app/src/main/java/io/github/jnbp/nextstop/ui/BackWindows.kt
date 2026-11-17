package io.github.jnbp.nextstop.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/**
 * Who works out the progress of a back gesture in the window of MainActivity, and a look at
 * the app's windows for the back log.
 *
 * Since Android 15 the system marks the window of an activity (its DecorView) with the hidden
 * private flag APP_PROGRESS_GENERATION_ALLOWED (ViewRootImpl.setView). With it the system
 * neither takes the touches of a back gesture away from the window nor sends it any progress:
 * the window has to work the progress out from the touches it gets itself
 * (DecorView.onInterceptTouchEvent -> WindowOnBackInvokedDispatcher.onMotionEvent). In the
 * window of MainActivity those touches did not arrive once the gesture had started (the back
 * log: "touch moves in gesture=0", progress stuck at 0), while a dialog window (the sheet of
 * the transfer rules) got them and its preview followed the finger.
 *
 * [handProgressToSystem] takes the flag off the window of MainActivity again: it hands the
 * window's own attributes to the window manager once more, which replaces the attributes of
 * the view root (ViewRootImpl.setLayoutParams copies them, private flags included, and the
 * flag lives only in the view root's copy). Without the flag the system pilfers the touches of
 * the gesture at its start and sends the progress itself (BackAnimationController
 * dispatchOnBackProgressed), as before Android 15, from the touches of its own input monitor,
 * which no window of the app can take away. If the copy keeps the flag on some release, the
 * flag is cleared on the view root's copy by reflection, as far as the platform allows.
 *
 * Debug and staging builds can switch this off in the performance lab ("Back progress"),
 * effective with the next start of the activity; release builds always hand it over.
 */
internal object BackWindows {
    private const val PREFS = "next-stop-perflab"
    private const val KEY = "back-progress-app"
    private const val FLAG_NAME = "APP_PROGRESS_GENERATION_ALLOWED"

    /** True: the system sends the progress (the fix); false: the window works it out (the platform default). */
    var systemProgress by mutableStateOf(true)
        private set

    private var loaded = false
    private var activityRef: WeakReference<Activity>? = null

    fun load(context: Context): Boolean {
        if (!loaded) {
            loaded = true
            if (PerfLab.available) {
                systemProgress = !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
            }
        }
        return systemProgress
    }

    /** The other way, kept in the preferences of the lab; takes effect when the activity is made again. */
    fun toggle(context: Context) {
        if (!PerfLab.available) return
        load(context)
        systemProgress = !systemProgress
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, !systemProgress).apply()
        BackLog.log("back progress now ${label()} (after restart)")
    }

    fun label(): String = if (systemProgress) "system sends progress" else "app window works it out (platform default)"

    /**
     * Called by MainActivity once its window is attached (the view root exists then and has
     * set the flag); does nothing below Android 15, where there is no such flag.
     */
    fun handProgressToSystem(activity: Activity) {
        activityRef = WeakReference(activity)
        if (Build.VERSION.SDK_INT < 35) return
        val window = activity.window
        val decor = window.peekDecorView() ?: return
        if (decor.parent == null) return
        val before = rootFlagSet(decor)
        if (!load(activity)) {
            BackLog.log("windows: back progress by the app window (lab), flag=${flagText(before)}")
            return
        }
        // The window's own attributes never carry the flag: handing them over again replaces
        // the view root's copy, and the window manager gets the copy with the next layout.
        window.attributes = window.attributes
        var after = rootFlagSet(decor)
        if (after == true) {
            clearRootFlag(decor)
            after = rootFlagSet(decor)
        }
        BackLog.log("windows: back progress handed to the system, flag before=${flagText(before)} after=${flagText(after)}")
    }

    /** Logs the windows of the app and the main window's frame, at the start of every gesture. */
    fun logStart() {
        if (!BackLog.on || Build.VERSION.SDK_INT < 30) return
        val activity = activityRef?.get() ?: return
        try {
            BackLog.log("windows at start: ${describe(activity)}")
        } catch (e: Throwable) {
            BackLog.log("windows at start: unreadable (${e.javaClass.simpleName})")
        }
    }

    private fun describe(activity: Activity): String {
        val out = StringBuilder()
        val decor = activity.window.peekDecorView()
        val bounds = activity.windowManager.currentWindowMetrics.bounds
        if (decor != null) {
            val at = IntArray(2)
            decor.getLocationOnScreen(at)
            val frame = "${at[0]},${at[1]} ${decor.width}x${decor.height}"
            val full = at[0] == bounds.left && at[1] == bounds.top &&
                decor.width == bounds.width() && decor.height == bounds.height()
            out.append("main frame=$frame display=${bounds.width()}x${bounds.height()} full=$full ")
            out.append("flag=${flagText(rootFlagSet(decor))} mode=${if (systemProgress) "system" else "app"} ")
            out.append("surfaces=${surfaces(decor)}; ")
        }
        val windows = globalWindows()
        if (windows == null) {
            out.append("window list unreadable")
        } else {
            out.append("${windows.size} window(s):")
            for ((view, params) in windows) {
                out.append(" [")
                out.append(params?.title ?: "?")
                out.append(" type=").append(params?.type ?: -1)
                if (params != null) {
                    val f = params.flags
                    out.append(" flags=0x").append(Integer.toHexString(f))
                    if ((f and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) out.append(" notTouchable")
                    if ((f and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) out.append(" notFocusable")
                    if ((f and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0) out.append(" notTouchModal")
                    if (params.toString().contains(FLAG_NAME)) out.append(" appProgress")
                }
                out.append(" view=").append(view.javaClass.simpleName)
                out.append(" ").append(view.width).append("x").append(view.height)
                out.append(if (view.isShown) " shown" else " hidden")
                if (view === decor) out.append(" MAIN")
                out.append("]")
            }
        }
        return out.toString()
    }

    // The SurfaceViews and TextureViews in the main window (the map), with their size.
    private fun surfaces(root: View): String {
        val found = ArrayList<String>()
        fun walk(view: View) {
            when (view) {
                is SurfaceView -> found.add("SurfaceView ${view.width}x${view.height}${if (view.isShown) "" else " hidden"}")
                is TextureView -> found.add("TextureView ${view.width}x${view.height}${if (view.isShown) "" else " hidden"}")
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return if (found.isEmpty()) "none" else found.joinToString(",")
    }

    // Every window of the process (WindowManagerGlobal.mViews/mParams; reflection, best effort).
    private fun globalWindows(): List<Pair<View, WindowManager.LayoutParams?>>? = try {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val viewsField = global.getDeclaredField("mViews").apply { isAccessible = true }
        val paramsField = global.getDeclaredField("mParams").apply { isAccessible = true }
        val views = (viewsField.get(instance) as List<*>).toList()
        val params = (paramsField.get(instance) as List<*>).toList()
        views.mapIndexedNotNull { i, v -> (v as? View)?.let { it to (params.getOrNull(i) as? WindowManager.LayoutParams) } }
    } catch (e: Throwable) {
        null
    }

    // The view root's copy of the attributes (ViewRootImpl.mWindowAttributes), if readable.
    private fun rootAttributes(decor: View): WindowManager.LayoutParams? = try {
        val root = decor.parent ?: return null
        val field = root.javaClass.getDeclaredField("mWindowAttributes").apply { isAccessible = true }
        field.get(root) as? WindowManager.LayoutParams
    } catch (e: Throwable) {
        null
    }

    // Whether the view root's copy has the flag: true, false or null (unreadable).
    private fun rootFlagSet(decor: View): Boolean? = rootAttributes(decor)?.toString()?.contains(FLAG_NAME)

    private fun clearRootFlag(decor: View) {
        try {
            val attrs = rootAttributes(decor) ?: return
            val flag = WindowManager.LayoutParams::class.java.getDeclaredField("PRIVATE_FLAG_$FLAG_NAME").getInt(null)
            val field = WindowManager.LayoutParams::class.java.getDeclaredField("privateFlags").apply { isAccessible = true }
            field.setInt(attrs, field.getInt(attrs) and flag.inv())
            decor.requestLayout()
        } catch (e: Throwable) {
            BackLog.log("windows: flag not cleared (${e.javaClass.simpleName})")
        }
    }

    private fun flagText(set: Boolean?): String = when (set) {
        true -> "set"
        false -> "clear"
        null -> "unreadable"
    }
}
