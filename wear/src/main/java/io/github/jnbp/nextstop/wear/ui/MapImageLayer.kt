package io.github.jnbp.nextstop.wear.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.MapImage
import io.github.jnbp.nextstop.wear.data.MapImageRequest
import io.github.jnbp.nextstop.wear.data.MapImageRetry
import io.github.jnbp.nextstop.wear.data.MapImageStatus
import io.github.jnbp.nextstop.wear.data.MapImages
import io.github.jnbp.nextstop.wear.data.WatchStore

/**
 * The map picture of the phone to draw ([image], null: the sketch alone) and how the last
 * request went ([status]: [MapImageStatus.Waiting] also while a failure is retried quietly, see
 * [MapImageRetry]); [retry] asks the phone again at once (the "Try again" of the status line).
 */
class MapImageState(val image: MapImage?, val status: MapImageStatus, val retry: () -> Unit = {})

/** Whether the map pictures are dark: the watch's map style setting, and always in [ambient] mode. */
@Composable
fun rememberMapDark(ambient: Boolean = false): Boolean {
    val style by WatchStore.mapStyle.collectAsState()
    return style.dark(ambient)
}

/**
 * The map picture of the phone for [view] (null: none wanted). A kept picture that covers the
 * view (memory or disk) is taken at once; else a kept one near it is shown meanwhile, and the
 * phone is asked once the view has rested for [MAP_IMAGE_DEBOUNCE_MILLIS] (at once while
 * nothing is shown yet). With nothing to show yet the phone sends a small quick picture
 * first, then the full one. A request under way is not given up for a newer view (the view
 * moves with every new position, and a picture takes the phone seconds): once it is answered,
 * the newest view is asked for if still needed. Without an answer it asks again: the first
 * time quietly after a moment (the status stays "loading"), then every
 * [MapImageRetry.PAUSE_MILLIS] with the problem shown, or at once on [MapImageState.retry].
 * The picture is the latest received, which may be one of an
 * earlier view: [drawMapImage] puts it at its own place and scale meanwhile. A picture of the
 * other style (light, dark) is dropped at once.
 */
@Composable
fun rememberMapImage(slot: String, view: MapImageRequest?): MapImageState {
    val context = LocalContext.current
    var image by remember(slot) { mutableStateOf<MapImage?>(null) }
    var status by remember(slot) { mutableStateOf(MapImageStatus.Waiting) }
    // Failed requests in a row (see MapImageRetry), and the taps on "Try again".
    var failures by remember(slot) { mutableStateOf(0) }
    val retries = remember(slot) { Channel<Unit>(Channel.CONFLATED) }
    val viewState = rememberUpdatedState(view)
    LaunchedEffect(slot) {
        snapshotFlow { viewState.value }.conflate().collect { first ->
            if (first == null) return@collect
            if (image?.frame?.dark != null && image?.frame?.dark != first.dark) image = null
            if (image?.covers(first) == true) return@collect
            val asked = System.currentTimeMillis()
            val kept = MapImages.cached(context, first)
            if (kept != null) {
                image = kept
                status = MapImageStatus.Ready
                Log.i(MapImages.TAG, "watch: slot=$slot picture from the cache in ${System.currentTimeMillis() - asked} ms")
                return@collect
            }
            // Nothing kept covers the view: a kept one near it meanwhile, when it shows more than the one on screen.
            val near = MapImages.nearest(context, first)
            if (near != null && near.frame.overlap(first) > (image?.frame?.overlap(first) ?: 0.0)) {
                image = near
                Log.i(MapImages.TAG, "watch: slot=$slot near picture from the cache meanwhile in ${System.currentTimeMillis() - asked} ms")
            }
            if (image != null) delay(MAP_IMAGE_DEBOUNCE_MILLIS)
            while (true) {
                // The newest view: it may have moved while resting or waiting.
                val wanted = viewState.value ?: break
                if (image?.covers(wanted) == true) break
                // A problem shown stays until a picture comes or the user asks again.
                if (image == null && !MapImageRetry.showProblem(status, failures)) status = MapImageStatus.Waiting
                // A quick picture first while little of the view is shown.
                val withQuick = (image?.frame?.overlap(wanted) ?: 0.0) < QUICK_BELOW_OVERLAP
                val result = MapImages.fetch(
                    context,
                    slot,
                    wanted.withMargin(),
                    onQuick = if (withQuick) {
                        { quick ->
                            image = quick
                            status = MapImageStatus.Ready
                        }
                    } else {
                        null
                    },
                )
                if (result.image != null) {
                    image = result.image
                    status = MapImageStatus.Ready
                    failures = 0
                    break
                }
                failures++
                // The first failure is retried quietly: the map keeps loading meanwhile.
                val shown = MapImageRetry.showProblem(result.status, failures)
                status = if (shown) result.status else MapImageStatus.Waiting
                Log.i(MapImages.TAG, "watch: slot=$slot failure $failures (${result.status}), ${if (shown) "shown" else "retried quietly"}")
                // A quick picture or a near one stays shown. A tap on "Try again" asks at once.
                if (withTimeoutOrNull(MapImageRetry.pauseAfter(failures)) { retries.receive() } != null) {
                    Log.i(MapImages.TAG, "watch: slot=$slot asked again by the user")
                    // Shimmers again while asked; a failure of this try is shown at once.
                    failures = MapImageRetry.QUIET_RETRIES
                    status = MapImageStatus.Waiting
                }
            }
        }
    }
    return MapImageState(image, status, retry = { retries.trySend(Unit) })
}

