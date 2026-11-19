package io.github.jnbp.nextstop.data

/*
 * The entrance a walk to a U-Bahn or S-Bahn takes where OpenStreetMap knows no platform of the
 * ride (#204). Pure logic, no Android.
 *
 * Without a platform, OsmStations.stationEntrance weighed the way outside against the way from
 * the entrance to one point inside, the start of the ride's way (often the provider's point of
 * the station, at one end). At a station whose U-Bahn platform is not mapped, a bus stop beside
 * one end of the line's entrances then got the entrance at the other end, near that point, and
 * the walk ran along the platform: minutes more than the way in at the near end. Every entrance
 * marked with the sign of the ride ("U", "S") leads to that line's platform, so the one nearest
 * to where the walk comes from is the way in.
 */

/**
 * Of [entrances], the one marked with [sign] ([marker] contains it) that is nearest to where
 * the walk comes from ([distance] from there); null without a sign or where none is marked so.
 */
fun <T> nearestSignedEntrance(entrances: List<T>, sign: String?, marker: (T) -> String?, distance: (T) -> Double): T? {
    if (sign == null) return null
    return entrances.filter { marker(it)?.contains(sign) == true }.minByOrNull(distance)
}
