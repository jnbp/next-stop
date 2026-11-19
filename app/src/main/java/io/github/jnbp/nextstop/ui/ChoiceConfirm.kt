package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.ChoiceMarks
import kotlin.math.PI
import kotlin.math.sin

/*
 * #115: the marks of the connection choice stand on the tiles themselves ("frühester" on the
 * earliest one that works without running, a quiet "Sprint" on an earlier one that needs a run),
 * and a tile that just became the chosen one confirms it: its check mark grows out of a dot and
 * draws its tick while the tile lights up briefly. Pure parts, drawn in ui/Companion.kt.
 */

/** The mark a tile of the connection choice carries. */
enum class TileMark { Earliest, Sprint }

/** The mark of the tile [index] by [marks]; null for a tile without one. */
fun tileMark(index: Int, marks: ChoiceMarks): TileMark? = when (index) {
    marks.earliest -> TileMark.Earliest
    marks.sprintEarlier -> TileMark.Sprint
    else -> null
}

/** How long the confirmation of a chosen tile runs at the normal animation speed. */
const val CONFIRM_MILLIS = 700

/**
 * The size of the check mark's circle at [progress] (0 to 1) of the confirmation: grows from
 * nothing with a small overshoot during the first 40 %, then stands at full size.
 */
fun confirmCircle(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    if (p >= 0.4f) return 1f
    val t = p / 0.4f
    // Up to 1.15 at 75 % of the way, back to 1 at the end.
    return if (t < 0.75f) t / 0.75f * 1.15f else 1.15f - (t - 0.75f) / 0.25f * 0.15f
}

/** How much of the tick is drawn at [progress]: from 30 % to 80 % of the way, then whole. */
fun confirmTick(progress: Float): Float = ((progress.coerceIn(0f, 1f) - 0.3f) / 0.5f).coerceIn(0f, 1f)

/** The strength of the tile's brief light at [progress]: up and down again, nothing at rest. */
fun confirmGlow(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return if (p <= 0f || p >= 1f) 0f else sin(PI * p).toFloat()
}
