package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.StopAction
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.mutableIntStateOf
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import io.github.jnbp.nextstop.data.departureKeys
import io.github.jnbp.nextstop.data.StopGroup
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.SolidColor
import io.github.jnbp.nextstop.data.ChartShortLegs
import io.github.jnbp.nextstop.data.ChartTotalTime
import io.github.jnbp.nextstop.data.SHORTCUT_MINUTE_CHOICES
import io.github.jnbp.nextstop.data.DelayDisplay
import io.github.jnbp.nextstop.data.SearchBlocks
import io.github.jnbp.nextstop.data.longDistanceBlocks
import io.github.jnbp.nextstop.data.searchSections
import io.github.jnbp.nextstop.data.departureModeFilters
import io.github.jnbp.nextstop.data.townOf
import io.github.jnbp.nextstop.data.BvgRestSource
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jnbp.nextstop.data.peopleIndex
import io.github.jnbp.nextstop.data.peopleMatchingIn
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.StopLine
import io.github.jnbp.nextstop.data.Accessibility
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Appointment
import io.github.jnbp.nextstop.data.Backend
import io.github.jnbp.nextstop.data.PteSource
import io.github.jnbp.nextstop.data.ContactAddress
import io.github.jnbp.nextstop.data.QuickStop
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.data.FavoritePlace
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.data.PLACE_RADII
import io.github.jnbp.nextstop.data.withLocalOnly
import io.github.jnbp.nextstop.data.PlaceDisruptions
import io.github.jnbp.nextstop.data.PlaceIcon
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jnbp.nextstop.data.PlannedTrips
import io.github.jnbp.nextstop.data.TripStats
import io.github.jnbp.nextstop.data.tripSummary
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.SavedPlace
import io.github.jnbp.nextstop.data.SavedRoute
import io.github.jnbp.nextstop.data.spotMeters
import io.github.jnbp.nextstop.data.SavedPattern
import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.DisruptionTapTarget
import io.github.jnbp.nextstop.data.disruptionTapTarget
import io.github.jnbp.nextstop.data.waysDisruptionsIndex
import io.github.jnbp.nextstop.data.WayNotices
import io.github.jnbp.nextstop.data.waysPill
import io.github.jnbp.nextstop.data.noticesOnLines
import io.github.jnbp.nextstop.data.wayNotices
import io.github.jnbp.nextstop.data.LiftKeys
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.data.TicketFilter
import io.github.jnbp.nextstop.data.ticketFilters
import io.github.jnbp.nextstop.data.SourceChoice
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.TransitSource
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.Via
import io.github.jnbp.nextstop.data.hasCalendarPermission
import io.github.jnbp.nextstop.data.isOsmPlace
import io.github.jnbp.nextstop.data.loadContactAddresses
import io.github.jnbp.nextstop.data.looksLikeAddress
import io.github.jnbp.nextstop.data.placeScore
import io.github.jnbp.nextstop.data.resolveContactPlace
import io.github.jnbp.nextstop.data.resolvePlace
import io.github.jnbp.nextstop.data.searchTokens
import io.github.jnbp.nextstop.data.servicesUnreachable
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.data.stopNamePart
import io.github.jnbp.nextstop.data.listedStopName
import io.github.jnbp.nextstop.data.upcomingAppointments
import io.github.jnbp.nextstop.data.UxPrefs
import io.github.jnbp.nextstop.data.walkableFrom
import io.github.jnbp.nextstop.data.anyWalkableFrom
import io.github.jnbp.nextstop.data.boardDisruptions
import io.github.jnbp.nextstop.data.stopsByDistance
import io.github.jnbp.nextstop.data.alertingWarnings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import io.github.jnbp.nextstop.ui.theme.ACCENT_PRESETS
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.AppFont
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.ColorChoice
import io.github.jnbp.nextstop.ui.theme.DarkChoice
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import io.github.jnbp.nextstop.ui.theme.VehicleDirectionChoice
import io.github.jnbp.nextstop.ui.theme.NotificationLook
import io.github.jnbp.nextstop.widget.WidgetColorChoice
import io.github.jnbp.nextstop.widget.WidgetUpdates

/**
 * Full-screen search for a stop, a point of interest or an address, or for a contact that
 * has a postal address. [expandFromY] is where the card that opened the search sat on the
 * screen: the input field starts there and springs up to its place. With [stopsOnly] it
 * finds stops and nothing else, and offers no contacts.
 */
