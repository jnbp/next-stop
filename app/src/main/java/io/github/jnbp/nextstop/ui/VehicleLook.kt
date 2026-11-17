package io.github.jnbp.nextstop.ui

import androidx.compose.ui.graphics.toArgb
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import io.github.jnbp.nextstop.data.Vehicle
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.replacedProduct
import io.github.jnbp.nextstop.data.replacedProductOrGuess
import io.github.jnbp.nextstop.data.ridePlace
import kotlin.math.atan2
import kotlin.math.cos

/*
 * What a vehicle on the map looks like, worked out once per vehicle and not once per frame of
 * its glide: the text of its badge (which runs regular expressions over its line and
 * direction), its colours (which look the line up in the colour tables, hex text made with
 * String.format) and the values of its map feature, ready to be shared by every feature made
 * for it. Before 0.1.3 all of that was made anew for each vehicle about 30 times per second,
 * which alone allocated tens of megabytes per second while the map was in view.
 */

/** The look of one vehicle on the map, and the parts of its feature made once (see pushVehicles). */
internal class VehicleLook(
    val line: String,
    val product: String,
    val direction: String,
    val realtime: Boolean,
    val providerColor: Int?,
    val providerTextColor: Int?,
    val label: String,
    val argb: Int,
    val hex: String,
    val rank: Int,
    val major: Boolean,
    tripId: String,
    ink: String,
) {
    val tripValue = JsonPrimitive(tripId)
    val labelValue = JsonPrimitive(label)
    val colorValue = JsonPrimitive(hex)
    val inkValue = JsonPrimitive(ink)
    val majorValue = JsonPrimitive(major)
    val rankValue = JsonPrimitive(rank)

    // The badge image of this look in the style it was made for (see badge).
    private var badgeStyle: Any? = null
    private var badgeValue: JsonPrimitive? = null

    /** The id of its badge image in [style], made there by [make] the first time. */
    fun badge(style: Any, make: () -> String): JsonPrimitive {
        val known = badgeValue
        if (known != null && badgeStyle === style) return known
        val made = JsonPrimitive(make())
        badgeStyle = style
        badgeValue = made
        return made
    }

    // The offsets of text and live dot for a badge right on its point (the usual case), made once.
    private var restText: JsonArray? = null
    private var restPulse: JsonArray? = null

    /** The offset of the text for a badge moved by ([dx], [dy]) dp; shared where it is not moved. */
    fun textOffset(dx: Float, dy: Float, textSp: Float): JsonArray {
        if (dx == 0f && dy == 0f) restText?.let { return it }
        val made = JsonArray().apply {
            add(dx / textSp)
            add(dy / textSp)
        }
        if (dx == 0f && dy == 0f) restText = made
        return made
    }

    /** The offset of the pulse of the live dot for a badge moved by ([dx], [dy]) dp; shared where it is not moved. */
    fun pulseOffset(dx: Float, dy: Float): JsonArray {
        if (dx == 0f && dy == 0f) restPulse?.let { return it }
        val corner = liveDotOffset(label.length)
        val made = JsonArray().apply {
            add(dx + corner[0])
            add(dy + corner[1])
        }
        if (dx == 0f && dy == 0f) restPulse = made
        return made
    }

    /** True when [vehicle] looks the same as this: a refresh of the same run. */
    fun fits(vehicle: Vehicle): Boolean =
        vehicle.line == line && vehicle.product == product && vehicle.direction == direction &&
            vehicle.realtime == realtime && vehicle.providerColor == providerColor &&
            vehicle.providerTextColor == providerTextColor
}

/** The look of [vehicle]: [before] where the vehicle still looks the same, else a new one. */
internal fun vehicleLook(vehicle: Vehicle, before: VehicleLook?): VehicleLook {
    if (before != null && before.fits(vehicle)) return before
    // A rail replacement bus ("Ersatzverkehr"): "SEV" and the line it stands in for, in that line's colour.
    val replacement = vehicle.railReplacement
    val replaced = replacement?.replaced
    val color = if (replaced != null) {
        lineColor(replaced, replacement?.replacedProductOrGuess ?: replacedProduct(replaced), vehicle.ridePlace)
    } else {
        lineColor(vehicle)
    }
    return VehicleLook(
        line = vehicle.line,
        product = vehicle.product,
        direction = vehicle.direction,
        realtime = vehicle.realtime,
        providerColor = vehicle.providerColor,
        providerTextColor = vehicle.providerTextColor,
        label = vehicleBadgeLabel(vehicle),
        argb = color.toArgb(),
        hex = hexColor(color),
        rank = vehicleRank(vehicle.product),
        major = isMajorProduct(vehicle.product),
        tripId = vehicle.tripId,
        ink = hexColor(lineInk(vehicle, color)),
    )
}

/**
 * The position after [progress] (0 to 1) of the way along [path] into [into] (latitude,
 * longitude) at [at]: as pointOnPath, without making a pair for it.
 */
internal fun placeOnPath(path: List<Pair<Double, Double>>, progress: Float, into: DoubleArray, at: Int = 0) {
    if (path.size < 2) {
        val only = path.first()
        into[at] = only.first
        into[at + 1] = only.second
        return
    }
    val scaled = progress.coerceIn(0f, 1f) * (path.size - 1)
    val index = scaled.toInt().coerceAtMost(path.size - 2)
    val part = scaled - index
    val from = path[index]
    val to = path[index + 1]
    into[at] = from.first + (to.first - from.first) * part
    into[at + 1] = from.second + (to.second - from.second) * part
}

/**
 * The direction a vehicle at [progress] of its [path] moves on the ground, as groundDirection,
 * into [into] (share to the north, share to the east) without making pairs; [scratch] holds
 * four numbers of work. False where the way does not move there (into is left alone).
 */
internal fun groundDirectionInto(path: List<Pair<Double, Double>>, progress: Float, into: DoubleArray, scratch: DoubleArray): Boolean {
    if (path.size < 2) return false
    val ahead = (progress.coerceIn(0f, 1f) + 0.1f).coerceAtMost(1f)
    placeOnPath(path, (ahead - 0.1f).coerceAtLeast(0f), scratch, 0)
    placeOnPath(path, ahead, scratch, 2)
    val fromLatitude = scratch[0]
    val fromLongitude = scratch[1]
    val toLatitude = scratch[2]
    val toLongitude = scratch[3]
    if (kotlin.math.abs(toLatitude - fromLatitude) < 1e-7 && kotlin.math.abs(toLongitude - fromLongitude) < 1e-7) return false
    // As bearingDegrees: the compass bearing, then its north and east shares.
    val north = toLatitude - fromLatitude
    val east = (toLongitude - fromLongitude) * cos(Math.toRadians((fromLatitude + toLatitude) / 2.0))
    if (north == 0.0 && east == 0.0) return false
    val angle = Math.toRadians((Math.toDegrees(atan2(east, north)) + 360.0) % 360.0)
    into[0] = cos(angle)
    into[1] = kotlin.math.sin(angle)
    return true
}
