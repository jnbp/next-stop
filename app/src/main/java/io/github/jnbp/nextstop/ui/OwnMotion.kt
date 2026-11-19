package io.github.jnbp.nextstop.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// How fast the shown position closes the gap to the predicted one (1/s): critically damped,
// about 95 % of a correction within 0.8 s, without overshoot.
internal const val MOTION_OMEGA = 6.0

private const val METRES_PER_DEGREE = 111_320.0

// A fix farther than this from the shown position is a jump: the dot is put there at once.
private const val JUMP_METRES = 2_000.0

// From this speed (m/s) the dot travels: it never moves backward along its way then.
private const val MOVING_SPEED = 2.0

// Below this speed (m/s) the traveller stands: fixes within the accuracy are jitter.
private const val STANDING_SPEED = 1.0

// No vehicle on the ground is faster (m/s); a faster speed is a bad fix.
private const val MAX_SPEED = 90.0

// The accuracy of a fix that tells none, and the bounds of the jitter gate (metres).
private const val UNKNOWN_ACCURACY = 10.0
private const val GATE_MIN_METRES = 3.0
private const val GATE_MAX_METRES = 50.0

// A dot ahead of a fix of a moving traveller by at most this much (or three times the
// accuracy) waits for it there instead of going back.
private const val AHEAD_MIN_METRES = 30.0

// A fix belongs to the way of the ride within this distance, widened by its accuracy (as in matchPosition).
private const val PATH_NEAR_MIN_METRES = 60.0
private const val PATH_NEAR_MAX_METRES = 220.0

// Fixes are expected this often at first (ms); the horizon of the prediction follows the real pace.
private const val FIRST_INTERVAL_MILLIS = 1_000.0
private const val HORIZON_MIN_MILLIS = 1_000.0
private const val HORIZON_MAX_MILLIS = 6_000.0

// A fix is at most this old when it comes in (ms); older ones count as this old.
private const val MAX_FIX_AGE_MILLIS = 3_000L

// The accuracy circle follows a new accuracy with this time constant (ms).
private const val ACCURACY_TAU_MILLIS = 400.0

// The accuracy circle changes its size only for a change of more than this (metres, or share).
private const val ACCURACY_STEP_METRES = 1.0
private const val ACCURACY_STEP_SHARE = 0.15

// A change of the accuracy circle by less than this share of its radius is taken at once, not
// eased: a standing traveller's wobbling accuracy then costs one frame, not a run of them.
private const val ACCURACY_SNAP_SHARE = 0.25

// A position whose last fix of the device is older than this is stale (ms): the dot shows it
// faded, and the first fresh fix glides there from it instead of jumping (#159).
const val OWN_STALE_MILLIS = 20_000L

// The glide from a stale position to the first fresh fix (ms, times the animator scale).
const val CATCH_UP_MILLIS = 750L

// Farther than this the first fresh fix is no catch-up but another place: the dot is put there (metres).
private const val CATCH_UP_MAX_METRES = 50_000.0

/**
 * The share of the glide from a stale position to a fresh fix done after [elapsedMillis] of
 * [durationMillis]: eased in and out, 0 at the start, 1 from the end on (#159).
 */
fun catchUpShare(elapsedMillis: Long, durationMillis: Long): Double {
    if (durationMillis <= 0L || elapsedMillis >= durationMillis) return 1.0
    if (elapsedMillis <= 0L) return 0.0
    val p = elapsedMillis.toDouble() / durationMillis
    return if (p < 0.5) 4.0 * p * p * p else 1.0 - Math.pow(-2.0 * p + 2.0, 3.0) / 2.0
}

/**
 * True while the own position is stale at [nowMillis] (#159): no fix of the device yet
 * ([lastFixMillis] null), or the last one taken more than [OWN_STALE_MILLIS] ago.
 */
fun ownPositionStale(lastFixMillis: Long?, nowMillis: Long): Boolean =
    lastFixMillis == null || nowMillis - lastFixMillis > OWN_STALE_MILLIS

// Below these the motion has come to rest (metres, m/s).
private const val REST_METRES = 0.05
private const val REST_SPEED = 0.05

