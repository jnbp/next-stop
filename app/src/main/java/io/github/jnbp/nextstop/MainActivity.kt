package io.github.jnbp.nextstop

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.jnbp.nextstop.data.AppLanguage
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.ui.ACTION_OPEN_TRIP
import io.github.jnbp.nextstop.ui.AppLinks
import io.github.jnbp.nextstop.ui.AppRoot
import io.github.jnbp.nextstop.ui.SetupState
import io.github.jnbp.nextstop.ui.SetupWizard
import io.github.jnbp.nextstop.ui.SplashContinuation
import io.github.jnbp.nextstop.ui.animationsAllowed
import io.github.jnbp.nextstop.ui.Startup
import io.github.jnbp.nextstop.ui.TripState
import io.github.jnbp.nextstop.ui.refreshTripNotification
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.DarkChoice
import io.github.jnbp.nextstop.ui.theme.NextStopTheme
import io.github.jnbp.nextstop.ui.theme.appInDark
import io.github.jnbp.nextstop.ui.theme.systemDark
import io.github.jnbp.nextstop.wear.PhoneBridge
import io.github.jnbp.nextstop.widget.WidgetLaunch
import io.github.jnbp.nextstop.widget.WidgetUpdates

// With animations on the splash screen stays as long as its logo animation (splash_icon, under
// 1 s) runs; it fades out in SPLASH_EXIT_MILLIS. Its end counts as done (Startup.splashDone) at
// the latest SPLASH_GONE_LATEST after it let go, also when the system shows no splash screen.
private const val SPLASH_ANIMATION_MILLIS = 800L
private const val SPLASH_EXIT_MILLIS = 260L
private const val SPLASH_GONE_LATEST = 600L

// The scrims behind three-button navigation, as the defaults of enableEdgeToEdge.
private val NAV_SCRIM_LIGHT = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NAV_SCRIM_DARK = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    // True while the end of the splash screen plays in Compose (see SplashContinuation).
    private var splashContinues by mutableStateOf(false)

    // False when the system has animations switched off: the splash then ends plainly.
    private val animationsOn: Boolean by lazy {
        val scale = try {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
        animationsAllowed(scale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.init(this)
        Appearance.load(this)
        followBars(
            when (Appearance.darkChoice) {
                DarkChoice.Light -> false
                DarkChoice.Dark -> true
                DarkChoice.System -> systemDark(this)
            },
        )
        // A page that is built again keeps what it showed; only a fresh start follows the tap.
        if (savedInstanceState == null) followTap(intent)
        // The setup comes first until it was finished or skipped once.
        if (!AppPrefs(this).setupDone) SetupState.open = true
        holdSplash()
        setContent {
            NextStopTheme {
                val dark = appInDark()
                val palette = AppColors.palette
                // The icons of the status and navigation bars, and the notification of a trip, follow the choice.
                DisposableEffect(dark, palette) {
                    followBars(dark)
                    refreshTripNotification(this@MainActivity)
                    onDispose {}
                }
                // The setup takes the place of the app; once it closes, the app starts afresh with its choices.
                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(targetState = SetupState.open, animationSpec = tween(400), label = "setup") { setup ->
                        if (setup) SetupWizard(onDone = { SetupState.open = false }) else AppRoot()
                    }
                    // The splash screen ends over the app: its blue and logo fade into it.
                    if (splashContinues) {
                        SplashContinuation(
                            onDone = {
                                splashContinues = false
                                Startup.splashDone = true
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * Keeps the splash screen until its logo animation has played (not at all with animations
     * off); then Compose fades the blue and the logo into the app (SplashContinuation), where
     * the map, if it is still loading, shows the logo in grey on the ground of the app (see
     * MapBackground). The map does not wait under the splash screen: it comes in only once the
     * splash screen is gone ([Startup.splashDone]), so its entrance and the rest of the start
     * are seen. Without animations the splash only fades out.
     */
    private fun holdSplash() {
        val content = findViewById<View>(android.R.id.content)
        val since = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val held = SystemClock.uptimeMillis() - since
                    // The logo animation plays to its end first, when animations are on.
                    val ready = !animationsOn || held >= SPLASH_ANIMATION_MILLIS
                    if (ready) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        if (animationsOn) {
                            // Done when SplashContinuation ends; this only covers a start without it.
                            content.postDelayed({ Startup.splashDone = true }, SPLASH_GONE_LATEST)
                        } else {
                            Startup.splashDone = true
                        }
                    }
                    return ready
                }
            },
        )
        // Nothing else may ask for a frame while it waits: one is asked for at the end of the wait.
        content.postDelayed({ content.invalidate() }, SPLASH_ANIMATION_MILLIS)
        splashScreen.setOnExitAnimationListener { splash ->
            if (animationsOn) {
                // Compose takes over with the same picture and fades it into the app; the
                // system splash goes once that picture was drawn (two frames later).
                splashContinues = true
                splash.postOnAnimation { splash.postOnAnimation { splash.remove() } }
            } else {
                splash.animate()
                    .alpha(0f)
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(SPLASH_EXIT_MILLIS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { splash.remove() }
                    .start()
            }
        }
    }

    // Light icons on dark pages and dark icons on light ones.
    private fun followBars(dark: Boolean) {
        val bar = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        val navigation = if (dark) {
            SystemBarStyle.dark(NAV_SCRIM_DARK)
        } else {
            SystemBarStyle.light(NAV_SCRIM_LIGHT, NAV_SCRIM_DARK)
        }
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = navigation)
    }

    override fun onStart() {
        super.onStart()
        // The widgets get the position of the app and fresh data while it is in front.
        WidgetUpdates.onAppForeground(this)
        // The watch gets favourites, places and the position of the phone, and every later change of them.
        PhoneBridge.watchPlaces(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        followTap(intent)
    }

    // A tap on the notification of a trip, or on the trip widget, opens the page of the trip;
    // a tap on another widget opens the departures of its stop or starts its connection; a
    // link of the app (see AppLinks) opens what it names, and so do a place shared by another
    // app and its "navigate here" link (geo:, google.navigation:, Google Maps).
    private fun followTap(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TRIP) TripState.openRequested.value = true
        // A trip of the watch the phone could not take over in the background: followed now.
        PhoneBridge.followTap(intent)
        WidgetLaunch.follow(intent)
        // A link of the app: a launcher shortcut, a stop on the home screen, a shared route.
        AppLinks.follow(intent)
    }
}
