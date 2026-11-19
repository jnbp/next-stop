package io.github.jnbp.nextstop.ui

import android.graphics.PointF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransitSource
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/*
 * "Choose on the map" for the start or the destination: the search hands over to the map tab,
 * where a pin stands in the middle of the screen and the map moves under it. Once the map rests
 * the place under the pin is named, the way a place pressed long on the map is (the device's
 * geocoder for its address, the provider for the stop next to it); the card at the bottom takes
 * the place, or the stop next to it, as if it had been found by the search. Back, or the back
 * button of the bar, returns to the search. The state is one holder (MapPick), so AppRoot only
 * reacts to it and passes nothing in.
 */

/** Whether the start or the destination is chosen on the map. */
enum class MapPickTarget { Origin, Destination }

/** How a pick on the map ended: the [place] taken for [target], or null where it was called off. */
class MapPickDone(val target: MapPickTarget, val place: Stop?)

/** The pick on the map that is going on, if any, and how the last one ended. */
object MapPick {
    /** What is chosen right now; null while nothing is. */
    var target by mutableStateOf<MapPickTarget?>(null)
        private set

    /** How the last pick ended, until AppRoot has taken it (see [take]). */
    var done by mutableStateOf<MapPickDone?>(null)
        private set

    // The provider of the search the pick came from, to name the stop next to the pin.
    internal var source: TransitSource? = null
        private set

    /** True while a place is chosen on the map. */
    val active: Boolean get() = target != null

    /** Starts choosing [what] on the map, naming stops through [source]. */
    fun begin(what: MapPickTarget, source: TransitSource) {
        this.source = source
        done = null
        target = what
    }

    /** Takes [place] for what is chosen and ends the pick. */
    fun finish(place: Stop) {
        val what = target ?: return
        target = null
        done = MapPickDone(what, place)
    }

    /** Ends the pick without a place: back to the search. */
    fun cancel() {
        val what = target ?: return
        target = null
        done = MapPickDone(what, null)
    }

    /** Ends the pick without a word, as when the user goes to another tab. */
    fun leave() {
        target = null
    }

    /** How the last pick ended, once: later calls give null. */
    fun take(): MapPickDone? {
        val ended = done
        done = null
        return ended
    }
}

/**
 * The name of the place under the pin: its [address] from the geocoder where there is one; else
 * "Point on the map" with the stop next to it ([pointNear], given the short name [nearStop]);
 * else just [point].
 */
fun mapPickName(address: String?, nearStop: String?, point: String, pointNear: (String) -> String): String = when {
    !address.isNullOrBlank() -> address
    !nearStop.isNullOrBlank() -> pointNear(nearStop)
    else -> point
}

/** The place at [latitude], [longitude] named [name], as a place pinned on the map is. */
fun mapPickPlace(latitude: Double, longitude: Double, name: String): Stop = Stop(
    id = "pin:$latitude,$longitude",
    name = name,
    latitude = latitude,
    longitude = longitude,
    distanceMeters = null,
    kind = PlaceKind.Address,
)

// The map rests this long before the place under the pin is looked up, so a pause in a pan does not ask.
private const val PICK_LOOKUP_DELAY_MILLIS = 300L

// Where the tip of the pin lies in its glyph, as a share of its height (see Glyph.Pin).
private const val PIN_TIP_SHARE = 0.9f

/** What is known of the place under the pin: its [name], its full address ([detail]) where known, the stop next to it. */
private class PickedSpot(val latitude: Double, val longitude: Double, val name: String, val detail: String?, val nearStop: Stop?)

/**
 * The pin, the bar and the card of a pick on the map, over the map tab while [MapPick] is
 * active; nothing otherwise. Everything else of the screen lets the map be moved under it.
 * [onLocate] puts the map back on the own position: the floating "centre on me" button at the
 * bottom right, above the card (#205); none while no position is known.
 */