/**
 * The motion of the own position between fixes, without Android: pure arithmetic on a few
 * numbers, no allocation per frame. A fix sets an anchor that moves on with the speed and
 * bearing of the fix (dead reckoning) for 1.5 times the interval between fixes at most; the
 * shown position closes the gap to it with a critically damped ease that starts with the
 * speed it had, so neither the position nor its speed jumps when a fix comes in. A fix of a
 * standing traveller within its accuracy is jitter and moves nothing; a moving dot never goes
 * back along its way for a fix that lags behind it. With a [setPath] (the way of the ride the
 * traveller is on), fixes near it are put onto it and the dot moves along it, following the
 * rails rather than cutting corners.
 *
 * Times are milliseconds of one monotonic clock. [evaluate] places the dot for a frame;
 * [lat], [lon] and [accuracy] are then what to show.
 */
class OwnMotion {
    /**
     * The system's animator duration scale: the correction glide towards a fix (and the change
     * of the accuracy circle) lasts this many times as long. The prediction between fixes keeps
     * real time, it tells where the traveller is. Only for a scale above 0; at 0 [smooth] is false.
     */
    var durationScale = 1f
        set(value) {
            val scale = if (value > 0f) value else 1f
            field = scale
            omega = MOTION_OMEGA / scale
            accuracyTau = ACCURACY_TAU_MILLIS * scale
        }

    // The rate of the correction glide (1/s) and the time constant of the accuracy circle (ms) at [durationScale].
    private var omega = MOTION_OMEGA
    private var accuracyTau = ACCURACY_TAU_MILLIS

    /** Predict between fixes; false (battery saver) only eases to each fix and rests there. */
    var extrapolate = true

    /** Ease at all; false (animations off) puts the dot at each fix at once. */
    var smooth = true

    /** True once there is a position. */
    var has = false
        private set

    /** The shown position after the last [evaluate] (degrees). */
    var lat = 0.0
        private set
    var lon = 0.0
        private set

    /** The shown accuracy radius after the last [evaluate] (metres), 0 for none. */
    var accuracy = 0.0
        private set

    /** True while the dot moves along the way of the ride. */
    var onPath = false
        private set

    // The anchor (free mode): position at [anchorT], velocity east/north in m/s.
    private var anchorLat = 0.0
    private var anchorLon = 0.0
    private var anchorT = 0L
    private var velE = 0.0
    private var velN = 0.0

    // The gap of the shown position to the anchor at [anchorT] (metres) and its rate (m/s).
    private var gapE = 0.0
    private var gapN = 0.0
    private var gapVE = 0.0
    private var gapVN = 0.0

    // The same along the way (path mode), in metres from its start.
    private var anchorS = 0.0
    private var velS = 0.0
    private var gapS = 0.0
    private var gapVS = 0.0

    // The way: positions and the metres from its start to each.
    private var pathLat: DoubleArray? = null
    private var pathLon: DoubleArray? = null
    private var pathCum: DoubleArray? = null

    // The direction the dot may not go back against (unit east/north), set while travelling.
    private var forward = false
    private var forwardE = 0.0
    private var forwardN = 0.0

    // The shown velocity (m/s), the shown place along the way, the time of the last evaluate.
    private var shownVE = 0.0
    private var shownVN = 0.0
    private var shownS = 0.0
    private var shownVS = 0.0
    private var lastT = 0L

    // The last fix as it came, for a speed derived from two fixes.
    private var fixLat = 0.0
    private var fixLon = 0.0
    private var fixT = 0L
    private var fixS = Double.NaN

    private var intervalMillis = FIRST_INTERVAL_MILLIS
    private var accuracyTarget = 0.0

    /** When the last fix of the device was taken (ms), null for none: the position is stale long after it (see [staleAt]). */
    var lastFixMillis: Long? = null
        private set

    // The glide from a stale position to the first fresh fix (#159): from where, when, how long.
    private var gliding = false
    private var glideLat = 0.0
    private var glideLon = 0.0
    private var glideS = Double.NaN
    private var glideT0 = 0L
    private var glideMillis = CATCH_UP_MILLIS