@Composable
fun SearchPanel(
    source: TransitSource,
    placeholder: String,
    suggestions: List<Stop>,
    expandFromY: Float?,
    onPick: (Stop) -> Unit,
    onClose: () -> Unit,
    stopsOnly: Boolean = false,
    favorites: List<FavoritePlace> = emptyList(),
    saved: List<SavedPlace> = emptyList(),
    history: List<Stop> = emptyList(),
    onRemember: (Stop) -> Unit = {},
    onForget: (Stop) -> Unit = {},
    onPickAppointment: ((Appointment, Stop) -> Unit)? = null,
    onCalendarGranted: () -> Unit = {},
    // A place shared by another app: its text is searched for at once, and its point, where
    // the share gave one, is offered on top while the text is unchanged. The user confirms.
    initialQuery: String = "",
    offered: Stop? = null,
    // With a target, a chip offers to choose the place on the map instead (see MapPick.kt).
    mapPick: MapPickTarget? = null,
    // "Von wo?": stops near the user under "Aktueller Standort" while nothing is typed (#173).
    nearbyQuick: List<QuickStop> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    // What was picked, shown in the field in place of the typed text from the tap on (#126): a
    // contact's address is looked up first, and the panel closes after that, so the text typed
    // before must not stay in the field meanwhile. Not searched for; typing again lets it go.
    var pickedText by remember { mutableStateOf<String?>(null) }
    // The text last put in with the paste button: it is searched for without the pause for typing.
    var pastedQuery by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    var results by remember { mutableStateOf<List<Stop>>(emptyList()) }
    // The stations of a far town the text names ("Hamburg" in Berlin), below the local hits (see longDistanceStations).
    var farResults by remember { mutableStateOf<List<Stop>>(emptyList()) }
    // True while the providers are asked for what is typed; true after a search that failed.
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    // Why it failed: no source reached says "Dienste nicht erreichbar" (#196).
    var searchFailure by remember { mutableStateOf<Exception?>(null) }
    val focusRequester = remember { FocusRequester() }
    // Contacts are read when the panel opens, where the app may read them (the search finds
    // them while typing, #68), or when asked for, and only held while this panel is open.
    var contactsOpen by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    var contactsDenied by remember { mutableStateOf(false) }
    var contactsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var contacts by remember { mutableStateOf<List<ContactAddress>?>(null) }
    var resolving by remember { mutableStateOf(false) }
    // The contact whose address is looked up now, and the one whose address was not found (#58).
    var resolvingContact by remember { mutableStateOf<ContactAddress?>(null) }
    var failedContact by remember { mutableStateOf<ContactAddress?>(null) }
    val expand = remember { Animatable(if (expandFromY == null) 1f else 0f) }
    val fieldTop = remember { mutableFloatStateOf(Float.NaN) }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        contactsDenied = !granted
        if (granted) contactsGranted = true
        calendarOpen = false
        contactsOpen = true
    }
    // The appointments of the calendar with a place, read when the panel opens where the app may
    // read them (#68) or when asked for, and only held while open.
    var calendarDenied by remember { mutableStateOf(false) }
    var calendarGranted by remember { mutableStateOf(onPickAppointment != null && hasCalendarPermission(context)) }
    var calendarList by remember { mutableStateOf<List<Appointment>?>(null) }
    var appointmentNotFound by remember { mutableStateOf(false) }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        calendarDenied = !granted
        if (granted) {
            calendarGranted = true
            onCalendarGranted()
        }
        contactsOpen = false
        calendarOpen = true
    }

    val approximateNote = stringResource(R.string.approximate_location)

    // The address of the picked contact is resolved by its fields and checked against its
    // postcode and town (see resolveContactPlace), so a street of the same name elsewhere is not taken.
    // The row says that it is looked up; a lookup that fails or takes too long ends as "not
    // found" beside the row, and the rows can be tapped again (#58: one that never ended left
    // every row disabled, without a word).
    fun pickContact(contact: ContactAddress) {
        if (resolving) return
        pickedText = contact.address.replace("\n", ", ")
        resolving = true
        resolvingContact = contact
        failedContact = null
        scope.launch {
            val place = try {
                withTimeoutOrNull(CONTACT_RESOLVE_MILLIS) { resolveContactPlace(context, source, contact, approximateNote) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                resolving = false
                resolvingContact = null
            }
            if (place != null) {
                onRemember(place)
                onPick(place)
            } else {
                failedContact = contact
                pickedText = null
            }
        }
    }

    // The place of a picked appointment is found from its location as for the appointment card.
    fun pickAppointment(appointment: Appointment) {
        val pick = onPickAppointment ?: return
        pickedText = appointment.location
        resolving = true
        appointmentNotFound = false
        scope.launch {
            val place = try {
                withTimeoutOrNull(CONTACT_RESOLVE_MILLIS) { source.resolvePlace(appointment.location) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                resolving = false
            }
            if (place != null) {
                val found = place.copy(distanceMeters = null)
                onRemember(found)
                pick(appointment, found)
            } else {
                appointmentNotFound = true
                pickedText = null
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(calendarOpen, calendarDenied, calendarGranted) {
        if (calendarList == null && (calendarGranted || (calendarOpen && !calendarDenied))) {
            calendarList = upcomingAppointments(
                context,
                days = APPOINTMENT_DAYS,
                limit = APPOINTMENT_ROWS,
                afterMinutes = UxPrefs(context).appointmentAfterMinutes,
            )
        }
    }
    LaunchedEffect(expand) {
        expand.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(contactsOpen, contactsDenied, contactsGranted) {
        if (stopsOnly || contacts != null) return@LaunchedEffect
        if (contactsGranted || (contactsOpen && !contactsDenied)) contacts = loadContactAddresses(context)
    }
    // Search as you type: every key restarts this effect, which cancels the request of the
    // text before (stale answers never land). After a short quiet the providers (BVG and the
    // others in use) and OpenStreetMap (Photon) are asked at once; until they answer, the
    // results of before that still fit stay, so the list does not flicker.
    LaunchedEffect(query, contactsOpen, calendarOpen) {
        val text = query.trim()
        if (contactsOpen || calendarOpen || text.length < SEARCH_MIN_CHARS) {
            results = emptyList()
            farResults = emptyList()
            searching = false
            searchFailed = false
            return@LaunchedEffect
        }
        results = keptWhileTyping(results, text)
        farResults = keptWhileTyping(farResults, text)
        if (text != pastedQuery) delay(SEARCH_DEBOUNCE_MS)
        searching = true
        try {
            coroutineScope {
                // A pasted whole address, as calendars and maps write it, is resolved too and put on top.
                val resolving = async {
                    if (!stopsOnly && looksLikeAddress(text)) {
                        try {
                            source.resolvePlace(text)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }
                // The stations of a far town, asked at the same time without the bias for the region.
                val far = async {
                    try {
                        source.searchLongDistance(text)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                var failure: Exception? = null
                val found = try {
                    source.searchStops(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = e
                    null
                }
                val resolved = resolving.await()
                searchFailed = found == null && resolved == null
                searchFailure = if (searchFailed) failure else null
                // A failed search keeps what is shown; an answer replaces it.
                if (!searchFailed) {
                    results = (listOfNotNull(resolved) + found.orEmpty())
                        .filter { !stopsOnly || it.kind == PlaceKind.Stop }
                        .distinctBy { it.id }
                }
                // Kept whole: a station among the local hits too moves into the block (see longDistanceBlocks).
                farResults = far.await()
            }
        } finally {
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .onGloballyPositioned { fieldTop.floatValue = it.positionInRoot().y }
                // Read in the draw step only, so the move costs no recomposition.
                .graphicsLayer {
                    val top = fieldTop.floatValue
                    if (expandFromY != null && !top.isNaN()) {
                        translationY = (expandFromY - top) * (1f - expand.value)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton(glyph = Glyph.Back, description = stringResource(R.string.back), onClick = onClose)
            GlassSurface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(30.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = pickedText ?: query,
                        onValueChange = {
                            pickedText = null
                            query = it
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AppColors.Ink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFont,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if ((pickedText ?: query).isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        color = AppColors.Muted,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    // Typed text goes with one tap; the field keeps the focus, so typing goes on.
                    FieldClearButton(
                        visible = (pickedText ?: query).isNotEmpty(),
                        onClear = {
                            query = ""
                            pickedText = null
                            pastedQuery = null
                            try {
                                focusRequester.requestFocus()
                            } catch (e: IllegalStateException) {
                                // Not attached: the field is just empty.
                            }
                        },
                    )
                    // A copied address goes into the field with one tap and is searched for at once.
                    ClipboardPasteButton(
                        onPaste = { text ->
                            pickedText = null
                            pastedQuery = text
                            contactsOpen = false
                            calendarOpen = false
                            query = text
                            keyboard?.hide()
                        },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
        }

        // What lies below the field comes in while the field settles.
        val listModifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = expand.value.coerceIn(0f, 1f) }
        // The favourite places that have a place, at most four, as one tap each; a search for
        // stops only offers those that are stops.
        val quickPlaces = favorites
            .filter { favorite -> favorite.place != null && (!stopsOnly || favorite.place.kind == PlaceKind.Stop) }
            .take(MAX_QUICK_PLACES)
        // After them the saved places, as many as there are: the row is pushed sideways.
        val quickSaved = saved.filter { !stopsOnly || it.place.kind == PlaceKind.Stop }
        val quickShown = (quickPlaces.isNotEmpty() || quickSaved.isNotEmpty()) && query.trim().length < 2 &&
            !contactsOpen && !calendarOpen
        if (!stopsOnly || quickShown) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .graphicsLayer { alpha = expand.value.coerceIn(0f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The place chosen on the map: the search gives way to the map with a pin in its middle.
                // Compact round buttons with their names read out (#68), not wide chips.
                if (mapPick != null) RoundButton(
                    glyph = Glyph.Map,
                    description = stringResource(R.string.r3map_pick_on_map),
                    size = SEARCH_TOOL_SIZE,
                    onClick = {
                        keyboard?.hide()
                        MapPick.begin(mapPick, source)
                        onClose()
                    },
                )
                // All contacts with an address, as a list to filter (the search finds them anyway).
                if (!stopsOnly) RoundButton(
                    glyph = Glyph.Friends,
                    description = stringResource(R.string.contacts),
                    size = SEARCH_TOOL_SIZE,
                    solid = contactsOpen,
                    modifier = Modifier.semantics { selected = contactsOpen },
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                            PackageManager.PERMISSION_GRANTED
                        when {
                            contactsOpen -> contactsOpen = false
                            granted -> {
                                contactsDenied = false
                                calendarOpen = false
                                contactsOpen = true
                            }
                            else -> contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                )
                // The appointments of the next days with a place: one tap takes that place.
                if (!stopsOnly && onPickAppointment != null) RoundButton(
                    glyph = Glyph.Calendar,
                    description = stringResource(R.string.appointments_chip),
                    size = SEARCH_TOOL_SIZE,
                    solid = calendarOpen,
                    modifier = Modifier.semantics { selected = calendarOpen },
                    onClick = {
                        when {
                            calendarOpen -> calendarOpen = false
                            hasCalendarPermission(context) -> {
                                calendarDenied = false
                                contactsOpen = false
                                calendarOpen = true
                            }
                            else -> calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    },
                )
                // The favourite and saved places after the small buttons, so these stay in reach.
                if (quickShown) {
                    quickPlaces.forEach { favorite ->
                        val place = favorite.place ?: return@forEach
                        QuickPlaceChip(
                            glyph = placeGlyph(favorite.icon),
                            name = favorite.name.ifBlank { placeIconLabel(favorite.icon) },
                            onClick = {
                                pickedText = place.name
                                onPick(place)
                            },
                        )
                    }
                    quickSaved.forEach { place ->
                        QuickPlaceChip(
                            glyph = placeGlyph(place.icon),
                            name = savedPlaceName(place),
                            onClick = {
                                pickedText = place.place.name
                                onPick(place.place)
                            },
                            small = true,
                        )
                    }
                }
            }
        }

        val text = query.trim()
        val known = contacts
        // The places picked before: the latest ones while nothing is typed, else those that
        // match; they come first, after the own position, and are not shown twice.
        val historyShown = history
            .filter { !stopsOnly || it.kind == PlaceKind.Stop }
            .let { all -> if (text.length < SEARCH_MIN_CHARS) all.take(HISTORY_EMPTY) else all.filter { historyMatches(it, text) }.take(HISTORY_TYPED) }
        val historyIds = historyShown.map { it.id }.toSet()
        val base = if (text.length < SEARCH_MIN_CHARS) suggestions else results
        val offeredShown = offered?.takeIf { text == initialQuery.trim() }
        val lead = listOfNotNull(offeredShown) + base.filter { it.id == CURRENT_LOCATION_ID }
        // Favourite and saved places that fit what is typed: at once, before any answer.
        val pinned = if (text.length < SEARCH_MIN_CHARS) {
            emptyList()
        } else {
            (
                favorites.filter { it.place != null && (it.name.contains(text, ignoreCase = true) || placeMatches(it.place, text)) }
                    .mapNotNull { it.place } +
                    saved.filter { it.name.contains(text, ignoreCase = true) || placeMatches(it.place, text) }.map { it.place }
                )
                .filter { !stopsOnly || it.kind == PlaceKind.Stop }
                .filter { it.id !in historyIds }
                .take(HISTORY_TYPED)
        }
        val listed = searchListing(lead, historyShown, pinned, base.filter { it.id != CURRENT_LOCATION_ID })
        // Below the local hits the stations of a far town, also those that came among the local hits.
        val blocks = if (text.length < SEARCH_MIN_CHARS) {
            SearchBlocks(listed, emptyList())
        } else {
            longDistanceBlocks(
                text,
                listed,
                farResults,
                source.searchCenter,
                fixed = (lead + historyShown + pinned).mapTo(HashSet()) { it.id },
            )
        }
        // While typing, the list comes in fixed sections (#208, SEARCH_SECTION_ORDER): the own position,
        // contacts and appointments, what is known at once (history, favourites), stations (local, then
        // long-distance), places. A late answer goes into its own section and never pushes an earlier
        // one down (see searchSections).
        val sections = if (text.length < SEARCH_MIN_CHARS) {
            null
        } else {
            val fixedIds = (lead + historyShown + pinned).mapTo(HashSet()) { it.id }
            searchSections(
                instant = blocks.local.filter { it.id in fixedIds },
                found = blocks.local.filter { it.id !in fixedIds },
                far = blocks.far,
                near = suggestions.firstOrNull { it.id == CURRENT_LOCATION_ID }?.position,
                leadIds = lead.mapTo(HashSet()) { it.id },
            )
        }
        // The fixed first rows: while typing only the own position (the rest of what is known
        // comes below the contacts and appointments), else the whole list as before.
        val shown = sections?.lead ?: blocks.local
        val knownShown = sections?.instant.orEmpty()
        val farShown = blocks.far
        val anyShown = shown.isNotEmpty() || (sections != null && sections.all.size > shown.size) || farShown.isNotEmpty()
        // Contacts and appointments that fit what is typed, where the app may read them (#68):
        // a few of each below the places, without opening their lists first.
        val listOpen = contactsOpen || calendarOpen
        // Their words are taken apart once when they are read, not at every key (#109).
        val contactIndex = remember(known) { peopleIndex(known.orEmpty()) { listOf(it.name, it.address, it.kind) } }
        val appointmentIndex = remember(calendarList) { peopleIndex(calendarList.orEmpty()) { listOf(it.title, it.location) } }
        val contactHits = remember(contactIndex, text, listOpen) {
            if (stopsOnly || listOpen) {
                emptyList()
            } else {
                peopleMatchingIn(contactIndex, text, INLINE_PEOPLE, SEARCH_MIN_CHARS)
            }
        }
        val appointmentHits = remember(appointmentIndex, text, listOpen) {
            if (stopsOnly || onPickAppointment == null || listOpen) {
                emptyList()
            } else {
                peopleMatchingIn(appointmentIndex, text, INLINE_PEOPLE, SEARCH_MIN_CHARS)
            }
        }
        val peopleShown = contactHits.isNotEmpty() || appointmentHits.isNotEmpty()
        if (calendarOpen) {
            GlassSurface(modifier = listModifier.weight(1f, fill = false), shape = RoundedCornerShape(28.dp)) {
                val list = calendarList
                val matching = list?.filter { appointment ->
                    text.isEmpty() ||
                        appointment.title.contains(text, ignoreCase = true) ||
                        appointment.location.contains(text, ignoreCase = true)
                }
                when {
                    calendarDenied -> Text(
                        text = stringResource(R.string.appointment_denied),
                        color = AppColors.Muted,
                        modifier = Modifier.padding(20.dp),
                    )

                    matching == null -> LoadingRow(
                        text = stringResource(R.string.appointment_loading),
                        modifier = Modifier.padding(20.dp),
                    )

                    matching.isEmpty() -> Text(
                        text = stringResource(R.string.appointments_empty, APPOINTMENT_DAYS),
                        color = AppColors.Muted,
                        modifier = Modifier.padding(20.dp),
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (appointmentNotFound) {
                            item {
                                Text(
                                    text = stringResource(R.string.appointment_place_missing),
                                    color = AppColors.DelayRed,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(matching, key = { it.key }) { appointment ->
                            AppointmentRow(appointment, enabled = !resolving, onClick = { pickAppointment(appointment) })
                        }
                    }
                }
            }
        } else if (contactsOpen) {
            GlassSurface(modifier = listModifier.weight(1f, fill = false), shape = RoundedCornerShape(28.dp)) {
                when {
                    contactsDenied -> Text(
                        text = stringResource(R.string.contacts_denied),
                        color = AppColors.Muted,
                        modifier = Modifier.padding(20.dp),
                    )

                    known == null -> LoadingRow(
                        text = stringResource(R.string.contacts_loading),
                        modifier = Modifier.padding(20.dp),
                    )

                    else -> {
                        val matching = known.filter { contact ->
                            text.isEmpty() ||
                                contact.name.contains(text, ignoreCase = true) ||
                                contact.address.contains(text, ignoreCase = true) ||
                                contact.kind?.contains(text, ignoreCase = true) == true
                        }
                        if (matching.isEmpty()) {
                            Text(
                                text = stringResource(R.string.contacts_empty),
                                color = AppColors.Muted,
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                items(matching) { contact ->
                                    ContactRow(
                                        contact,
                                        enabled = !resolving,
                                        onClick = { pickContact(contact) },
                                        busy = contact == resolvingContact,
                                        note = if (contact == failedContact) stringResource(R.string.contacts_not_found) else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (!anyShown && !peopleShown && text.length >= SEARCH_MIN_CHARS && (searching || searchFailed)) {
            // Nothing to show yet: say that the search runs (with the little train), or that it failed.
            if (searching) {
                LoadingRow(
                    text = stringResource(R.string.search_running),
                    compact = true,
                    modifier = listModifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                val failure = searchFailure
                Text(
                    text = if (failure != null && servicesUnreachable(failure)) {
                        problemText(errorText(context, failure))
                    } else {
                        AnnotatedString(stringResource(R.string.search_failed))
                    },
                    color = AppColors.Muted,
                    fontSize = 14.sp,
                    modifier = listModifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        } else if (anyShown || peopleShown) {
            val resultScroll = rememberScrollState()
            GlassSurface(modifier = listModifier.weight(1f, fill = false), shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier
                        .fadeEdges(resultScroll, horizontal = false)
                        .verticalScroll(resultScroll)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // The quick picks follow "Aktueller Standort", only while nothing is typed (#173).
                    val quick = if (query.isEmpty() && pickedText == null && shown.firstOrNull()?.id == CURRENT_LOCATION_ID) nearbyQuick else emptyList()
                    // A stop among the quick picks is not listed a second time further down.
                    val quickIds = quick.mapTo(HashSet()) { it.stop.id }
                    val rows = if (quickIds.isEmpty()) shown else shown.filter { it.id !in quickIds }
                    val rowsLast = rows.lastIndex + quick.size
                    rows.forEachIndexed { index, place ->
                        val row = if (index == 0) 0 else index + quick.size
                        key(place.id) {
                            ResultRow(
                                place = place,
                                fromHistory = place.id in historyIds,
                                modifier = appearModifier(row),
                                shape = groupShape(row, rowsLast),
                                onClick = {
                                    onRemember(place)
                                    pickedText = place.name
                                    onPick(place)
                                },
                                onForget = { onForget(place) },
                            )
                        }
                        if (index == 0) {
                            quick.forEachIndexed { at, pick ->
                                key("quick:" + pick.stop.id) {
                                    NearbyQuickRow(
                                        quick = pick,
                                        shape = groupShape(at + 1, rowsLast),
                                        modifier = appearModifier(at + 1),
                                        onClick = {
                                            val place = pick.stop.copy(distanceMeters = null)
                                            onRemember(place)
                                            pickedText = place.name
                                            onPick(place)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    // The contacts and appointments that fit, each under its heading (#68).
                    if (contactHits.isNotEmpty()) {
                        SearchSectionTitle(stringResource(R.string.r5search_from_contacts), first = shown.isEmpty())
                        contactHits.forEach { contact ->
                            ContactRow(
                                contact,
                                enabled = !resolving,
                                onClick = { pickContact(contact) },
                                busy = contact == resolvingContact,
                                note = if (contact == failedContact) stringResource(R.string.contacts_not_found) else null,
                            )
                        }
                    }
                    if (appointmentHits.isNotEmpty()) {
                        SearchSectionTitle(stringResource(R.string.r5search_from_appointments), first = shown.isEmpty() && contactHits.isEmpty())
                        if (appointmentNotFound) {
                            Text(
                                text = stringResource(R.string.appointment_place_missing),
                                color = AppColors.DelayRed,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            )
                        }
                        appointmentHits.forEach { appointment ->
                            AppointmentRow(appointment, enabled = !resolving, onClick = { pickAppointment(appointment) })
                        }
                    }
                    // The places picked before and the favourites that fit, below the contacts and
                    // appointments (#208); under a heading only when these stand above them.
                    if (knownShown.isNotEmpty()) {
                        val peopleAbove = contactHits.size + appointmentHits.size
                        val knownAbove = shown.size + peopleAbove
                        if (peopleAbove > 0) SearchSectionTitle(stringResource(R.string.r7night_search_known), first = false)
                        knownShown.forEachIndexed { index, place ->
                            key("known:" + place.id) {
                                ResultRow(
                                    place = place,
                                    fromHistory = place.id in historyIds,
                                    modifier = appearModifier(knownAbove + index),
                                    shape = groupShape(index, knownShown.lastIndex),
                                    onClick = {
                                        onRemember(place)
                                        pickedText = place.name
                                        onPick(place)
                                    },
                                    onForget = { onForget(place) },
                                )
                            }
                        }
                    }
                    // The stations and places below, each group under its heading (#208); without
                    // typing, the stations of a far town as before.
                    val groups = if (sections != null) {
                        listOf(
                            R.string.r7search_local_transport to sections.local,
                            R.string.search_long_distance to sections.longDistance,
                            R.string.r7search_places to sections.places,
                        )
                    } else {
                        listOf(R.string.search_long_distance to farShown)
                    }
                    var above = shown.size + contactHits.size + appointmentHits.size + knownShown.size
                    groups.forEach { (title, places) ->
                        if (places.isEmpty()) return@forEach
                        SearchSectionTitle(stringResource(title), first = above == 0)
                        places.forEachIndexed { index, place ->
                            key("sec:" + title + ":" + place.id) {
                                ResultRow(
                                    place = place,
                                    fromHistory = false,
                                    modifier = appearModifier(above + index),
                                    shape = groupShape(index, places.lastIndex),
                                    onClick = {
                                        onRemember(place)
                                        pickedText = place.name
                                        onPick(place)
                                    },
                                    onForget = { onForget(place) },
                                )
                            }
                        }
                        above += places.size
                    }
                    // Places from OpenStreetMap (Photon) are credited below the list.
                    if ((sections?.all ?: shown).any { isOsmPlace(it) }) {
                        Text(
                            text = stringResource(R.string.osm_attribution),
                            color = AppColors.Muted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// Favourite places offered as chips above the suggestions of a search.
private const val MAX_QUICK_PLACES = 4

// Contacts and appointments shown among the results while typing, at most this many of each (#68).
private const val INLINE_PEOPLE = 3

// The round buttons beside the places above the results: map, contacts, appointments (#68).
private val SEARCH_TOOL_SIZE = 44.dp

// A contact's address or an appointment's place is given up on after this long (#58).
private const val CONTACT_RESOLVE_MILLIS = 25_000L

/** A heading of a group among the results of the search (contacts, appointments). */
@Composable
private fun SearchSectionTitle(text: String, first: Boolean) {
    Text(
        text = text,
        color = AppColors.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = if (first) 4.dp else 10.dp, bottom = 2.dp),
    )
}

// Places from the history shown while nothing is typed, and at most while typing.
private const val HISTORY_EMPTY = 8
private const val HISTORY_TYPED = 5

/**
 * True when every word of [text] begins a word of the name or the areas of [place], in any
 * order and whatever the case; words that only name the town may be missing.
 */
private fun historyMatches(place: Stop, text: String): Boolean =
    placeScore(searchTokens(text), place, null) >= 0.0

/**
 * A place in the list of a search. One from the history has a small clock before it and a
 * small cross at its end that removes it from the history; a long press offers the same.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(
    place: Stop,
    fromHistory: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // A stop pressed long opens the menu every stop has (#139); the tap still picks it.
    val stopHost = LocalStopMenuHost.current?.takeIf { place.kind == PlaceKind.Stop }
    var stopMenuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(AppColors.Card)
                .then(
                    when {
                        stopHost != null -> Modifier.combinedClickable(
                            onLongClickLabel = stringResource(R.string.stop_menu),
                            onLongClick = { stopMenuOpen = true },
                            onClick = onClick,
                        )
                        fromHistory -> Modifier.combinedClickable(
                            onLongClickLabel = stringResource(R.string.history_remove),
                            onLongClick = { menuOpen = true },
                            onClick = onClick,
                        )
                        else -> Modifier.clickable(onClick = onClick)
                    },
                )
                .defaultMinSize(minHeight = 52.dp)
                .padding(start = 14.dp, end = if (fromHistory) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (fromHistory) GlyphIcon(Glyph.Recent, color = AppColors.Muted, size = 16.dp)
            PlaceSymbols(place)
            // A point of interest or an address has its kind and where it lies as a second line.
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    text = place.name.shortStopName(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = place.detail ?: if (place.kind == PlaceKind.Stop) place.name.stopNamePart() else null
                if (detail != null) {
                    Text(
                        text = detail,
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (fromHistory) {
                val removeLabel = stringResource(R.string.history_remove)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = removeLabel, onClick = onForget)
                        .semantics { contentDescription = removeLabel }
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Close, color = AppColors.Muted, size = 14.dp)
                }
            }
        }
        if (stopHost != null) {
            StopMenu(
                open = stopMenuOpen,
                onDismiss = { stopMenuOpen = false },
                onAction = { action -> stopHost.onAction(place, action) },
                place = place,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.history_remove)) },
                onClick = {
                    menuOpen = false
                    onForget()
                },
            )
        }
    }
}

/** A favourite place in the search: its icon and name in a chip, picked with one tap. */
@Composable
private fun QuickPlaceChip(glyph: Glyph, name: String, onClick: () -> Unit, small: Boolean = false) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(AppColors.Card)
            .border(1.dp, glassRim(), shape)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A saved place has a quieter disc than a favourite place.
        Box(
            modifier = Modifier
                .size(if (small) 24.dp else 28.dp)
                .clip(CircleShape)
                .background(if (small) AppColors.BlueContainer else AppColors.Blue),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, color = if (small) AppColors.OnBlueContainer else AppColors.OnBlue, size = if (small) 14.dp else 16.dp)
        }
        Text(
            text = name,
            color = AppColors.Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

// The search offers the appointments of this many days ahead, at most this many.
private const val APPOINTMENT_DAYS = 14
private const val APPOINTMENT_ROWS = 30

/** An appointment in the search: its title, below it when it starts and where it takes place. */
@Composable
private fun AppointmentRow(appointment: Appointment, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = appointment.title.ifBlank { appointment.location },
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = appointmentTime(appointment, OffsetDateTime.now()),
            color = AppColors.Blue,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Text(
            text = appointment.location,
            color = AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A contact in the search: the name, and below it the address the journey would lead to. */
@Composable
private fun ContactRow(
    contact: ContactAddress,
    enabled: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
    note: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // The type of the address as a small chip, so several addresses of one person differ.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = contact.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val kind = contact.kind
            if (kind != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = kind,
                    color = AppColors.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, AppColors.Muted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Text(
            text = contact.address,
            color = AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // While its address is looked up the row says so; one not found says that beside it (#58).
        if (busy) {
            LoadingRow(
                text = stringResource(R.string.r5search_contact_resolving),
                compact = true,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (note != null) {
            Text(
                text = note,
                color = AppColors.DelayRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The departures of one stop. The stop is chosen from the pill on top; the chips below
 * it set the time the list starts at and narrow it down to one line. A tap on a departure
 * shows the course of its line, its ⋮ (or a long press) opens its menu. Pulling the list
 * down asks for the departures again ([onRefresh]); [refreshing] while that runs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DeparturesScreen(
    stops: List<Stop>,
    selectedStopId: String?,
    onSelectStop: (String) -> Unit,
    departures: List<Departure>,
    loaded: Boolean,
    // Stops shown together with the selected one ([onToggleAdded] adds or removes one); the
    // rows of [departures] then name their stop.
    addedStops: List<Stop> = emptyList(),
    onToggleAdded: (Stop) -> Unit = {},
    // The menu of the selected stop: journeys from or to it, a shortcut on the home screen.
    onStopAction: ((Stop, StopAction) -> Unit)? = null,
    error: String?,
    now: OffsetDateTime,
    time: TimeChoice?,
    onTime: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDepartureAction: (Departure, DepartureAction) -> Unit,
    // Whether the stop a row leaves from is a favourite stop, for the menu of the rows; the
    // star above stands for the shown stops together ([isFavorite]: with several, their combination).
    stopFavorite: Boolean = isFavorite,
    onSearchStop: () -> Unit = {},
    nearby: Boolean = false,
    onNearby: (Boolean) -> Unit = {},
    nearbyRadius: Int = 500,
    more: MoreDepartures? = null,
    onLoadMore: () -> Unit = {},
    // Shows the selected stop on the map tab; null leaves the button out.
    onShowStation: (() -> Unit)? = null,
    // Filters and scroll position, kept by the caller so a visit to the map comes back to them.
    view: DeparturesView = remember { DeparturesView() },
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    // Where the user is, for "Wann los?"; null without a position.
    here: Pair<Double, Double>? = null,
    leaveOrder: Boolean = false,
    onLeaveOrder: (Boolean) -> Unit = {},
) {
    val selected = stops.firstOrNull { it.id == selectedStopId }
    var choosing by remember { mutableStateOf(false) }
    var stopMenuOpen by remember { mutableStateOf(false) }
    val animationsOn = rememberAnimationsOn()
    // Several stops by choice (not all of them nearby): each row then says where it leaves from.
    val several = !nearby && addedStops.isNotEmpty()
    // The stops to choose from, nearest first by the distance they show; the selected one on top.
    val choices = remember(stops, addedStops, here, selectedStopId) {
        stopsByDistance(stops + addedStops.filter { added -> stops.none { it.id == added.id } }, here, selectedStopId)
    }
    val addLabel = stringResource(R.string.departures_add_stop)
    // The town of the board: directions in other towns name theirs ("Kirchstr. · Zagelsdorf").
    val boardTown = selected?.let { townOf(it.name) ?: it.areas.firstOrNull() }
    // The kind of transport and the line the list is narrowed down to; another stop shows
    // everything again. Both filters work together, on the list as it is loaded.
    // Another stop shows everything again, also when it was picked while the page was away.
    LaunchedEffect(selectedStopId) {
        if (view.stopId != selectedStopId) {
            view.stopId = selectedStopId
            view.product = null
            view.line = null
        }
    }
    val product = view.product.takeIf { view.stopId == selectedStopId }
    val line = view.line.takeIf { view.stopId == selectedStopId }
    fun setFilter(newProduct: String?, newLine: String?) {
        view.stopId = selectedStopId
        view.product = newProduct
        view.line = newLine
    }
    val products = DEPARTURE_PRODUCTS.filter { key -> departures.any { it.product == key } }
    val pickedProduct = product?.takeIf { it in products }
    val ofProduct = if (pickedProduct == null) departures else departures.filter { it.product == pickedProduct }
    val lines = ofProduct.map { it.line }.distinct()
    val picked = line?.takeIf { it in lines }
    val shown = if (picked == null) ofProduct else ofProduct.filter { it.line == picked }
    // The notices of the shown stops, each once, those of the stations first; with a filter
    // only those of its lines (and the stations'). One quiet line, a tap shows them all.
    val filtered = pickedProduct != null || picked != null
    val notices = remember(departures, shown, filtered) {
        boardDisruptions(departures, if (filtered) shown.map { it.line }.toSet() else null)
    }
    // The last notices stay in the line while it folds away.
    val lastNotices = remember { arrayOf(notices) }
    if (notices.isNotEmpty()) lastNotices[0] = notices
    var noticesOpen by remember { mutableStateOf(false) }
    // The list, kept by the caller with its scroll position.
    val listState = view.listState
    // What stands between the stop on top and the list folds away while the list is scrolled
    // down and comes back with any scroll up (see QuickReturnState); the stop stays.
    val quickReturn = rememberQuickReturnState(animationsOn) { listState.canScrollForward || listState.canScrollBackward }
    LaunchedEffect(selectedStopId, nearby) { quickReturn.show() }
    // Whether the part above the list is mostly away; read here only when that changes.
    val headerFolded by remember(quickReturn) { derivedStateOf { quickReturn.mostlyFolded } }
    // "Wann los?": by the latest time to set off on foot; only with a position, and for one
    // stop (or the stops chosen together) only when one is close enough to walk to.
    val leaveUnavailable = when {
        here == null -> R.string.leave_order_no_location
        several && !anyWalkableFrom(here, listOfNotNull(selected) + addedStops) -> R.string.leave_order_too_far
        !nearby && !several && !walkableFrom(here, selected) -> R.string.leave_order_too_far
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GlassSurface(shape = RoundedCornerShape(28.dp)) {
                    Row(
                        modifier = Modifier
                            // A long press opens the menu of the stop, as the ⋮ beside it does.
                            .combinedClickable(
                                onLongClickLabel = if (onStopAction != null && selected != null && !nearby) stringResource(R.string.stop_menu) else null,
                                onLongClick = if (onStopAction != null && selected != null && !nearby) {
                                    { stopMenuOpen = true }
                                } else {
                                    null
                                },
                                onClick = { choosing = true },
                            )
                            .defaultMinSize(minHeight = 56.dp)
                            .then(if (animationsOn) Modifier.animateContentSize() else Modifier)
                            .padding(start = 20.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // The part of a big station the one stop shown is, small below its name.
                        val headerPart = if (nearby || several) null else selected?.name?.stopNamePart()
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = when {
                                    nearby -> stringResource(R.string.departures_nearby_title)
                                    // "Albertstr. +2": the selected stop and how many are added.
                                    several && selected != null ->
                                        stringResource(R.string.departures_stop_plus, selected.name.shortStopName(), addedStops.size)
                                    else -> selected?.name?.shortStopName() ?: stringResource(R.string.choose_stop)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // The sub-line (#223): the part of the station, then how fresh the live
                            // times are ("● Live vor 10 s"); a tap on those loads the list again.
                            val liveShown = loaded && time == null
                            if (headerPart != null || liveShown) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (headerPart != null) {
                                        Text(
                                            text = if (liveShown) "$headerPart · " else headerPart,
                                            color = AppColors.Muted,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                    if (liveShown) {
                                        FreshnessChip(
                                            source = LiveSource.Departures,
                                            onRefresh = onRefresh,
                                            refreshing = refreshing,
                                            minHeight = 24.dp,
                                            plain = true,
                                        )
                                    }
                                }
                            }
                        }
                        // With the notice line folded away above the list, its sign and count
                        // stay here; a tap shows them as the line does.
                        val noticeCount = lastNotices[0].size
                        val noticeLabel = pluralStringResource(R.plurals.departures_notice_count, noticeCount, noticeCount)
                        AnimatedVisibility(
                            visible = loaded && notices.isNotEmpty() && headerFolded,
                            enter = if (animationsOn) fadeIn() + scaleIn(initialScale = 0.6f) else EnterTransition.None,
                            exit = if (animationsOn) fadeOut() + scaleOut(targetScale = 0.6f) else ExitTransition.None,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClickLabel = noticeLabel) { noticesOpen = true }
                                    .semantics { contentDescription = noticeLabel }
                                    .defaultMinSize(minHeight = 40.dp)
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                WarningMark(size = 16.dp)
                                Text(text = noticeCount.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                        Chevron(open = choosing, color = AppColors.Ink)
                    }
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    choices.forEach { stop ->
                        val distance = stop.distanceMeters
                        val isSelected = stop.id == selectedStopId
                        val added = addedStops.any { it.id == stop.id }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (distance != null) {
                                        stringResource(R.string.stop_with_distance, stop.name.listedStopName(), formatDistance(distance))
                                    } else {
                                        stop.name.listedStopName()
                                    },
                                    fontWeight = if (isSelected || added) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                choosing = false
                                // A stop picked by hand leaves the list of all stops nearby, and
                                // is shown on its own.
                                if (nearby) onNearby(false)
                                onSelectStop(stop.id)
                            },
                            // The box at the end shows the stop together with the selected one;
                            // the menu stays open for more.
                            trailingIcon = if (selectedStopId != null) {
                                {
                                    Checkbox(
                                        checked = (isSelected && !nearby) || added,
                                        onCheckedChange = if (isSelected) null else { { _: Boolean -> onToggleAdded(stop) } },
                                        enabled = !isSelected,
                                        modifier = Modifier.semantics { contentDescription = addLabel },
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    // Any other stop, found by its name; it stays in this list afterwards.
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GlyphIcon(Glyph.Search, color = AppColors.Muted)
                                Text(text = stringResource(R.string.search_other_stop), fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = {
                            choosing = false
                            onSearchStop()
                        },
                    )
                }
            }
            if (selectedStopId != null && !nearby && onShowStation != null) {
                RoundButton(
                    glyph = Glyph.Map,
                    description = stringResource(R.string.station_on_map),
                    onClick = onShowStation,
                    size = 56.dp,
                )
            }
            if (selectedStopId != null && !nearby) {
                RoundButton(
                    glyph = if (isFavorite) Glyph.StarFilled else Glyph.Star,
                    // With several stops the star keeps them together, as one favourite.
                    description = stringResource(
                        when {
                            several && isFavorite -> R.string.stop_group_on
                            several -> R.string.stop_group_off
                            isFavorite -> R.string.favorite_on
                            else -> R.string.favorite_off
                        },
                    ),
                    onClick = onToggleFavorite,
                    size = 56.dp,
                )
            }
            // The menu of the stop in plain sight, beside its favourite: "Add to home screen" and
            // journeys from or to it.
            // Journeys from or to the stop, and the stop on the home screen (see StopMenu); the
            // map and the departures have their own places here. The menu opens at the button.
            if (selected != null && !nearby && onStopAction != null) {
                Box {
                    RoundButton(
                        glyph = Glyph.More,
                        description = stringResource(R.string.stop_menu),
                        onClick = { stopMenuOpen = true },
                        size = 44.dp,
                    )
                    StopMenu(
                        open = stopMenuOpen,
                        onDismiss = { stopMenuOpen = false },
                        onAction = { action -> onStopAction(selected, action) },
                        // The star beside it keeps the favourite.
                        omit = setOf(StopAction.Departures, StopAction.Map, StopAction.Favorite),
                    )
                }
            }
        }
        QuickReturnSection(state = quickReturn, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        // The lifts of the station, where a lift source has a key (see LiftUi).
        if (!nearby) StationLiftStatus(selected)
        // The second row of the header (#223): the time ("Jetzt ▾"), the stops ("500 m ▾": this
        // one or all around), then the kinds of transport ("Alle · S · U · <bus>") and the lines.
        // The live status is in the line below the name of the stop; the notices are the chip below.
        val chipScroll = rememberScrollState()
        var radiusOpen by remember { mutableStateOf(false) }
        val radiusText = radiusLabel(nearbyRadius)
        val modes = departureModeFilters(DEPARTURE_PRODUCTS, products)
        Row(
            modifier = Modifier
                .fadeEdges(chipScroll, horizontal = true)
                .horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The time the list starts at: now, or what the user picked (opens the time sheet).
            val timeLabel = when {
                time == null -> stringResource(R.string.time_now)
                time.arrival -> stringResource(R.string.time_arrive_by, dateTimeLabel(time.time))
                else -> dateTimeLabel(time.time)
            }
            HeaderDropChip(
                text = timeLabel,
                open = false,
                active = time != null,
                onClick = onTime,
                description = stringResource(R.string.time_chip, timeLabel),
            )
            // This stop only, or all stops around the user at once, merged by time; the filters still apply.
            val onlyStop = selected?.name?.shortStopName()
            val nearbyAll = stringResource(R.string.r7search_nearby_all, radiusText)
            Box {
                HeaderDropChip(
                    text = radiusText,
                    open = radiusOpen,
                    active = nearby,
                    onClick = { radiusOpen = true },
                    description = stringResource(
                        R.string.r7search_radius_choice,
                        if (nearby || onlyStop == null) nearbyAll else stringResource(R.string.r7search_only_stop, onlyStop),
                    ),
                )
                DropdownMenu(expanded = radiusOpen, onDismissRequest = { radiusOpen = false }) {
                    if (onlyStop != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.r7search_only_stop, onlyStop),
                                    fontWeight = if (!nearby) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                radiusOpen = false
                                if (nearby) onNearby(false)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(text = nearbyAll, fontWeight = if (nearby) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            radiusOpen = false
                            if (!nearby) onNearby(true)
                        },
                    )
                }
            }
            // The kinds of transport that leave here, when there is more than one.
            if (modes.isNotEmpty()) {
                HeaderDivider()
                ModeFilterChip(product = null, active = pickedProduct == null, onClick = { setFilter(null, line) })
                modes.forEach { key ->
                    ModeFilterChip(product = key, active = pickedProduct == key, onClick = { setFilter(key, null) })
                }
            }
            // The lines, when there is more than one.
            if (lines.size > 1) {
                HeaderDivider()
                ChoiceChip(
                    text = stringResource(R.string.r7search_all_lines),
                    active = picked == null,
                    onClick = { setFilter(product, null) },
                    strong = true,
                    minHeight = 40.dp,
                )
                lines.forEach { name ->
                    ChoiceChip(text = badgeLine(name) ?: name, active = picked == name, onClick = { setFilter(product, name) }, strong = true, minHeight = 40.dp)
                }
            }
        }
        // The third row: the order ("Nach Abfahrt | Wann los?") and, at its end, the notices of the stop.
        // The toggle keeps its whole width (#234): with a weight it got at most half of the free
        // row and "Wann los?" was cut to "Wa…" or nothing; where both do not fit, the chip of the
        // notices moves to the next line instead.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LeaveOrderToggle(
                leaveOrder = leaveOrder,
                onLeaveOrder = onLeaveOrder,
                unavailable = leaveUnavailable,
            )
            AnimatedVisibility(
                visible = loaded && notices.isNotEmpty(),
                enter = if (animationsOn) fadeIn() + scaleIn(initialScale = 0.8f) else EnterTransition.None,
                exit = if (animationsOn) fadeOut() + scaleOut(targetScale = 0.8f) else ExitTransition.None,
            ) {
                DisruptionCountChip(count = lastNotices[0].size, onClick = { noticesOpen = true })
            }
        }
        }
        } // The part that folds away; what is inside keeps its old indent.
        if (noticesOpen && notices.isNotEmpty()) {
            WaysDisruptionsSheet(
                title = when {
                    nearby -> stringResource(R.string.departures_nearby_title)
                    else -> selected?.name?.shortStopName() ?: stringResource(R.string.choose_stop)
                },
                subtitle = if (several) (listOfNotNull(selected) + addedStops).joinToString(" · ") { it.name.shortStopName() } else null,
                items = notices,
                cancelled = emptyList(),
                onOpenRoute = {},
                onAll = null,
                onShowOnMap = null,
                onDismiss = { noticesOpen = false },
            )
        }
        // Near the end of the list the next departures load by themselves, once per arrival
        // there; after a failed or empty page only the button below asks again.
        val autoMore by rememberUpdatedState(more != null && more.auto && !more.loading && loaded && shown.isNotEmpty())
        val loadMore by rememberUpdatedState(onLoadMore)
        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                (listState.canScrollForward || listState.canScrollBackward) && last != null &&
                    last.index == info.totalItemsCount - 1 &&
                    last.offset + last.size - info.viewportEndOffset <= MORE_DEPARTURES_EDGE_PX
            }
                .distinctUntilChanged()
                .collect { atEnd -> if (atEnd && autoMore) loadMore() }
        }
        // "Wann los?" with a position and a stop to walk to: the rows by the time to set off.
        val leaveShown = leaveOrder && leaveUnavailable == null && here != null
        val leaves = if (leaveShown && here != null) {
            // Rows of several stops carry their own (see mergeStopBoards).
            rememberLeaveRows(shown, here, if (nearby || several) null else selected, now)
        } else {
            emptyList()
        }
        // What stands in the card instead of rows: the error, the little train, "nothing leaves".
        val failed = error != null && departures.isEmpty()
        val statusText = when {
            failed -> error
            !loaded -> null
            shown.isEmpty() || (leaveShown && leaves.isEmpty()) -> stringResource(R.string.no_departures)
            else -> null
        }
        val loadingShown = !failed && !loaded
        // Each row keeps its key from one refresh to the next (never its index): it stays the
        // same row, its new times roll in, and it moves to its new place (see departureKeys).
        val rowKeys = remember(shown) { departureKeys(shown) }
        // In a list of several stops each stop has its dot, the same in every row of it.
        val stopMarkerMap = remember(shown) { stopMarkers(shown.mapNotNull { it.stop?.id }) }
        val leaveKeys = remember(leaves) { departureKeys(leaves.map { it.departure }) }
        // Pulled down, the list loads anew: the standard indicator, in the blue of the app.
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp)
                // The list ends above the tab bar, with the same gap as every sheet.
                .padding(bottom = LocalNavInset.current),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = AppColors.Blue,
                )
            },
        ) {
            // The card stays; the rows scroll inside it. Room below for its shadow.
            GlassSurface(modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Inside the pull to refresh: a pull at the top still reaches it.
                        .nestedScroll(quickReturn.connection)
                        // No animated size here: the list follows the part above as it folds, frame by frame.
                        .fadeEdges(listState, horizontal = false),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    when {
                        statusText != null -> item(key = "status") {
                            Text(
                                text = problemText(statusText),
                                color = AppColors.Muted,
                                modifier = rowMotion(animationsOn).padding(12.dp),
                            )
                        }

                        // The little train rides while the first departures load.
                        loadingShown -> item(key = "loading") {
                            LoadingRow(
                                text = stringResource(R.string.loading_departures),
                                modifier = rowMotion(animationsOn).padding(12.dp),
                            )
                        }

                        leaveShown -> itemsIndexed(leaves, key = { index, _ -> leaveKeys[index] }) { index, leave ->
                            LeaveRow(
                                leave = leave,
                                now = now,
                                stopFavorite = stopFavorite,
                                onDepartureAction = onDepartureAction,
                                modifier = rowMotion(animationsOn)
                                    .clip(groupShape(index, leaves.lastIndex, outer = 24.dp))
                                    .background(AppColors.Card),
                                boardTown = boardTown,
                                markers = stopMarkerMap,
                            )
                        }

                        else -> itemsIndexed(shown, key = { index, _ -> rowKeys[index] }) { index, departure ->
                            val rowModifier = rowMotion(animationsOn)
                                .clip(groupShape(index, shown.lastIndex, outer = 24.dp))
                                .background(AppColors.Card)
                            if (departure.stop == null) {
                                DepartureItem(
                                    departure = departure,
                                    now = now,
                                    stopFavorite = stopFavorite,
                                    onAction = { action -> onDepartureAction(departure, action) },
                                    modifier = rowModifier,
                                    contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                                    boardTown = boardTown,
                                )
                            } else {
                                // A list of several stops: the stop with its dot heads the row, as in "Wann los?".
                                Column(modifier = rowModifier) {
                                    StopDepartureHeader(
                                        departure = departure,
                                        boardTown = boardTown,
                                        markers = stopMarkerMap,
                                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                                    )
                                    DepartureItem(
                                        departure = departure,
                                        now = now,
                                        stopFavorite = stopFavorite,
                                        onAction = { action -> onDepartureAction(departure, action) },
                                        contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                                        boardTown = boardTown,
                                        stopInHeader = true,
                                    )
                                }
                            }
                        }
                    }
                    // Later departures: they load near the end by themselves, or with the button.
                    if (more != null && loaded && departures.isNotEmpty()) {
                        item(key = "more") {
                            MoreDeparturesFooter(more = more, onLoadMore = onLoadMore, modifier = rowMotion(animationsOn))
                        }
                    }
                }
            }
        }
    }
}

/**
 * What the user set on the departures tab that outlasts the page: the filters of [stopId]
 * (another stop shows everything again) and how far the list was scrolled. Kept by AppRoot,
 * so "Station on map" and back lead to the list as it was.
 */
@Stable
class DeparturesView {
    var stopId by mutableStateOf<String?>(null)
    var product by mutableStateOf<String?>(null)
    var line by mutableStateOf<String?>(null)
    val listState = LazyListState()
}

/**
 * The state of the later pages of the departures board: [loading] while one is asked for,
 * [failed] when the last one could not be loaded, [none] when it brought nothing new (the
 * next one then starts an hour later), [auto] whether reaching the end loads by itself.
 */
data class MoreDepartures(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val none: Boolean = false,
) {
    val auto: Boolean get() = !failed && !none
}

// Scrolled this close to the end of the departures, the next ones are asked for.
private const val MORE_DEPARTURES_EDGE_PX = 240

/** Below the departures: a spinner while more load, else a visible "Load more" with a note why it stopped. */
@Composable
private fun MoreDeparturesFooter(more: MoreDepartures, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (more.loading) {
            LoadingRow(
                text = stringResource(R.string.departures_more_loading),
                compact = true,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            )
        } else {
            when {
                more.failed -> R.string.departures_more_failed
                more.none -> R.string.departures_more_none
                else -> null
            }?.let { note ->
                Text(text = stringResource(note), color = AppColors.Muted, fontSize = 13.sp)
            }
            TextButton(onClick = onLoadMore, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(text = stringResource(R.string.departures_more), fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
            }
        }
    }
}

/**
 * How a row of the departures comes, goes and moves when the list changes: it fades in, fades
 * out, and slides to its new place with a spring; with animations off it is simply there.
 */
private fun LazyItemScope.rowMotion(animationsOn: Boolean): Modifier =
    if (animationsOn) {
        Modifier.animateItem(
            fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
            placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
            fadeOutSpec = tween(180),
        )
    } else {
        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
    }

// The kinds of transport the departures can be narrowed down to, in this order.
private val DEPARTURE_PRODUCTS = listOf("suburban", "subway", "tram", "bus", "regional", "express", "coach", "ferry")

/** The name of a kind of transport, as on the filter of the departures. */
private fun productName(key: String): Int = productLabel(key)

/** Sheet on the map tab for the selected stop. */
@Composable
fun MapStopSheet(
    stop: Stop?,
    departures: List<Departure>,
    now: OffsetDateTime,
    isFavorite: Boolean,
    onDepartureAction: (Departure, DepartureAction) -> Unit,
    onRouteHere: () -> Unit,
    onDepartures: () -> Unit,
    onCardTop: (Float) -> Unit = {},
    lines: List<StopLine> = emptyList(),
    onLine: (StopLine) -> Unit = {},
    lineDirections: LineDirections? = null,
    onDirection: (Departure) -> Unit = {},
    // Lets go of the stop: the close button in the header and a swipe down on the card.
    onClose: () -> Unit = {},
    // A tapped stop position of the stop: the card shows it instead (see PositionCardContent),
    // with the departures from there; back, its button and a swipe down lead to the stop again.
    position: MapPositionPick? = null,
    positionDepartures: List<Departure> = emptyList(),
    onPositionClose: () -> Unit = {},
    onPositionLine: (OsmLine) -> Unit = {},
    onWalkHere: () -> Unit = {},
    // A back gesture that lets go of the stop (the card shrinks over the map) or of the stop
    // position (its content dissolves into the stop's as the finger goes).
    cardBack: BackPreview? = null,
    positionBack: BackPreview? = null,
) {
    val markedId = stop?.id
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        // The card springs in when a stop is marked and out when it is let go; while it leaves
        // it keeps showing the stop it had.
        val lastStop = remember { arrayOfNulls<Stop>(1) }
        if (stop != null) lastStop[0] = stop
        AnimatedVisibility(
            visible = stop != null,
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f) +
                fadeIn(tween(160)),
            exit = slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { it / 3 } +
                scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.9f) +
                fadeOut(tween(140)),
            modifier = Modifier.backFront(cardBack?.takeIf { it.active }),
        ) {
            val shownStop = stop ?: lastStop[0]
            if (shownStop != null) {
                val stop = shownStop
                GlassSurface(
                    // The top edge of the card on the screen, for a map that centres what is in view above it.
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { onCardTop(it.positionInRoot().y) }
                        .swipeDownToDismiss(markedId to position, if (position != null) onPositionClose else onClose),
                    shape = RoundedCornerShape(32.dp),
                ) {
                    // A new stop or position replaces what the card showed: the old content fades
                    // and slides out while the new one comes in, the card resizing between them.
                    // A back gesture from a stop position seeks the change to the stop as it goes.
                    rememberBackSeek(stop.id to position, positionBack, backTarget = stop.id to null, label = "mapCardContent").AnimatedContent(
                        transitionSpec = {
                            if (positionBack?.active == true) {
                                (fadeIn(tween(BACK_PAGE_MILLIS)) togetherWith fadeOut(tween(BACK_PAGE_MILLIS)))
                                    .using(SizeTransform(clip = false))
                            } else {
                                (fadeIn(tween(180, delayMillis = 60)) + slideInVertically(tween(220)) { it / 6 })
                                    .togetherWith(fadeOut(tween(120)))
                                    .using(SizeTransform(clip = false))
                            }
                        },
                    ) { (_, shownPosition) ->
                    if (shownPosition != null) {
                        PositionCardContent(
                            modifier = Modifier.backShrink(positionBack?.takeIf { it.active }),
                            position = shownPosition,
                            stopName = stop.name.shortStopName(),
                            departures = positionDepartures,
                            now = now,
                            isFavorite = isFavorite,
                            onDepartureAction = onDepartureAction,
                            onLine = onPositionLine,
                            onRouteHere = onRouteHere,
                            onWalkHere = onWalkHere,
                            onBack = onPositionClose,
                        )
                    } else {
                    // Departures that arrive take their room softly.
                    Column(modifier = Modifier.softSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stop.name.shortStopName(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            val distance = stop.distanceMeters
                            if (distance != null) {
                                Text(
                                    text = formatDistance(distance),
                                    color = AppColors.Muted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                            RoundButton(
                                glyph = Glyph.Close,
                                description = stringResource(R.string.map_stop_close),
                                onClick = onClose,
                                size = 36.dp,
                                solid = true,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        // The part of a big station the stop is ("Bus K.-Liebknecht-Str."), small below its name.
                        stop.name.stopNamePart()?.let { part ->
                            Text(
                                text = part,
                                color = AppColors.Muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        departures.take(2).forEachIndexed { index, departure ->
                            if (index > 0) RowDivider()
                            DepartureItem(
                                departure = departure,
                                now = now,
                                stopFavorite = isFavorite,
                                onAction = { action -> onDepartureAction(departure, action) },
                                // They come in one after the other once loaded.
                                modifier = appearModifier(index).clip(RoundedCornerShape(14.dp)),
                                boardTown = stop?.let { townOf(it.name) ?: it.areas.firstOrNull() },
                            )
                        }
                        // Every line that serves the stop, also when nothing leaves right now: a
                        // tap opens its course from here, in the direction picked below.
                        AnimatedVisibility(visible = lines.isNotEmpty()) {
                            StopLinesRow(lines = lines, picked = lineDirections?.line, onLine = onLine, at = stop.position)
                        }
                        AnimatedVisibility(visible = lineDirections != null && lineDirections.line in lines) {
                            val shownDirections = lineDirections
                            if (shownDirections != null) DirectionChips(shownDirections, onDirection)
                        }
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PrimaryButton(
                                text = stringResource(R.string.route_from_here),
                                onClick = onRouteHere,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(26.dp, 10.dp, 10.dp, 26.dp),
                                minHeight = 52.dp,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.all_departures),
                                onClick = onDepartures,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp, 26.dp, 26.dp, 10.dp),
                                minHeight = 52.dp,
                            )
                        }
                    }
                    }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(LocalNavInset.current))
    }
}

/**
 * The directions of one line at a stop to open its course in: [departures] holds the next
 * departure in each direction, null while they load; empty when none runs within a day.
 */
data class LineDirections(val line: StopLine, val departures: List<Departure>?)

// The order of the groups of lines on the card of a stop.
private val STOP_LINE_ORDER = listOf("suburban", "subway", "express", "regional", "tram", "bus", "coach", "ferry")

/** A line name in an order for people: "M10" before "M27", "100" before "200", "N5" before "N40". */
private fun lineSortKey(line: String): String =
    line.replace(Regex("\\d+")) { match -> match.value.padStart(5, '0') }

/**
 * The lines of a stop as badges in one row that scrolls sideways, grouped by product with a
 * little more room between the groups; the picked one is outlined.
 */
@Composable
private fun StopLinesRow(lines: List<StopLine>, picked: StopLine?, onLine: (StopLine) -> Unit, at: Pair<Double, Double>?) {
    val groups = remember(lines) {
        lines.groupBy { it.product }
            .toList()
            .sortedBy { (product, _) -> STOP_LINE_ORDER.indexOf(product).let { if (it < 0) STOP_LINE_ORDER.size else it } }
            .map { (_, group) -> group.sortedBy { lineSortKey(it.line) } }
    }
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .fadeEdges(scroll, horizontal = true)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEachIndexed { index, group ->
            if (index > 0) Spacer(modifier = Modifier.width(6.dp))
            group.forEach { line ->
                val on = line == picked
                LineBadge(
                    line = line.line,
                    product = line.product,
                    minWidth = 38.dp,
                    at = at,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (on) 2.dp else 0.dp, if (on) AppColors.Ink else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { onLine(line) },
                )
            }
        }
    }
}

/** The directions of the picked line, one chip each; a tap opens the course that way. */
@Composable
private fun DirectionChips(directions: LineDirections, onDirection: (Departure) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .fadeEdges(scroll, horizontal = true)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val departures = directions.departures
        when {
            departures == null -> LoadingRow(
                text = stringResource(R.string.loading_departures),
                compact = true,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            departures.isEmpty() -> Text(
                text = stringResource(R.string.line_no_trip),
                color = AppColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            else -> departures.forEach { departure ->
                ChoiceChip(
                    text = stringResource(R.string.line_direction, departure.direction.shortStopName()),
                    active = false,
                    onClick = { onDirection(departure) },
                )
            }
        }
    }
}

/** One tappable line with the sign of a place, used in lists. */
@Composable
private fun PlaceRow(
    place: Stop,
    shape: Shape,
    onClick: () -> Unit,
    // A notice within the stop's reach (its bell on): a "!" that opens them.
    badge: Boolean = false,
    onBadge: () -> Unit = {},
    // The key of its bell (see placeWatchKey); null: no bell.
    bellKey: String? = null,
    onBell: () -> Unit = {},
    onWatchSettings: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlaceSymbols(place)
        Text(
            text = place.name.shortStopName(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (badge) PoppingWarning(modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onBadge))
        if (bellKey != null) DisruptionBell(watchKey = bellKey, onChange = onBell, onSettings = onWatchSettings, size = 40.dp)
    }
}

/** A saved route: a tap searches it again, the cross at the end removes it. */
@Composable
private fun RouteRow(
    route: SavedRoute,
    shape: Shape,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    disruptions: List<Disruption> = emptyList(),
    cancelled: Boolean = false,
    onDisruptions: (() -> Unit)? = null,
    bell: Boolean = false,
    onWatchSettings: (() -> Unit)? = null,
) {
    val routeDescription = stringResource(R.string.route_pair, route.from.name.shortStopName(), route.to.name.shortStopName())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .disruptionFrame(disruptions.isNotEmpty() || cancelled, shape)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 60.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            RoutePairText(
                from = route.from,
                to = route.to,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clearAndSetSemantics { contentDescription = routeDescription },
            )
            // The notices on its next journeys, in one line (see CardDisruptionLine).
            CardDisruptionLine(items = disruptions, onClick = onDisruptions, cancelled = cancelled)
        }
        if (bell) {
            DisruptionBell(route, onSettings = onWatchSettings)
            Spacer(modifier = Modifier.width(6.dp))
        }
        RoundButton(
            glyph = Glyph.Close,
            description = stringResource(R.string.remove_route),
            onClick = onRemove,
            solid = true,
        )
    }
}

/**
 * "My routes": the remembered connections first, then the saved routes, the places with the
 * next journey there, the saved places and the favourite stops; an empty section is one quiet
 * line (an empty list of saved places goes last). The disruptions stay small: a pill beside
 * the title counts them, and the cards they touch are framed with one line about them; both
 * open them in a sheet. The page scrolls as a whole.
 */
@Composable
fun FavoritesScreen(
    tiles: List<PlaceTile>,
    routes: List<SavedRoute>,
    favorites: List<Stop>,
    onOpenRoute: (SavedRoute) -> Unit,
    onRemoveRoute: (SavedRoute) -> Unit,
    onOpen: (Stop) -> Unit,
    // Favourites of several stops together (see StopGroup), listed with the favourite stops.
    stopGroups: List<StopGroup> = emptyList(),
    onOpenGroup: (StopGroup) -> Unit = {},
    savedPlaces: List<SavedPlace> = emptyList(),
    onOpenSaved: (SavedPlace) -> Unit = {},
    onSavedChange: (List<SavedPlace>) -> Unit = {},
    places: List<FavoritePlace> = emptyList(),
    onPlaceChange: (Int, FavoritePlace) -> Unit = { _, _ -> },
    onPlaceChoose: (Int) -> Unit = {},
    onPlaceRemove: (Int) -> Unit = {},
    onPlaceAdd: () -> Unit = {},
    // The favourite places in a new order (a tile dragged in the grid).
    onPlacesOrder: (List<FavoritePlace>) -> Unit = {},
    patterns: List<SavedPattern> = emptyList(),
    onOpenPattern: (SavedPattern) -> Unit = {},
    onRemovePattern: (SavedPattern) -> Unit = {},
    disruptions: DisruptionAccess? = null,
    page: FavoritesPage? = null,
    // The page under [page], where back leads (null: the first page), and the back gesture to it.
    pageBefore: FavoritesPage? = null,
    back: BackPreview? = null,
    onPage: (FavoritesPage) -> Unit = {},
    onPageBack: () -> Unit = {},
    onShowStopOnMap: ((Stop) -> Unit)? = null,
    onWatchSettings: (() -> Unit)? = null,
    here: Pair<Double, Double>? = null,
    showMap: Boolean = false,
    onShowArea: ((List<Pair<Double, Double>>) -> Unit)? = null,
) {
    // All current disruptions open as a page of their own over the favourites. The page
    // belongs to the tab (AppRoot keeps it): it is there again when the tab is picked again,
    // and back goes to the favourites first. It comes and goes as the pages of the app do
    // (fades in rising a little; the old one fades), at once with animations off.
    val shown = if (disruptions != null) page else null
    val pageContent: @Composable (FavoritesPage?) -> Unit = { target ->
        if (target != null && disruptions != null) {
            when (target) {
                FavoritesPage.AllDisruptions -> {
                    // The region: around home and where the user is. The watched ways count
                    // wherever they are: the lines of routes with a bell, places and stops with one.
                    val context = LocalContext.current
                    val watched = remember { DisruptionWatch.watched(context) }
                    val home = places.firstOrNull { it.icon == PlaceIcon.Home }?.place?.position
                    val watchedLines = remember(watched, routes) {
                        routes.filter { it.id in watched }.flatMap { route ->
                            DisruptionWatch.statusOf(context, route.id)?.let { status -> status.lines + status.next.flatMap { it.second } }.orEmpty()
                        }.toSet()
                    }
                    val watchedSpots = remember(watched, places, savedPlaces, favorites) {
                        (places.mapNotNull { it.place } + savedPlaces.map { it.place } + favorites)
                            .filter { place -> placeWatchKey(place)?.let { it in watched } == true }
                            .mapNotNull { it.position }
                    }
                    AllDisruptionsPage(
                        access = disruptions,
                        onBack = onPageBack,
                        onShowOnMap = onShowStopOnMap,
                        anchors = listOfNotNull(home, here),
                        watchedLines = watchedLines,
                        watchedSpots = watchedSpots,
                    )
                }
                is FavoritesPage.PlaceDisruptionsPage ->
                    AllDisruptionsPage(access = disruptions, onBack = onPageBack, place = target, onShowOnMap = onShowStopOnMap)
            }
        } else {
            FavoritesStartPage(
                tiles = tiles,
                routes = routes,
                favorites = favorites,
                onOpenRoute = onOpenRoute,
                onRemoveRoute = onRemoveRoute,
                onOpen = onOpen,
                stopGroups = stopGroups,
                onOpenGroup = onOpenGroup,
                savedPlaces = savedPlaces,
                onOpenSaved = onOpenSaved,
                onSavedChange = onSavedChange,
                places = places,
                onPlaceChange = onPlaceChange,
                onPlaceChoose = onPlaceChoose,
                onPlaceRemove = onPlaceRemove,
                onPlaceAdd = onPlaceAdd,
                onPlacesOrder = onPlacesOrder,
                patterns = patterns,
                onOpenPattern = onOpenPattern,
                onRemovePattern = onRemovePattern,
                disruptions = disruptions,
                onPage = onPage,
                onShowStopOnMap = onShowStopOnMap,
                onWatchSettings = onWatchSettings,
                here = here,
                showMap = showMap,
                onShowArea = onShowArea,
            )
        }
    }
    if (rememberAnimationsOn()) {
        // A back gesture seeks the change to the page before as it goes: the page shrinks over it.
        val shownBefore = if (disruptions != null) pageBefore else null
        rememberBackSeek(shown, back, backTarget = shownBefore, label = "favoritesPage").AnimatedContent(
            transitionSpec = {
                if (back?.active == true) {
                    backContentTransform(back)
                } else {
                    val rise = slideInVertically(
                        spring(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold,
                        ),
                    ) { height -> height / 18 }
                    (fadeIn(tween(220)) + rise) togetherWith fadeOut(tween(160))
                }
            },
        ) { target ->
            // The page a back gesture leaves shrinks over the one before, on a ground of its own.
            val leaving = back?.takeIf { it.active && target != shownBefore }
            Box(
                modifier = if (leaving != null) {
                    Modifier.backShrink(leaving).background(AppColors.PlainGround)
                } else {
                    Modifier
                },
            ) { pageContent(target) }
        }
    } else {
        pageContent(shown)
    }
}

/**
 * The first page of "My routes" (see [FavoritesScreen]), design "E2": one page, no tabs.
 * From the top: the planned trips, the map of the user's ways with the strip of the own
 * statistics below it, the disruptions on the user's ways, the favourite places as a grid of
 * four, the routes and connections in one list, the further places, the favourite stops and
 * the statistics in detail. A lazy list: what is off screen is not composed; the sections come
 * in staggered once (see waysAppear). Nothing is asked while the tab is not shown.
 */
@Composable
private fun FavoritesStartPage(
    tiles: List<PlaceTile>,
    routes: List<SavedRoute>,
    favorites: List<Stop>,
    onOpenRoute: (SavedRoute) -> Unit,
    onRemoveRoute: (SavedRoute) -> Unit,
    onOpen: (Stop) -> Unit,
    // Favourites of several stops together (see StopGroup), listed with the favourite stops.
    stopGroups: List<StopGroup> = emptyList(),
    onOpenGroup: (StopGroup) -> Unit = {},
    savedPlaces: List<SavedPlace> = emptyList(),
    onOpenSaved: (SavedPlace) -> Unit = {},
    onSavedChange: (List<SavedPlace>) -> Unit = {},
    places: List<FavoritePlace> = emptyList(),
    onPlaceChange: (Int, FavoritePlace) -> Unit = { _, _ -> },
    onPlaceChoose: (Int) -> Unit = {},
    onPlaceRemove: (Int) -> Unit = {},
    onPlaceAdd: () -> Unit = {},
    onPlacesOrder: (List<FavoritePlace>) -> Unit = {},
    patterns: List<SavedPattern> = emptyList(),
    onOpenPattern: (SavedPattern) -> Unit = {},
    onRemovePattern: (SavedPattern) -> Unit = {},
    disruptions: DisruptionAccess? = null,
    onPage: (FavoritesPage) -> Unit = {},
    onShowStopOnMap: ((Stop) -> Unit)? = null,
    onWatchSettings: (() -> Unit)? = null,
    here: Pair<Double, Double>? = null,
    showMap: Boolean = false,
    onShowArea: ((List<Pair<Double, Double>>) -> Unit)? = null,
) {
    val context = LocalContext.current
    // A bell switched on or off: the watched keys are read again.
    var bellVersion by remember { mutableIntStateOf(0) }
    val watchedKeys = remember(bellVersion) { DisruptionWatch.watched(context) }
    // The routes whose next journeys are checked: the saved routes, and the ends of the saved
    // connections (their notices are those on the connection's lines).
    val checkedRoutes = remember(routes, patterns) { (routes + patterns.map { SavedRoute(it.from, it.to) }).distinctBy { it.id } }
    // How far around the places and the ends and changes of the routes notices count (a setting
    // of the disruption watch); read again whenever the page comes back from the settings.
    val radius = DisruptionWatch.radiusMeters(context)
    val statusRun = rememberRouteStatusVersion(checkedRoutes, disruptions, radius)
    val statusVersion = statusRun.version
    val statuses = remember(checkedRoutes, statusVersion, disruptions) {
        if (disruptions == null) {
            emptyMap()
        } else {
            checkedRoutes.mapNotNull { route -> DisruptionWatch.statusOf(context, route.id)?.let { route.id to it } }.toMap()
        }
    }
    // The favourite stops with a bell count as places: the notices within their reach.
    val belledStops = remember(favorites, watchedKeys) { favorites.filter { stop -> placeWatchKey(stop)?.let { it in watchedKeys } == true } }
    // The notices around the favourite and the saved places, asked as the routes' are.
    val placeStops = places.mapNotNull { it.place } + savedPlaces.map { it.place } + belledStops
    val placeRun = rememberPlaceDisruptionsVersion(placeStops, disruptions, radius)
    val placeVersion = placeRun.version
    val placeFound = remember(placeVersion, placeStops, radius) {
        placeStops.mapNotNull { place -> placeDisruptionKey(place)?.let { key -> PlaceDisruptionCache.get(key, radius)?.let { key to it } } }.toMap()
    }
    fun aroundOf(place: Stop?): PlaceDisruptions? =
        if (disruptions == null || place == null) null else placeDisruptionKey(place)?.let { placeFound[it] }
    // Every notice once, and each card's: a notice on two cards is one in the pill and the sheet.
    val notices = remember(statuses, placeFound, patterns, routes, places, savedPlaces, belledStops, disruptions) {
        if (disruptions == null) {
            WayNotices.NONE
        } else {
            wayNotices(
                buildList<Pair<String, List<Disruption>>> {
                    patterns.forEach { pattern ->
                        // Those on the connection's lines, and those around its ends and changes.
                        val status = statuses[SavedRoute(pattern.from, pattern.to).id]
                        val onLines = noticesOnLines(status?.rideDisruptions().orEmpty(), pattern.lines)
                        add(patternCardKey(pattern) to onLines + status?.aroundDisruptions().orEmpty())
                    }
                    routes.forEach { route -> add(routeCardKey(route) to statuses[route.id]?.disruptions().orEmpty()) }
                    places.forEachIndexed { at, favorite -> add(placeCardKey(at) to aroundOf(favorite.place)?.disruptions.orEmpty()) }
                    savedPlaces.forEach { saved -> add(savedPlaceCardKey(saved) to aroundOf(saved.place)?.disruptions.orEmpty()) }
                    belledStops.forEach { stop -> add(WAYS_STOP_KEY + stop.id to aroundOf(stop)?.disruptions.orEmpty()) }
                },
            )
        }
    }
    val cancelledRoutes = routes.filter { statuses[it.id]?.cancelled == true }
    // Nothing known yet while something is still to be asked: the pill keeps its room empty.
    // When every check failed, nothing is known: no pill (see waysPill).
    val checkable = checkedRoutes.isNotEmpty() || (disruptions?.checkPlace != null && placeStops.any { it.position != null })
    val pill = waysPill(
        checkable = checkable,
        known = statuses.isNotEmpty() || placeFound.isNotEmpty(),
        done = statusRun.done && placeRun.done,
        count = notices.all.size + cancelledRoutes.size,
    )
    // The open sheet of notices: the key of its card (WAYS_ALL for all of them) and its title.
    var sheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The sheet of the notices hidden from the maps.
    var hiddenOpen by remember { mutableStateOf(false) }
    // The favourite places are managed here (not in the settings): a long press on a tile, or
    // "Edit", opens the editor of the places, that tile's place unfolded.
    var editing by rememberSaveable { mutableStateOf(false) }
    var openPlace by remember { mutableStateOf<Int?>(null) }
    // The statistics in detail, unfolded by a tap on the strip or their title.
    var statsOpen by rememberSaveable { mutableStateOf(false) }
    val allTitle = if (pill.count > 0) {
        context.resources.getQuantityString(R.plurals.ways_disruptions_title, pill.count, pill.count)
    } else {
        stringResource(R.string.ways_pill_clear)
    }
    val now = rememberMinuteClock()
    val today = remember(now) { now.atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate() }
    // The planned trips, read from the device once (see PlannedTrips).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            PlannedTrips.load(context.applicationContext)
            PlannedTrips.prune(context.applicationContext)
        }
    }
    val planned by PlannedTrips.trips.collectAsState()
    val upcoming = remember(planned, now) { planned.filter { !it.leaveAt.isBefore(now) }.sortedBy { it.leaveAt }.take(MAX_PLANNED_SHOWN) }
    // A planned trip cancelled from its card folds away, and comes back with "Undo".
    val plannedMotion = rememberAnimationsOn()
    // The own statistics: read once from the device, off the main thread.
    val recordsRead by TripStats.records.collectAsState()
    LaunchedEffect(Unit) { if (TripStats.records.value == null) withContext(Dispatchers.IO) { TripStats.load(context.applicationContext) } }
    val records = recordsRead.orEmpty()
    val summary = remember(records, today) { tripSummary(records, today) }
    val seen = remember { WaysAppearance() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // A tap on a notification of the disruption watch (see DisruptionTap): the section comes into
    // view and the notices it reported light up briefly; one alone opens as from its row. What is
    // still being asked gets a moment first; what is over is said so (by AppRoot).
    var tapHighlight by remember { mutableStateOf(emptySet<String>()) }
    val tap by DisruptionTap.pending.collectAsState()
    val tapNotices by rememberUpdatedState(notices.all)
    val tapIndex by rememberUpdatedState(waysDisruptionsIndex(upcoming.size, showMap))
    val checksDone = statusRun.done && placeRun.done
    LaunchedEffect(tap, checksDone) {
        val request = tap ?: return@LaunchedEffect
        if (!checksDone) delay(TAP_WAIT_MILLIS)
        val target = if (disruptions == null) DisruptionTapTarget.Gone else disruptionTapTarget(request, tapNotices)
        // Taken: clearing it restarts this effect, so what follows runs in the page's scope.
        DisruptionTap.pending.value = null
        if (target == DisruptionTapTarget.Gone) {
            DisruptionTap.gone.value = true
            return@LaunchedEffect
        }
        scope.launch {
            if (plannedMotion) list.animateScrollToItem(tapIndex) else list.scrollToItem(tapIndex)
            tapHighlight = when (target) {
                is DisruptionTapTarget.One -> setOf(target.id)
                is DisruptionTapTarget.Several -> target.ids.toSet()
                else -> emptySet()
            }
            if (target is DisruptionTapTarget.One) {
                animationDelay(TAP_SHEET_DELAY_MILLIS)
                tapNotices.firstOrNull { it.id == target.id }?.let { item -> sheet = (NOTICE_KEY + item.id) to item.summary.ifEmpty { item.text } }
            }
            delay(TAP_HIGHLIGHT_MILLIS)
            tapHighlight = emptySet()
        }
    }
    // The tiles of the favourite places only (the start page's row has those of routes too).
    val placeTiles = tiles.filter { it.key.startsWith("place") && it.key.removePrefix("place").toIntOrNull() != null }
    // Keys of the favourite places that follow a place when the order changes (the tiles'
    // own keys are by position): a dropped tile stays where it was let go.
    val placeKeys = remember(places) {
        val counts = HashMap<String, Int>()
        places.map { favorite ->
            val base = favorite.icon.name + "|" + favorite.name + "|" + favorite.place?.id
            val seenBefore = counts[base] ?: 0
            counts[base] = seenBefore + 1
            "$base#$seenBefore"
        }
    }
    LazyColumn(
        state = list,
        modifier = Modifier
            .fillMaxSize()
            .fadeEdges(horizontal = false, cutAtStart = { list.canScrollBackward }, cutAtEnd = { list.canScrollForward })
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.tab_favorites),
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // The disruptions on the user's ways, known but small (see WaysStatusPill).
                if (disruptions != null && !pill.unknown) WaysStatusPill(pill = pill, onClick = { sheet = WAYS_ALL to allTitle })
            }
        }
        // a. The planned trips, soonest first.
        items(upcoming, key = { "planned:" + it.id }) { trip ->
            PlannedTripCard(trip = trip, now = now, modifier = rowMotion(plannedMotion).waysAppear("planned:" + trip.id, 0, seen))
        }
        // b. The map of the user's world, and the strip of the own statistics under it.
        if (showMap) {
            item(key = "map") {
                Box(modifier = Modifier.waysAppear("map", 1, seen)) {
                    WaysMap(
                        tiles = tiles,
                        places = places,
                        savedPlaces = savedPlaces,
                        routes = routes,
                        patterns = patterns,
                        statuses = statuses,
                        notices = notices,
                        radius = radius,
                        here = here,
                        onOpenSaved = onOpenSaved,
                        onNotice = { id ->
                            val notice = notices.all.firstOrNull { it.id == id }
                            if (notice != null) sheet = (NOTICE_KEY + id) to notice.summary.ifEmpty { notice.text }
                        },
                        onShowArea = onShowArea,
                    )
                }
            }
        }
        item(key = "strip") {
            WaysStatsStrip(
                summary = summary,
                onClick = {
                    statsOpen = true
                    scope.launch {
                        // The details are the last but one item; scrolled to once they are there.
                        list.animateScrollToItem((list.layoutInfo.totalItemsCount - 2).coerceAtLeast(0))
                    }
                },
                modifier = Modifier.waysAppear("strip", 2, seen),
            )
        }
        // c. The disruptions on the user's ways.
        if (disruptions != null) {
            item(key = "disruptions") {
                Box(modifier = Modifier.waysAppear("disruptions", 3, seen)) {
                    WaysDisruptionsSection(
                        items = notices.all,
                        known = !pill.unknown && pill.count == 0 && statusRun.done && placeRun.done,
                        onItem = { item -> sheet = (NOTICE_KEY + item.id) to item.summary.ifEmpty { item.text } },
                        onMore = { sheet = WAYS_ALL to allTitle },
                        onAll = { onPage(FavoritesPage.AllDisruptions) },
                        highlight = tapHighlight,
                    )
                }
            }
        }
        // d. The favourite places, two by two.
        item(key = "places") {
            Column(modifier = Modifier.waysAppear("places", 4, seen), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.ways_places_title), modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(if (editing) R.string.places_edit_done else R.string.places_edit),
                        color = AppColors.Blue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                editing = !editing
                                if (!editing) openPlace = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                PlaceTileGrid(
                    tiles = placeTiles.map { tile ->
                        val at = tile.key.removePrefix("place").toIntOrNull() ?: 0
                        val key = placeCardKey(at)
                        tile.copy(
                            key = placeKeys.getOrElse(at) { tile.key },
                            onLongClick = {
                                editing = true
                                openPlace = at
                            },
                            disruptions = notices.of(key),
                            onDisruptions = { sheet = key to tile.title },
                            disruptionRadius = radius,
                        )
                    },
                    now = now,
                    onMove = { keys ->
                        // The places in the new order of their keys.
                        val moved = keys.mapNotNull { key -> placeKeys.indexOf(key).takeIf { it >= 0 }?.let { places[it] } }
                        if (moved.size == places.size) onPlacesOrder(moved)
                    },
                )
                AnimatedVisibility(visible = editing) {
                    FavoritePlacesEditor(
                        places = places,
                        openPlace = openPlace,
                        onOpenPlace = { openPlace = it },
                        onPlaceChange = onPlaceChange,
                        onPlaceChoose = onPlaceChoose,
                        onPlaceRemove = onPlaceRemove,
                        onPlaceAdd = onPlaceAdd,
                        onBell = { bellVersion++ },
                        onWatchSettings = onWatchSettings,
                    )
                }
            }
        }
        // e. The saved routes and connections, one list, each with its kind.
        item(key = "routes") {
            Box(modifier = Modifier.waysAppear("routes", 5, seen)) {
                if (patterns.isEmpty() && routes.isEmpty()) {
                    EmptySectionLine(stringResource(R.string.ways_routes_title), stringResource(R.string.ways_empty_routes))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(stringResource(R.string.ways_routes_title))
                        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                            // A card that goes or comes moves the others softly (see softSize).
                            Column(modifier = Modifier.softSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val last = patterns.size + routes.size - 1
                                val nowMillis = now.toInstant().toEpochMilli()
                                routes.forEachIndexed { index, route ->
                                    key(routeCardKey(route)) {
                                        val cardKey = routeCardKey(route)
                                        val title = stringResource(R.string.route_pair, route.from.name.shortStopName(), route.to.name.shortStopName())
                                        // The next journey: of its tile where the start page has one, else as last checked.
                                        val next = tiles.firstOrNull { it.key == route.id }?.journey?.departure?.toInstant()?.toEpochMilli()
                                            ?.takeIf { it >= nowMillis }
                                            ?: statuses[route.id]?.nextDeparture(nowMillis)
                                        Column {
                                            WaysTypeLine(pattern = false, next = next, now = now)
                                            RouteRow(
                                                route = route,
                                                shape = groupShape(index, last),
                                                onClick = { onOpenRoute(route) },
                                                onRemove = { onRemoveRoute(route) },
                                                disruptions = notices.of(cardKey),
                                                cancelled = route in cancelledRoutes,
                                                onDisruptions = { sheet = cardKey to title },
                                                bell = disruptions != null,
                                                onWatchSettings = onWatchSettings,
                                            )
                                        }
                                    }
                                }
                                patterns.forEachIndexed { index, pattern ->
                                    key(patternCardKey(pattern)) {
                                        val cardKey = patternCardKey(pattern)
                                        val next = statuses[SavedRoute(pattern.from, pattern.to).id]?.nextDeparture(nowMillis, pattern.lines)
                                        Column {
                                            WaysTypeLine(pattern = true, next = next, now = now)
                                            PatternRow(
                                                pattern = pattern,
                                                shape = groupShape(routes.size + index, last),
                                                onClick = { onOpenPattern(pattern) },
                                                onRemove = { onRemovePattern(pattern) },
                                                disruptions = notices.of(cardKey),
                                                onDisruptions = { sheet = cardKey to pattern.label },
                                                bellKey = if (disruptions != null) SavedRoute(pattern.from, pattern.to).id else null,
                                                onWatchSettings = onWatchSettings,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // f. The further places, as many as the user keeps.
        item(key = "saved") {
            Box(modifier = Modifier.waysAppear("saved", 6, seen)) {
                if (savedPlaces.isEmpty()) {
                    EmptySectionLine(stringResource(R.string.ways_more_places), stringResource(R.string.ways_empty_places))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(stringResource(R.string.ways_more_places))
                        SavedPlacesList(
                            places = savedPlaces,
                            onOpen = onOpenSaved,
                            onChange = onSavedChange,
                            disruptionsOf = { saved -> notices.of(savedPlaceCardKey(saved)) },
                            onDisruptions = { saved -> sheet = savedPlaceCardKey(saved) to savedPlaceName(saved) },
                            disruptionRadius = radius,
                            bell = disruptions != null,
                            onBell = { bellVersion++ },
                            onWatchSettings = onWatchSettings,
                        )
                    }
                }
            }
        }
        // The favourite stops and the stops kept together, each stop with its bell.
        item(key = "stops") {
            Box(modifier = Modifier.waysAppear("stops", 6, seen)) {
                if (favorites.isEmpty() && stopGroups.isEmpty()) {
                    EmptySectionLine(stringResource(R.string.favorite_stops), stringResource(R.string.ways_empty_stops))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(stringResource(R.string.favorite_stops))
                        val lastRow = favorites.size + stopGroups.size - 1
                        GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                            Column(modifier = Modifier.softSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                favorites.forEachIndexed { index, stop ->
                                    key(stop.id) {
                                        val stopKey = WAYS_STOP_KEY + stop.id
                                        PlaceRow(
                                            place = stop,
                                            shape = groupShape(index, lastRow),
                                            onClick = { onOpen(stop) },
                                            badge = notices.of(stopKey).isNotEmpty(),
                                            onBadge = { sheet = stopKey to stop.name.shortStopName() },
                                            bellKey = if (disruptions != null) placeWatchKey(stop) else null,
                                            onBell = { bellVersion++ },
                                            onWatchSettings = onWatchSettings,
                                        )
                                    }
                                }
                                stopGroups.forEachIndexed { index, group ->
                                    key(group.id) {
                                        StopGroupRow(
                                            group = group,
                                            shape = groupShape(favorites.size + index, lastRow),
                                            onClick = { onOpenGroup(group) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // g. The own trips in detail: only what the strip does not show.
        item(key = "stats") {
            Column(modifier = Modifier.waysAppear("stats", 6, seen), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { statsOpen = !statsOpen }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(stringResource(R.string.ways_stats_title), modifier = Modifier.weight(1f))
                    Chevron(open = statsOpen, modifier = Modifier.padding(end = 8.dp))
                }
                AnimatedVisibility(visible = statsOpen) {
                    WaysStatsDetails(records = records, today = today, summary = summary)
                }
            }
        }
        item(key = "end") {
            Column {
                // The notices hidden from the maps, to show again; only while there are any.
                HiddenNoticesLink(onClick = { hiddenOpen = true })
                Spacer(modifier = Modifier.height(LocalNavInset.current))
            }
        }
    }
    if (hiddenOpen) HiddenNoticesSheet(onDismiss = { hiddenOpen = false })
    val open = sheet
    if (open != null && disruptions != null) {
        val (key, title) = open
        val all = key == WAYS_ALL
        val items = when {
            all -> notices.all
            // One notice, from its marker on the map or its row.
            key.startsWith(NOTICE_KEY) -> notices.all.filter { it.id == key.removePrefix(NOTICE_KEY) }
            else -> notices.of(key)
        }
        // Where the notices of one card were looked for: around a place, or on a route and around it.
        val around = radiusLabel(radius)
        val subtitle = when {
            key.startsWith(WAYS_ROUTE_KEY) || key.startsWith(WAYS_PATTERN_KEY) -> stringResource(R.string.ways_sheet_route_radius, around)
            key.startsWith(WAYS_PLACE_KEY) || key.startsWith(WAYS_SAVED_KEY) || key.startsWith(WAYS_STOP_KEY) ->
                pluralStringResource(R.plurals.disruptions_within, items.size, items.size, around)
            else -> null
        }
        WaysDisruptionsSheet(
            title = title,
            subtitle = subtitle,
            items = items,
            cancelled = if (all) cancelledRoutes else cancelledRoutes.filter { routeCardKey(it) == key },
            onOpenRoute = onOpenRoute,
            onAll = {
                sheet = null
                onPage(FavoritesPage.AllDisruptions)
            },
            onShowOnMap = onShowStopOnMap?.let { show ->
                { stop: Stop ->
                    sheet = null
                    show(stop)
                }
            },
            onDismiss = { sheet = null },
        )
    }
}

/**
 * The map of "My routes" (see WaysMapHeader): the favourite and saved places as pins, the
 * notices at their stops, the saved routes, and the radius around each place and spot.
 */
@Composable
private fun WaysMap(
    tiles: List<PlaceTile>,
    places: List<FavoritePlace>,
    savedPlaces: List<SavedPlace>,
    routes: List<SavedRoute>,
    patterns: List<SavedPattern>,
    statuses: Map<String, StoredRouteStatus>,
    notices: WayNotices,
    radius: Int,
    here: Pair<Double, Double>?,
    onOpenSaved: (SavedPlace) -> Unit,
    onNotice: (String) -> Unit,
    onShowArea: ((List<Pair<Double, Double>>) -> Unit)?,
) {
    val mapPlaces = buildList<WaysMapPlace> {
        places.forEachIndexed { at, favorite ->
            val position = favorite.place?.position ?: return@forEachIndexed
            val tile = tiles.firstOrNull { it.key == "place$at" }
            add(
                WaysMapPlace(
                    key = placeCardKey(at),
                    glyph = placeGlyph(favorite.icon),
                    name = tile?.title ?: favorite.name,
                    position = position,
                    minutes = tile?.takeIf { !it.here }?.journey?.durationMinutes?.toInt(),
                    affected = notices.of(placeCardKey(at)).isNotEmpty(),
                    onClick = tile?.onClick ?: {},
                ),
            )
        }
        savedPlaces.forEach { saved ->
            val position = saved.place.position ?: return@forEach
            add(
                WaysMapPlace(
                    key = savedPlaceCardKey(saved),
                    glyph = placeGlyph(saved.icon),
                    name = savedPlaceName(saved),
                    position = position,
                    minutes = null,
                    affected = notices.of(savedPlaceCardKey(saved)).isNotEmpty(),
                    onClick = { onOpenSaved(saved) },
                ),
            )
        }
    }
    val mapRoutes = routes.mapNotNull { route ->
        val from = route.from.position ?: return@mapNotNull null
        val to = route.to.position ?: return@mapNotNull null
        WaysMapRoute(from, to)
    }
    // The radius around each place, and around the start, the end and the changes of
    // each saved route and connection: in the colour of warnings where a notice lies in it.
    val mapAreas = remember(mapPlaces, statuses, routes, patterns, notices, radius) {
        val placeAreas = mapPlaces.map { WaysMapArea(it.position, radius, it.affected) }
        val wayAreas = (routes.map { routeCardKey(it) to it } + patterns.map { patternCardKey(it) to SavedRoute(it.from, it.to) })
            .flatMap { (key, route) ->
                val status = statuses[route.id]?.takeIf { it.radiusMeters == radius } ?: return@flatMap emptyList<WaysMapArea>()
                val found = notices.of(key)
                status.spots.mapNotNull { spot ->
                    val position = spot.position ?: return@mapNotNull null
                    val touched = found.any { item -> item.stops.any { stop -> stop.position?.let { spotMeters(position, it) <= radius } == true } }
                    WaysMapArea(position, radius, touched)
                }
            }
        // A spot of two routes, or of a route and a place, is one circle; touched when one of them is.
        (placeAreas + wayAreas).groupBy { it.position }.map { (position, same) -> WaysMapArea(position, radius, same.any { it.affected }) }
    }
    WaysMapHeader(
        here = here,
        places = mapPlaces,
        notices = remember(notices, mapAreas) { noticeSpots(notices.all, mapAreas) },
        routes = mapRoutes,
        areas = mapAreas,
        onNotice = onNotice,
        onMap = { points -> onShowArea?.invoke(points) },
    )
}

/** The editor of the favourite places, under their grid: one row per place (with its bell), and "Add". */
@Composable
private fun FavoritePlacesEditor(
    places: List<FavoritePlace>,
    openPlace: Int?,
    onOpenPlace: (Int?) -> Unit,
    onPlaceChange: (Int, FavoritePlace) -> Unit,
    onPlaceChoose: (Int) -> Unit,
    onPlaceRemove: (Int) -> Unit,
    onPlaceAdd: () -> Unit,
    onBell: () -> Unit,
    onWatchSettings: (() -> Unit)?,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val canAdd = places.size < MAX_FAVORITE_PLACES
            val lastRow = if (canAdd) places.size else places.lastIndex
            places.forEachIndexed { index, favorite ->
                FavoritePlaceRow(
                    shape = groupShape(index, lastRow),
                    favorite = favorite,
                    open = openPlace == index,
                    onToggle = { onOpenPlace(if (openPlace == index) null else index) },
                    onChange = { onPlaceChange(index, it) },
                    onChoose = { onPlaceChoose(index) },
                    onRemove = {
                        onOpenPlace(null)
                        onPlaceRemove(index)
                    },
                    onBell = onBell,
                    onWatchSettings = onWatchSettings,
                )
            }
            if (canAdd) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(groupShape(places.size, lastRow))
                        .background(AppColors.Card)
                        .clickable {
                            onOpenPlace(places.size)
                            onPlaceAdd()
                        }
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlyphIcon(Glyph.Plus, color = AppColors.Blue)
                    Text(
                        text = stringResource(R.string.place_add),
                        color = AppColors.Blue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/** A section of "My routes" without an entry: its name and a hint, in one quiet line. */
@Composable
private fun EmptySectionLine(label: String, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, color = AppColors.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text(
            text = hint,
            color = AppColors.Muted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// The keys of the cards of "My routes" for their notices (see wayNotices), and of the sheet of all of them.
private const val WAYS_ALL = "*"
private const val NOTICE_KEY = "notice:"
// A tap on a disruption notification: how long the checks may still answer, when the one
// notice opens (x the animator scale) and how long the reported notices stay lit.
private const val TAP_WAIT_MILLIS = 6_000L
private const val TAP_SHEET_DELAY_MILLIS = 450L
private const val TAP_HIGHLIGHT_MILLIS = 1_600L
private const val WAYS_PATTERN_KEY = "pattern:"
private const val WAYS_ROUTE_KEY = "route:"
private const val WAYS_PLACE_KEY = "place:"
private const val WAYS_SAVED_KEY = "saved:"
private const val WAYS_STOP_KEY = "stop:"
private fun patternCardKey(pattern: SavedPattern): String = WAYS_PATTERN_KEY + pattern.id
private fun routeCardKey(route: SavedRoute): String = WAYS_ROUTE_KEY + route.id
private fun placeCardKey(at: Int): String = WAYS_PLACE_KEY + at
private fun savedPlaceCardKey(saved: SavedPlace): String = WAYS_SAVED_KEY + saved.id

// The planned trips shown at most at the top of "My routes".
private const val MAX_PLANNED_SHOWN = 2

// The last row of the connections group of the settings.
private const val SEARCH_LAST = 8

// Where OpenStreetMap explains its copyright and licence.
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

@Composable
fun SettingsScreen(
    options: SearchOptions,
    onOptions: (SearchOptions) -> Unit,
    delayDisplay: DelayDisplay,
    onDelayDisplay: (DelayDisplay) -> Unit,
    fitLine: Boolean,
    onFitLine: (Boolean) -> Unit,
    startVehicles: Boolean,
    onStartVehicles: (Boolean) -> Unit,
    startMap: Boolean,
    onStartMap: (Boolean) -> Unit,
    startClassic: Boolean,
    onStartClassic: (Boolean) -> Unit,
    startCards: Boolean,
    onStartCards: (Boolean) -> Unit,
    startDepartures: Boolean,
    onStartDepartures: (Boolean) -> Unit,
    startCalendar: Boolean,
    onStartCalendar: (Boolean) -> Unit,
    nearbyRadius: Int,
    onNearbyRadius: (Int) -> Unit,
    boardingHints: Boolean,
    onBoardingHints: (Boolean) -> Unit,
    transferChoice: Boolean,
    onTransferChoice: (Boolean) -> Unit,
    planFirst: Boolean,
    onPlanFirst: (Boolean) -> Unit,
    listView: Boolean,
    onListView: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    connectionsFull: Boolean,
    onConnectionsFull: (Boolean) -> Unit,
    chartChangeStops: Boolean,
    onChartChangeStops: (Boolean) -> Unit,
    chartTotalTime: ChartTotalTime,
    onChartTotalTime: (ChartTotalTime) -> Unit,
    connectionChoice: Boolean,
    onConnectionChoice: (Boolean) -> Unit,
    sourceChoice: SourceChoice,
    onSourceChoice: (SourceChoice) -> Unit,
    ownServer: String,
    onOwnServer: (String) -> Unit,
    onBack: () -> Unit,
    focusWatch: Boolean = false,
    onFocusWatchDone: () -> Unit = {},
    onOpenWays: (() -> Unit)? = null,
) {
    // The technical settings live on a page of their own ("Advanced"), opened at the bottom.
    var advanced by rememberSaveable { mutableStateOf(false) }
    // The categories that are unfolded (see SettingsCategory): none at first, kept across a rotation.
    var openCategories by rememberSaveable { mutableStateOf("") }
    // The group of the disruption watch in the scrolled column, to scroll to it.
    val watchTarget = remember { ScrollTarget() }
    // Back from "Advanced" is predictive: the settings lie underneath while the page shrinks.
    val advancedBack = PredictiveNestedBackHandler(enabled = advanced, label = "advanced") { advanced = false }
    // "Advanced" slides in from the right over the settings and, closed by its button, out again;
    // the settings stay underneath while it moves (see AdvancedSlide).
    val advancedSlide = remember { AdvancedSlide(if (advanced) 1f else 0f) }
    val slideScope = rememberCoroutineScope()
    LaunchedEffect(advanced) {
        if (advanced) advancedSlide.open() else advancedSlide.shut()
    }
    val closeAdvanced: () -> Unit = { slideScope.launch { advancedSlide.close { advanced = false } } }
    // The unfolded previews of the rows stay so while the settings are open.
    ProvidePreviewStates {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundButton(
                glyph = Glyph.Back,
                description = stringResource(R.string.back),
                onClick = { if (advanced) closeAdvanced() else onBack() },
            )
            Text(
                text = stringResource(if (advanced) R.string.settings_advanced else R.string.settings),
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
            )
        }
        // Kept while the advanced page is open, so going back lands where it was.
        val scroll = rememberScrollState()
        // The header of the open category whose content passes the top sticks there (see StickyCategories).
        val sticky = remember(scroll) { StickyCategories(scroll) { openCategories } }
        // Opened for the disruption watch (from a bell): its category unfolds, then straight to its group.
        LaunchedEffect(focusWatch) {
            if (!focusWatch) return@LaunchedEffect
            advanced = false
            openCategories = withCategoryOpen(openCategories, SettingsCategoryId.ON_THE_WAY)
            // Its header sticks over the top once scrolled there: the group starts just below it.
            scrollWhenSettled(watchTarget, scroll) { sticky.headerHeightOf(SettingsCategoryId.ON_THE_WAY) }
            onFocusWatchDone()
        }
        // While a back gesture runs on "Advanced" (and while it settles) the settings are there
        // underneath it; the page above shrinks with the gesture and fades once let go.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
        if (!advanced || advancedBack.active || advancedSlide.moving) Box(
            modifier = Modifier
                .fillMaxSize()
                // The pinned header is pushed up out of the page here, not over the title above;
                // clipped only at the top and bottom, like the scrolled column, so the shadows of
                // the cards still reach into the margins at the sides.
                .clipScrollableContainer(Orientation.Vertical),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadeEdges(scroll, horizontal = false)
                    .verticalScroll(scroll)
                    .onGloballyPositioned {
                        watchTarget.content = it
                        sticky.content = it
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The setup again, as the very first thing: most choices are made best there.
                SetupRestartCard()
                // Every category folds; all start folded (see SettingsCategory).
                val onOpen: (String) -> Unit = { openCategories = it }
                SettingsCategory(
                    id = SettingsCategoryId.APPEARANCE,
                    title = stringResource(R.string.settings_group_appearance),
                    summary = appearanceSummary(),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    AppearanceSettings(delayDisplay = delayDisplay, onDelayDisplay = onDelayDisplay)
                    // Haptics (release 0.2); the battery saver has its own category below.
                    ComfortSettings()
                    // How the details of a connection look below their head (#129): new or classic.
                    // Before the Live-Fahrt (#199): a connection is looked at before it is ridden.
                    DetailStyleSettings()
                    // How the companion looks during a ride (calm, with map, classic).
                    io.github.jnbp.nextstop.ui.companion.CompanionLayoutSettings()
                    // Beside them (#201, #203): what the detail and the Live-Fahrt show at the changes and walks.
                    io.github.jnbp.nextstop.ui.companion.TripFoldSettings(
                        connectionChoice = connectionChoice,
                        onConnectionChoice = onConnectionChoice,
                    )
                }
                // The battery saver, as a category of its own so it is easy to find.
                SettingsCategory(
                    id = SettingsCategoryId.BATTERY,
                    title = stringResource(R.string.settings_group_battery),
                    summary = batterySaverSummary(),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    BatterySaverSettings()
                }
                SettingsCategory(
                    id = SettingsCategoryId.MAP,
                    title = stringResource(R.string.settings_group_map),
                    summary = mapSummary(),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    MapSettings(
                        startVehicles = startVehicles,
                        onStartVehicles = onStartVehicles,
                        fitLine = fitLine,
                        onFitLine = onFitLine,
                    )
                }
                SettingsCategory(
                    id = SettingsCategoryId.CONNECTIONS,
                    title = stringResource(R.string.settings_group_connections),
                    summary = connectionsSummary(options, listView),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    SettingsGroup(label = stringResource(R.string.settings_group_connections)) {
                        TransferChoiceRow(
                            shape = groupShape(0, SEARCH_LAST),
                            on = transferChoice,
                            onChange = onTransferChoice,
                            level = options.comfort,
                            onLevel = { onOptions(options.copy(comfort = it)) },
                            preview = true,
                        )
                        SettingsRow(
                            shape = groupShape(1, SEARCH_LAST),
                            title = stringResource(R.string.products_title),
                            text = stringResource(R.string.products_text),
                        ) {
                            val labels = PRODUCT_KEYS.associateWith { stringResource(productLabel(it)) }
                            // The chips wrap onto further lines where they do not fit.
                            WrapRow {
                                PRODUCT_KEYS.forEach { product ->
                                    val on = product in options.products
                                    ToggleChip(
                                        text = labels.getValue(product),
                                        on = on,
                                        onClick = {
                                            val changed = if (on) options.products - product else options.products + product
                                            // A search without any product finds nothing: the last one stays on.
                                            if (changed.isNotEmpty()) onOptions(options.copy(products = changed))
                                        },
                                    )
                                }
                            }
                        }
                        SwitchRow(
                            shape = groupShape(2, SEARCH_LAST),
                            title = stringResource(R.string.skip_plan_title),
                            text = stringResource(R.string.skip_plan_text),
                            preview = { SkipPlanPreview(skip = !planFirst, list = listView) },
                            // Stored as "plan first": skipping the route map is its opposite.
                            checked = !planFirst,
                            onChange = { skip -> onPlanFirst(!skip) },
                        )
                        SettingsRow(
                            shape = groupShape(3, SEARCH_LAST),
                            title = stringResource(R.string.view_title),
                            text = stringResource(R.string.view_text),
                            preview = { ConnectionsViewPreview(listView) },
                            previewPick = { index -> onListView(index == 1) },
                        ) {
                            SegmentedToggle(
                                labels = listOf(stringResource(R.string.view_chart), stringResource(R.string.view_list)),
                                selected = if (listView) 1 else 0,
                                onSelect = { index -> onListView(index == 1) },
                                minHeight = 40.dp,
                                ground = AppColors.Chip,
                            )
                        }
                        SwitchRow(
                            shape = groupShape(4, SEARCH_LAST),
                            title = stringResource(R.string.connections_full_title),
                            text = stringResource(R.string.connections_full_text),
                            preview = { ConnectionsFullPreview(connectionsFull) },
                            checked = connectionsFull,
                            onChange = onConnectionsFull,
                        )
                        SwitchRow(
                            shape = groupShape(5, SEARCH_LAST),
                            title = stringResource(R.string.chart_change_stops_title),
                            text = stringResource(R.string.chart_change_stops_text),
                            preview = { ChartChangeStopsPreview(chartChangeStops) },
                            checked = chartChangeStops,
                            onChange = onChartChangeStops,
                        )
                        SettingsRow(
                            shape = groupShape(6, SEARCH_LAST),
                            title = stringResource(R.string.chart_total_time_title),
                            text = stringResource(R.string.chart_total_time_text),
                            // The choice fills the width of the row: it needs the bounded width, not a sideways scroll.
                            scroll = false,
                            preview = { ChartTotalTimePreview(chartTotalTime) },
                            previewTap = {
                                val all = ChartTotalTime.entries
                                onChartTotalTime(all[cycleIndex(all.indexOf(chartTotalTime), all.size)])
                            },
                        ) {
                            ChartTotalTimeToggle(selected = chartTotalTime, onSelect = onChartTotalTime)
                        }
                        SettingsRow(
                            shape = groupShape(7, SEARCH_LAST),
                            title = stringResource(R.string.chart_short_legs_title),
                            text = stringResource(R.string.chart_short_legs_text),
                            // The choice fills the width of the row: it needs the bounded width, not a sideways scroll.
                            scroll = false,
                            preview = { ChartShortLegsPreview(ChartShortLegsSetting.load(LocalContext.current)) },
                            previewPick = LocalContext.current.let { context -> { index: Int -> ChartShortLegsSetting.update(context, ChartShortLegs.entries[index]) } },
                        ) {
                            ChartShortLegsToggle()
                        }
                        ClearHistoryRow(shape = groupShape(8, SEARCH_LAST), onClear = onClearHistory)
                    }
                    TicketGroup(options = options, onOptions = onOptions)
                }
                // On the way: the companion, the notifications of a trip and the daily checks of disruptions.
                SettingsCategory(
                    id = SettingsCategoryId.ON_THE_WAY,
                    title = stringResource(R.string.settings_group_on_the_way),
                    summary = onTheWaySummary(boardingHints),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    CompanionGroup(
                        boardingHints = boardingHints,
                        onBoardingHints = onBoardingHints,
                    )
                    AlightReminderGroup()
                    TripAlarmsGroup()
                    NotificationLookCard()
                    Box(modifier = Modifier.onGloballyPositioned { watchTarget.target = it }) {
                        DisruptionWatchGroup(onOpenWays = onOpenWays)
                    }
                    // Forgets the recorded trips behind the statistics of "My ways" (#135).
                    StatsResetGroup()
                }
                SettingsCategory(
                    id = SettingsCategoryId.START,
                    title = stringResource(R.string.settings_group_start),
                    summary = startSummary(startClassic, nearbyRadius),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    StartPageSettings(
                        startClassic = startClassic,
                        onStartClassic = onStartClassic,
                        startCards = startCards,
                        onStartCards = onStartCards,
                        startDepartures = startDepartures,
                        onStartDepartures = onStartDepartures,
                        startCalendar = startCalendar,
                        onStartCalendar = onStartCalendar,
                        startMap = startMap,
                        onStartMap = onStartMap,
                        nearbyRadius = nearbyRadius,
                        onNearbyRadius = onNearbyRadius,
                    )
                }
                // Checked every 10 s while the settings are open: the summary and the row say the same.
                val watchStatus = rememberWatchStatus()
                SettingsCategory(
                    id = SettingsCategoryId.WATCH,
                    title = stringResource(R.string.watch_group),
                    summary = watchSummary(watchStatus),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    // The state of the watch and every setting of the watch app (see WatchSettingsUi.kt).
                    WatchSettingsGroup(watchStatus)
                }
                SettingsCategory(
                    id = SettingsCategoryId.ACCESSIBILITY,
                    title = stringResource(R.string.settings_group_accessibility),
                    summary = accessibilitySummary(options),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    AccessibilitySettings(options = options, onOptions = onOptions)
                }
                SettingsCategory(
                    id = SettingsCategoryId.LIVE_LINK,
                    title = stringResource(R.string.settings_live_link),
                    summary = liveLinkSummary(),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    LiveNameSettings()
                }
                AdvancedEntryRow(onClick = { advanced = true })
                // Last: what Next Stop is, its license, its libraries and where its data comes from.
                SettingsCategory(
                    id = SettingsCategoryId.ABOUT,
                    title = stringResource(R.string.about_title),
                    summary = aboutSummary(),
                    open = openCategories,
                    onOpen = onOpen,
                    sticky = sticky,
                ) {
                    AboutSettings()
                }
                Text(
                    text = stringResource(R.string.language_hint),
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                // The OpenStreetMap attribution stays in sight; the timetable sources, the change
                // positions and everything else are under "About" › "Data sources & attribution".
                AttributionLine(text = stringResource(R.string.map_attribution), url = OSM_COPYRIGHT_URL)
                Spacer(modifier = Modifier.height(LocalNavInset.current))
            }
            // Over the page, at its top edge: the header of the open category passing by.
            PinnedCategoryHeader(sticky = sticky, onOpen = { openCategories = it })
        }
        if (advanced || advancedBack.active || advancedSlide.moving) {
            AdvancedSettingsPage(
                sourceChoice = sourceChoice,
                onSourceChoice = onSourceChoice,
                ownServer = ownServer,
                onOwnServer = onOwnServer,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (advancedBack.active) {
                            // A page of its own while it shrinks: the settings underneath do not show through.
                            Modifier.backFront(advancedBack).background(AppColors.PlainGround)
                        } else {
                            // Read in the layer only: the slide redraws, it does not recompose.
                            Modifier
                                .graphicsLayer {
                                    val shown = advancedSlide.shown.value
                                    translationX = size.width * ADVANCED_SLIDE_SHIFT * (1f - shown)
                                    alpha = shown
                                }
                                .background(AppColors.PlainGround)
                        },
                    ),
            )
        }
        }
    }
    }
}

/**
 * What the trip companion shows on the way: the boarding hints, the position and Google Maps.
 * The choice of connecting departures at changes and the small maps of walks stand under
 * Darstellung (TripFoldSettings, #201, #202).
 */
@Composable
internal fun CompanionGroup(
    boardingHints: Boolean,
    onBoardingHints: (Boolean) -> Unit,
) {
    SettingsGroup(label = stringResource(R.string.settings_group_on_the_way)) {
        // "Anschluss unterwegs wählen" and "Minikarte für Fußwege" stand under Darstellung beside
        // the Live-Fahrt now (#201, #202).
        SwitchRow(
            shape = groupShape(0, 2),
            title = stringResource(R.string.boarding_hints_title),
            text = stringResource(R.string.boarding_hints_text),
            preview = { BoardingHintsPreview(boardingHints) },
            checked = boardingHints,
            onChange = onBoardingHints,
        )
        // "Standort im Begleiter nutzen": the position only while the companion runs.
        CompanionLocationRow(shape = groupShape(1, 2))
        // What "Google Maps" does on the last way of a trip (#143).
        MapsHandoffRow(shape = groupShape(2, 2))
    }
}

/**
 * The ticket defaults of every search: "Only Deutschlandticket", a shorthand that switches
 * long-distance trains and coaches off (and on) in the products and shows on whenever both are
 * off; only rides that take a bicycle; and the bike combo. The search options
 * (SearchOptionsSheet) change the same stored values.
 */
@Composable
private fun TicketGroup(options: SearchOptions, onOptions: (SearchOptions) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    SettingsGroup(label = stringResource(R.string.settings_ticket)) {
        SwitchRow(
            shape = groupShape(0, 2),
            title = stringResource(R.string.local_only_title),
            text = stringResource(R.string.local_only_text),
            checked = options.localOnly,
            // Stored with the products.
            onChange = { value -> onOptions(options.withLocalOnly(value)) },
        )
        SwitchRow(
            shape = groupShape(1, 2),
            title = stringResource(R.string.bike_title),
            text = stringResource(R.string.bike_text),
            checked = options.bike,
            onChange = { value ->
                prefs.bikeDefault = value
                onOptions(options.copy(bike = value))
            },
        )
        SwitchRow(
            shape = groupShape(2, if (options.bikeCombo) 3 else 2),
            title = stringResource(R.string.bike_combo_title),
            text = stringResource(R.string.bike_combo_text),
            checked = options.bikeCombo,
            onChange = { value ->
                prefs.bikeComboDefault = value
                onOptions(options.copy(bikeCombo = value))
            },
        )
        // The pace of the stretches by bike, only with the bike combo.
        SettingReveal(visible = options.bikeCombo) {
            BikeSpeedRow(shape = groupShape(3, 3))
        }
    }
}

/**
 * The disruption watch ("Störungswächter", see DisruptionWatch) in one group: what it does;
 * what is checked (each saved route with its bell, the same as the bell on "My routes", and
 * the places); how often on the way; and the fixed times of the day. While nothing is
 * watched it says so, with the way to "My routes", and the times look inactive (they stay
 * editable). [onOpenWays] opens "My routes".
 */
@Composable
internal fun DisruptionWatchGroup(onOpenWays: (() -> Unit)? = null) {
    val context = LocalContext.current
    val routes = remember { AppPrefs(context).loadRoutes() }
    val placeCount = remember { DisruptionWatch.watchablePlaces(context).size }
    var watched by remember { mutableStateOf(DisruptionWatch.watched(context)) }
    var places by remember { mutableStateOf(DisruptionWatch.checkPlaces(context)) }
    var radius by remember { mutableIntStateOf(DisruptionWatch.radiusMeters(context)) }
    var times by remember { mutableStateOf(DisruptionWatch.checkTimes(context)) }
    var period by remember { mutableIntStateOf(DisruptionWatch.periodMinutes(context)) }
    val askNotify = rememberNotificationAsk()
    // Something is watched: a route with the bell on, or the places (when there is one).
    val active = routes.any { it.id in watched } || (places && placeCount > 0)
    val inactive = Modifier.alpha(if (active) 1f else 0.5f)
    SettingsGroup(label = stringResource(R.string.dwatch_group_title)) {
        // What it does, and whether anything is watched now.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(groupShape(0, WATCH_LAST))
                .background(AppColors.Card)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dwatch_explain),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            if (!active) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WarningMark(size = 14.dp)
                    Text(
                        text = stringResource(R.string.dwatch_nothing),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onOpenWays != null) {
                    SmallChipButton(text = stringResource(R.string.dwatch_open_ways), onClick = onOpenWays)
                }
            }
        }
        // What is checked: the routes, each with its bell, and the places.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(groupShape(1, WATCH_LAST))
                .background(AppColors.Card)
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SettingText(stringResource(R.string.dwatch_what), "")
            if (routes.isEmpty()) {
                Text(
                    text = stringResource(R.string.dwatch_no_routes),
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            routes.forEach { route ->
                val on = route.id in watched
                val ticks = rememberTicks()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = on, role = Role.Switch) { value ->
                            ticks.toggle(value)
                            watched = if (value) watched + route.id else watched - route.id
                            DisruptionWatch.setWatched(context, route.id, value)
                            if (value) askNotify()
                        }
                        .defaultMinSize(minHeight = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RoutePairText(
                        from = route.from,
                        to = route.to,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
                }
            }
        }
        SwitchRow(
            shape = groupShape(2, WATCH_LAST),
            title = stringResource(R.string.dwatch_places_title),
            text = stringResource(R.string.dwatch_places_text),
            checked = places,
            onChange = { value ->
                places = value
                DisruptionWatch.setCheckPlaces(context, value)
                if (value) askNotify()
            },
        )
        // How far around the places and the ends and changes of the routes notices count: for
        // the cards of "My routes" and their map as much as for the watch, so never inactive.
        SettingsRow(
            shape = groupShape(3, WATCH_LAST),
            title = stringResource(R.string.dwatch_radius_title),
            text = stringResource(R.string.dwatch_radius_text),
        ) {
            SegmentedToggle(
                labels = PLACE_RADII.map { meters -> radiusLabel(meters) },
                selected = PLACE_RADII.indexOf(radius).coerceAtLeast(0),
                onSelect = { index ->
                    radius = PLACE_RADII[index]
                    DisruptionWatch.setRadiusMeters(context, radius)
                },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
        }
        // "Unterwegs prüfen": a check every n minutes, opt-in and paused while the battery
        // saver is active (battery rule). Besides, the watch checks when the app opens and at
        // the times below, each a single job.
        Box(modifier = inactive) {
            CheckOnTheWayRow(
                shape = groupShape(4, WATCH_LAST),
                period = period,
                onPeriod = { value ->
                    period = value
                    DisruptionWatch.setPeriodMinutes(context, value)
                },
            )
        }
        Box(modifier = inactive) {
            SettingsRow(
                shape = groupShape(5, WATCH_LAST),
                title = stringResource(R.string.dwatch_times_title),
                text = stringResource(R.string.disruption_check_text),
                scroll = false,
            ) {
                CheckTimesEditor(
                    times = times,
                    onTimes = { value ->
                        times = value
                        DisruptionWatch.setCheckTimes(context, value)
                    },
                )
            }
        }
    }
}

// The last row of the group of the disruption watch.
private const val WATCH_LAST = 5

/**
 * The keys of the lift sources: an app token of accessibility.cloud (BVG, S-Bahn and DB lifts
 * of Berlin, the data of brokenlifts.org) and a client id with API key of the DB API
 * Marketplace (FaSta, DB stations). They stay on the device. With one set, the departures
 * show the lifts of a station, and with step-free travel set, a journey warns of broken lifts.
 */
@Composable
internal fun LiftKeysGroup() {
    val context = LocalContext.current
    val keys = remember { LiftKeys(context) }
    var token by remember { mutableStateOf(keys.accessibilityCloudToken) }
    var clientId by remember { mutableStateOf(keys.dbClientId) }
    var apiKey by remember { mutableStateOf(keys.dbApiKey) }
    SettingsGroup(label = stringResource(R.string.lift_group)) {
        SettingsRow(
            shape = groupShape(0, 1),
            title = stringResource(R.string.lift_ac_title),
            text = stringResource(R.string.lift_ac_text),
        ) {
            KeyField(
                value = token,
                onChange = {
                    token = it
                    keys.accessibilityCloudToken = it
                },
                placeholder = stringResource(R.string.lift_ac_placeholder),
            )
            BuiltInKeyNote(builtIn = keys.builtInAccessibilityCloud, ownEntered = token.isNotBlank())
        }
        SettingsRow(
            shape = groupShape(1, 1),
            title = stringResource(R.string.lift_db_title),
            text = stringResource(R.string.lift_db_text),
        ) {
            KeyField(
                value = clientId,
                onChange = {
                    clientId = it
                    keys.dbClientId = it
                },
                placeholder = stringResource(R.string.lift_db_client_placeholder),
            )
            KeyField(
                value = apiKey,
                onChange = {
                    apiKey = it
                    keys.dbApiKey = it
                },
                placeholder = stringResource(R.string.lift_db_key_placeholder),
            )
            BuiltInKeyNote(builtIn = keys.builtInFasta, ownEntered = clientId.isNotBlank() && apiKey.isNotBlank())
        }
    }
}

/** The reminder to get off during a followed trip, with sound and vibration; on by default. */
@Composable
internal fun AlightReminderGroup() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var on by remember { mutableStateOf(prefs.alightReminders) }
    // Below the switches one row per channel: it opens that channel in the system settings,
    // where sound and vibration are chosen (Android keeps them per channel).
    val channels = AppChannel.entries
    var shortcut by remember { mutableStateOf(prefs.shortcutAlert) }
    var sameComfort by remember { mutableStateOf(prefs.shortcutSameComfort) }
    var quietStart by remember { mutableStateOf(prefs.shortcutQuietStart) }
    var minutes by remember { mutableStateOf(prefs.shortcutMinutes) }
    // The three options of the shortcut alert show only while it is on (they fold away when off;
    // the rows below keep their shapes either way).
    val options = 3
    val last = channels.size + 1 + options
    SettingsGroup(label = stringResource(R.string.setup_notifications_title)) {
        SwitchRow(
            shape = groupShape(0, last),
            title = stringResource(R.string.alight_reminders_title),
            text = stringResource(R.string.alight_reminders_text),
            preview = { AlightReminderPreview(on) },
            checked = on,
            onChange = { value ->
                on = value
                prefs.alightReminders = value
                AlightReminders.check(context)
            },
        )
        // "Abkürzungs-Alarm" for every followed trip; "Every minute counts" switches it on for its trip.
        SwitchRow(
            shape = groupShape(1, last),
            title = stringResource(R.string.shortcut_alert),
            text = stringResource(R.string.shortcut_alert_text),
            checked = shortcut,
            onChange = { value ->
                shortcut = value
                prefs.shortcutAlert = value
            },
        )
        SettingReveal(visible = shortcut) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // "Ab wie vielen Minuten": 3 by default; "Jede Minute zählt" always takes one minute.
                SettingsRow(
                    shape = groupShape(2, last),
                    title = stringResource(R.string.shortcut_minutes_title),
                    text = stringResource(R.string.shortcut_minutes_text),
                ) {
                    SegmentedToggle(
                        labels = SHORTCUT_MINUTE_CHOICES.map { stringResource(R.string.wake_minutes_choice, it) },
                        selected = SHORTCUT_MINUTE_CHOICES.indexOf(minutes).coerceAtLeast(0),
                        onSelect = { index ->
                            minutes = SHORTCUT_MINUTE_CHOICES[index]
                            prefs.shortcutMinutes = minutes
                        },
                        minHeight = 40.dp,
                        ground = AppColors.Chip,
                    )
                }
                SwitchRow(
                    shape = groupShape(3, last),
                    title = stringResource(R.string.shortcut_same_comfort),
                    text = stringResource(R.string.shortcut_same_comfort_text),
                    checked = sameComfort,
                    onChange = { value ->
                        sameComfort = value
                        prefs.shortcutSameComfort = value
                    },
                )
                SwitchRow(
                    shape = groupShape(4, last),
                    title = stringResource(R.string.shortcut_quiet_start),
                    text = stringResource(R.string.shortcut_quiet_start_text),
                    checked = quietStart,
                    onChange = { value ->
                        quietStart = value
                        prefs.shortcutQuietStart = value
                    },
                )
            }
        }
        channels.forEachIndexed { index, channel ->
            ChannelRow(
                shape = groupShape(index + 2 + options, last),
                title = stringResource(channel.title),
                onClick = { openChannelSettings(context, channel) },
            )
        }
    }
}

/** A notification channel as a row: its name and "change sound & vibration"; a tap opens it. */
@Composable
private fun ChannelRow(shape: Shape, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title, stringResource(R.string.channel_sound_vibration), modifier = Modifier.weight(1f))
        Text(text = "›", color = AppColors.Muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** A line of small print about the data, which opens [url] on a tap; [link] shows it as a link. */
@Composable
private fun AttributionLine(text: String, url: String, link: Boolean = false) {
    val context = LocalContext.current
    Text(
        text = text,
        color = if (link) AppColors.Blue else AppColors.Muted,
        fontSize = 12.sp,
        fontWeight = if (link) FontWeight.SemiBold else null,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (e: ActivityNotFoundException) {
                    // No browser: the text stays readable here.
                }
            }
            .padding(horizontal = 18.dp, vertical = 6.dp),
    )
}

/**
 * Where the data comes from: the backends in the order they are asked, each with a switch and
 * dragged to another place to change the order, then the access keys and the own server. The
 * first backend in use answers; when it fails, the next one does (see SourceChain).
 */
@Composable
internal fun DataSourceGroup(
    choice: SourceChoice,
    onChoice: (SourceChoice) -> Unit,
    ownServer: String,
    onOwnServer: (String) -> Unit,
) {
    val backends = choice.ordered
    // The key of public-transport-enabler only in builds that include it.
    val lastRow = backends.size + if (PteSource.INCLUDED) 2 else 1
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val gap = with(LocalDensity.current) { GroupRowGap.toPx() }
    val drag = remember { BackendDrag() }
    // The gestures outlive a composition: they read the order and the callback from here.
    val latestChoice by rememberUpdatedState(choice)
    val latestOnChoice by rememberUpdatedState(onChoice)
    val latestGap by rememberUpdatedState(gap)
    // [gesture] tells the row's long press from the handle's drag: a long press on the handle
    // picks the row up, then the handle's drag takes it over (it sees the moves first).
    fun pickUp(backend: Backend, gesture: Int): Boolean {
        if (drag.key == backend && drag.from >= 0) {
            drag.owner = gesture
            return true
        }
        val order = latestChoice.ordered
        if (!drag.start(backend, order.indexOf(backend), order, latestGap)) return false
        drag.owner = gesture
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        return true
    }
    fun dragBy(backend: Backend, dy: Float) {
        // A tick for each new slot the row would drop into.
        if (drag.key == backend && drag.moveBy(dy, latestGap)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    fun drop(backend: Backend, commit: Boolean, gesture: Int) {
        // Only the gesture holding the row drops it (not the long press the handle took over from).
        if (drag.key != backend || drag.owner != gesture) return
        drag.drop(scope, latestGap, commit) { from, to ->
            val now = latestChoice
            latestOnChoice(now.copy(order = now.ordered.movedTo(from, to)))
        }
    }
    SettingsGroup(label = stringResource(R.string.settings_sources)) {
        Text(
            text = stringResource(R.string.sources_order_text),
            color = AppColors.Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        backends.forEachIndexed { index, backend ->
            // Keyed by the backend, a row keeps its animations when the order changes.
            key(backend) {
                val on = backend in choice.enabled
                val status = when {
                    backend == Backend.Pte && !PteSource.INCLUDED -> stringResource(R.string.source_not_included)
                    !choice.configured(backend) -> stringResource(R.string.source_not_configured)
                    else -> ""
                }
                fun moved(by: Int): SourceChoice =
                    choice.copy(order = backends.movedTo(index, (index + by).coerceIn(0, backends.lastIndex)))
                val title = backendTitle(LocalContext.current, backend)
                // While another row is held this one steps aside for it, smoothly; on the drop the
                // new order takes its place in the same frame, so it snaps.
                val aside = animateFloatAsState(
                    targetValue = stepAside(index, drag.from, drag.target, drag.heights, gap),
                    animationSpec = if (drag.from >= 0) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
                    label = "aside",
                )
                // The held row lifts a little: slightly larger, with a shadow.
                val lift = animateFloatAsState(
                    targetValue = if (drag.from >= 0 && drag.key == backend) 1f else 0f,
                    label = "lift",
                )
                val rowShape = groupShape(index, lastRow)
                BackendRow(
                    shape = rowShape,
                    title = title,
                    text = listOf(backendText(backend), status).filter { it.isNotEmpty() }.joinToString(" · "),
                    checked = on,
                    links = backendLinks(backend),
                    moveActions = listOfNotNull(
                        if (index > 0) {
                            CustomAccessibilityAction(stringResource(R.string.source_move_up, title)) { onChoice(moved(-1)); true }
                        } else null,
                        if (index < backends.lastIndex) {
                            CustomAccessibilityAction(stringResource(R.string.source_move_down, title)) { onChoice(moved(1)); true }
                        } else null,
                    ),
                    // The offsets are read in the layer alone: a drag redraws, it does not compose.
                    modifier = Modifier
                        .onSizeChanged { drag.measured[backend] = it.height }
                        .zIndex(if (drag.key == backend) 1f else 0f)
                        .graphicsLayer {
                            translationY = when {
                                drag.key == backend -> drag.offset
                                drag.from >= 0 -> aside.value
                                else -> 0f
                            }
                            val raised = lift.value
                            scaleX = 1f + 0.03f * raised
                            scaleY = scaleX
                            shadowElevation = 8.dp.toPx() * raised
                            shape = rowShape
                            clip = false
                        }
                        // Held a moment, the whole row can be dragged.
                        .pointerInput(backend) {
                            detectRowDrag(
                                afterLongPress = true,
                                pickUp = { pickUp(backend, ROW_GESTURE) },
                                dragBy = { dy -> dragBy(backend, dy) },
                                drop = { commit -> drop(backend, commit, ROW_GESTURE) },
                            )
                        },
                    // The handle drags at once.
                    handleModifier = Modifier.pointerInput(backend) {
                        detectRowDrag(
                            afterLongPress = false,
                            pickUp = { pickUp(backend, HANDLE_GESTURE) },
                            dragBy = { dy -> dragBy(backend, dy) },
                            drop = { commit -> drop(backend, commit, HANDLE_GESTURE) },
                        )
                    },
                    onChange = { value ->
                        val changed = if (value) choice.enabled + backend else choice.enabled - backend
                        // Without any backend in use nothing could be found: the last one stays on.
                        if (choice.copy(enabled = changed).inUse.isNotEmpty() || value) onChoice(choice.copy(enabled = changed))
                    },
                )
            }
        }
        // The official VBB API needs a key of one's own; it stays on this device.
        SettingsRow(
            shape = groupShape(backends.size, lastRow),
            title = stringResource(R.string.vbb_key_title),
            text = stringResource(R.string.vbb_key_text),
        ) {
            SecretField(value = choice.vbbAccessId, onValue = { onChoice(choice.copy(vbbAccessId = it)) })
            BuiltInKeyNote(builtIn = choice.builtInVbbAccessId.isNotBlank(), ownEntered = choice.vbbAccessId.isNotBlank())
        }
        if (PteSource.INCLUDED) {
            SettingsRow(
                shape = groupShape(backends.size + 1, lastRow),
                title = stringResource(R.string.pte_key_title),
                text = stringResource(R.string.pte_key_text),
            ) {
                SecretField(value = choice.pteAuthorization, onValue = { onChoice(choice.copy(pteAuthorization = it)) })
            }
        }
        // For those who run their own server with the same API as the public Berlin one.
        SettingsRow(
            shape = groupShape(lastRow, lastRow),
            title = stringResource(R.string.own_server_title),
            text = stringResource(R.string.own_server_text),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = ownServer,
                    onValueChange = { onOwnServer(it.take(200)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    textStyle = TextStyle(color = AppColors.Ink, fontSize = 14.sp, fontFamily = AppFont),
                    cursorBrush = SolidColor(AppColors.Blue),
                    modifier = Modifier
                        .weight(1f, fill = false).width(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Chip)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            // The public server stands in while no address of its own is set.
                            if (ownServer.isEmpty()) {
                                Text(text = BvgRestSource.DEFAULT_URL, color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
                            }
                            innerTextField()
                        }
                    },
                )
                if (ownServer.isNotEmpty()) {
                    SmallChipButton(text = stringResource(R.string.own_server_reset), onClick = { onOwnServer("") })
                }
            }
            // The software such a server runs.
            SourceLinks(listOf(stringResource(R.string.own_server_link) to HAFAS_REST_API_URL))
        }
    }
}

/** Small links of a data source to its website or code, each opening the browser. */
@Composable
private fun SourceLinks(links: List<Pair<String, String>>) {
    if (links.isEmpty()) return
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEach { (label, url) ->
            Text(
                text = label,
                color = AppColors.Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) {
                            // No browser: nothing to open.
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Where to read about a data source. Transitous asks every app for a visible link to the list
 * of its sources, so that one is always there.
 */
@Composable
private fun backendLinks(backend: Backend): List<Pair<String, String>> {
    val website = stringResource(R.string.source_link_website)
    val github = stringResource(R.string.source_link_github)
    return when (backend) {
        Backend.Vbb -> listOf(website to VBB_API_URL, stringResource(R.string.source_link_docs) to VBB_API_DOCS_URL)
        Backend.Bvg -> listOf(website to BvgRestSource.DEFAULT_URL, github to BVG_REST_GITHUB_URL)
        Backend.Pte -> listOf(github to PTE_GITHUB_URL)
        Backend.Transitous -> listOf(website to TRANSITOUS_URL, stringResource(R.string.source_link_sources) to TransitousSource.SOURCES_URL)
    }
}

// The pages the settings link for the data sources.
private const val VBB_API_URL = "https://unternehmen.vbb.de/en/digital-services/"
private const val VBB_API_DOCS_URL = "https://www.vbb.de/fileadmin/user_upload/VBB/Dokumente/API-Datensaetze/hafas-api-dokumentation.pdf"
private const val BVG_REST_GITHUB_URL = "https://github.com/derhuerst/bvg-rest"
private const val PTE_GITHUB_URL = "https://github.com/schildbach/public-transport-enabler"
private const val TRANSITOUS_URL = "https://transitous.org"
private const val HAFAS_REST_API_URL = "https://github.com/public-transport/hafas-rest-api"

/**
 * One backend: a handle to drag it to another place in the order, its name and what it covers,
 * and a switch. Without dragging, [moveActions] move it up or down (for TalkBack and the like).
 */
@Composable
private fun BackendRow(
    shape: Shape,
    title: String,
    text: String,
    checked: Boolean,
    links: List<Pair<String, String>>,
    moveActions: List<CustomAccessibilityAction>,
    handleModifier: Modifier,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ticks = rememberTicks()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { customActions = moveActions }
            .clip(shape)
            .background(AppColors.Card)
            .toggleable(value = checked, role = Role.Switch) { value -> ticks.toggle(value); onChange(value) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // As large as a finger; only a mark for the eye, the row's actions speak for it.
        Box(
            modifier = handleModifier
                .size(width = 36.dp, height = 60.dp)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "⠿", color = AppColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingText(title, text)
            SourceLinks(links)
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
    }
}

/**
 * Drags a row, after a long press or at once, through [pickUp] (true when this gesture got the
 * row), [dragBy] with the vertical movement and [drop] (false when the gesture was cancelled).
 */
private suspend fun PointerInputScope.detectRowDrag(
    afterLongPress: Boolean,
    pickUp: () -> Boolean,
    dragBy: (Float) -> Unit,
    drop: (commit: Boolean) -> Unit,
) {
    // Only the gesture that picked the row up moves it: the handle lies inside the row, so a
    // long press on it starts both.
    var mine = false
    val onStart: (Offset) -> Unit = { mine = pickUp() }
    val onEnd: () -> Unit = {
        if (mine) drop(true)
        mine = false
    }
    val onCancel: () -> Unit = {
        if (mine) drop(false)
        mine = false
    }
    val onDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
        if (mine) {
            change.consume()
            dragBy(amount.y)
        }
    }
    if (afterLongPress) {
        detectDragGesturesAfterLongPress(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = onCancel, onDrag = onDrag)
    } else {
        detectDragGestures(onDragStart = onStart, onDragEnd = onEnd, onDragCancel = onCancel, onDrag = onDrag)
    }
}

/**
 * The backend row being dragged to another place in the order. Only [key], [from] and [target]
 * change what is composed (on the pick-up, on each new slot and on the drop); [offset] follows
 * the finger and is read in the rows' graphics layers alone.
 */
@Stable
private class BackendDrag {
    /** The row held, or still gliding into its new place; null while none is. */
    var key by mutableStateOf<Backend?>(null)
        private set

    /** Its place in the order when picked up; -1 once dropped. */
    var from by mutableIntStateOf(-1)
        private set

    /** The slot it would drop into now. */
    var target by mutableIntStateOf(-1)
        private set

    /** How far it is moved from its place in the layout, in pixels. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** The heights of the rows as last measured, by backend. */
    val measured = HashMap<Backend, Int>()

    /** The heights of the rows in their order at the pick-up. */
    var heights = IntArray(0)
        private set

    /** The gesture that holds the row (ROW_GESTURE or HANDLE_GESTURE). */
    var owner = 0

    private var minOffset = 0f
    private var maxOffset = 0f
    private var settle: Job? = null

    /** Picks up [backend] at [index] of [order]; false while another row is held. */
    fun start(backend: Backend, index: Int, order: List<Backend>, gap: Float): Boolean {
        if (from >= 0 || index < 0) return false
        // A row still gliding into its place lands at once.
        settle?.cancel()
        heights = IntArray(order.size) { measured[order[it]] ?: 0 }
        // Not past the first slot nor the last.
        minOffset = slotDelta(index, 0, heights, gap)
        maxOffset = slotDelta(index, order.lastIndex, heights, gap)
        offset = 0f
        target = index
        key = backend
        from = index
        return true
    }

    /** Moves the held row by [dy]; true when that brings it over a new slot. */
    fun moveBy(dy: Float, gap: Float): Boolean {
        if (from < 0) return false
        offset = (offset + dy).coerceIn(minOffset, maxOffset)
        val slot = dropSlot(from, offset, heights, gap)
        if (slot == target) return false
        target = slot
        return true
    }

    /**
     * Drops the held row: with [commit] into its slot through [reorder], otherwise back to its
     * place. Either way it glides there from where the finger left it.
     */
    fun drop(scope: CoroutineScope, gap: Float, commit: Boolean, reorder: (from: Int, to: Int) -> Unit) {
        val start = from
        if (start < 0) return
        val to = if (commit) target else start
        // The new order and the offset from the new place change in the same frame: no jump.
        if (to != start) reorder(start, to)
        offset -= slotDelta(start, to, heights, gap)
        from = -1
        target = -1
        settle = scope.launch {
            animate(offset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value }
            key = null
        }
    }
}

// The two gestures that can hold a backend row (see BackendDrag.owner).
private const val ROW_GESTURE = 1
private const val HANDLE_GESTURE = 2

@Composable
private fun SmallChipButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Chip)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = AppColors.Blue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

/** A field for an access key: hidden as dots, with a button to show it and one to clear it. */
@Composable
private fun SecretField(value: String, onValue: (String) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { onValue(it.trim().take(500)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = if (shown) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            },
            textStyle = TextStyle(color = AppColors.Ink, fontSize = 14.sp, fontFamily = AppFont),
            cursorBrush = SolidColor(AppColors.Blue),
            modifier = Modifier
                .weight(1f, fill = false).width(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Chip)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = stringResource(R.string.source_key_empty), color = AppColors.Muted, fontSize = 14.sp, maxLines = 1)
                    }
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            SmallChipButton(
                text = stringResource(if (shown) R.string.source_key_hide else R.string.source_key_show),
                onClick = { shown = !shown },
            )
            SmallChipButton(text = stringResource(R.string.own_server_reset), onClick = { onValue("") })
        }
    }
}

/** What a backend covers, in one line. */
@Composable
private fun backendText(backend: Backend): String = when (backend) {
    Backend.Vbb -> stringResource(R.string.source_vbb_text)
    Backend.Bvg -> stringResource(R.string.source_bvg_text)
    Backend.Pte -> stringResource(R.string.source_pte_text)
    Backend.Transitous -> stringResource(R.string.source_transitous_text)
}

/** A setting as a white row with a check box at its end; greyed out while it cannot be changed. */
/**
 * How the app looks: the colour scheme, the accent colour of an own scheme, light or dark,
 * and from Android 16 on how a followed trip shows in the notifications. A change shows at once.
 */
/** The preset accent colours as round swatches; the picked one has a ring. */
@Composable
internal fun AccentSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val names = listOf(
        stringResource(R.string.accent_indigo),
        stringResource(R.string.accent_violet),
        stringResource(R.string.accent_magenta),
        stringResource(R.string.accent_rose),
        stringResource(R.string.accent_red),
        stringResource(R.string.accent_orange),
        stringResource(R.string.accent_amber),
        stringResource(R.string.accent_green),
        stringResource(R.string.accent_teal),
        stringResource(R.string.accent_sky),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ACCENT_PRESETS.forEachIndexed { index, accent ->
            val picked = accent == selected
            val name = names.getOrElse(index) { "" }
            Box(
                modifier = Modifier
                    .semantics { contentDescription = name }
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(if (picked) Modifier.border(3.dp, AppColors.Ink, CircleShape).padding(5.dp) else Modifier)
                    .clip(CircleShape)
                    .background(Color(accent))
                    .clickable { onSelect(accent) },
            )
        }
    }
}

@Composable
private fun CheckRow(
    shape: Shape,
    title: String,
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title, text, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.minimumInteractiveComponentSize())
    }
}

// The space between the rows of a SettingsGroup.
private val GroupRowGap = 3.dp

/** Settings that belong together: a label, and their rows on one glass card. */
@Composable
internal fun SettingsGroup(label: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Without a label the card belongs to the group above it; in a folding category of the
        // settings a label the same as the category's title is left out (its header says it).
        if (label != null && label != LocalSettingsCategory.current) SectionLabel(label)
        if (LocalFlatSettingsGroups.current) {
            // Under the glass header of an open category (or on the page "Advanced"): the rows
            // are the one surface, no glass frame around them (a card in a card, #133). The depth
            // stays one deeper, so glass inside a row keeps its inner look.
            CompositionLocalProvider(LocalGlassDepth provides LocalGlassDepth.current + 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(GroupRowGap),
                    content = content,
                )
            }
        } else {
            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(GroupRowGap),
                    content = content,
                )
            }
        }
    }
}

/** The title of a setting and one explaining line. */
@Composable
internal fun SettingText(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (text.isNotEmpty()) Text(text = text, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/**
 * The setting "choose transfer times": off, the search shows normal and relaxed journeys and
 * no selector; on, the pills of the levels shows over the connections and the plan, and here too.
 * One short line says what it does; "Learn more" unfolds how changes are rated.
 */
@Composable
internal fun TransferChoiceRow(
    shape: Shape,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    level: TransferComfort,
    onLevel: (TransferComfort) -> Unit,
    preview: Boolean = false,
) {
    var more by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The switch's row reaches to the card's edges, so all of it toggles and ripples.
        val ticks = rememberTicks()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = on, role = Role.Switch) { value -> ticks.toggle(value); onChange(value) }
                .padding(start = 16.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingText(
                stringResource(R.string.comfort_choice_title),
                stringResource(R.string.comfort_choice_text),
                modifier = Modifier.weight(1f),
            )
            Switch(checked = on, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingReveal(visible = on) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The schematic change of the setup: its gap as wide as the level lets a change be.
                    if (preview) TransferSketch(level = level)
                    LevelSelector(
                        level = level,
                        onLevel = onLevel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // How changes are rated, in a sheet built from the numbers the rating uses.
            TransferRatingInfoLink()
            // The buffers of the rating, to tune; folded away as most never need them.
            Text(
                text = stringResource(if (more) R.string.comfort_less else R.string.rating_tuning_show),
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { more = !more }
                    .padding(vertical = 4.dp),
            )
            SettingReveal(visible = more) {
                TransferTuningControls()
            }
        }
    }
}

/**
 * A setting as a white row with its own control below the text. With a [preview] the row ends
 * in the quiet expander "Preview" that unfolds it (see PreviewExpander).
 */
@Composable
internal fun SettingsRow(
    shape: Shape,
    title: String,
    text: String,
    @Suppress("UNUSED_PARAMETER") scroll: Boolean = true,
    preview: (@Composable () -> Unit)? = null,
    // A tap on the preview changes the value (e.g. the next one of a choice), a tap on one of its options picks it.
    previewTap: (() -> Unit)? = null,
    previewPick: ((Int) -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingText(title, text)
        // Nothing in the settings is pushed sideways: the control gets the width of the row, a
        // choice that does not fit wraps into a grid or a list (LocalWrapChoices) and hints
        // wrap onto further lines. [scroll] is kept for the callers; it no longer scrolls.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompositionLocalProvider(LocalWrapChoices provides true) {
                control()
            }
        }
        if (preview != null) PreviewExpander(key = title, onTap = previewTap, onPick = previewPick, content = preview)
    }
}

/** A chip that is on (blue) or off (grey), for a choice of several things at once. */
@Composable
internal fun ToggleChip(text: String, on: Boolean, onClick: () -> Unit) {
    val ground by animateColorAsState(
        if (on) AppColors.Blue else AppColors.Chip,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleGround",
    )
    val ink by animateColorAsState(
        if (on) AppColors.OnBlue else AppColors.Ink,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleInk",
    )
    val ticks = rememberTicks()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ground)
            .clickable {
                ticks.tick()
                onClick()
            }
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ink,
            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.sp,
            // In a row that wraps, a name longer than the row takes a second line.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The start page shows no more favourite places than this. */
const val MAX_FAVORITE_PLACES = 4

/**
 * A favourite place as a row: its icon, its name and where it is. A tap unfolds what can
 * be changed: the name, the icon out of the set of icons, the place itself, and a way to
 * remove the favourite.
 */
@Composable
private fun FavoritePlaceRow(
    shape: Shape,
    favorite: FavoritePlace,
    open: Boolean,
    onToggle: () -> Unit,
    onChange: (FavoritePlace) -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    onBell: () -> Unit = {},
    onWatchSettings: (() -> Unit)? = null,
) {
    val place = favorite.place
    val fallbackName = placeIconLabel(favorite.icon)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphIcon(placeGlyph(favorite.icon))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = favorite.name.ifBlank { fallbackName },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = place?.name?.shortStopName() ?: stringResource(R.string.not_set),
                    color = if (place != null) AppColors.Blue else AppColors.Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // "Tell me about problems" near the place: the stops within its reach.
            place?.let { placeWatchKey(it) }?.let { key ->
                DisruptionBell(watchKey = key, onChange = onBell, onSettings = onWatchSettings, size = 40.dp)
            }
            Chevron(open = open)
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicTextField(
                    value = favorite.name,
                    onValueChange = { onChange(favorite.copy(name = it.take(24))) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AppColors.Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AppFont,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Chip)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            // The name of the icon stands in while the place has no name of its own.
                            if (favorite.name.isEmpty()) {
                                Text(
                                    text = fallbackName,
                                    color = AppColors.Muted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                IconPicker(selected = favorite.icon, onSelect = { onChange(favorite.copy(icon = it)) })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SecondaryButton(
                        text = stringResource(if (place == null) R.string.place_choose else R.string.place_change),
                        onClick = onChoose,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp, 8.dp, 8.dp, 20.dp),
                        ground = AppColors.BlueContainer,
                        ink = AppColors.OnBlueContainer,
                        minHeight = 44.dp,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.place_remove),
                        onClick = onRemove,
                        shape = RoundedCornerShape(8.dp, 20.dp, 20.dp, 8.dp),
                        ground = AppColors.Chip,
                        minHeight = 44.dp,
                    )
                }
            }
        }
    }
}

/** A row that empties the search history, after asking whether that is meant. */
@Composable
private fun ClearHistoryRow(shape: Shape, onClear: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable { asking = true }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingText(
            title = stringResource(R.string.history_clear_title),
            text = stringResource(R.string.history_clear_text),
            modifier = Modifier.weight(1f),
        )
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(text = stringResource(R.string.history_clear_title)) },
            text = { Text(text = stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onClear()
                    },
                ) { Text(text = stringResource(R.string.history_clear_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(text = stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * A setting as a white row with a switch at its end. With a [preview] the expander "Preview"
 * stands below the text and unfolds it (see PreviewExpander).
 */
@Composable
internal fun SwitchRow(
    shape: Shape,
    title: String,
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    preview: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card),
    ) {
        // The switch's row reaches to the card's edges, so all of it toggles and ripples.
        val ticks = rememberTicks()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch) { value ->
                    ticks.toggle(value)
                    onChange(value)
                }
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = if (preview != null) 0.dp else 8.dp)
                .defaultMinSize(minHeight = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingText(title, text, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
        }
        // A tap on the preview turns the switch over, as the switch itself would.
        if (preview != null) {
            Box(modifier = Modifier.padding(start = 16.dp, end = 12.dp, bottom = 4.dp)) {
                PreviewExpander(key = title, onTap = { onChange(!checked) }, content = preview)
            }
        }
    }
}

/**
 * Planning a journey: start and destination on top, the map with all route options
 * free below, and one button that opens the connections.
 */
@Composable
fun PlanScreen(
    fromName: String,
    toName: String,
    loading: Boolean,
    error: String?,
    count: Int,
    filteredCount: Int,
    // Asks for the journeys again ([asked] by hand); its job, so the retries wait for it (#59).
    onRetry: ((asked: Boolean) -> kotlinx.coroutines.Job?)? = null,
    // The app is on screen: only then the failed search is asked again by itself.
    active: Boolean = true,
    // Asking again for the journeys shown failed: they stay, with a note.
    stale: Boolean = false,
    filteredLevels: Set<TransferComfort>,
    tighterCount: Int,
    tighterLevel: TransferComfort?,
    onShowFiltered: () -> Unit,
    options: List<Journey>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    time: TimeChoice?,
    onTime: () -> Unit,
    comfort: TransferComfort,
    onComfort: (TransferComfort) -> Unit,
    everyMinute: Boolean,
    onEveryMinute: (Boolean) -> Unit,
    via: Via?,
    onAddVia: () -> Unit,
    onEditVia: () -> Unit,
    onRemoveVia: () -> Unit,
    routeSaved: Boolean,
    onToggleRoute: () -> Unit,
    onBack: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onSwap: () -> Unit,
    onConfirm: () -> Unit,
    productsOff: Set<String> = emptySet(),
    onAllProducts: () -> Unit = {},
    levelChoice: Boolean = true,
    searchOptions: SearchOptions? = null,
    onSearchOptions: () -> Unit = {},
    onTicketOff: (TicketFilter) -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // Held sideways on a phone the page is a panel at the side of the map: the map keeps the
    // rest of the width, and the panel scrolls above the options and the button, which stay.
    val side = sideBySide(maxWidth, maxHeight)
    val paneScroll = rememberScrollState()
    val covers = LocalMapCover.current
    // Upright, nothing covers the map at its side.
    if (!side) SideEffect { covers?.let { if (it.size > 2) it[2] = Float.NaN } }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .then(
                if (side) {
                    Modifier
                        .width((maxWidth * 0.42f).coerceIn(280.dp, SIDE_PANEL_MAX))
                        .coversMapStart()
                } else {
                    Modifier.fillMaxWidth()
                },
            )
            .padding(start = 12.dp, end = 12.dp, bottom = LocalNavInset.current),
    ) {
        // At the side the panel covers the map from its top to its foot.
        if (side) Spacer(modifier = Modifier.fillMaxWidth().coversMapTop())
        Column(
            modifier = if (side) {
                Modifier
                    .weight(1f)
                    .fadeEdges(paneScroll, horizontal = false)
                    .verticalScroll(paneScroll)
            } else {
                Modifier
            },
        ) {
        GlassSurface(modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundButton(
                    glyph = Glyph.Back,
                    description = stringResource(R.string.back),
                    onClick = onBack,
                    solid = true,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.from_label, fromName),
                        color = AppColors.Blue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPickOrigin)
                            .padding(top = 8.dp, bottom = 2.dp),
                    )
                    // Between start and destination, in the order of the journey: the quiet
                    // "Über …" that adds a place on the way, or the place with its stay.
                    ViaLine(
                        via = via,
                        onAdd = onAddVia,
                        onEdit = onEditVia,
                        onRemove = onRemoveVia,
                        modifier = Modifier.animateContentSize(),
                    )
                    Text(
                        text = toName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPickDestination)
                            .padding(vertical = 6.dp),
                    )
                }
                RoundButton(
                    glyph = Glyph.Swap,
                    description = stringResource(R.string.swap_ends),
                    onClick = onSwap,
                    solid = true,
                )
            }
        }
        val chipScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fadeEdges(chipScroll, horizontal = true)
                .horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // When to travel: now, or the time the user picked; a new choice searches again.
            TimeChip(choice = time, onClick = onTime)
            // A trip of this search looks for faster ways on the way (see FasterWatch).
            EveryMinuteChip(active = everyMinute, onToggle = onEveryMinute)
            // Start and destination as a pair to search again later from the favourites: a star
            // only, as the row is tight.
            RouteSaveChip(saved = routeSaved, onToggle = onToggleRoute)
            // Kinds of transport, Deutschlandticket and bicycle; kept for the next searches.
            if (searchOptions != null) SearchOptionsChip(options = searchOptions, onClick = onSearchOptions)
        }
        // How tight changes may be: the same single choice as on the connections page, as it
        // decides what the options below show. The lowest of the parts on top: the map frames
        // the options below it.
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .then(if (side) Modifier else Modifier.coversMapTop()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Only for those who chose to pick transfer times in the settings; on a glass card,
            // so the pills read over the map.
            if (levelChoice) {
                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        LevelSelector(
                            level = comfort,
                            onLevel = onComfort,
                            tighterCount = if (count > 0) tighterCount else 0,
                            tighterLevel = tighterLevel,
                            onTighter = onShowFiltered,
                            modifier = Modifier.weight(1f),
                        )
                        // How the levels are rated, and their buffers.
                        TransferRatingInfoButton()
                    }
                }
            }
            // Kinds of transport switched off, said aloud.
            SearchLimits(
                productsOff = productsOff,
                onAllProducts = onAllProducts,
                tickets = searchOptions?.let { ticketFilters(it) }.orEmpty(),
                onTicketOff = onTicketOff,
            )
        }
        }
        if (!side) Spacer(modifier = Modifier.weight(1f))
        // The options as cards: tapping one brings its way to the front on the map.
        // For an arrival time the option that fits best is the last card: it is brought into view.
        val optionsScroll = rememberScrollState()
        val endFirst = time?.arrival == true
        LaunchedEffect(endFirst, options.lastOrNull()?.id) {
            if (endFirst) optionsScroll.scrollTo(optionsScroll.maxValue)
        }
        // Options and button always stay in view: the map above them is only a preview.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (side) Modifier.padding(top = 8.dp) else Modifier.coversMapBottom()),
        ) {
            if (!loading && options.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fadeEdges(optionsScroll, horizontal = true)
                        .horizontalScroll(optionsScroll)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { journey ->
                        key(journey.id) {
                            PlanOption(
                                journey = journey,
                                picked = journey.id == selectedId,
                                onClick = { onSelect(journey.id) },
                            )
                        }
                    }
                }
            }
            when {
                loading -> GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    LoadingRow(
                        text = stringResource(R.string.searching_connections),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }

                // A failed search says why, with a button to try again, and is asked again by
                // itself while it is in view (#59).
                error != null -> if (onRetry != null) {
                    AutoRetry(enabled = active) { onRetry(false) }
                    RetryCard(text = error, onRetry = { onRetry(true) })
                } else {
                    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                        Text(
                            text = problemText(error),
                            color = AppColors.Muted,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }

                // Connections were found, but the chips hide all of them: as on the connections page.
                count == 0 && filteredCount > 0 -> FilteredJourneysCard(
                    count = filteredCount,
                    levels = filteredLevels,
                    onShow = onShowFiltered,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Only a search that really found nothing says so.
                count == 0 -> GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                    Text(text = stringResource(R.string.no_connections), modifier = Modifier.padding(18.dp))
                }

                else -> {
                    // Asking again failed: the options stay, with a note and a retry (#59).
                    if (stale && onRetry != null) {
                        AutoRetry(enabled = active) { onRetry(false) }
                        RetryCard(
                            text = stringResource(R.string.r5search_refresh_failed),
                            onRetry = { onRetry(true) },
                            compact = true,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.show_connections, count),
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (side) Spacer(modifier = Modifier.fillMaxWidth().coversMapBottom())
    }
    }
}

/** One route option of the planning view: times, lines with the walks around them, duration. */
@Composable
private fun PlanOption(journey: Journey, picked: Boolean, onClick: () -> Unit) {
    // The picked card turns blue and comes forward a little; the others step back.
    val ground by animateColorAsState(
        if (picked) AppColors.BlueContainer else AppColors.Card,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "optionGround",
    )
    val scale by animateFloatAsState(
        if (picked) 1f else 0.94f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "optionScale",
    )
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(ground)
            .clickable(onClick = onClick)
            // Never wider than most of a phone screen; what does not fit ends in "…" or "+n".
            .widthIn(max = PLAN_OPTION_MAX)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.time_range,
                    journey.departure.clock(),
                    journey.arrival.clock(),
                ),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (alertingWarnings(journey.warnings, rememberStepFree()).isNotEmpty()) WarningMark()
            if (rememberNeedsZoneC(journey)) FareZoneChip()
        }
        // The walks at either end stand before and after the lines, each with its minutes.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val walkBefore = journey.walkBeforeMinutes
            val walkAfter = journey.walkAfterMinutes
            if (walkBefore > 0) WalkMark(walkBefore, cycling = journey.legs.firstOrNull()?.cycling == true)
            // Many rides: the first few badges and how many more, so the card is never cut.
            val rides = journey.rides
            val badges = if (rides.size > PLAN_OPTION_BADGES) rides.take(PLAN_OPTION_BADGES - 1) else rides
            badges.forEach { leg -> LineBadge(leg, minWidth = 34.dp) }
            if (badges.size < rides.size) {
                Text(
                    text = "+${rides.size - badges.size}",
                    color = AppColors.Muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (walkAfter > 0) WalkMark(walkAfter, cycling = journey.legs.lastOrNull()?.cycling == true)
        }
        Text(
            text = stringResource(
                R.string.duration_and_transfers,
                durationLabel(journey.durationMinutes),
                journey.transfers,
            ),
            color = AppColors.Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// A route card of the plan is at most this wide and shows at most this many line badges.
private val PLAN_OPTION_MAX = 280.dp
private const val PLAN_OPTION_BADGES = 5

/**
 * How far the page "Advanced" has slid in over the settings, 0 to 1: [open] slides it in,
 * [close] out before the page goes; [moving] while either runs, so the settings stay underneath.
 */
@Stable
internal class AdvancedSlide(start: Float) {
    val shown = Animatable(start)
    var moving by mutableStateOf(false)
        private set

    /** A close runs: it alone ends the move. */
    var closing = false
        private set

    suspend fun open() {
        if (shown.value >= 1f) return
        moving = true
        try {
            shown.animateTo(1f, tween(ADVANCED_SLIDE_MILLIS, easing = AdvancedSlideEasing))
        } finally {
            // Taken over by a close: that one ends the move. Cut short otherwise (a back while
            // it slid in), the page is gone: shut() takes it away.
            if (!closing) moving = false
        }
    }

    suspend fun close(done: () -> Unit) {
        if (closing) return
        closing = true
        moving = true
        try {
            shown.animateTo(0f, tween(ADVANCED_SLIDE_MILLIS * 2 / 3, easing = FastOutLinearInEasing))
        } finally {
            closing = false
            done()
            moving = false
        }
    }

    /** Gone without a slide (the back gesture already took it away). */
    suspend fun shut() {
        if (closing) return
        moving = false
        shown.snapTo(0f)
    }
}

// The page "Advanced" slides in over this long, from this share of its width to the right.
private const val ADVANCED_SLIDE_MILLIS = 320
private const val ADVANCED_SLIDE_SHIFT = 0.25f
private val AdvancedSlideEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
