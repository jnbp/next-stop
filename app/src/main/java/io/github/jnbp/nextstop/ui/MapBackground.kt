package io.github.jnbp.nextstop.ui

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.SystemClock
import android.view.SurfaceView
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import io.github.jnbp.nextstop.data.DiagLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeoutOrNull
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

// After the last change two more pictures are taken, so the final frame is not missed. The
// pause between them comes from backdropPause (see MapPacing.kt).
private const val BACKDROP_ROUNDS = 2

/**
 * Where the camera was when the picture behind the glass was taken, so the picture can move
 * and grow with the map while the camera pans or zooms (see follow below), and whether the
 * camera is on its way right now. Only used on the main thread.
 */
private class BackdropCamera {
    // The point of the map at the top left corner of the picture; NaN while there is none.
    var latitude = Double.NaN
    var longitude = Double.NaN
    var zoom = 0.0
    var bearing = 0.0
    var tilt = 0.0

    // True from the start of a move of the camera to its idle callback.
    var moving = false
    var movedAt = 0L
    var restedAt = 0L

    fun clear() {
        latitude = Double.NaN
        longitude = Double.NaN
    }

    /** True while the camera is on its way; a move that never reported its end counts as over after a while. */
    fun inMotion(now: Long): Boolean = moving && now - movedAt < CAMERA_STILL_MILLIS
}

/**
 * Reads where [map] stands now into [into] (latitude and longitude of the top left corner of
 * the screen, zoom, bearing, tilt), for a picture that is being taken; false where the
 * projection gives no point.
 */
private fun readCorner(map: MapLibreMap, into: DoubleArray): Boolean {
    val corner = map.projection.fromScreenLocation(PointF(0f, 0f))
    if (corner.latitude.isNaN() || corner.longitude.isNaN()) return false
    val camera = map.cameraPosition
    into[0] = corner.latitude
    into[1] = corner.longitude
    into[2] = camera.zoom
    into[3] = camera.bearing
    into[4] = camera.tilt
    return true
}

/** Takes the place read by [readCorner] as the place of the picture now shown. */
private fun BackdropCamera.use(values: DoubleArray) {
    latitude = values[0]
    longitude = values[1]
    zoom = values[2]
    bearing = values[3]
    tilt = values[4]
}

/**
 * Moves the picture behind the glass with the camera of [map]: where the top left corner of
 * the picture lies on the screen now, and how much it has grown. False where the picture
 * cannot follow (no picture, or the map was turned or tilted since): then it has to be taken
 * again.
 */
private fun BackdropCamera.follow(map: MapLibreMap, backdrop: Backdrop): Boolean {
    if (latitude.isNaN() || backdrop.image == null) return false
    val camera = map.cameraPosition
    if (!backdropCanFollow(camera.tilt, camera.bearing, tilt, bearing)) return false
    val corner = map.projection.toScreenLocation(LatLng(latitude, longitude))
    if (corner.x.isNaN() || corner.y.isNaN()) return false
    backdrop.shiftX = corner.x
    backdrop.shiftY = corner.y
    backdrop.grow = backdropGrow(camera.zoom, zoom)
    return true
}

/** How far the map view was taken through its lifecycle: created only, started, or resumed. */
private enum class MapRun { Idle, Started, Resumed }

// A map that rested and comes back waits this long at most under the plain ground for its
// complete picture, and then fades in this long (see MapBackground).
private const val WAKE_MAX_MILLIS = 700L
private const val WAKE_FADE_MILLIS = 200

