package io.github.jnbp.nextstop.wear.data

/**
 * The colours of the S- and U-Bahn lines of Berlin and of the other products, as ARGB, the
 * same as in the phone app (ui/Components.kt lineColor).
 */
private val LINE_COLORS: Map<String, Long> = mapOf(
    // U-Bahn: the colours of the BVG's current line signs.
    "U1" to 0xFF419328, "U2" to 0xFFED3C12, "U3" to 0xFF016144,
    "U4" to 0xFFFFC101, "U5" to 0xFF603B2C, "U6" to 0xFF504F87,
    "U7" to 0xFF0279BD, "U8" to 0xFF013F75, "U9" to 0xFFF96815,
    "S1" to 0xFFDA6BA2, "S2" to 0xFF007734, "S25" to 0xFF007734,
    "S26" to 0xFF007734, "S3" to 0xFF0066AD, "S41" to 0xFFAD5937,
    "S42" to 0xFFCB6418, "S45" to 0xFFCD9C53, "S46" to 0xFFCD9C53,
    "S47" to 0xFFCD9C53, "S5" to 0xFFEB7405, "S7" to 0xFF816DA6,
    "S75" to 0xFF816DA6, "S8" to 0xFF66AA22, "S85" to 0xFF66AA22,
    "S9" to 0xFF992746,
)

/**
 * The colour of [line] of [product] as ARGB: [color] where the phone sent the colour of the very
 * ride (worked out there from the ride's own colour and place); else the colours of Berlin's
 * network map, only for a U-Bahn named U… or an S-Bahn named S… (a regional train named "U1"
 * elsewhere is no Berlin U1); else by kind and product.
 */
fun lineArgb(line: String?, product: String, color: Long? = null): Long {
    if (color != null) return color
    // An S- or U-Bahn line named only by its number ("1" for an extra S1) is that line.
    val plain = line?.trim()
    val letter = when (product) { "suburban" -> "S"; "subway" -> "U"; else -> "" }
    val name = if (letter.isNotEmpty() && plain != null && plain.length in 1..2 && plain.all { it.isDigit() }) "$letter$plain" else plain
    val key = name?.uppercase()?.replace(" ", "")
    if (letter.isNotEmpty() && key != null && key.startsWith(letter)) LINE_COLORS[key]?.let { return it }
    val kind = name?.uppercase()?.takeWhile { it.isLetter() }.orEmpty()
    return when {
        kind == "ICE" -> 0xFFEC0016
        kind == "FLX" -> 0xFF97D700
        (product == "coach" || product == "bus") && kind == "FLIXBUS" -> 0xFF97D700
        else -> when (product) {
            "subway" -> 0xFF115D91
            "suburban" -> 0xFF2D8B3C
            "tram" -> 0xFFBE1414
            "bus" -> 0xFF95276E
            "ferry" -> 0xFF528DBA
            "regional" -> 0xFFDA251D
            "express" -> 0xFF4A4F57
            "coach" -> 0xFF2F6F73
            else -> 0xFF5A6072
        }
    }
}

// The colours of the U-Bahn lines of Berlin, whose ink is that of the BVG's signs (see onLineArgb).
private val SUBWAY_SIGN_COLORS: Set<Long> = LINE_COLORS.filterKeys { it.startsWith("U") }.values.toSet()

/**
 * On the colour of a U-Bahn line of Berlin the ink of the BVG's sign: white, black on the U4
 * (the phone's LineColors.berlinSubwayText). Elsewhere dark ink on light line colours (FLX),
 * white on the others: the phone's rule (onLineColor
 * there, Compose's luminance above 0.45), so a badge has the same ink on both. Compose's
 * luminance is the relative luminance of the linear sRGB channels; with the plain channels and
 * 0.55 the watch put dark ink on the green of the S8 and the ochre of the S47, white on the phone.
 */
fun onLineArgb(argb: Long): Long {
    val opaque = argb or 0xFF000000
    if (opaque == LINE_COLORS["U4"]) return 0xFF000000
    if (opaque in SUBWAY_SIGN_COLORS) return 0xFFFFFFFF
    fun linear(channel: Long): Double {
        val c = (channel and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val luminance = 0.2126 * linear(argb shr 16) + 0.7152 * linear(argb shr 8) + 0.0722 * linear(argb)
    return if (luminance > 0.45) 0xFF16130A else 0xFFFFFFFF
}

/**
 * Whole minutes from [now] until [time], never below zero: the full minutes left, as the phone
 * counts them (minutesBetween there), so watch and phone show the same number at the same moment
 * and "now" in the last minute.
 */
fun minutesUntil(time: Long, now: Long = System.currentTimeMillis()): Long =
    ((time - now) / 60_000).coerceAtLeast(0)
