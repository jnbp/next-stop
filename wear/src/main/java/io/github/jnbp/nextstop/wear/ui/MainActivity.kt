package io.github.jnbp.nextstop.wear.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.CompanionAutoOpen
import io.github.jnbp.nextstop.wear.data.DepartureCache
import io.github.jnbp.nextstop.wear.data.MapWarmUp
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.hasLocationPermission
import io.github.jnbp.nextstop.wear.data.locationGrants

/** The app on the watch: start page, departures nearby, the trip and the route search. */
class MainActivity : ComponentActivity() {
    private val prefetchClient = BvgClient()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Screens without a position load again.
        if (hasLocationPermission(this)) locationGrants.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.load(this)
        val wanted = buildList {
            if (!hasLocationPermission(this@MainActivity)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (wanted.isNotEmpty() && savedInstanceState == null) permissions.launch(wanted.toTypedArray())
        if (savedInstanceState == null) OpenRequest.take(intent)
        wakeFor(intent)
        setContent { WearApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Opened again from the ongoing activity or a tile while the app runs.
        OpenRequest.take(intent)
        wakeFor(intent)
    }

    // Opened by itself for a trip started on the phone (CompanionAutoOpen): over a locked watch
    // face and with the screen on, like the wake-up alarm; only until the app leaves the screen.
    private fun wakeFor(intent: Intent?) {
        if (intent?.getBooleanExtra(CompanionAutoOpen.EXTRA_WAKE, false) != true) return
        intent.removeExtra(CompanionAutoOpen.EXTRA_WAKE)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        woken = true
    }

    private var woken = false

    override fun onStop() {
        if (woken) {
            woken = false
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        AppForeground.resumed.value = true
        // The app is in front: the nudge to open the companion has done its job.
        CompanionAutoOpen.dismiss(this)
        // On every return to the app: read what the phone wrote, check the phone and ask it for fresh places.
        lifecycleScope.launch {
            WatchStore.refresh(this@MainActivity)
            // The favourite stops' departures, for the time the watch is offline.
            DepartureCache.prefetchFavorites(this@MainActivity, prefetchClient)
        }
        // The phone gets its map pictures ready meanwhile (at most every few minutes, see MapWarmUp).
        lifecycleScope.launch { MapWarmUp.maybeSend(this@MainActivity) }
    }

    override fun onPause() {
        AppForeground.resumed.value = false
        super.onPause()
    }
}

/**
 * Whether the app is in front of the user (between onResume and onPause of [MainActivity]).
 * Pages that load on a timer (the live departures of the start page) load only while it is
 * true: a composition stays alive with the screen off or the app behind another, its timers would not stop.
 */
object AppForeground {
    val resumed = MutableStateFlow(false)
}
