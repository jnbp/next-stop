package io.github.jnbp.nextstop.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.germanStreetOrder
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.data.stopNamePart
import io.github.jnbp.nextstop.data.streetWithNumber
import io.github.jnbp.nextstop.ui.theme.AppColors

/** What the device's geocoder knows of a place: a name for it and its address in one line. */
data class PlaceDetails(val title: String?, val address: String?)

/**
 * The name and address of the place at [latitude], [longitude], from the device's geocoder;
 * null without one, without network or without an answer.
 */
suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): PlaceDetails? =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
                ?: return@withContext null
            // The street first, then the number ("Musterstraße 32"), whatever the language of the geocoder.
            val street = streetWithNumber(address.thoroughfare, address.subThoroughfare)
            val town = listOfNotNull(address.postalCode, address.locality).joinToString(" ").ifEmpty { null }
            // A named place (a shop, a park) has a feature name other than its house number.
            val feature = address.featureName?.takeIf { name ->
                name.isNotBlank() && name != address.subThoroughfare && name != street && name.any { it.isLetter() } &&
                    // A house number with a letter ("10b") is no name of a place.
                    !name.trim().first().isDigit()
            }
            PlaceDetails(
                title = feature ?: street,
                address = listOfNotNull(street, town).joinToString(", ").ifEmpty { address.getAddressLine(0)?.let { germanStreetOrder(it, address = true) } },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

// The pace of a walk, in metres per minute, and how much longer the way on the streets is than the straight line.
private const val WALK_METERS_PER_MINUTE = 80.0
private const val WALK_DETOUR = 1.25

/**
 * The card of a place marked on the map, at the bottom like the card of a stop: its name, its
 * address, how far it is from [own] on foot, and what to do with it. It springs in when a
 * place is marked and out when it is let go, showing its last content while it leaves. On a
 * wide screen it keeps a readable width instead of stretching.
 */
@Composable
fun PlaceCard(
    position: Pair<Double, Double>?,
    title: String,
    address: String?,
    own: Pair<Double, Double>?,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onSave: () -> Unit,
    onClose: (() -> Unit)? = null,
    onCardTop: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val last = remember { arrayOfNulls<Any>(3) }
    if (position != null) {
        last[0] = position
        last[1] = title
        last[2] = address
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = position != null,
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f) +
                fadeIn(tween(160)),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 3 } +
                scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.9f) +
                fadeOut(tween(140)),
        ) {
            @Suppress("UNCHECKED_CAST")
            val place = (position ?: last[0]) as Pair<Double, Double>? ?: return@AnimatedVisibility
            val shownTitle = if (position != null) title else last[1] as String? ?: title
            val shownAddress = if (position != null) address else last[2] as String?
            GlassSurface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { onCardTop(it.positionInRoot().y) },
                shape = RoundedCornerShape(32.dp),
            ) {
                PlaceContent(
                    position = place,
                    title = shownTitle,
                    address = shownAddress,
                    own = own,
                    onFrom = onFrom,
                    onTo = onTo,
                    onSave = onSave,
                    onClose = onClose,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                    loading = loading && position != null,
                )
            }
        }
    }
}

// Stands for "the address is still loading" among the texts of AnimatedContent.
private const val PLACE_LOADING = "\u0000loading"

/** How a text of the place card gives way to the next: a fade with a small slide upwards. */
private fun placeTextSwap(): ContentTransform =
    (fadeIn(Motion.fadeIn()) + slideInVertically(Motion.settle()) { it / 3 }) togetherWith
        (fadeOut(Motion.fadeOut()) + slideOutVertically(Motion.settle()) { -it / 3 })

/**
 * The address of a place as the line under its [title]: without the part that only repeats
 * the title, so a street address shows as "Street 15" over "12345 Town" and not twice. Null
 * when nothing is left to say.
 */
fun placeSubtitle(title: String, address: String?): String? {
    val text = address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val name = title.trim()
    if (name.isEmpty()) return text
    if (text.equals(name, ignoreCase = true)) return null
    if (text.startsWith(name, ignoreCase = true)) {
        val rest = text.substring(name.length).trimStart(',', ' ', '\u00B7', '-').trim()
        return rest.ifEmpty { null }
    }
    return text
}

/**
 * What the card of a place shows, without its ground: its name, its address, how far it is
 * from [own] on foot, and what to do with it. Used by the floating card and by the start
 * sheet, which shows it in place of its own content while a place is marked. With [onClose]
 * a small button in the corner lets go of the place.
 */
