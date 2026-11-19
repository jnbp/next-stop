package io.github.jnbp.nextstop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import io.github.jnbp.nextstop.data.WalkDetails
import io.github.jnbp.nextstop.ui.theme.AppColors
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/*
 * The big map of a walk (WalkFocus) with what OpenStreetMap knows along it, whatever the zoom
 * and the settings of the map's own detail layers: the other stop positions of the stops at
 * its ends as small pale dots with "Pos. 3", the stop position it leads to (and the one it
 * starts at) as the pin "Pos. 4" with its line chips, the entrances and exits it passes with
 * their letters, and the lifts and escalators on its way. Drawn by the same code as the small
 * map of the walk (WalkMarks), so both show the same; over the map like the journey's labels,
 * placed anew whenever the camera moved.
 */

/**
 * The details of a walk ([details], see walkDetails) over [map]; [brokenLifts] in red. A walk
 * that ends at the destination's badge ([badgeAt]) moves its signs off the badge (#181c).
 */
@Composable
fun WalkDetailsOverlay(
    map: MapLibreMap,
    cameraTick: () -> Int,
    details: WalkDetails,
    brokenLifts: List<Pair<Double, Double>> = emptyList(),
    badgeAt: Pair<Double, Double>? = null,
) {
    if (details.isEmpty) return
    val accent = AppColors.Blue.toArgb()
    val pins = remember(details, accent) {
        listOfNotNull(details.start, details.target).map { position ->
            PositionPin(position.position, position.pos, position.lines, pinColor(position.lines, position.position, accent))
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Read so the marks are placed anew when the camera moved.
        cameraTick()
        if (!MapAlive.alive(map)) return@Canvas
        val pixel: (Pair<Double, Double>) -> Pair<Float, Float> = { point ->
            val at = map.projection.toScreenLocation(LatLng(point.first, point.second))
            at.x to at.y
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            drawWalkUnder(native, details, density, skip = pins.map { it.point }, pixel = pixel)
            drawWalkOver(native, details, brokenLifts, density, pins, badge = badgeAt, badgeRadiusDp = DESTINATION_BADGE_RADIUS_DP, pixel = pixel)
        }
    }
}