/**
 * Full-size map used as the background of the app. With a [backdrop], the map also
 * delivers the blurred picture of itself that the glass surfaces show. While [visible]
 * is false the map is not shown and rests: it draws nothing and loads nothing. The map
 * moves to a new [latitude] and [longitude] only while [follow] is true: the first time it
 * is put there at [zoom], after that it glides there and keeps the zoom it has. The map
 * shows the style at [styleUrl] (see [mapStyleUrl]) and can change it while it runs; each
 * time a style has loaded, [onStyleLoaded] gets it, so the app can add its own sources and
 * layers again (a new style drops them). The ground while the map loads shows the logo of
 * the app only while [logo] is true (the start page, where the splash screen hands over to
 * it); over any other page (a link that opens a route) it only breathes gently.
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
    logo: Boolean = true,
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
    // Where the picture behind the glass was taken, and whether the camera moves right now.
    val backdropCamera = remember { BackdropCamera() }
    // Says that the camera came to rest: a calm pause between two pictures ends early then.
    val rested = remember { Channel<Unit>(Channel.CONFLATED) }
    // Says that the map has nothing left to draw: its tiles and labels are in and nothing
    // animates. Emptied when the camera starts to move, so it always means "since the last move".
    val mapIdle = remember { Channel<Unit>(Channel.CONFLATED) }
    // Until when the signals of the map are no change of it: a snapshot makes the map draw a
    // frame of its own and become idle again (uptime millis; see snap below).
    val quietUntil = remember { longArrayOf(0L) }
    val currentBackdrop by rememberUpdatedState(backdrop)
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
    // A surface of its own (SurfaceView) unless the lab asks for a texture (TextureView). A
    // texture can be read back as the picture behind the glass, but every frame of the map then
    // becomes a frame of the whole window: the main thread and the RenderThread of the app take
    // part in each one and draw all the glass over it again, which made panning stutter. A
    // surface goes to the screen without the app's own drawing; the glass is frosted then.
    val textureMode = remember { PerfLab.textureMap(context) }
    val mapView = remember {
        MapLibre.getInstance(context)
        PerfLab.textureMapInUse = textureMode
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(textureMode)
        MapView(context, options).apply {
            onCreate(null)
            addOnDidFinishLoadingStyleListener {
                styleDone.set(true)
                Startup.mapReady = true
            }
            // Tiles and labels that arrive change the picture without any move of the camera.
            addOnDidFinishRenderingMapListener { fully ->
                // A page built again shows its map as soon as it has drawn at its place.
                if ((fully || Resume.rebuilt) && styleDone.get() && cameraSet.get()) rendered.value = true
                drawn.set(true)
                if (SystemClock.uptimeMillis() > quietUntil[0]) changed.trySend(Unit)
            }
            addOnDidBecomeIdleListener {
                if (SystemClock.uptimeMillis() <= quietUntil[0]) return@addOnDidBecomeIdleListener
                mapIdle.trySend(Unit)
                if (drawn.get()) changed.trySend(Unit)
            }
            // Debug and staging builds: when the map draws again after the page came back.
            if (ResumeLog.on) {
                addOnDidFinishRenderingFrameListener { fully, _, _ -> ResumeLog.once("map frame", if (fully) "fully true" else "fully false") }
            }
            if (!textureMode) keepSurfaceWhileAttached()
        }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    // The glass knows whether the map can be read back at once (see Backdrop.readback).
    SideEffect { backdrop?.readback = textureMode }
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
            PerfLab.detach(mapView)
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
        if (animate && !Resume.rebuilt) {
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

    // Whether the map rested (hidden) since it was last shown, read while composing so the
    // first frame it is shown again is already covered; and the cover while it comes back.
    val resting = remember { booleanArrayOf(!visible) }
    if (!visible) resting[0] = true
    val waking = remember { mutableStateOf(false) }
    val wake = remember { Animatable(0f) }
    // Counts the complete pictures of the map, to know when a map that rested has drawn its place.
    val fullFrames = remember { mutableIntStateOf(0) }
    DisposableEffect(mapView) {
        val listener = MapView.OnDidFinishRenderingMapListener { fully -> if (fully) fullFrames.intValue++ }
        mapView.addOnDidFinishRenderingMapListener(listener)
        onDispose { mapView.removeOnDidFinishRenderingMapListener(listener) }
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
                readyMap.addOnCameraMoveStartedListener {
                    backdropCamera.moving = true
                    backdropCamera.movedAt = SystemClock.uptimeMillis()
                    mapIdle.tryReceive()
                }
                // While the camera pans or zooms, the picture behind the glass moves with the map
                // instead of being read back from the map again for every frame, which stalled
                // the drawing of the frames of the gesture.
                readyMap.addOnCameraMoveListener {
                    AllocProbe.begin(AllocProbe.GLASS)
                    backdropCamera.moving = true
                    backdropCamera.movedAt = SystemClock.uptimeMillis()
                    val glass = currentBackdrop
                    val followed = glass != null && PerfLab.glassFollows && backdropCamera.follow(readyMap, glass)
                    if (!followed && drawn.get()) changed.trySend(Unit)
                    AllocProbe.end(AllocProbe.GLASS)
                }
                readyMap.addOnCameraIdleListener {
                    backdropCamera.moving = false
                    backdropCamera.restedAt = SystemClock.uptimeMillis()
                    rested.trySend(Unit)
                    if (drawn.get()) changed.trySend(Unit)
                }
                map = readyMap
                // Snapshots of the map alone, without MapLibre's merge of the view (see dropSnapshotViewMerge).
                if (!textureMode) PerfLab.snapshotPlain = mapView.dropSnapshotViewMerge()
                PerfLab.attach(mapView, readyMap)
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
    if (visible && loading) MapLoading(entered = { enter.value }, animate = animate, logo = logo)
    // A map that rested comes back (the map switched off for the start page, then a search
    // or a journey that is about the map): it loaded nothing meanwhile, so its surface first
    // shows its last picture, of wherever it was, and then fills its new place piece by
    // piece. The plain ground it lay under stays over it until it has drawn its place fully
    // (or a moment at most), and then fades, so neither the jump nor the empty frames are seen.
    if (visible && (resting[0] || waking.value)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (resting[0]) 1f else wake.value }
                .background(AppColors.PlainGround),
        )
    }
    }

    LaunchedEffect(visible) {
        if (!visible || !resting[0]) return@LaunchedEffect
        if (loading) {
            // The first entrance of the map covers it by itself (see MapLoading).
            resting[0] = false
            return@LaunchedEffect
        }
        waking.value = true
        wake.snapTo(1f)
        resting[0] = false
        try {
            val before = fullFrames.intValue
            withTimeoutOrNull(WAKE_MAX_MILLIS) { snapshotFlow { fullFrames.intValue }.first { it != before } }
            if (animate) wake.animateTo(0f, tween(WAKE_FADE_MILLIS)) else wake.snapTo(0f)
        } finally {
            waking.value = false
        }
    }

    // A style or tiles that do not load (no network) still let the map, and after it the rest
    // of the start, come in.
    LaunchedEffect(mapView) {
        delay(if (Resume.rebuilt) MAP_SHOW_LATEST_REBUILT else MAP_SHOW_LATEST)
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
    // The frames of the map, counted for the performance lab while its panel is open.
    val labShown = PerfLab.shown
    DisposableEffect(mapView, labShown) {
        if (!labShown) return@DisposableEffect onDispose { }
        val listener = MapView.OnDidFinishRenderingFrameListener { _, encoding, rendering -> PerfLab.mapFrame(encoding, rendering) }
        mapView.addOnDidFinishRenderingFrameListener(listener)
        onDispose { mapView.removeOnDidFinishRenderingFrameListener(listener) }
    }

    // The picture behind the glass: taken only while the map is shown and changes. In
    // between, this waits for the next change without waking up. While the camera pans or
    // zooms nothing is taken: the picture moves with the map (see BackdropCamera.follow) and is
    // taken again once the camera rests. Changes of the map alone (gliding vehicles, the beat
    // of the live dots) renew it calmly, a moment after the camera came to rest also the tiles
    // that come in (see backdropPause).
    // Not before the map has come in: the picture would show the map before it is seen.
    // A map that is a surface (SurfaceView) is read only by snapshots, and only while the camera
    // rests (see snap below); while it moves, the last picture moves with it as above.
    val glassOn = PerfLab.glass
    val glassFollows = PerfLab.glassFollows
    val snapshotOn = PerfLab.glassSnapshot
    LaunchedEffect(mapView, backdrop, visible, loading, glassOn, glassFollows, snapshotOn) {
        if (backdrop == null || !visible || loading || !glassOn) return@LaunchedEffect
        if (!textureMode && !snapshotOn) return@LaunchedEffect
        val capture = BackdropCapture(backdrop, BackdropCapture.radiusFor(density))
        // A camera that can carry the picture along: no picture while it moves.
        fun carried(): Boolean =
            glassFollows && backdropCamera.inMotion(SystemClock.uptimeMillis()) &&
                map?.let { backdropCamera.follow(it, backdrop) } == true
        val corner = DoubleArray(5)
        suspend fun take() {
            val texture = mapView.textureChild() ?: return
            val readyMap = map
            // Where the camera stands as the map is read: the picture belongs to that place.
            val placed = readyMap != null && readCorner(readyMap, corner)
            if (!capture.update(texture)) return
            if (placed) backdropCamera.use(corner) else backdropCamera.clear()
            // Taken where the camera stood a moment ago: moved on by what it did meanwhile.
            backdrop.settle()
            if (readyMap != null && glassFollows) backdropCamera.follow(readyMap, backdrop)
        }
        // A snapshot of a map that is a surface: read on the render thread of the map, made
        // small and blurred off the main thread. A picture during which the camera moved is
        // dropped: it belongs to no place any more.
        suspend fun snap() {
            val readyMap = map ?: return
            val width = mapView.width
            val height = mapView.height
            val placed = readCorner(readyMap, corner)
            val asked = SystemClock.uptimeMillis()
            quietUntil[0] = Long.MAX_VALUE
            val picture = try {
                withTimeoutOrNull(SNAPSHOT_TIMEOUT_MILLIS) { readyMap.awaitSnapshot() }
            } finally {
                quietUntil[0] = SystemClock.uptimeMillis() + SNAPSHOT_ECHO_MILLIS
            }
            if (picture == null) return
            try {
                if (backdropCamera.moving || backdropCamera.movedAt >= asked) return
                if (!capture.update(picture, width, height)) return
            } finally {
                picture.recycle()
            }
            if (placed) backdropCamera.use(corner) else backdropCamera.clear()
            backdrop.settle()
            if (glassFollows) backdropCamera.follow(readyMap, backdrop)
        }
        // A map that was drawn before gives its picture at once, also when nothing changes right now.
        if (drawn.get()) changed.trySend(Unit)
        try {
            if (!textureMode) {
                // When the last snapshot was asked for; 0 before the first one.
                var shotAt = 0L
                for (signal in changed) {
                    // Never while the camera moves: the last picture moves with it, and the
                    // idle camera sends the next signal.
                    if (backdropCamera.inMotion(SystemClock.uptimeMillis())) continue
                    // One snapshot for each rest of the camera (and the first one), else a rare
                    // refresh for changes of the map alone (vehicles, labels).
                    val afterRest = shotAt == 0L || backdropCamera.restedAt > shotAt
                    if (!afterRest) {
                        val due = shotAt + SNAPSHOT_REFRESH_MILLIS - SystemClock.uptimeMillis()
                        if (due > 0) {
                            // Waits out the pause; a camera that comes to rest ends it.
                            rested.tryReceive()
                            withTimeoutOrNull(due) { rested.receive() }
                            changed.trySend(Unit)
                            continue
                        }
                    }
                    // Taken once the map is idle: tiles and labels in, nothing animating. After a
                    // rest it is taken at the latest after SNAPSHOT_IDLE_WAIT_MILLIS; a refresh
                    // while the map keeps animating is left out until the next pause is over.
                    val idle = withTimeoutOrNull(SNAPSHOT_IDLE_WAIT_MILLIS) { mapIdle.receive() } != null
                    if (backdropCamera.inMotion(SystemClock.uptimeMillis())) continue
                    // Never two snapshots closer than SNAPSHOT_MIN_GAP_MILLIS, whatever signals
                    // come (a snapshot that made the map signal a change would else take the next).
                    val gap = SystemClock.uptimeMillis() - shotAt
                    if (shotAt != 0L && gap < SNAPSHOT_MIN_GAP_MILLIS) {
                        DiagLog.w(PERF_LOG_TAG) { "glass snapshot asked again after $gap ms: held back" }
                        delay(SNAPSHOT_MIN_GAP_MILLIS - gap)
                        if (backdropCamera.inMotion(SystemClock.uptimeMillis())) continue
                    }
                    shotAt = SystemClock.uptimeMillis()
                    if (!idle && !afterRest) continue
                    snap()
                    // Signals that came while the snapshot was taken are no change of the map.
                    delay(SNAPSHOT_ECHO_MILLIS)
                    while (changed.tryReceive().isSuccess) Unit
                    mapIdle.tryReceive()
                }
                return@LaunchedEffect
            }
            for (signal in changed) {
                if (carried()) continue
                var rounds = BACKDROP_ROUNDS
                while (rounds > 0) {
                    take()
                    val now = SystemClock.uptimeMillis()
                    // A camera that turns the map cannot carry the picture: then it is renewed
                    // quickly, as it always was with the new pace switched off in the lab.
                    val pause = if (!glassFollows || backdropCamera.inMotion(now)) {
                        BACKDROP_FAST_MILLIS
                    } else {
                        backdropPause(now - backdropCamera.restedAt)
                    }
                    // A camera that comes to rest ends the pause: its place is taken at once.
                    rested.tryReceive()
                    withTimeoutOrNull(pause) { rested.receive() }
                    // A change during the pause starts the count again, unless the camera
                    // carries the picture now.
                    rounds = when {
                        carried() -> 0
                        changed.tryReceive().isSuccess -> BACKDROP_ROUNDS
                        else -> rounds - 1
                    }
                }
            }
        } finally {
            capture.release()
            backdropCamera.clear()
        }
    }
}

// Snapshots of a map that is a surface: one each time the camera came to rest, once the map is
// idle or at the latest SNAPSHOT_IDLE_WAIT_MILLIS later; besides that at most one per
// SNAPSHOT_REFRESH_MILLIS for changes of the map alone, only while it is idle (gliding vehicles
// hardly show behind the blur). Awaited at most SNAPSHOT_TIMEOUT_MILLIS; the signals of the
// snapshot's own frame are ignored until SNAPSHOT_ECHO_MILLIS after it.
private const val SNAPSHOT_IDLE_WAIT_MILLIS = 2_000L
private const val SNAPSHOT_REFRESH_MILLIS = 10_000L
private const val SNAPSHOT_TIMEOUT_MILLIS = 1_500L
private const val SNAPSHOT_ECHO_MILLIS = 300L
private const val SNAPSHOT_MIN_GAP_MILLIS = 1_000L

/**
 * Lets snapshots of this map be the map alone. MapLibre merges into each snapshot a software
 * drawing of the whole MapView (its logo and attribution): drawn on the main thread into a
 * bitmap as large as the screen and merged into one more, which held the main thread for over
 * 100 ms after the camera came to rest. It does so only while its native map holds the MapView
 * as its view callback; this clears that reference. The field is found by what it holds (an
 * interface of the MapView with a single method that gives a Bitmap), so shrunk names do not
 * matter. False where it is not found: then snapshots keep the merge.
 */