    /** True while the position shown is stale at [nowMillis] (see [ownPositionStale]). */
    fun staleAt(nowMillis: Long): Boolean = ownPositionStale(lastFixMillis, nowMillis)

    /** True while the dot glides from a stale position to a fresh fix. */
    val catchingUp: Boolean get() = gliding

    // Scratch of [project] and [pointAt].
    private var hitS = 0.0
    private var hitOff = 0.0
    private var tanE = 0.0
    private var tanN = 0.0
    private var outLat = 0.0
    private var outLon = 0.0

    /** How long the anchor moves on after a fix (ms). */
    val horizonMillis: Double
        get() = if (!extrapolate) 0.0 else (1.5 * intervalMillis).coerceIn(HORIZON_MIN_MILLIS, HORIZON_MAX_MILLIS)

    /**
     * The way of the ride to keep the dot on ([lats] and [lons] of its points), null for none.
     * The same arrays again change nothing.
     */
    fun setPath(lats: DoubleArray?, lons: DoubleArray?) {
        if (lats === pathLat && lons === pathLon) return
        if (onPath) leavePath()
        if (lats == null || lons == null || lats.size < 2 || lats.size != lons.size) {
            pathLat = null
            pathLon = null
            pathCum = null
            fixS = Double.NaN
            return
        }
        val cum = DoubleArray(lats.size)
        for (i in 1 until lats.size) cum[i] = cum[i - 1] + metres(lats[i - 1], lons[i - 1], lats[i], lons[i])
        pathLat = lats
        pathLon = lons
        pathCum = cum
        fixS = Double.NaN
    }

    /** No position any more. */
    fun clear() {
        has = false
        gliding = false
        lastFixMillis = null
        onPath = false
        accuracy = 0.0
        accuracyTarget = 0.0
        fixS = Double.NaN
        intervalMillis = FIRST_INTERVAL_MILLIS
    }

    /**
     * A fix of the device: where ([latitude], [longitude]), how exact ([accuracyMetres], 0 or
     * less for unknown), how fast ([speed] m/s) and whither ([bearing] degrees), each NaN where
     * unknown, taken at [atMillis] and coming in at [nowMillis].
     */
    fun fix(
        latitude: Double,
        longitude: Double,
        accuracyMetres: Double,
        speed: Double,
        bearing: Double,
        atMillis: Long,
        nowMillis: Long,
    ) {
        val wasStale = staleAt(nowMillis)
        takeFix(latitude, longitude, accuracyMetres, speed, bearing, atMillis, nowMillis, wasStale)
        lastFixMillis = lastFixMillis?.let { max(it, atMillis) } ?: atMillis
    }

    private fun takeFix(
        latitude: Double,
        longitude: Double,
        accuracyMetres: Double,
        speed: Double,
        bearing: Double,
        atMillis: Long,
        nowMillis: Long,
        wasStale: Boolean,
    ) {
        val acc = if (accuracyMetres > 0.0) accuracyMetres else UNKNOWN_ACCURACY
        // A small change of the accuracy is no reason for frames: the circle keeps its size.
        if (abs(acc - accuracyTarget) > max(ACCURACY_STEP_METRES, ACCURACY_STEP_SHARE * accuracyTarget)) {
            accuracyTarget = acc
            if (accuracy > 0.0 && abs(acc - accuracy) < ACCURACY_SNAP_SHARE * accuracy) accuracy = acc
        }
        if (!extrapolate) accuracy = accuracyTarget
        if (!has || !smooth) {
            snap(latitude, longitude, nowMillis)
            if (accuracy == 0.0 || !smooth) accuracy = acc
            remember(latitude, longitude, atMillis, Double.NaN)
            return
        }
        evaluate(nowMillis)
        // A fix ends a glide: the ease takes over from where the dot is.
        gliding = false
        val away = metres(lat, lon, latitude, longitude)
        // The first fresh fix after a stale position (the map just opened, #159): the dot glides
        // there, along the way of the ride where it is on it, however far it is (no jump rule).
        if (wasStale && nowMillis - atMillis <= OWN_STALE_MILLIS && away <= CATCH_UP_MAX_METRES) {
            catchUp(latitude, longitude, acc, speed, bearing, atMillis, nowMillis)
            return
        }
        if (away > JUMP_METRES) {
            snap(latitude, longitude, nowMillis)
            accuracy = acc
            remember(latitude, longitude, atMillis, Double.NaN)
            return
        }
        val gap = (atMillis - fixT).toDouble()
        if (gap in 200.0..20_000.0) intervalMillis = 0.7 * intervalMillis + 0.3 * gap
        val age = (nowMillis - atMillis).coerceIn(0L, MAX_FIX_AGE_MILLIS) / 1000.0

        // On the way of the ride when near enough of it.
        if (pathCum != null) {
            project(latitude, longitude)
            val near = (acc * 1.5).coerceIn(PATH_NEAR_MIN_METRES, PATH_NEAR_MAX_METRES)
            if (hitOff <= near) {
                pathFix(hitS, acc, speed, gap, age, nowMillis)
                remember(latitude, longitude, atMillis, hitS)
                return
            }
        }
        if (onPath) leavePath()
        freeFix(latitude, longitude, acc, speed, bearing, gap, age, nowMillis)
        remember(latitude, longitude, atMillis, Double.NaN)
    }

