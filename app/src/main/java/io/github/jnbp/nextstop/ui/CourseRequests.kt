package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Leg
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A request to open the course of a ride from a page that has no callback for it (the new
 * companion views: a long press on a ride's head, as in the detail of a connection). The root
 * of the app opens it (see AppRoot, openCourse) and clears the request.
 */
object CourseRequests {
    val leg = MutableStateFlow<Leg?>(null)

    fun open(leg: Leg) {
        this.leg.value = leg
    }
}
