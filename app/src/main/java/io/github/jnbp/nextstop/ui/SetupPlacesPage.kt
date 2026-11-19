package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.favoriteAt
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.data.withFavoriteAt
import io.github.jnbp.nextstop.data.withSetupSaved
import io.github.jnbp.nextstop.data.withoutFavoriteAt
import io.github.jnbp.nextstop.ui.theme.AppColors

/**
 * The step "Orte" of the setup (#229): Zuhause first, Arbeit and more places if wanted, each
 * found with the search of the app (stops, addresses, contacts). They are the favourite and
 * saved places of the start page (see SetupPlaces.kt), stored at once; disruption alerts look
 * at them and the start page offers routes there with one tap. Skippable like every step.
 */
@Composable
internal fun SetupPlacesPage(prefs: AppPrefs) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(prefs.loadFavoritePlaces()) }
    var saved by remember { mutableStateOf(prefs.loadSavedPlaces()) }
    // The place the search is open for: Home, Work, or Pin for one more place.
    var searchingFor by remember { mutableStateOf<PlaceIcon?>(null) }
    val home = favoriteAt(favorites, PlaceIcon.Home)
    val work = favoriteAt(favorites, PlaceIcon.Work)
    fun storeFavorites(changed: List<io.github.jnbp.nextstop.data.FavoritePlace>) {
        favorites = changed
        prefs.saveFavoritePlaces(changed)
    }
    fun storeSaved(changed: List<io.github.jnbp.nextstop.data.SavedPlace>) {
        saved = changed
        prefs.saveSavedPlaces(changed)
    }
    SetupCard(
        title = stringResource(R.string.r7search_setup_places_title),
        text = stringResource(R.string.r7search_setup_places_short),
        more = stringResource(R.string.r7search_setup_places_more),
    ) {
        SetupPlacesPreview(homeSet = home != null)
        Column(
            modifier = Modifier.animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SetupPlaceRow(
                glyph = Glyph.Home,
                label = stringResource(R.string.home),
                place = home,
                optional = false,
                onSearch = { searchingFor = PlaceIcon.Home },
                onClear = { storeFavorites(withoutFavoriteAt(favorites, PlaceIcon.Home)) },
            )
            SetupPlaceRow(
                glyph = Glyph.Work,
                label = stringResource(R.string.work),
                place = work,
                optional = true,
                onSearch = { searchingFor = PlaceIcon.Work },
                onClear = { storeFavorites(withoutFavoriteAt(favorites, PlaceIcon.Work)) },
            )
            saved.forEach { entry ->
                SetupPlaceRow(
                    glyph = placeGlyph(entry.icon),
                    label = savedPlaceName(entry),
                    place = entry.place,
                    optional = false,
                    small = true,
                    onSearch = null,
                    onClear = { storeSaved(saved.filter { it.id != entry.id }) },
                )
            }
            SetupAddPlaceRow(onClick = { searchingFor = PlaceIcon.Pin })
        }
    }
    val target = searchingFor
    if (target != null) {
        val source = remember {
            RoutingSource(choice = { prefs.sourceChoice }, userAgent = TransitousSource.userAgent(context), bvgUrl = { ownServerUrl(prefs.ownServer) })
        }
        Dialog(
            onDismissRequest = { searchingFor = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            // A dialog is a window of its own: no picture of the map behind its glass.
            CompositionLocalProvider(LocalBackdrop provides null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.PlainGround)
                        .systemBarsPadding()
                        .imePadding(),
                ) {
                    SearchPanel(
                        source = source,
                        placeholder = when (target) {
                            PlaceIcon.Home -> stringResource(R.string.home)
                            PlaceIcon.Work -> stringResource(R.string.work)
                            else -> stringResource(R.string.r7search_setup_places_search)
                        },
                        suggestions = emptyList(),
                        expandFromY = null,
                        onPick = { place: Stop ->
                            when (target) {
                                PlaceIcon.Home, PlaceIcon.Work -> storeFavorites(withFavoriteAt(favorites, target, place))
                                else -> storeSaved(withSetupSaved(saved, favorites, UUID.randomUUID().toString(), place))
                            }
                            searchingFor = null
                        },
                        onClose = { searchingFor = null },
                    )
                }
            }
        }
    }
}

/** One place of the step: its icon and label, the place or "Ort wählen"; a tap searches, the cross clears. */
@Composable
private fun SetupPlaceRow(
    glyph: Glyph,
    label: String,
    place: Stop?,
    optional: Boolean,
    onSearch: (() -> Unit)?,
    onClear: () -> Unit,
    small: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Card)
            .then(if (onSearch != null) Modifier.clickable(onClick = onSearch) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (small) 28.dp else 32.dp)
                .clip(CircleShape)
                .background(if (small) AppColors.BlueContainer else AppColors.Blue),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, color = if (small) AppColors.OnBlueContainer else AppColors.OnBlue, size = 16.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = label, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (optional) Text(text = stringResource(R.string.r7search_setup_places_optional), color = AppColors.Muted, fontSize = 12.sp)
            }
            Text(
                text = place?.name?.shortStopName() ?: stringResource(R.string.place_choose),
                color = if (place != null) AppColors.Ink else AppColors.Blue,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (place != null) {
            RoundButton(
                glyph = Glyph.Close,
                description = stringResource(R.string.r7search_setup_places_remove, label),
                onClick = onClear,
                size = 40.dp,
            )
        }
    }
}

/** "Weiteren Ort hinzufügen": opens the search for one more saved place. */
@Composable
private fun SetupAddPlaceRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(Glyph.Plus, color = AppColors.Blue, size = 20.dp)
        Text(text = stringResource(R.string.r7search_setup_places_add), color = AppColors.Blue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/**
 * The sketch of the step: the start page with the chips of Zuhause and Arbeit, and a notice
 * of a disruption on the way home below them, which comes in once Zuhause is set.
 * Decorative, hidden from accessibility services.
 */
@Composable
private fun SetupPlacesPreview(homeSet: Boolean) {
    val animate = rememberAnimationsOn()
    val shown by animateFloatAsState(
        targetValue = if (homeSet) 1f else 0f,
        animationSpec = if (animate) spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "setupPlacesNotice",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Chip)
            .padding(10.dp)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewPlaceChip(Glyph.Home, stringResource(R.string.home), strong = true)
            PreviewPlaceChip(Glyph.Work, stringResource(R.string.work), strong = false)
        }
        Row(
            modifier = Modifier
                .graphicsLayer {
                    alpha = shown.coerceIn(0f, 1f)
                    translationY = (1f - shown) * 12.dp.toPx()
                }
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Card)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WarningMark(size = 14.dp)
            Text(text = stringResource(R.string.r7search_setup_places_preview), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun PreviewPlaceChip(glyph: Glyph, name: String, strong: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (strong) AppColors.Blue else AppColors.BlueContainer),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, color = if (strong) AppColors.OnBlue else AppColors.OnBlueContainer, size = 12.dp)
        }
        Text(text = name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
    }
}