    /** A position without speed (the timetable's guess): the dot eases there and rests. */
    fun ease(latitude: Double, longitude: Double, nowMillis: Long) {
        accuracyTarget = 0.0
        if (!has || !smooth) {
            snap(latitude, longitude, nowMillis)
            accuracy = 0.0
            return
        }
        evaluate(nowMillis)
        gliding = false
        if (metres(lat, lon, latitude, longitude) > JUMP_METRES) {
            snap(latitude, longitude, nowMillis)
            return
        }
        if (onPath) leavePath()
        forward = false
        anchorLat = latitude
        anchorLon = longitude
        anchorT = nowMillis
        velE = 0.0
        velN = 0.0
        gapE = (lon - longitude) * eastScale(latitude)
        gapN = (lat - latitude) * METRES_PER_DEGREE
        gapVE = shownVE
        gapVN = shownVN
        fixS = Double.NaN
    }

    /** Places the dot for the frame at [nowMillis]: [lat], [lon], [accuracy]. */
    fun evaluate(nowMillis: Long) {
        if (!has) return
        val dt = (nowMillis - lastT).coerceAtLeast(0L).toDouble()
        lastT = nowMillis
        accuracy += (accuracyTarget - accuracy) * (1.0 - exp(-dt / accuracyTau))
        val tau = (nowMillis - anchorT).coerceAtLeast(0L) / 1000.0
        val horizon = horizonMillis / 1000.0
        val ahead = min(tau, horizon)
        val going = if (tau < horizon) 1.0 else 0.0
        val fade = exp(-omega * tau)
        if (onPath) {
            val c = gapVS + omega * gapS
            var s = anchorS + velS * ahead + (gapS + c * tau) * fade
            if (forward && !gliding && s < shownS) s = shownS
            val total = pathCum!!.last()
            s = s.coerceIn(0.0, total)
            shownVS = velS * going + (gapVS - omega * c * tau) * fade
            val share = glideShare(nowMillis)
            if (share < 1.0 && !glideS.isNaN()) s = glideS + (s - glideS) * share
            shownS = s
            pointAt(s)
            lat = outLat
            lon = outLon
            if (share < 1.0 && glideS.isNaN()) {
                lat = glideLat + (lat - glideLat) * share
                lon = glideLon + (lon - glideLon) * share
            }
            shownVE = tanE * shownVS
            shownVN = tanN * shownVS
            return
        }
        val cE = gapVE + omega * gapE
        val cN = gapVN + omega * gapN
        val east = velE * ahead + (gapE + cE * tau) * fade
        val north = velN * ahead + (gapN + cN * tau) * fade
        var newLat = anchorLat + north / METRES_PER_DEGREE
        var newLon = anchorLon + east / eastScale(anchorLat)
        // Never back along the way while travelling: the part of the step against it is dropped.
        if (forward) {
            val uE = forwardE
            val uN = forwardN
            val k = eastScale(lat)
            val stepE = (newLon - lon) * k
            val stepN = (newLat - lat) * METRES_PER_DEGREE
            val along = stepE * uE + stepN * uN
            if (along < 0.0) {
                newLon -= along * uE / k
                newLat -= along * uN / METRES_PER_DEGREE
            }
        }
        val share = glideShare(nowMillis)
        if (share < 1.0) {
            newLat = glideLat + (newLat - glideLat) * share
            newLon = glideLon + (newLon - glideLon) * share
        }
        lat = newLat
        lon = newLon
        shownVE = velE * going + (gapVE - omega * cE * tau) * fade
        shownVN = velN * going + (gapVN - omega * cN * tau) * fade
    }