private fun MapView.dropSnapshotViewMerge(): Boolean {
    try {
        for (outer in MapView::class.java.declaredFields) {
            if (!outer.type.isInterface) continue
            outer.isAccessible = true
            val native = outer.get(this) ?: continue
            for (inner in native.javaClass.declaredFields) {
                val type = inner.type
                if (!type.isInterface || !type.isInstance(this)) continue
                val methods = type.declaredMethods
                if (methods.size != 1 || methods[0].returnType != Bitmap::class.java) continue
                inner.isAccessible = true
                if (inner.get(native) !== this) continue
                inner.set(native, null)
                return true
            }
        }
    } catch (e: Exception) {
        // Not as expected in this version of MapLibre: the merge stays.
    }
    return false
}

/** A snapshot of the map as it is drawn next; the bitmap belongs to the caller. */
private suspend fun MapLibreMap.awaitSnapshot(): Bitmap = suspendCancellableCoroutine { continuation ->
    snapshot { picture ->
        // A snapshot that comes after the wait was given up is not needed.
        if (continuation.isActive) continuation.resume(picture) else picture.recycle()
    }
}

// The map fades in and settles from a slight zoom over this time once its first picture is
// complete; it comes in at the latest after MAP_SHOW_LATEST (no network).
private const val MAP_FADE_MILLIS = 520
private const val MAP_SHOW_LATEST = 6_000L
private const val MAP_SHOW_LATEST_REBUILT = 1_500L
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
 * little. With animations off ([animate]) the logo stands still. Without [logo] (a page is in
 * front, not the start page) the logo goes at once and only the ground breathes a little,
 * where the map is to come: the logo in the middle would lie over the page.
 */
