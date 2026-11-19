package io.github.jnbp.nextstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.lang.ref.WeakReference
import kotlin.math.abs
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/*
 * The compass of the map: a round button that shows up once the map is turned away from north
 * and turns it back with a short animation; hidden again when the map faces north. The main map
 * tells MapCamera where it faces as it moves (one write per change of the bearing, none while
 * the map only pans), so the button needs nothing passed through the pages: the start page, the
 * map tab and the companion's map each just place it.
 */

/** A map turned less than this either way from north counts as facing north: no compass. */
const val NORTH_TOLERANCE_DEGREES = 1.5

/** How long the map takes to turn back to north, in ms. */
const val NORTH_TURN_MILLIS = 450

/** True when a map turned to [bearing] (degrees, any value) is far enough from north to show the compass. */
fun compassShown(bearing: Double): Boolean {
    if (bearing.isNaN() || bearing.isInfinite()) return false
    return northApart(bearing) > NORTH_TOLERANCE_DEGREES
}

/** How far [bearing] (degrees, any value) is from north, either way: 0 to 180. */
fun northApart(bearing: Double): Double {
    val turn = ((bearing % 360.0) + 360.0) % 360.0
    return if (turn > 180.0) 360.0 - turn else turn
}

/**
 * The camera of the app's main map as far as parts outside AppRoot need it: where it faces,
 * whether it moves, a count of its rests, and the map itself (held weakly) to turn or read it.
 * Written on the main thread by the listeners of the map (see AppRoot), read in composition.
 */
object MapCamera {
    private var held: WeakReference<MapLibreMap>? = null

    /** The bearing of the map, degrees clockwise from north. */
    var bearing by mutableFloatStateOf(0f)
        private set

    /** True from the first move of the camera until it rests again. */
    var moving by mutableStateOf(false)
        private set

    /** Counts up each time the camera comes to rest. */
    var rests by mutableIntStateOf(0)
        private set

    /** The main map, while it lives. */
    val map: MapLibreMap? get() = held?.get()

    /** The main map is ready: it is held (weakly) from now on. */
    fun attach(map: MapLibreMap) {
        held = WeakReference(map)
        bearing = map.cameraPosition.bearing.toFloat()
    }

    /** The main map [map] is gone (destroyed): it is no longer held. */
    fun detach(map: MapLibreMap) {
        if (held?.get() === map) held = null
        moving = false
    }

    /** The camera moved, now facing [bearing]. */
    fun moved(bearing: Double) {
        face(bearing)
        if (!moving) moving = true
    }

    /** The camera came to rest, facing [bearing]. */
    fun rested(bearing: Double) {
        face(bearing)
        moving = false
        rests++
    }

    private fun face(value: Double) {
        val now = value.toFloat()
        // Only a real turn is written: a pan or a zoom changes nothing here.
        if (abs(now - bearing) > 0.05f) bearing = now
    }

    /** Turns the map back to north where it stands, keeping its place, zoom and tilt; at once with [animate] false. */
    fun north(animate: Boolean) {
        val map = map ?: return
        val update = CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(map.cameraPosition).bearing(0.0).build())
        if (animate) map.animateCameraScaled(update, NORTH_TURN_MILLIS) else map.moveCameraSafely(update)
    }
}

// The red of the needle's north end.
private val NEEDLE_NORTH = Color(0xFFE5483B)

/**
 * The compass button of the map, [diameter] round like the other round buttons of the map: it pops
 * in once the map is turned and goes once it faces north again; its needle points to north on
 * the map as the map turns (read while drawing, so a turn costs no recomposition). A tap turns
 * the map back to north. Without animations it appears and goes at once and the map snaps.
 */
@Composable
fun CompassButton(modifier: Modifier = Modifier, diameter: Dp = 48.dp) {
    val shown by remember { derivedStateOf { compassShown(MapCamera.bearing.toDouble()) } }
    val animate = rememberAnimationsOn()
    AnimatedVisibility(
        visible = shown,
        enter = if (animate) {
            fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.6f)
        } else {
            EnterTransition.None
        },
        exit = if (animate) fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.6f) else ExitTransition.None,
        modifier = modifier,
    ) {
        val interactions = remember { MutableInteractionSource() }
        val label = stringResource(R.string.r3map_compass_north)
        val muted = AppColors.Muted
        // Made once and filled anew in each frame of a turn.
        val north = remember { Path() }
        val south = remember { Path() }
        Box(
            modifier = Modifier
                .semantics { contentDescription = label }
                .springPress(interactions)
                .clip(CircleShape)
                .then(glassGround())
                .border(1.dp, glassRim(), CircleShape)
                .clickable(interactionSource = interactions, indication = LocalIndication.current) { MapCamera.north(animate) }
                .size(diameter),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    // North on the map lies the other way round from the turn of the map.
                    .graphicsLayer { rotationZ = -MapCamera.bearing },
            ) {
                val middle = Offset(size.width / 2f, size.height / 2f)
                val half = size.width * 0.2f
                val reach = size.height * 0.46f
                north.rewind()
                north.moveTo(middle.x, middle.y - reach)
                north.lineTo(middle.x + half, middle.y)
                north.lineTo(middle.x - half, middle.y)
                north.close()
                south.rewind()
                south.moveTo(middle.x, middle.y + reach)
                south.lineTo(middle.x + half, middle.y)
                south.lineTo(middle.x - half, middle.y)
                south.close()
                drawPath(north, NEEDLE_NORTH)
                drawPath(south, muted)
            }
        }
    }
}