    /** True when the dot rests at [nowMillis] (after [evaluate]): no more frames are needed until the next fix. */
    fun resting(nowMillis: Long): Boolean {
        if (!has) return true
        if (gliding) return false
        if (abs(accuracyTarget - accuracy) > 0.5) return false
        val tau = (nowMillis - anchorT).coerceAtLeast(0L) / 1000.0
        if (tau < horizonMillis / 1000.0) {
            val travelling = if (onPath) velS > REST_SPEED else (abs(velE) > REST_SPEED || abs(velN) > REST_SPEED)
            if (travelling) return false
        }
        val fade = exp(-omega * tau)
        return if (onPath) {
            val c = gapVS + omega * gapS
            abs((gapS + c * tau) * fade) < REST_METRES && abs((gapVS - omega * c * tau) * fade) < REST_SPEED
        } else {
            val cE = gapVE + omega * gapE
            val cN = gapVN + omega * gapN
            abs((gapE + cE * tau) * fade) < REST_METRES && abs((gapN + cN * tau) * fade) < REST_METRES &&
                abs((gapVE - omega * cE * tau) * fade) < REST_SPEED &&
                abs((gapVN - omega * cN * tau) * fade) < REST_SPEED
        }
    }

    // The share of the running glide done at [now] (1 without one); ends it at its end.
    private fun glideShare(now: Long): Double {
        if (!gliding) return 1.0
        val share = catchUpShare(now - glideT0, glideMillis)
        if (share >= 1.0) gliding = false
        return share
    }

    // The first fresh fix after a stale position (#159): the anchor goes to the fix (on the way
    // of the ride where it is near it), and the dot glides there from where it is shown.
    private fun catchUp(latitude: Double, longitude: Double, acc: Double, speed: Double, bearing: Double, atMillis: Long, now: Long) {
        val fromLat = lat
        val fromLon = lon
        val age = (now - atMillis).coerceIn(0L, MAX_FIX_AGE_MILLIS) / 1000.0
        var sp = if (!speed.isNaN() && speed >= 0.0) min(speed, MAX_SPEED) else 0.0
        if (!extrapolate || sp < STANDING_SPEED) sp = 0.0
        var fromS = Double.NaN
        var targetS = Double.NaN
        if (pathCum != null) {
            project(latitude, longitude)
            if (hitOff <= (acc * 1.5).coerceIn(PATH_NEAR_MIN_METRES, PATH_NEAR_MAX_METRES)) {
                targetS = hitS
                project(fromLat, fromLon)
                if (hitOff <= PATH_NEAR_MAX_METRES) fromS = hitS
            }
        }
        snap(latitude, longitude, now)
        accuracy = acc
        if (!targetS.isNaN()) {
            onPath = true
            velS = sp
            anchorS = (targetS + sp * age).coerceIn(0.0, pathCum!!.last())
            gapS = 0.0
            gapVS = 0.0
            shownS = anchorS
            shownVS = sp
            forward = sp >= MOVING_SPEED
            pointAt(anchorS)
            lat = outLat
            lon = outLon
        } else if (sp > 0.0 && !bearing.isNaN()) {
            velE = sin(Math.toRadians(bearing)) * sp
            velN = cos(Math.toRadians(bearing)) * sp
            anchorLat = latitude + velN * age / METRES_PER_DEGREE
            anchorLon = longitude + velE * age / eastScale(latitude)
            shownVE = velE
            shownVN = velN
        }
        remember(latitude, longitude, atMillis, targetS)
        gliding = true
        glideLat = fromLat
        glideLon = fromLon
        glideS = fromS
        glideT0 = now
        glideMillis = (CATCH_UP_MILLIS * durationScale).toLong()
        // Shown where it was until the first frame of the glide.
        lat = fromLat
        lon = fromLon
    }

