package io.github.jnbp.nextstop.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.ContactUsage
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.SyncedContact
import io.github.jnbp.nextstop.wear.data.TransitRouter
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.indexLetter

/** All recent destinations of the phone, the last one first ("Show all" of the section "Recent"). */
@Composable
fun AllRecentScreen(onDestination: (Place) -> Unit) {
    val places by WatchStore.places.collectAsState()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(stringResource(R.string.recent)) } }
            if (places.recent.isEmpty()) item { Note(stringResource(R.string.no_results)) }
            items(places.recent) { place -> PlaceButton(place, onClick = { onDestination(place.place) }) }
        }
    }
}

/**
 * All contacts of the phone with an address, by name and filed under their first letter.
 * At the top a search (typed or spoken, as the destination) narrows the list by name or
 * address. The crown jumps from letter to letter, the letter shown big in the middle while it
 * turns. A tap looks the address up and opens the journeys there ([onDestination]); the
 * contact counts as used ([ContactUsage]).
 */
@Composable
fun AllContactsScreen(onDestination: (Place) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val places by WatchStore.places.collectAsState()
    var filter by rememberSaveable { mutableStateOf("") }
    // The contact whose address is being looked up, and those whose address was not found.
    var looking by remember { mutableStateOf<String?>(null) }
    var notFound by remember { mutableStateOf(setOf<String>()) }
    val openInput = rememberTextInput(
        label = stringResource(R.string.search_contacts),
        onText = { filter = it.trim() },
        onUnavailable = {},
    )
    val shown = remember(places.contacts, filter) { ContactUsage.byName(places.contacts.filter { matches(it, filter) }) }
    // By letter in the order of the names; names without a letter at the end.
    val groups = remember(shown) { shown.groupBy { indexLetter(it.name) }.toList().sortedBy { it.first == '#' } }
    // The items before the first letter: title, search, and the button that clears it or the note.
    val lead = 2 + (if (filter.isNotEmpty()) 1 else 0) + (if (shown.isEmpty()) 1 else 0)
    val headers = remember(groups, lead) {
        var index = lead
        groups.map { (letter, members) -> (letter to index).also { index += 1 + members.size } }
    }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    // The crown: one letter per [SCRUB_PIXELS_PER_LETTER] turned, shown while it turns.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubIndex by remember { mutableIntStateOf(0) }
    var turned by remember { mutableFloatStateOf(0f) }
    var lastTurn by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastTurn) {
        if (lastTurn == 0L) return@LaunchedEffect
        delay(SCRUB_HIDE_MILLIS)
        scrubbing = false
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: IllegalStateException) {
            // Not attached yet: the list still scrolls by touch.
        }
    }
    fun open(contact: SyncedContact) {
        if (looking != null) return
        val key = ContactUsage.keyOf(contact)
        ContactUsage.record(context, contact)
        looking = key
        notFound = notFound - key
        scope.launch {
            val found = try {
                TransitRouter.searchPlaces(context, contact.address, results = 1).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            looking = null
            if (found != null) onDestination(found) else notFound = notFound + key
        }
    }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                // The crown scrubs through the letters instead of scrolling.
                rotaryScrollableBehavior = null,
                modifier = Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { event ->
                        if (headers.isEmpty()) return@onRotaryScrollEvent false
                        if (!scrubbing) {
                            // Starts at the letter in the middle of the screen.
                            val centre = listState.centerItemIndex
                            scrubIndex = headers.indexOfLast { it.second <= centre }.coerceAtLeast(0)
                            turned = 0f
                            scrubbing = true
                        }
                        turned += event.verticalScrollPixels
                        val steps = (turned / SCRUB_PIXELS_PER_LETTER).toInt()
                        if (steps != 0) {
                            turned -= steps * SCRUB_PIXELS_PER_LETTER
                            val next = (scrubIndex + steps).coerceIn(0, headers.lastIndex)
                            if (next != scrubIndex) {
                                scrubIndex = next
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { listState.scrollToItem(headers[next].second) }
                            }
                        }
                        lastTurn = System.nanoTime()
                        true
                    }
                    .focusRequester(focus)
                    .focusable(),
            ) {
                item { ListHeader { Text(stringResource(R.string.contacts)) } }
                item {
                    val press = rememberWatchPress()
                    Button(
                        onClick = openInput,
                        modifier = Modifier.fillMaxWidth().then(press.modifier),
                        interactionSource = press.source,
                        icon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null) },
                        label = { Text(stringResource(R.string.search_contacts), maxLines = 1) },
                        secondaryLabel = if (filter.isNotEmpty()) {
                            { Text("„$filter“", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        } else {
                            null
                        },
                    )
                }
                if (filter.isNotEmpty()) {
                    item { SmallAction(stringResource(R.string.clear_search), onClick = { filter = "" }, icon = R.drawable.ic_close) }
                }
                if (shown.isEmpty()) item { Note(stringResource(R.string.no_contact_found)) }
                groups.forEach { (letter, members) ->
                    item { ListHeader { Text(letter.toString()) } }
                    items(members) { contact ->
                        val key = ContactUsage.keyOf(contact)
                        ContactButton(
                            contact,
                            onSearch = { open(contact) },
                            status = when {
                                looking == key -> stringResource(R.string.looking_up_address)
                                key in notFound -> stringResource(R.string.no_results)
                                else -> null
                            },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = scrubbing && scrubIndex in headers.indices,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Box(
                    modifier = Modifier
                        .size(SCRUB_BADGE_SIZE)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        headers.getOrNull(scrubIndex)?.first?.toString() ?: "",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** True when every word of [filter] is in the name or the address of [contact], ignoring case and accents. */
private fun matches(contact: SyncedContact, filter: String): Boolean {
    if (filter.isBlank()) return true
    val text = folded(contact.name + " " + contact.address)
    return folded(filter).split(' ').filter { it.isNotEmpty() }.all { it in text }
}

private fun folded(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)

private val MARKS = Regex("\\p{M}+")

// Crown pixels per letter: about one notch of a rotating bezel.
private const val SCRUB_PIXELS_PER_LETTER = 40f
private const val SCRUB_HIDE_MILLIS = 900L
private val SCRUB_BADGE_SIZE = 76.dp
