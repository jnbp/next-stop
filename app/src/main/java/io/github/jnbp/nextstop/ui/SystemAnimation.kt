package io.github.jnbp.nextstop.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.maps.MapLibreMap

/**
 * The system's "Animator duration scale" (Settings.Global.ANIMATOR_DURATION_SCALE, developer
 * options) for every animation of the app: 1 normal, larger slower, 0 no animations. Compose's
 * own animations follow it by themselves; the app's own loops, waits between the steps of an
 * animation and the map camera ask here (see AnimationScale.kt for the arithmetic).
 *
 * Read when the app starts and again whenever it comes to the front, and followed while it is
 * in front (a ContentObserver registered from MainActivity.onStart to onStop), so a change in
 * the developer options shows at once. Main thread only.
 */
object SystemAnimation {
    private val current = mutableFloatStateOf(1f)
    private var observer: ContentObserver? = null
    private val clock = AnimationClock()

    /** The scale now, as state: a composable or a draw step that reads it follows a change. */
    val scale: State<Float> get() = current

    /** The scale now, as a plain number (reads the state). */
    val now: Float get() = current.floatValue

    /** False when the system has animations switched off (scale 0). */
    val on: Boolean get() = current.floatValue > 0f

    /** Reads the scale from the system. */
    fun read(context: Context) {
        val raw = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
        val scale = sanitizeAnimatorScale(raw)
        if (scale != current.floatValue) current.floatValue = scale
    }

    /** The app came to the front: reads the scale and follows its changes until [stop]. */
    fun start(context: Context) {
        val app = context.applicationContext
        read(app)
        if (observer != null) return
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                read(app)
            }
        }
        try {
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                watcher,
            )
            observer = watcher
        } catch (e: Exception) {
            // Not followed: the scale is read again the next time the app comes to the front.
        }
    }

    /** The app went to the background: no more following (read again at the next [start]). */
    fun stop(context: Context) {
        val watcher = observer ?: return
        observer = null
        try {
            context.applicationContext.contentResolver.unregisterContentObserver(watcher)
        } catch (e: Exception) {
            // Gone already.
        }
    }

    /** A duration of [millis] at the scale now (0 without animations). */
    fun millis(millis: Long): Long = scaledMillis(millis, current.floatValue)

    /** A duration of [millis] at the scale now (0 without animations). */
    fun millis(millis: Int): Int = scaledMillis(millis, current.floatValue)

    /**
     * The time of the one clock of the app's endless loops (flowing arrows, the pulse of live
     * trains), at [realMillis] of uptime: it runs at 1 / scale and stands still without animations.
     */
    fun clockMillis(realMillis: Long = SystemClock.uptimeMillis()): Long = clock.at(realMillis, current.floatValue)
}

/** Waits [millis] between the steps of an animation (a stagger, a pause before it), x the system's scale; not at all without animations. */
suspend fun animationDelay(millis: Long) {
    val wait = SystemAnimation.millis(millis)
    if (wait > 0L) delay(wait)
}

/** MapLibre's duration of animateCamera when none is given, in milliseconds. */
const val MAP_CAMERA_DEFAULT_MILLIS = 300

/**
 * animateCamera at the system's animator duration scale: [millis] x scale, and a plain move
 * (at once) without animations, since MapLibre takes no duration of 0.
 */
fun MapLibreMap.animateCameraScaled(
    update: CameraUpdate,
    millis: Int = MAP_CAMERA_DEFAULT_MILLIS,
    callback: MapLibreMap.CancelableCallback? = null,
) {
    // Never to a camera MapLibre must not be given (#284, see cameraUpdateOk).
    if (!cameraUpdateOk(update)) return
    val scaled = SystemAnimation.millis(millis)
    if (scaled <= 0) moveCamera(update, callback) else animateCamera(update, scaled, callback)
}

/** animateCameraScaled with MapLibre's own duration. */
fun MapLibreMap.animateCameraScaled(update: CameraUpdate, callback: MapLibreMap.CancelableCallback?) =
    animateCameraScaled(update, MAP_CAMERA_DEFAULT_MILLIS, callback)

/** easeCamera at the system's animator duration scale: [millis] x scale, at once without animations. */
fun MapLibreMap.easeCameraScaled(update: CameraUpdate, millis: Int, easingInterpolator: Boolean = true) {
    if (!cameraUpdateOk(update)) return
    val scaled = SystemAnimation.millis(millis)
    if (scaled <= 0) moveCamera(update) else easeCamera(update, scaled, easingInterpolator)
}