    private fun freeFix(latitude: Double, longitude: Double, acc: Double, speed: Double, bearing: Double, gap: Double, age: Double, now: Long) {
        val k = eastScale(latitude)
        val dE = (longitude - fixLon) * k
        val dN = (latitude - fixLat) * METRES_PER_DEGREE
        val moved = sqrt(dE * dE + dN * dN)
        var sp = if (!speed.isNaN() && speed >= 0.0) {
            speed
        } else if (gap in 300.0..10_000.0 && moved > acc) {
            moved / (gap / 1000.0)
        } else {
            0.0
        }
        sp = min(sp, MAX_SPEED)
        var uE = 0.0
        var uN = 0.0
        if (!bearing.isNaN()) {
            uE = sin(Math.toRadians(bearing))
            uN = cos(Math.toRadians(bearing))
        } else if (moved > 0.5) {
            uE = dE / moved
            uN = dN / moved
        } else {
            val shown = sqrt(shownVE * shownVE + shownVN * shownVN)
            if (shown > 0.1) {
                uE = shownVE / shown
                uN = shownVN / shown
            } else {
                sp = 0.0
            }
        }
        val standing = sp < STANDING_SPEED
        if (!extrapolate) sp = 0.0
        anchorT = now
        forward = false
        if (standing && (metres(lat, lon, latitude, longitude) < acc.coerceIn(GATE_MIN_METRES, GATE_MAX_METRES) ||
                freeAheadOf(latitude, longitude, acc))
        ) {
            // Jitter of a standing traveller: the dot comes to rest where it is going.
            velE = 0.0
            velN = 0.0
            restHere()
            return
        }
        if (sp < STANDING_SPEED) {
            velE = 0.0
            velN = 0.0
            anchorLat = latitude
            anchorLon = longitude
        } else {
            velE = uE * sp
            velN = uN * sp
            // The fix moved on to now.
            anchorLat = latitude + velN * age / METRES_PER_DEGREE
            anchorLon = longitude + velE * age / k
            if (sp >= MOVING_SPEED) {
                // A dot ahead of the fix waits for it there.
                val aheadE = (lon - anchorLon) * k
                val aheadN = (lat - anchorLat) * METRES_PER_DEGREE
                val ahead = aheadE * uE + aheadN * uN
                if (ahead > 0.0 && ahead <= max(AHEAD_MIN_METRES, 3.0 * acc)) {
                    anchorLat += ahead * uN / METRES_PER_DEGREE
                    anchorLon += ahead * uE / k
                    // Slower until the fixes have caught up, by the next one.
                    val slower = min(sp, ahead / (intervalMillis / 1000.0))
                    velE -= uE * slower
                    velN -= uN * slower
                }
                forward = true
                forwardE = uE
                forwardN = uN
            }
        }
        gapE = (lon - anchorLon) * eastScale(anchorLat)
        gapN = (lat - anchorLat) * METRES_PER_DEGREE
        gapVE = shownVE - velE
        gapVN = shownVN - velN
        if (forward) {
            // Slowing down: the anchor goes as far ahead as the dot coasts, so the ease only
            // takes speed away and never turns it back.
            val gapAlong = gapE * forwardE + gapN * forwardN
            val rateAlong = gapVE * forwardE + gapVN * forwardN
            if (rateAlong > 0.0 && gapAlong > -rateAlong / omega) {
                val shift = gapAlong + rateAlong / omega
                anchorLat += shift * forwardN / METRES_PER_DEGREE
                anchorLon += shift * forwardE / eastScale(anchorLat)
                gapE -= shift * forwardE
                gapN -= shift * forwardN
            }
        }
    }