/**
 * One short line on how the map picture of the phone is coming along, shown while there is
 * none: "Map from phone…" with a soft shimmer while it loads (also while a failure is retried
 * quietly), "No phone connected", or "Map not loaded · Try again", which asks again on a tap
 * ([onRetry]); nothing once it is there.
 */
@Composable
fun MapImageStatusLine(status: MapImageStatus, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val text = when (status) {
        MapImageStatus.Ready -> return
        MapImageStatus.Waiting -> R.string.map_from_phone
        MapImageStatus.NoPhone -> R.string.map_no_phone
        MapImageStatus.NoAnswer, MapImageStatus.NotDrawn -> R.string.map_retry
    }
    val canRetry = onRetry != null && (status == MapImageStatus.NoAnswer || status == MapImageStatus.NotDrawn)
    val pill = modifier
        .clip(RoundedCornerShape(50))
        .background(Color.Black.copy(alpha = 0.55f))
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelSmall,
        color = if (canRetry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = when {
            canRetry -> pill.clickable { onRetry?.invoke() }
            // Loading: the band of light sweeps over it (still with animations off).
            status == MapImageStatus.Waiting -> pill.watchShimmer()
            else -> pill
        }.padding(horizontal = 8.dp, vertical = if (canRetry) 4.dp else 2.dp),
    )
}

/**
 * Draws [image] under a view whose middle is the world point ([viewX], [viewY]) with
 * [pixelsPerUnit] screen pixels per unit of the world (see WebMercator), stretched over the
 * area the phone drew ([MapImage.frame], see [MapFrame.placement]) wherever that lies in the view.
 */
fun DrawScope.drawMapImage(image: MapImage, viewX: Double, viewY: Double, pixelsPerUnit: Double, alpha: Float = 1f) {
    val place = image.frame.placement(viewX, viewY, pixelsPerUnit, size.width.toDouble(), size.height.toDouble())
    // Far too small or too large to tell anything (a zoom far from the picture's): not drawn.
    if (place.width < 8 || place.width > MAX_DRAWN_PIXELS || place.height > MAX_DRAWN_PIXELS) return
    val left = place.left.roundToInt()
    val top = place.top.roundToInt()
    drawImage(
        image.bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.bitmap.width, image.bitmap.height),
        dstOffset = IntOffset(left, top),
        // From the rounded edges, so neighbouring pixels of the screen are not left out.
        dstSize = IntSize((place.left + place.width).roundToInt() - left, (place.top + place.height).roundToInt() - top),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/** The small "© OSM" of the map data, measured to be drawn with [drawMapAttribution]. */
fun TextMeasurer.measureAttribution(text: String, dark: Boolean): TextLayoutResult =
    measure(text, TextStyle(color = if (dark) Color(0xFFBDBDBD) else Color(0xFF424242), fontSize = 8.sp))

/**
 * Draws the attribution [text] on a faint ground: at the right edge in the middle (seen on a
 * round screen too) or, with [corner], in the lower right corner (a small map in a card).
 */
fun DrawScope.drawMapAttribution(text: TextLayoutResult, dark: Boolean, corner: Boolean = false) {
    val pad = 2.dp.toPx()
    val textSize = Size(text.size.width.toFloat(), text.size.height.toFloat())
    val topLeft = if (corner) {
        Offset(size.width - textSize.width - 8.dp.toPx(), size.height - textSize.height - 4.dp.toPx())
    } else {
        Offset(size.width - textSize.width - 6.dp.toPx(), size.height / 2 - textSize.height / 2)
    }
    drawRect(
        color = if (dark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f),
        topLeft = Offset(topLeft.x - pad, topLeft.y),
        size = Size(textSize.width + 2 * pad, textSize.height),
    )
    drawText(text, topLeft = topLeft)
}

// The view rests this long before a new picture is asked for: a zoom or a drag ends first.
const val MAP_IMAGE_DEBOUNCE_MILLIS = 400L

// The phone draws a quick picture first while less than this share of the view is shown.
private const val QUICK_BELOW_OVERLAP = 0.9
private const val MAX_DRAWN_PIXELS = 16_000.0
