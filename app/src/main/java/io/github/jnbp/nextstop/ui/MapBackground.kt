package io.github.jnbp.nextstop.ui

import android.os.SystemClock
import android.view.TextureView
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// OpenStreetMap styles served by OpenFreeMap: calm light, colourful and dark. No API key needed.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
private const val COLORFUL_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

/** The style of the map for the [choice] of the user; automatic follows the light or dark look of the app ([appDark]). */
fun mapStyleUrl(choice: MapStyleChoice, appDark: Boolean): String = when (choice) {
    MapStyleChoice.Light -> STYLE_URL
    MapStyleChoice.Colorful -> COLORFUL_STYLE_URL
    MapStyleChoice.Dark -> DARK_STYLE_URL
    MapStyleChoice.Auto -> if (appDark) DARK_STYLE_URL else STYLE_URL
}

/** True when the map of [styleUrl] is dark, so what the app draws on it can use light ink. */
fun isDarkMapStyle(styleUrl: String): Boolean = styleUrl == DARK_STYLE_URL

// While the map changes, the picture behind the glass surfaces is renewed this often at most.
private const val BACKDROP_MILLIS = 100L

// After the last change two more pictures are taken, so the final frame is not missed.
private const val BACKDROP_ROUNDS = 2

/** How far the map view was taken through its lifecycle: created only, started, or resumed. */
private enum class MapRun { Idle, Started, Resumed }

/**
 * Full-size map used as the background of the app. With a [backdrop], the map also
 * delivers the blurred picture of itself that the glass surfaces show. While [visible]
 * is false the map is not shown and rests: it draws nothing and loads nothing. The map
 * moves to a new [latitude] and [longitude] only while [follow] is true: the first time it
 * is put there at [zoom], after that it glides there and keeps the zoom it has. The map
 * shows the style at [styleUrl] (see [mapStyleUrl]) and can change it while it runs; each
 * time a style has loaded, [onStyleLoaded] gets it, so the app can add its own sources and
 * layers again (a new style drops them).
 */