@Composable
private fun MapLoading(entered: () -> Float, animate: Boolean, logo: Boolean) {
    val logoIn = remember { Animatable(0f) }
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MAP_LOGO_DELAY_MILLIS)
        waited = true
    }
    LaunchedEffect(logo, waited) {
        // The map is coming in already, or a page is in front: no logo (any more).
        val show = logo && waited && entered() <= 0f
        val target = if (show) 1f else 0f
        if (logoIn.value == target) return@LaunchedEffect
        if (animate) logoIn.animateTo(target, tween(MAP_LOGO_FADE_MILLIS)) else logoIn.snapTo(target)
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
            .background(AppColors.Ground)
            .drawBehind {
                // Without the logo the ground itself shows that the map is on its way.
                val quiet = 1f - logoIn.value
                val beat = breath?.value ?: 0.5f
                if (!logo && quiet > 0f) drawRect(tint, alpha = quiet * (0.03f + 0.05f * beat))
            },
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

/**
 * Keeps the surface the map draws into (a SurfaceView) for as long as the map is in the window,
 * also while the app is not on screen (Android 14+). By default a SurfaceView gives up its
 * surface when the app is stopped (screen off, another app in front) and gets a new, empty one
 * when it comes back: the map then had to set up its drawing and draw everything again before
 * the window counted as drawn, and until then the system showed a stale, blurred picture and
 * passed on no touches. With the surface kept, the map's last picture is still there and
 * the map only draws on into it. The cost is the memory of the surface while the app is away.
 */
private fun MapView.keepSurfaceWhileAttached() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child is SurfaceView) child.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT)
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
