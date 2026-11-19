package io.github.jnbp.nextstop.ui

/*
 * #212: what kind of vehicle a ride is, so texts about it name it ("Bus verpasst", "Meine Tram
 * ist zu spät", "Ich sitze im falschen Zug") and say "Fahrt" where the kind is not known. Free of
 * Android, so it can be tested; the words are picked in RideWords.kt.
 */

/** The kind of vehicle a ride is, as the texts name it. */
enum class RideKind { Bus, Tram, UBahn, SBahn, Train, Ferry, Unknown }

// The products of the sources that are trains (long-distance and regional), see productKey.
private val TRAIN_PRODUCTS = setOf("express", "regional", "national", "nationalExpress", "regionalExpress", "train")

/** The kind of a ride of [product] (the product key of a leg); [RideKind.Unknown] for none or one not known. */
fun rideKindOf(product: String?): RideKind = when (product) {
    "bus", "coach" -> RideKind.Bus
    "tram" -> RideKind.Tram
    "subway" -> RideKind.UBahn
    "suburban" -> RideKind.SBahn
    "ferry" -> RideKind.Ferry
    in TRAIN_PRODUCTS -> RideKind.Train
    else -> RideKind.Unknown
}