@Composable
fun PlaceContent(
    position: Pair<Double, Double>,
    title: String,
    address: String?,
    own: Pair<Double, Double>?,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onSave: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val context = LocalContext.current
    val subtitle = placeSubtitle(title, address)
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            // Name and address change softly when the address is found (or the pin moves): the
            // old text fades and slides out, the new one in; while it loads a line shimmers.
            Column(modifier = Modifier.weight(1f).animateContentSize(Motion.settle())) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { placeTextSwap() },
                    label = "placeTitle",
                ) { shownTitle ->
                    Text(
                        text = shownTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedContent(
                    targetState = if (loading && subtitle == null) PLACE_LOADING else subtitle,
                    transitionSpec = { placeTextSwap() },
                    label = "placeAddress",
                ) { shownSubtitle ->
                    when (shownSubtitle) {
                        null -> Spacer(modifier = Modifier.height(0.dp))
                        PLACE_LOADING -> SkeletonLine(modifier = Modifier.padding(top = 5.dp).fillMaxWidth(0.55f).height(12.dp))
                        else -> Text(
                            text = shownSubtitle,
                            color = AppColors.Muted,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (onClose != null) {
                RoundButton(
                    glyph = Glyph.Close,
                    description = stringResource(R.string.place_close),
                    onClick = onClose,
                    size = 36.dp,
                    solid = true,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (own != null) {
            val result = FloatArray(1)
            Location.distanceBetween(own.first, own.second, position.first, position.second, result)
            val meters = result[0].toDouble()
            val minutes = ceil(meters * WALK_DETOUR / WALK_METERS_PER_MINUTE).toInt().coerceAtLeast(1)
            val distance = formatDistance(meters)
            Text(
                // Far away only the distance: a time on foot would be no help.
                text = if (walkTimeShown(meters)) {
                    stringResource(R.string.place_walk, distance, walkDurationLabel(minutes.toLong()))
                } else {
                    distance
                },
                color = AppColors.Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.from_here),
                onClick = onFrom,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp, 10.dp, 10.dp, 26.dp),
                minHeight = 50.dp,
            )
            PrimaryButton(
                text = stringResource(R.string.to_here),
                onClick = onTo,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp, 26.dp, 26.dp, 10.dp),
                minHeight = 50.dp,
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.place_save),
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp, 10.dp, 10.dp, 26.dp),
                minHeight = 44.dp,
            )
            SecondaryButton(
                text = stringResource(R.string.share_short),
                onClick = { sharePlace(context, position, title, address) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                minHeight = 44.dp,
            )
            SecondaryButton(
                text = stringResource(R.string.place_google_maps),
                onClick = { openInMaps(context, position) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp, 26.dp, 26.dp, 10.dp),
                minHeight = 44.dp,
            )
        }
    }
}

/**
 * What the start sheet shows for a stop tapped on its map: its name (and the part of a big
 * station it is), how far it is, and what to do with it, its departures first. With [onClose]
 * a small button in the corner lets go of it again.
 */
@Composable
fun StopPickContent(
    stop: Stop,
    onDepartures: () -> Unit,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.name.shortStopName(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val part = stop.name.stopNamePart()
                val distance = stop.distanceMeters?.let { formatDistance(it) }
                val detail = listOfNotNull(part, distance).joinToString(" \u00B7 ")
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        color = AppColors.Muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (onClose != null) {
                RoundButton(
                    glyph = Glyph.Close,
                    description = stringResource(R.string.map_stop_close),
                    onClick = onClose,
                    size = 36.dp,
                    solid = true,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        PrimaryButton(
            text = stringResource(R.string.departures_here),
            onClick = onDepartures,
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            minHeight = 50.dp,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.from_here),
                onClick = onFrom,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp, 10.dp, 10.dp, 26.dp),
                minHeight = 44.dp,
            )
            SecondaryButton(
                text = stringResource(R.string.to_here),
                onClick = onTo,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp, 26.dp, 26.dp, 10.dp),
                minHeight = 44.dp,
            )
        }
    }
}

/** Shares a place as text: its name and address, a geo: link and a link to OpenStreetMap. */
fun sharePlace(context: Context, position: Pair<Double, Double>, title: String, address: String?) {
    val (latitude, longitude) = position
    val coordinates = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)
    val osm = String.format(
        Locale.ROOT,
        "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f",
        latitude, longitude, latitude, longitude,
    )
    val text = listOfNotNull(title, address?.takeIf { it != title }, "geo:$coordinates", osm).joinToString("\n")
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.place_share_chooser)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_share_app, Toast.LENGTH_SHORT).show()
    }
}

/** Opens a place in Google Maps: the app where it is installed, the website otherwise. */
fun openInMaps(context: Context, position: Pair<Double, Double>) {
    val query = String.format(Locale.ROOT, "%.6f,%.6f", position.first, position.second)
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$query"))
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_SHORT).show()
    }
}

/**
 * A duration for people (a journey, a ride): "48 min" below an hour, "2 h 23 min" ("2 Std 23 min")
 * and "2 h" from it, never one big number of minutes (see [durationSpan]).
 */
@Composable
fun durationLabel(minutes: Long): String {
    val (hours, rest) = durationSpan(minutes)
    return when {
        hours == null -> stringResource(R.string.walk_rest_minutes, rest ?: 0L)
        rest == null -> stringResource(R.string.span_whole_hours, hours)
        else -> stringResource(R.string.span_hours, hours, rest)
    }
}

/** [durationLabel] outside of the interface, for texts to share. */
fun durationText(context: Context, minutes: Long): String {
    val (hours, rest) = durationSpan(minutes)
    return when {
        hours == null -> context.getString(R.string.walk_rest_minutes, rest ?: 0L)
        rest == null -> context.getString(R.string.span_whole_hours, hours)
        else -> context.getString(R.string.span_hours, hours, rest)
    }
}

/** A time on foot for people: "12 min" below an hour, "1 h 10 min" ("1 Std 10 min") from it (see walkSpan). */
@Composable
fun walkDurationLabel(minutes: Long): String {
    val (hours, rest) = walkSpan(minutes)
    return if (hours == null) {
        stringResource(R.string.walk_rest_minutes, rest)
    } else {
        stringResource(R.string.span_hours, hours, rest)
    }
}