    private fun pathFix(s: Double, acc: Double, speed: Double, gap: Double, age: Double, now: Long) {
        if (!onPath) enterPath()
        val moved = if (fixS.isNaN()) 0.0 else s - fixS
        var sp = if (!speed.isNaN() && speed >= 0.0) {
            speed
        } else if (gap in 300.0..10_000.0 && abs(moved) > acc) {
            max(0.0, moved / (gap / 1000.0))
        } else {
            0.0
        }
        sp = min(sp, MAX_SPEED)
        val standing = sp < STANDING_SPEED
        if (!extrapolate) sp = 0.0
        anchorT = now
        forward = false
        val behind = shownS - s
        if (standing && (abs(behind) < acc.coerceIn(GATE_MIN_METRES, GATE_MAX_METRES) ||
                (shownVS >= STANDING_SPEED && behind > 0.0 && behind <= max(AHEAD_MIN_METRES, 3.0 * acc)))
        ) {
            velS = 0.0
            anchorS = shownS + shownVS / omega
            gapS = shownS - anchorS
            gapVS = shownVS
            return
        }
        if (sp < STANDING_SPEED) {
            velS = 0.0
            anchorS = s
        } else {
            velS = sp
            anchorS = s + sp * age
            val ahead = shownS - anchorS
            if (sp >= MOVING_SPEED) {
                if (ahead > 0.0 && ahead <= max(AHEAD_MIN_METRES, 3.0 * acc)) {
                    anchorS = shownS
                    velS -= min(sp, ahead / (intervalMillis / 1000.0))
                }
                forward = true
            }
        }
        gapS = shownS - anchorS
        gapVS = shownVS - velS
        if (forward && gapVS > 0.0 && gapS > -gapVS / omega) {
            anchorS += gapS + gapVS / omega
            gapS = -gapVS / omega
        }
    }

    // True when the dot, travelling until now, is ahead of the fix along its way, by little:
    // a traveller who stopped there; the dot rests where it is going instead of going back.
    private fun freeAheadOf(latitude: Double, longitude: Double, acc: Double): Boolean {
        val shown = sqrt(shownVE * shownVE + shownVN * shownVN)
        if (shown < STANDING_SPEED) return false
        val k = eastScale(lat)
        val aheadE = (lon - longitude) * k
        val aheadN = (lat - latitude) * METRES_PER_DEGREE
        val ahead = (aheadE * shownVE + aheadN * shownVN) / shown
        return ahead > 0.0 && metres(lat, lon, latitude, longitude) <= max(AHEAD_MIN_METRES, 3.0 * acc)
    }

    // The anchor where the shown motion comes to rest by itself: no overshoot, no way back.
    private fun restHere() {
        anchorLat = lat + shownVN / omega / METRES_PER_DEGREE
        anchorLon = lon + shownVE / omega / eastScale(lat)
        gapE = (lon - anchorLon) * eastScale(anchorLat)
        gapN = (lat - anchorLat) * METRES_PER_DEGREE
        gapVE = shownVE
        gapVN = shownVN
    }

    private fun enterPath() {
        project(lat, lon)
        shownS = hitS
        shownVS = max(0.0, shownVE * tanE + shownVN * tanN)
        onPath = true
    }

    // Back to free motion from the shown state, which goes on as it was and comes to rest.
    private fun leavePath() {
        onPath = false
        forward = false
        velE = 0.0
        velN = 0.0
        anchorT = lastT
        restHere()
    }

    private fun snap(latitude: Double, longitude: Double, now: Long) {
        has = true
        forward = false
        onPath = false
        anchorLat = latitude
        anchorLon = longitude
        anchorT = now
        lastT = now
        velE = 0.0
        velN = 0.0
        gapE = 0.0
        gapN = 0.0
        gapVE = 0.0
        gapVN = 0.0
        shownVE = 0.0
        shownVN = 0.0
        lat = latitude
        lon = longitude
    }

    private fun remember(latitude: Double, longitude: Double, at: Long, s: Double) {
        fixLat = latitude
        fixLon = longitude
        fixT = at
        fixS = s
    }