@Composable
fun MapPickOverlay(onLocate: (() -> Unit)? = null) {
    val what = MapPick.target ?: return
    val context = LocalContext.current
    val animate = rememberAnimationsOn()
    BackHandler { MapPick.cancel() }
    val point = stringResource(R.string.r3map_pick_point)
    val near = stringResource(R.string.r3map_pick_point_near)
    // The middle of the screen, where the tip of the pin is: the map fills the same box.
    val middle = remember { FloatArray(2) { Float.NaN } }
    var spot by remember { mutableStateOf<PickedSpot?>(null) }
    var looking by remember { mutableStateOf(true) }
    val rests = MapCamera.rests
    LaunchedEffect(rests, what) {
        looking = true
        delay(PICK_LOOKUP_DELAY_MILLIS)
        val map = MapCamera.map?.takeIf { MapAlive.alive(it) } ?: return@LaunchedEffect
        if (middle[0].isNaN()) return@LaunchedEffect
        val at = map.projection.fromScreenLocation(PointF(middle[0], middle[1]))
        val latitude = at.latitude
        val longitude = at.longitude
        val source = MapPick.source
        // Both asked at once: the address from the device, the stop next to it from the provider.
        val found = coroutineScope {
            val details = async { reverseGeocode(context, latitude, longitude) }
            val nearest = async {
                try {
                    source?.nearestPlace(latitude, longitude)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Without an answer the point keeps its plain name.
                    null
                }
            }
            details.await() to nearest.await()
        }
        val nearStop = found.second?.takeIf { it.kind == PlaceKind.Stop }
        val nearName = found.second?.name?.shortStopName()
        val name = mapPickName(found.first?.title, nearName, point) { stop -> String.format(near, stop) }
        spot = PickedSpot(latitude, longitude, name, found.first?.address, nearStop)
        looking = false
    }
    // While the map moves the place under the pin is not known yet.
    val busy = looking || MapCamera.moving
    // The pin lifts while the map moves under it and sets down where it rests.
    val lift by animateFloatAsState(
        if (MapCamera.moving) 1f else 0f,
        if (animate) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium) else tween(0),
        label = "pickPinLift",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                middle[0] = size.width / 2f
                middle[1] = size.height / 2f
            },
    ) {
        // The spot the pin marks, and its shadow growing as it lifts.
        val ink = AppColors.Ink
        Canvas(modifier = Modifier.align(Alignment.Center).size(16.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.18f + 0.1f * lift), radius = 3.dp.toPx() + 3.dp.toPx() * lift, center = centre)
            drawCircle(ink, radius = 2.dp.toPx(), center = centre)
        }
        GlyphIcon(
            glyph = Glyph.Pin,
            color = AppColors.Blue,
            size = PIN_SIZE,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    // The tip of the pin on the middle of the screen, lifted while the map moves.
                    translationY = -(PIN_TIP_SHARE - 0.5f) * PIN_SIZE.toPx() - 10.dp.toPx() * lift
                },
        )
        // The bar on top: back to the search, and what is chosen.
        GlassSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = SEARCH_FIELD_INSET, top = 8.dp, end = SEARCH_FIELD_INSET)
                .fillMaxWidth(),
            shape = RoundedCornerShape(SEARCH_FIELD_CORNER),
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoundButton(glyph = Glyph.Back, description = stringResource(R.string.back), onClick = { MapPick.cancel() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (what == MapPickTarget.Origin) R.string.r3map_pick_title_origin else R.string.r3map_pick_title_destination,
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.r3map_pick_hint),
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // The card at the bottom: the place under the pin and what to take; above it at the right
        // the "centre on me" button of every map page (#205).
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
        val lastLocate = remember { arrayOf<(() -> Unit)?>(null) }
        if (onLocate != null) lastLocate[0] = onLocate
        AnimatedVisibility(
            visible = onLocate != null,
            enter = if (animate) fadeIn(tween(180)) + scaleIn(initialScale = 0.8f) else EnterTransition.None,
            exit = if (animate) fadeOut(tween(180)) + scaleOut(targetScale = 0.8f) else ExitTransition.None,
        ) {
            RoundButton(
                glyph = Glyph.Locate,
                description = stringResource(R.string.my_location),
                onClick = { lastLocate[0]?.invoke() },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val shown = spot
                AnimatedContent(
                    targetState = if (busy || shown == null) null else shown,
                    transitionSpec = { if (animate) fadeIn(tween(180)) togetherWith fadeOut(tween(120)) else fadeIn(tween(0)) togetherWith fadeOut(tween(0)) },
                    label = "pickName",
                ) { known ->
                    if (known == null) {
                        LoadingRow(text = stringResource(R.string.r3map_pick_looking), compact = true)
                    } else {
                        Column {
                            Text(
                                text = known.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val detail = known.detail
                            if (detail != null && detail != known.name) {
                                Text(text = detail, color = AppColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                PrimaryButton(
                    text = stringResource(R.string.r3map_pick_use),
                    onClick = {
                        val known = spot
                        if (known != null && !busy) MapPick.finish(mapPickPlace(known.latitude, known.longitude, known.name))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = if (busy) 0.5f else 1f },
                    glyph = Glyph.Pin,
                )
                // The stop next to the pin, as the start or destination instead of the point.
                val stop = spot?.nearStop
                if (stop != null && !busy) {
                    SecondaryButton(
                        text = stringResource(R.string.r3map_pick_use_stop, stop.name.shortStopName()),
                        onClick = { MapPick.finish(stop.copy(distanceMeters = null)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        }
    }
}

// The size of the pin in the middle of the screen.
private val PIN_SIZE = 44.dp