@Composable
fun MapBackground(
    latitude: Double,
    longitude: Double,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    zoom: Double = 14.5,
    backdrop: Backdrop? = null,
    visible: Boolean = true,
    follow: Boolean = true,
    styleUrl: String = STYLE_URL,
    onMapReady: (MapLibreMap) -> Unit = {},
    onStyleLoaded: (Style) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    // Says that the picture of the map has changed. Nothing is sent while the map is still,
    // so the glass costs nothing then.
    val changed = remember { Channel<Unit>(Channel.CONFLATED) }
    // An empty texture would give a black picture: nothing is taken before the map has drawn once.
    val drawn = remember { AtomicBoolean(false) }
    // The map comes in only with a complete first picture (style and tiles), never as an empty
    // map that fills piece by piece. A "complete" picture before the style has loaded is the
    // empty map, so it does not count.
    val styleDone = remember { AtomicBoolean(false) }
    // The first complete picture counts only once the camera stands at its place: before, the
    // map shows the whole world, complete at once, while the tiles of the real place still
    // come over the network (then signs and vehicles were seen on an empty map).
    val cameraSet = remember { AtomicBoolean(!follow) }
    val rendered = remember { mutableStateOf(false) }
    val mapView = remember {
        MapLibre.getInstance(context)
        // Drawn into a texture instead of a surface of its own: only then can the map be read back as a picture.
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).apply {
            onCreate(null)
            addOnDidFinishLoadingStyleListener {
                styleDone.set(true)
                Startup.mapReady = true
            }
            // Tiles and labels that arrive change the picture without any move of the camera.
            addOnDidFinishRenderingMapListener { fully ->
                if (fully && styleDone.get() && cameraSet.get()) rendered.value = true
                drawn.set(true)
                changed.trySend(Unit)
            }
            addOnDidBecomeIdleListener { if (drawn.get()) changed.trySend(Unit) }
        }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val styleLoaded by rememberUpdatedState(onStyleLoaded)
    // The style the map was given last, so a change of light and dark loads the other one.
    val shownStyle = remember { arrayOf<String?>(null) }
    val wantedStyle by rememberUpdatedState(styleUrl)

    // The map runs as far as the screen does, and not at all while it is hidden. The view
    // is told step by step, in the order its lifecycle asks for.
    val run = remember { arrayOf(MapRun.Idle) }
    val wanted by rememberUpdatedState(visible)
    DisposableEffect(lifecycleOwner, mapView) {
        fun follow(state: Lifecycle.State) {
            val target = when {
                !wanted || !state.isAtLeast(Lifecycle.State.STARTED) -> MapRun.Idle
                state.isAtLeast(Lifecycle.State.RESUMED) -> MapRun.Resumed
                else -> MapRun.Started
            }
            while (run[0] != target) {
                if (run[0] < target) {
                    if (run[0] == MapRun.Idle) {
                        mapView.onStart()
                        run[0] = MapRun.Started
                    } else {
                        mapView.onResume()
                        run[0] = MapRun.Resumed
                    }
                } else {
                    if (run[0] == MapRun.Resumed) {
                        mapView.onPause()
                        run[0] = MapRun.Started
                    } else {
                        mapView.onStop()
                        run[0] = MapRun.Idle
                    }
                }
            }
        }
        val observer = LifecycleEventObserver { owner, _ -> follow(owner.lifecycle.currentState) }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Showing or hiding the map takes effect at once, not at the next lifecycle event.
        val watch = snapshotFlow { wanted }
        val job = scope.launch { watch.collect { follow(lifecycleOwner.lifecycle.currentState) } }
        onDispose {
            job.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            follow(Lifecycle.State.CREATED)
            mapView.onDestroy()
        }
    }

    // The entrance of the map, 0 to 1: it settles from a slight zoom while the loading ground
    // over it (see MapLoading) fades out. The map itself is never made transparent: the
    // texture it draws into is covered instead, which works on every device. It starts once
    // the first picture is complete and the splash screen is gone, so it is seen; with
    // animations off the map is simply there.
    val animate = rememberAnimationsOn()
    val enter = remember { Animatable(0f) }
    val loading by remember { derivedStateOf { enter.value < 1f } }
    LaunchedEffect(mapView) {
        snapshotFlow { rendered.value && Startup.splashDone }.first { it }
        // The beat of the rest of the start (see awaitIntroStep): only the first map gives it.
        if (Startup.mapShownAt == 0L) Startup.mapShownAt = SystemClock.uptimeMillis()
        Startup.mapIn = true
        if (animate) {
            enter.animateTo(1f, tween(MAP_FADE_MILLIS, easing = FastOutSlowInEasing))
        } else {
            enter.snapTo(1f)
        }
    }
    // A start without the map (switched off for the start page): nothing to wait for, the
    // rest of the start begins once the splash screen is gone.
    LaunchedEffect(visible) {
        if (visible || Startup.mapShownAt != 0L) return@LaunchedEffect
        snapshotFlow { Startup.splashDone }.first { it }
        if (Startup.mapShownAt == 0L) Startup.mapShownAt = SystemClock.uptimeMillis()
        Startup.mapIn = true
    }

    Box(modifier = modifier) {
    AndroidView(
        factory = {
            mapView.getMapAsync { readyMap ->
                shownStyle[0] = wantedStyle
                readyMap.setStyle(Style.Builder().fromUri(wantedStyle)) { style -> styleLoaded(style) }
                readyMap.uiSettings.isCompassEnabled = false
                // Images the map asks for that are made only when first shown: the stop positions
                // with the chips of their lines (see provideStationDetailImage).
                mapView.addOnStyleImageMissingListener { id ->
                    readyMap.style?.let { provideStationDetailImage(it, id) }
                }
                readyMap.addOnCameraMoveListener { if (drawn.get()) changed.trySend(Unit) }
                readyMap.addOnCameraIdleListener { if (drawn.get()) changed.trySend(Unit) }
                map = readyMap
                onMapReady(readyMap)
            }
            mapView
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val settle = MAP_ENTER_SCALE + (1f - MAP_ENTER_SCALE) * enter.value
                scaleX = settle
                scaleY = settle
            },
        update = { view -> view.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
    )
    if (visible && loading) MapLoading(entered = { enter.value }, animate = animate)
    }

    // A style or tiles that do not load (no network) still let the map, and after it the rest
    // of the start, come in.
    LaunchedEffect(mapView) {
        delay(MAP_SHOW_LATEST)
        rendered.value = true
    }

    // True once the map was put at its place.
    val placed = remember { booleanArrayOf(false) }
    LaunchedEffect(map, latitude, longitude) {
        // Not a key: a map that shows something else is left alone, also after that is over.
        if (!follow) return@LaunchedEffect
        val readyMap = map ?: return@LaunchedEffect
        val target = LatLng(latitude, longitude)
        if (!placed[0]) {
            placed[0] = true
            readyMap.cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .build()
            cameraSet.set(true)
            // A map whose picture is already complete there draws it again; that frame counts.
            readyMap.triggerRepaint()
        } else if (visible) {
            readyMap.animateCamera(CameraUpdateFactory.newLatLng(target))
        } else {
            // A map that rests does not animate.
            readyMap.moveCamera(CameraUpdateFactory.newLatLng(target))
        }
    }
    LaunchedEffect(map, styleUrl) {
        val readyMap = map ?: return@LaunchedEffect
        if (shownStyle[0] != styleUrl) {
            shownStyle[0] = styleUrl
            readyMap.setStyle(Style.Builder().fromUri(styleUrl)) { style -> styleLoaded(style) }
        }
    }
    LaunchedEffect(map, interactive) {
        map?.uiSettings?.setAllGesturesEnabled(interactive)
    }

    // The picture behind the glass: taken only while the map is shown and changes. In
    // between, this waits for the next change without waking up.
    // Not before the map has come in: the picture would show the map before it is seen.
    LaunchedEffect(mapView, backdrop, visible, loading) {
        if (backdrop == null || !visible || loading) return@LaunchedEffect
        val capture = BackdropCapture(backdrop, BackdropCapture.radiusFor(density))
        // A map that was drawn before gives its picture at once, also when nothing changes right now.
        if (drawn.get()) changed.trySend(Unit)
        try {
            for (signal in changed) {
                var rounds = BACKDROP_ROUNDS
                while (rounds > 0) {
                    val texture = mapView.textureChild()
                    if (texture != null) capture.update(texture)
                    delay(BACKDROP_MILLIS)
                    // A change during the pause starts the count again.
                    rounds = if (changed.tryReceive().isSuccess) BACKDROP_ROUNDS else rounds - 1
                }
            }
        } finally {
            capture.release()
        }
    }
}

// The map fades in and settles from a slight zoom over this time once its first picture is
// complete; it comes in at the latest after MAP_SHOW_LATEST (no network).
private const val MAP_FADE_MILLIS = 520
private const val MAP_SHOW_LATEST = 6_000L
private const val MAP_ENTER_SCALE = 1.04f

// The logo of the loading ground shows only when the map takes longer than this, so a quick
// map never flashes it; it fades in over MAP_LOGO_FADE_MILLIS and breathes once per
// MAP_LOGO_BREATH_MILLIS. It is drawn as big as the splash screen draws its icon, so the
// splash screen hands over to it in place (see SplashContinuation).
private const val MAP_LOGO_DELAY_MILLIS = 250L
private const val MAP_LOGO_FADE_MILLIS = 320
private const val MAP_LOGO_BREATH_MILLIS = 1_300
private val MAP_LOGO_SIZE = 240.dp

/**
 * The ground of the app over the map while it loads, with the logo of the app in grey that
 * breathes gently; it fades out as the map comes in ([entered], 0 to 1) while the logo grows a
 * little. With animations off ([animate]) the logo stands still.
 */
@Composable
private fun MapLoading(entered: () -> Float, animate: Boolean) {
    val logoIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(MAP_LOGO_DELAY_MILLIS)
        // The map is coming in already: no logo any more.
        if (entered() > 0f) return@LaunchedEffect
        if (animate) logoIn.animateTo(1f, tween(MAP_LOGO_FADE_MILLIS)) else logoIn.snapTo(1f)
    }
    val breath = if (animate) {
        rememberInfiniteTransition(label = "mapLogo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(MAP_LOGO_BREATH_MILLIS, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "mapLogoBreath",
        )
    } else {
        null
    }
    val tint = AppColors.Muted
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - entered() }
            .background(AppColors.Ground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(MAP_LOGO_SIZE)
                .graphicsLayer {
                    val beat = breath?.value ?: 0.5f
                    val grow = 0.97f + 0.06f * beat + 0.1f * entered()
                    scaleX = grow
                    scaleY = grow
                    alpha = logoIn.value * (0.32f + 0.22f * beat)
                },
        )
    }
}

/** The texture the map draws into, once the view has created it. */
private fun MapView.textureChild(): TextureView? {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child is TextureView) return child
    }
    return null
}
