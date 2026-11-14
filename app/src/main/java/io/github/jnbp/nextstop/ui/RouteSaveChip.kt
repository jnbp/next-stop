package io.github.jnbp.nextstop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * "Save route" as a round chip with only a star, where a row of chips has little room: an
 * outlined star while the route is not saved, a filled one in the colour of the app once it
 * is. A change pops the star a little (not with animations switched off). To accessibility
 * services it is a toggle named "Save route" or "Route saved". Menus keep the long label.
 */
@Composable
fun RouteSaveChip(saved: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val interactions = remember { MutableInteractionSource() }
    val animate = rememberAnimationsOn()
    val pop = remember { Animatable(1f) }
    // Only a change pops, not the first showing.
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(saved) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (!animate) return@LaunchedEffect
        pop.snapTo(1f)
        pop.animateTo(if (saved) 1.3f else 0.8f, tween(90))
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    val label = stringResource(if (saved) R.string.action_route_saved else R.string.action_route_save)
    Box(
        modifier = modifier
            .semantics { contentDescription = label }
            .springPress(interactions)
            .size(size)
            .clip(CircleShape)
            .then(if (saved) Modifier.background(AppColors.Card) else glassGround())
            .border(1.dp, glassRim(), CircleShape)
            .toggleable(
                value = saved,
                interactionSource = interactions,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = if (saved) Glyph.StarFilled else Glyph.Star,
            color = if (saved) AppColors.Blue else AppColors.Ink,
            size = 22.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
        )
    }
}