    // The nearest point of the way to the position: [hitS] along it, [hitOff] off it, [tanE]/[tanN] its direction.
    private fun project(latitude: Double, longitude: Double) {
        val lats = pathLat!!
        val lons = pathLon!!
        val cum = pathCum!!
        val k = eastScale(latitude)
        var best = Double.MAX_VALUE
        for (i in 0 until lats.size - 1) {
            val ax = (lons[i] - longitude) * k
            val ay = (lats[i] - latitude) * METRES_PER_DEGREE
            val bx = (lons[i + 1] - longitude) * k
            val by = (lats[i + 1] - latitude) * METRES_PER_DEGREE
            val sx = bx - ax
            val sy = by - ay
            val length2 = sx * sx + sy * sy
            val t = if (length2 <= 1e-9) 0.0 else (-(ax * sx + ay * sy) / length2).coerceIn(0.0, 1.0)
            val px = ax + sx * t
            val py = ay + sy * t
            val d = sqrt(px * px + py * py)
            if (d < best) {
                best = d
                hitS = cum[i] + (cum[i + 1] - cum[i]) * t
                hitOff = d
                val length = sqrt(length2)
                tanE = if (length > 0.0) sx / length else 0.0
                tanN = if (length > 0.0) sy / length else 0.0
            }
        }
    }

    // The point [s] metres along the way into [outLat]/[outLon], its direction into [tanE]/[tanN].
    private fun pointAt(s: Double) {
        val lats = pathLat!!
        val lons = pathLon!!
        val cum = pathCum!!
        var low = 0
        var high = cum.size - 1
        while (high - low > 1) {
            val mid = (low + high) ushr 1
            if (cum[mid] <= s) low = mid else high = mid
        }
        val length = cum[high] - cum[low]
        val t = if (length > 0.0) ((s - cum[low]) / length).coerceIn(0.0, 1.0) else 0.0
        outLat = lats[low] + (lats[high] - lats[low]) * t
        outLon = lons[low] + (lons[high] - lons[low]) * t
        if (length > 0.0) {
            tanE = (lons[high] - lons[low]) * eastScale(outLat) / length
            tanN = (lats[high] - lats[low]) * METRES_PER_DEGREE / length
        }
    }
}

private fun eastScale(latitude: Double): Double = METRES_PER_DEGREE * cos(Math.toRadians(latitude))

private fun metres(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
    val north = (otherLatitude - latitude) * METRES_PER_DEGREE
    val east = (otherLongitude - longitude) * eastScale(latitude)
    return sqrt(north * north + east * east)
}

// The own dot is placed at most about this often (frames per second): on a faster display it
// takes every second (or third …) frame, evenly, and the map is drawn again only that often.
private const val OWN_FRAME_CAP_HZ = 60.0

// A frame further than this from the last one (ns) ends a run: the next one is taken at once.
private const val FRAME_GAP_NANOS = 50_000_000L

/**
 * Which frames of the display the own dot's frame loop takes (see PositionDot): every frame up to
 * about 60 Hz, every second one at 120 or 144 Hz, never unevenly (a 90 Hz display keeps all of
 * its frames: every second one there would be slower than 60). [due] is given each frame's time.
 */
class FramePace(private val capHz: Double = OWN_FRAME_CAP_HZ) {
    // The frame interval of the display as measured (ns), 0 while not known.
    var intervalNanos = 0L
        private set
    private var lastFrame = 0L
    private var skipped = 0

    /** The frames taken per frame shown: 1 up to about [capHz], else the whole share that keeps it near. */
    fun stride(): Int {
        if (intervalNanos <= 0L) return 1
        val ratio = 1e9 / capHz / intervalNanos
        return max(1, (ratio + 0.1).toInt())
    }

    /** True when the frame at [frameNanos] is to be taken. */
    fun due(frameNanos: Long): Boolean {
        val delta = if (lastFrame == 0L) Long.MAX_VALUE else frameNanos - lastFrame
        lastFrame = frameNanos
        if (delta <= 0L || delta > FRAME_GAP_NANOS) {
            skipped = 0
            return true
        }
        // The smallest steady gap is the display's; a late frame (jank) does not count.
        intervalNanos = when {
            intervalNanos == 0L -> delta
            delta < intervalNanos -> (intervalNanos + delta * 3) / 4
            delta < intervalNanos * 3 / 2 -> (intervalNanos * 7 + delta) / 8
            else -> intervalNanos
        }
        if (++skipped >= stride()) {
            skipped = 0
            return true
        }
        return false
    }
}
