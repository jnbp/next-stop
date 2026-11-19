package io.github.jnbp.nextstop.data

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The fare zones of the VBB around Berlin: [A] inside the S-Bahn ring (the ring stations
 * included), [B] the rest of Berlin, [C] the surroundings up to roughly 15 km beyond the
 * city limit (Potsdam, the airport BER, Oranienburg, Bernau, Erkner ...). [Outside] is a
 * stop beyond C or outside the VBB: a Berlin AB/ABC ticket does not cover it.
 */
enum class FareZone { A, B, C, Outside }

/**
 * Which fare zones a journey touches, from the stops of its rides. A hint only, not a fare
 * calculation: it says when a journey leaves the zones A and B, so a Berlin AB ticket is not
 * enough. Kept free of Android, so it can be tested on the JVM.
 *
 * The data, small enough to live in the code:
 * - zone A: the polygon through the 27 stations of the Ringbahn (positions from the station
 *   asset built from OpenStreetMap, ODbL), plus a margin around each ring station, since the
 *   ring stations themselves are in A;
 * - Berlin or Brandenburg: the VBB stop number "900XXnnnn" carries its area in XX, 00 to 19
 *   are the districts of Berlin, 20 and up the districts of Brandenburg (checked against all
 *   321 stations of the asset); a DHID ("de:11000:...") says it by its state code (11 Berlin,
 *   12 Brandenburg);
 * - zone C: a stop of Brandenburg within [C_RADIUS_KM] of the centre. The real border of C
 *   follows municipalities; the circle is an approximation that keeps Potsdam, BER,
 *   Oranienburg, Bernau, Strausberg and Königs Wusterhausen in and leaves Nauen, Fürstenwalde
 *   and Eberswalde out.
 */
object VbbZones {
    /** The zone of a stop; null when nothing about the place says where it lies. */
    fun zoneOf(place: Stop?): FareZone? {
        if (place == null) return null
        val area = areaOf(place.id)
        val position = place.position
        return when (area) {
            Area.Berlin -> if (position != null && inRing(position)) FareZone.A else FareZone.B
            Area.Brandenburg -> when {
                position == null -> FareZone.C
                kmBetween(position, CENTRE) <= C_RADIUS_KM -> FareZone.C
                else -> FareZone.Outside
            }
            Area.Elsewhere -> FareZone.Outside
            // No id that tells: only the ring says something by position alone.
            null -> if (position != null && inRing(position)) FareZone.A else null
        }
    }

    /** The zones the rides of [journey] touch: where they are boarded and left, and the stops between where known. */
    fun zonesOf(journey: Journey): Set<FareZone> =
        journey.legs.filter { !it.walking }
            .flatMap { leg -> listOf(leg.fromPlace, leg.toPlace) + leg.stopovers.map { it.place } }
            .mapNotNull { zoneOf(it) }
            .toSet()

    /**
     * True when [journey] needs the zone C: it touches C and stays within A, B and C. A journey
     * that reaches beyond C, or never touches Berlin, gets no hint (outside the scope of
     * the Berlin tickets).
     */
    fun needsZoneC(journey: Journey): Boolean {
        val zones = zonesOf(journey)
        return FareZone.C in zones && FareZone.Outside !in zones && (FareZone.A in zones || FareZone.B in zones)
    }

    /** The zones as the ticket names them: "AB", "BC", "ABC"; empty for none of A, B, C. */
    fun label(zones: Set<FareZone>): String =
        listOf(FareZone.A, FareZone.B, FareZone.C).filter { it in zones }.joinToString("") { it.name }

    internal enum class Area { Berlin, Brandenburg, Elsewhere }

    /**
     * Berlin, Brandenburg or elsewhere by the id of a stop; null when the id says nothing
     * (an address, a point of interest, a provider with its own numbers).
     */
    internal fun areaOf(id: String): Area? {
        DHID.find(id)?.let { match ->
            return when (match.groupValues[1]) {
                "11" -> Area.Berlin
                "12" -> Area.Brandenburg
                else -> Area.Elsewhere
            }
        }
        VBB_ID.find(id)?.let { match ->
            val code = match.groupValues[1].toInt()
            return if (code < FIRST_BRANDENBURG_CODE) Area.Berlin else Area.Brandenburg
        }
        return null
    }

    /** True inside the Ringbahn or at one of its stations. */
    fun inRing(position: Pair<Double, Double>): Boolean =
        RING.any { kmBetween(position, it) <= RING_STATION_KM } || insidePolygon(position, RING)

    /** Even-odd test of a point against a polygon of latitude and longitude. */
    internal fun insidePolygon(point: Pair<Double, Double>, polygon: List<Pair<Double, Double>>): Boolean {
        val (y, x) = point
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val (yi, xi) = polygon[i]
            val (yj, xj) = polygon[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun kmBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dy = (a.first - b.first) * 111.32
        val dx = (a.second - b.second) * 111.32 * cos(Math.toRadians((a.first + b.first) / 2))
        return sqrt(dx * dx + dy * dy)
    }

    // "de:11000:900100003" and the like; the first two digits are the state.
    private val DHID = Regex("""(?:^|[^0-9a-z])de:(\d{2})\d{3}:""")

    // A VBB stop number "900" + area (2 digits) + 4 digits, also inside a longer id.
    private val VBB_ID = Regex("""(?<!\d)900(\d{2})\d{4}(?!\d)""")

    private const val FIRST_BRANDENBURG_CODE = 20

    // Alexanderplatz as the centre of the circle of zone C.
    private val CENTRE = 52.5215 to 13.4121
    private const val C_RADIUS_KM = 35.0

    // Within this distance of a ring station counts as the station itself (zone A).
    private const val RING_STATION_KM = 0.3

    // The Ringbahn clockwise from Westkreuz, by its stations (OpenStreetMap, ODbL).
    internal val RING: List<Pair<Double, Double>> = listOf(
        52.500910 to 13.284332, // Westkreuz
        52.496025 to 13.290555, // Halensee
        52.488627 to 13.300242, // Hohenzollerndamm
        52.480046 to 13.312160, // Heidelberger Platz
        52.477653 to 13.329024, // Bundesplatz
        52.478547 to 13.342607,
        52.479349 to 13.351918,
        52.476255 to 13.365417, // Südkreuz
        52.470258 to 13.385036, // Tempelhof
        52.467448 to 13.431353, // Hermannstraße
        52.469054 to 13.442789, // Neukölln
        52.472981 to 13.455690, // Sonnenallee
        52.493849 to 13.461830, // Treptower Park
        52.503079 to 13.469513, // Ostkreuz
        52.514253 to 13.475034, // Frankfurter Allee
        52.523743 to 13.464889, // Storkower Straße
        52.529482 to 13.454780, // Landsberger Allee
        52.540246 to 13.438989, // Greifswalder Straße
        52.544790 to 13.425980, // Prenzlauer Allee
        52.549366 to 13.414653, // Schönhauser Allee
        52.548881 to 13.389533, // Gesundbrunnen
        52.542925 to 13.366019, // Wedding
        52.536506 to 13.343495, // Westhafen
        52.534408 to 13.329387, // Beusselstraße
        52.530490 to 13.299855, // Jungfernheide
        52.518014 to 13.284420, // Westend
        52.507400 to 13.283500, // Messe Nord/ICC
    )
}
