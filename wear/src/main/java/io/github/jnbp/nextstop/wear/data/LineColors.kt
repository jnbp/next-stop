package io.github.jnbp.nextstop.wear.data

/**
 * The colours of the S- and U-Bahn lines of Berlin and of the other products, as ARGB, the
 * same as in the phone app (ui/Components.kt lineColor).
 */
private val LINE_COLORS: Map<String, Long> = mapOf(
    "U1" to 0xFF7DAD4C, "U2" to 0xFFDA421E, "U3" to 0xFF16683D,
    "U4" to 0xFFF0D722, "U5" to 0xFF7E5330, "U6" to 0xFF8C6DAB,
    "U7" to 0xFF528DBA, "U8" to 0xFF224F86, "U9" to 0xFFF3791D,
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

/** Dark ink on light line colours (U4, FLX), white on the others. */
fun onLineArgb(argb: Long): Long {
    val r = (argb shr 16 and 0xFF) / 255.0
    val g = (argb shr 8 and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return if (luminance > 0.55) 0xFF16130A else 0xFFFFFFFF
}

/** Whole minutes from [now] until [time], never below zero. */
fun minutesUntil(time: Long, now: Long = System.currentTimeMillis()): Long =
    ((time - now + 30_000) / 60_000).coerceAtLeast(0)
