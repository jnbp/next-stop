package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.RescueSearches
import io.github.jnbp.nextstop.data.StopAction
import io.github.jnbp.nextstop.data.CompanionPosition
import io.github.jnbp.nextstop.data.RideTracker
import io.github.jnbp.nextstop.data.sameChoiceRide
import io.github.jnbp.nextstop.data.rescueChoice
import io.github.jnbp.nextstop.data.failureOfAll
import io.github.jnbp.nextstop.data.followedTripLive
import io.github.jnbp.nextstop.ui.companion.companionCalmHere
import io.github.jnbp.nextstop.ui.companion.companionModernHere
import io.github.jnbp.nextstop.ui.companion.CompanionLayout
import io.github.jnbp.nextstop.ui.companion.CompanionOffers
import io.github.jnbp.nextstop.ui.companion.ForgetMapWhenGone
import io.github.jnbp.nextstop.ui.companion.NoMapStart
import io.github.jnbp.nextstop.ui.companion.mapComposed
import io.github.jnbp.nextstop.ui.companion.rememberMapKept
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.TripMatch
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.sqrt
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.tripRefreshMillis
import io.github.jnbp.nextstop.data.StopLine
import io.github.jnbp.nextstop.data.LineColors
import io.github.jnbp.nextstop.data.placedAt
import io.github.jnbp.nextstop.data.StopDepartures
import io.github.jnbp.nextstop.data.mergeStopBoards
import io.github.jnbp.nextstop.data.shareLiveTimes
import io.github.jnbp.nextstop.data.leaveDeparturesFrom
import io.github.jnbp.nextstop.data.leaveStopWalkSeconds
import io.github.jnbp.nextstop.data.withStopGroup
import io.github.jnbp.nextstop.data.stopGroupOf
import io.github.jnbp.nextstop.data.restoreStopGroup
import io.github.jnbp.nextstop.data.findStopGroup
import io.github.jnbp.nextstop.data.StopGroup
import io.github.jnbp.nextstop.data.stopsByDistance
import io.github.jnbp.nextstop.data.ridePlace
import io.github.jnbp.nextstop.data.withRideLines
import io.github.jnbp.nextstop.data.RideName
import io.github.jnbp.nextstop.data.keptKinds
import io.github.jnbp.nextstop.data.RideKindFixes
import io.github.jnbp.nextstop.data.withKnownBoardRuns
import io.github.jnbp.nextstop.data.withKnownRuns
import io.github.jnbp.nextstop.data.TrackCache
import io.github.jnbp.nextstop.data.withLentTrack
import io.github.jnbp.nextstop.data.withLentTracks
import io.github.jnbp.nextstop.data.rideNames
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Appointment
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.FavoritePlace
import io.github.jnbp.nextstop.data.HttpStatusException
import io.github.jnbp.nextstop.data.FootPaths
import io.github.jnbp.nextstop.data.FootWay
import io.github.jnbp.nextstop.data.OffRouteWatch
import io.github.jnbp.nextstop.data.activeWalkIndex
import io.github.jnbp.nextstop.data.routeMeters
import io.github.jnbp.nextstop.data.walkMinutesFor
import io.github.jnbp.nextstop.data.walkMissesRide
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PositionFix
import io.github.jnbp.nextstop.data.RideState
import io.github.jnbp.nextstop.data.RideStage
import io.github.jnbp.nextstop.data.QuickStop
import io.github.jnbp.nextstop.data.ALT_CHANGE_SWITCH
import io.github.jnbp.nextstop.data.KeptPage
import io.github.jnbp.nextstop.data.tripIdAt
import io.github.jnbp.nextstop.data.JourneyPage
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.data.withoutTicketFilter
import io.github.jnbp.nextstop.data.withLocalOnly
import io.github.jnbp.nextstop.data.TicketFilter
import io.github.jnbp.nextstop.data.LONG_DISTANCE_PRODUCTS
import io.github.jnbp.nextstop.data.LONG_FEEDER_RESULTS
import io.github.jnbp.nextstop.data.LONG_FEEDER_SEARCH_CAP
import io.github.jnbp.nextstop.data.LongFeederEnd
import io.github.jnbp.nextstop.data.longFeederRequests
import io.github.jnbp.nextstop.data.longFeederVariants
import io.github.jnbp.nextstop.data.nationwideDisruptions
import io.github.jnbp.nextstop.data.placeDisruptions
import io.github.jnbp.nextstop.data.routeStatus
import io.github.jnbp.nextstop.data.PathJoin
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.SavedPlace
import io.github.jnbp.nextstop.data.SharedPlace
import io.github.jnbp.nextstop.data.followShortMapLink
import io.github.jnbp.nextstop.data.parseNavLink
import io.github.jnbp.nextstop.data.SavedRoute
import io.github.jnbp.nextstop.data.SavedPattern
import io.github.jnbp.nextstop.data.NoticeMarker
import io.github.jnbp.nextstop.data.LocalTown
import io.github.jnbp.nextstop.data.noticeMarkers
import io.github.jnbp.nextstop.data.PATTERN_WINDOW_MINUTES
import io.github.jnbp.nextstop.data.inDepartureOrder
import io.github.jnbp.nextstop.data.mergeDepartures
import io.github.jnbp.nextstop.data.nextDeparturesStart
import io.github.jnbp.nextstop.data.nextOfPattern
import io.github.jnbp.nextstop.data.patternLines
import io.github.jnbp.nextstop.data.patternView
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.data.SourceChoice
import io.github.jnbp.nextstop.data.SourceHealth
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.TransitSource
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.movesWithoutWay
import io.github.jnbp.nextstop.data.Vehicle
import io.github.jnbp.nextstop.data.bearingDegrees
import io.github.jnbp.nextstop.data.chipPlaceName
import io.github.jnbp.nextstop.data.hasCalendarPermission
import io.github.jnbp.nextstop.data.journeyComfort
import io.github.jnbp.nextstop.data.ridePlatforms
import io.github.jnbp.nextstop.data.journeyStations
import io.github.jnbp.nextstop.data.courseStations
import io.github.jnbp.nextstop.data.nearestStationIndex
import io.github.jnbp.nextstop.data.stepFreeSetting
import io.github.jnbp.nextstop.data.stepFreeWalks
import io.github.jnbp.nextstop.data.nearbyStopsAsked
import io.github.jnbp.nextstop.data.nearbyStopsKept
import io.github.jnbp.nextstop.data.walkSpeedFactor
import io.github.jnbp.nextstop.data.transferWalkSeconds
import io.github.jnbp.nextstop.data.Accessibility
import io.github.jnbp.nextstop.data.samePlatformRecord
import io.github.jnbp.nextstop.data.ChangePositions
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.WalkAccess
import io.github.jnbp.nextstop.data.platformNumber
import io.github.jnbp.nextstop.data.lineKey
import io.github.jnbp.nextstop.data.OsmLine
import io.github.jnbp.nextstop.data.WalkTarget
import io.github.jnbp.nextstop.data.SAME_SPOT_METERS
import io.github.jnbp.nextstop.data.stopSpotMeters
import io.github.jnbp.nextstop.data.journeyKey
import io.github.jnbp.nextstop.data.mergeSearchPages
import io.github.jnbp.nextstop.data.searchOrder
import io.github.jnbp.nextstop.data.journeyChangesOf
import io.github.jnbp.nextstop.data.loosestLevel
import io.github.jnbp.nextstop.data.loadRapidNetwork
import io.github.jnbp.nextstop.data.drivesOnRoads
import io.github.jnbp.nextstop.data.loadTramNetwork
import io.github.jnbp.nextstop.data.StationIdentity
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.refreshed
import io.github.jnbp.nextstop.data.resolvePlace
import io.github.jnbp.nextstop.data.routeId
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.data.spliceJourney
import io.github.jnbp.nextstop.data.Via
import io.github.jnbp.nextstop.data.retargetPoints
import io.github.jnbp.nextstop.data.retargeted
import io.github.jnbp.nextstop.data.searchRetarget
import io.github.jnbp.nextstop.data.replanPoint
import io.github.jnbp.nextstop.data.viaAhead
import io.github.jnbp.nextstop.data.upcomingAppointments
import io.github.jnbp.nextstop.data.UxPrefs
import io.github.jnbp.nextstop.data.withKnownSeating
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.data.boardingHolds
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.MapVehiclesChoice
import io.github.jnbp.nextstop.ui.theme.VehicleDirectionChoice
import io.github.jnbp.nextstop.wear.PhoneBridge
import io.github.jnbp.nextstop.widget.WidgetJourneys
import io.github.jnbp.nextstop.widget.WidgetLaunch
import io.github.jnbp.nextstop.widget.WidgetUpdates
import io.github.jnbp.nextstop.widget.recordWidgetPosition
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.FeatureCollection

enum class Tab { Route, Departures, Map, Favorites }

/** A place shared by another app in the search for a destination; [serial] tells two shares apart. */
internal data class SharedSearch(val serial: Int, val query: String, val place: Stop?)

/** What the search panel is asking for: an end of the journey, or where a favourite place is. */
// TripTarget: a new destination for the followed trip (see startRetarget).
// MapPlace: a place or stop to show on the map tab (see pickMapPlace).
internal enum class SearchMode { Origin, Destination, Place, DepartureStop, Via, TripTarget, MapPlace }

/** Which search the open time sheet is asking a time for. */
private enum class TimeTarget { Journey, Departures }

/** The page in front of the map. A change from one to another is animated. */
internal enum class Screen { Start, Search, Settings, Plan, Connections, Trip, Course, Departures, MapSheet, Favorites }

// The signs of disruptions on the map: from this zoom on the area in view is asked (see
// MapNoticePool.refreshArea) once the map rests this long; new signs fade in this long.
private const val NOTICE_REFRESH_ZOOM = 13.0
private const val NOTICE_REFRESH_DELAY_MILLIS = 1500L
private const val NOTICE_APPEAR_MILLIS = 260

// A change between the pages of two tabs (a tap on the tab bar, or a link to another tab)
// slides along the order of the tabs: the new page comes in from the side of its tab, this
// far (share of the width), while the old one leaves to the other side; both fade.
private const val TAB_SLIDE_MILLIS = 250
private const val TAB_SLIDE_SHARE = 0.12f

// Emphasized easing of Material motion: the page coming in decelerates into place, the one
// leaving accelerates away.
private val TabEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val TabExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * The place in the tab bar of the tab a page belongs to, or null for a page that lies over
 * any tab (the search, a line course): a change from or to one of those is not a tab change.
 */
internal fun tabOrderOf(screen: Screen): Int? = when (screen) {
    Screen.Start, Screen.Plan, Screen.Connections, Screen.Trip, Screen.Settings -> Tab.Route.ordinal
    Screen.Departures -> Tab.Departures.ordinal
    Screen.MapSheet -> Tab.Map.ordinal
    Screen.Favorites -> Tab.Favorites.ordinal
    Screen.Search, Screen.Course -> null
}

/** Which way a change from [from] to [to] slides: 1 to a tab further right, -1 left, 0 none (same tab, or no tab). */
internal fun tabSlideOf(from: Screen, to: Screen): Int {
    val before = tabOrderOf(from) ?: return 0
    val after = tabOrderOf(to) ?: return 0
    return after.compareTo(before)
}

/** What a page lies on: the map itself, fog over the map, or the plain ground (the map switched off). */
internal enum class PageGround { Map, Fog, Plain }

/** The pages that do not need the map: they lie on fog, or on the plain ground without the map. */
internal val GROUNDED_PAGES = setOf(Screen.Search, Screen.Settings, Screen.Connections, Screen.Departures, Screen.Favorites)

/**
 * What [page] lies on; [startMap] is whether the map is on outside the pages that are about it,
 * [planMap] whether the planning page has its map (not with the battery saver, #77).
 */
internal fun pageGround(page: Screen, startMap: Boolean, planMap: Boolean = true): PageGround = when {
    page in GROUNDED_PAGES -> if (startMap) PageGround.Fog else PageGround.Plain
    page == Screen.Start && !startMap -> PageGround.Plain
    page == Screen.Plan && !planMap -> PageGround.Plain
    else -> PageGround.Map
}

// How long a back gesture from a page on the map waits at most for the picture of the map it
// takes along (see the back handler of AppRoot); without it the page shrinks without its map.
private const val BACK_SHOT_MILLIS = 180L
// Off a page on its own ground onto the map: its content fades first, its ground after it.
private const val CONTENT_OUT_MILLIS = 120
private const val GROUND_OUT_MILLIS = 140

/**
 * A change from [from] on its own ground (fog or plain) to [to] on the map that fades and
 * rises (not a change of tab, the search or the course, which move in their own ways): the
 * ground goes last.
 */
internal fun groundLast(from: Screen, to: Screen, startMap: Boolean, planMap: Boolean = true): Boolean =
    from != to && tabSlideOf(from, to) == 0 && from != Screen.Search && to != Screen.Course &&
        pageGround(from, startMap, planMap) != PageGround.Map && pageGround(to, startMap, planMap) == PageGround.Map

// How fast the page a back gesture leaves fades once let go: quickly, so nothing of it
// lingers over the page back leads to.
private const val BACK_COMMIT_FADE_MILLIS = 150
// How long the tab bar takes to sink away as the new companion opens, and to come back.
private const val TABS_AWAY_MILLIS = 220
// How long the pictures a page took along take to give way to the live map after a cancelled back.
private const val BACK_SHOT_FADE_MILLIS = 180

/** What decides which page is in front of the map (see [pageOf]). */
internal data class PageState(
    val courseView: Boolean,
    val searchMode: SearchMode?,
    val tab: Tab,
    val settingsOpen: Boolean,
    val tripOpen: Boolean,
    val showingConnections: Boolean,
    val planning: Boolean,
    // The map tab was opened from the departures ("Show on map", "Station on map"): back leads there.
    val backToDepartures: Boolean = false,
    // "My routes" was opened from the map tab (all disruptions, from a sign on the map): back leads there.
    val backToMap: Boolean = false,
)

/** The page in front of the map for [state]. */
internal fun pageOf(state: PageState): Screen = with(state) {
    when {
        courseView -> Screen.Course
        // The search for another stop opens over the departures.
        searchMode == SearchMode.DepartureStop -> Screen.Search
        // The place of a favourite tile is picked over the favourites tab too, as on the start page.
        searchMode == SearchMode.Place && tab == Tab.Favorites -> Screen.Search
        // The search of the map tab opens over the map.
        searchMode == SearchMode.MapPlace && tab == Tab.Map -> Screen.Search
        tab == Tab.Departures -> Screen.Departures
        tab == Tab.Map -> Screen.MapSheet
        tab == Tab.Favorites -> Screen.Favorites
        searchMode != null -> Screen.Search
        settingsOpen -> Screen.Settings
        tripOpen -> Screen.Trip
        showingConnections && planning -> Screen.Plan
        showingConnections -> Screen.Connections
        else -> Screen.Start
    }
}

/**
 * The page a back gesture leads to from [state], as the back of AppRoot goes (see goBack
 * there): for the predictive back, which shows it under the page that shrinks away. Null
 * where back does not lead to another page (a walk on the map, a marked place, the start).
 */
internal fun pageAfterBack(state: PageState, planFirst: Boolean): Screen? {
    val after = with(state) {
        when {
            courseView && backToDepartures && tab == Tab.Map -> copy(courseView = false, tab = Tab.Departures)
            courseView -> copy(courseView = false)
            searchMode != null -> copy(searchMode = null)
            settingsOpen && tab == Tab.Route -> copy(settingsOpen = false)
            tab == Tab.Map && backToDepartures -> copy(tab = Tab.Departures)
            tab == Tab.Favorites && backToMap -> copy(tab = Tab.Map, backToMap = false)
            tab != Tab.Route -> copy(tab = Tab.Route)
            tripOpen -> copy(tripOpen = false)
            showingConnections && !planning && planFirst -> copy(planning = true)
            showingConnections -> copy(showingConnections = false, planning = false)
            else -> return null
        }
    }
    return pageOf(after).takeIf { it != pageOf(state) }
}

/**
 * What one back does (see goBack in AppRoot), in the order back takes them: first what lies
 * on the page in front (a walk on the big map, a marked place, a card of the map, a page of
 * "My routes"), then the pages themselves. [System] leaves back to the system.
 */
internal enum class BackStep {
    CloseWalk, Unpin, ClosePick, ClosePosition, UnmarkStop, LeaveCourse, CloseSearch, FavoritesPage,
    CloseSettings, MapToDepartures, FavoritesToMap, RouteTab, CloseTrip, ToPlan, CloseConnections, System;

    /**
     * Back stays on the same page and changes what it shows: the page previews it itself
     * (see the preview of AppRoot inside a page). Every other step leads to another page
     * ([pageAfterBack]), which the gesture shows underneath.
     */
    val inPage: Boolean
        get() = this == CloseWalk || this == Unpin || this == ClosePick || this == ClosePosition || this == UnmarkStop ||
            this == FavoritesPage
}

/** What else than the page decides where back leads (see [backStep]). */
internal data class BackFacts(
    val page: PageState,
    // A walk is on the big map (the page has slid away).
    val walk: Boolean = false,
    // A marked place shows: in the start sheet or as the card of the map tab.
    val pin: Boolean = false,
    // The card of a stop tapped on the map of the start page shows in the start sheet.
    val pickedStop: Boolean = false,
    // The card of a stop position on the map tab, or that of the marked stop.
    val positionCard: Boolean = false,
    val stopCard: Boolean = false,
    // A page of "My routes" lies over its first page (over the page opened from the map tab,
    // where "My routes" was opened from there: see PageState.backToMap).
    val favoritesSub: Boolean = false,
)

/** What back does from [facts]: the one table goBack and the predictive back of AppRoot share. */
internal fun backStep(facts: BackFacts, planFirst: Boolean): BackStep = with(facts.page) {
    when {
        facts.walk -> BackStep.CloseWalk
        facts.pin && !courseView -> BackStep.Unpin
        facts.pickedStop && !courseView -> BackStep.ClosePick
        facts.positionCard -> BackStep.ClosePosition
        facts.stopCard -> BackStep.UnmarkStop
        courseView -> BackStep.LeaveCourse
        searchMode != null -> BackStep.CloseSearch
        facts.favoritesSub && tab == Tab.Favorites -> BackStep.FavoritesPage
        settingsOpen && tab == Tab.Route -> BackStep.CloseSettings
        tab == Tab.Map && backToDepartures -> BackStep.MapToDepartures
        tab == Tab.Favorites && backToMap -> BackStep.FavoritesToMap
        tab != Tab.Route -> BackStep.RouteTab
        tripOpen -> BackStep.CloseTrip
        showingConnections && !planning && planFirst -> BackStep.ToPlan
        showingConnections -> BackStep.CloseConnections
        else -> BackStep.System
    }
}

// Used when no location is available or the device is outside the Berlin area.
private val FALLBACK_CENTER = 52.5219 to 13.4132

// Moving the map further than this loads the stops around the new centre.
private const val RELOAD_DISTANCE_METERS = 250

// "Nearby" departures: so many departures of each stop within the radius; how many stops are
// asked and kept follows the radius (see nearbyStopsAsked, nearbyStopsKept).
private const val NEARBY_DEPARTURES_PER_STOP = 8

// The departures of one stop are asked for in pages of this many.
private const val DEPARTURE_PAGE = 12

// HAFAS answers for the hour after the time asked: an empty page moves the next one on by that.
private const val MORE_DEPARTURES_SKIP_MINUTES = 60L

// A refresh with this many vehicles or more is logged (see loadMapVehicles for the limits).
private const val MAX_VEHICLES = 150

// A tap this close to a vehicle on the map, in dp, hits it.
private const val VEHICLE_TAP_DP = 14f

// How far from a stop sign a tap still hits it, in dp.
private const val STOP_TAP_DP = 6f
// The room around a station of a journey or line on the map (a small dot) that a finger hits.
private const val ROUTE_STATION_TAP_DP = 18f
// A tap this close to a sign of disruptions opens it before the stop whose corner it sits on.
private const val NOTICE_TAP_DP = 3f

// The network of the city view is read or built this long after the map was shown, at a rest of the camera (see MapQuiet).
private const val RAPID_NETWORK_DELAY_MILLIS = 4_000L

// The packs of other states are read no sooner than this after the map was shown (see MapQuiet).
private const val OSM_PACKS_AFTER_SHOWN_MILLIS = 3_000L

// The tram network is read (or built) a little after the S- and U-Bahn network.
private const val TRAM_NETWORK_DELAY_MILLIS = 6_000L

// Below this zoom the camera reads no more of the OpenStreetMap packs (see OsmStations.ensureRegions):
// nothing of them shows that far out.
private const val OSM_REGIONS_MIN_ZOOM = 11.0

// Logged with PERF_LOG_TAG: work on the main thread longer than this, and more stop signs than this.
private const val PERF_SLOW_MAIN_MILLIS = 50L
private const val PERF_MANY_SIGNS = 1_500

// The glide of a vehicle from one update to the next lasts as long as the provider looks ahead.
internal const val VEHICLE_GLIDE_MILLIS = 30_000L

// The share of a glide that a vehicle keeps moving after it, to settle a correction: about a second.
internal const val VEHICLE_SETTLE_SHARE = 1_200f / VEHICLE_GLIDE_MILLIS

internal const val CURRENT_LOCATION_ID = "current-location"

// Whether a start at the position of the user is awaited ([0], like a flag), with the wait that runs for it.
internal class StartWait {
    var pending = false
    var job: Job? = null

    // Says that no start was found (startNotFound of AppRoot, declared after its first user).
    var notFound: (() -> Unit)? = null
    operator fun get(index: Int): Boolean = pending
    operator fun set(index: Int, value: Boolean) {
        pending = value
    }
}

// The pages before and after the loaded connections and the jobs loading them, in one holder
// so the big lambdas of AppRoot capture one object instead of six (ART limits captures).
@Stable
private class JourneyPaging {
    // The references to the pages before and after the loaded ones, one per level of transfer
    // comfort that is asked for: the picked one, or all of them when every connection is shown.
    var earlierRefs by mutableStateOf<Map<TransferComfort, String>>(emptyMap())
    var laterRefs by mutableStateOf<Map<TransferComfort, String>>(emptyMap())
    var loadingEarlier by mutableStateOf(false)
    var loadingLater by mutableStateOf(false)
    var laterJob by mutableStateOf<Job?>(null)
    var earlierJob by mutableStateOf<Job?>(null)
    // How the last page at each edge ended, for the note the chart shows there (see ChartEdges.kt).
    var earlierResult by mutableStateOf<EdgeResult?>(null)
    var laterResult by mutableStateOf<EdgeResult?>(null)
    private var resultSeq = 0
    // Which request at an edge is the latest: a cancelled one that ends after a newer one
    // started leaves the newer one's loading flag alone.
    var earlierGen = 0
    var laterGen = 0

    fun result(outcome: PageOutcome): EdgeResult = EdgeResult(outcome, ++resultSeq)
}

/** How many levels of [pages] failed; an answer that found nothing is no failure. */
private fun failedLevels(pages: List<LevelPage>): Int =
    pages.count { part -> part.error != null && !(part.error is HttpStatusException && part.error.nothingFound) }

// The tile of the next appointment on the start page and the device calendar behind it (see
// JourneyPaging for why it is one holder). "Later" hides the tile until the app starts again.
@Stable
private class CalendarTile(startCalendar: Boolean, granted: Boolean) {
    var startCalendar by mutableStateOf(startCalendar)
    var calendarLater by mutableStateOf(false)
    var calendarGranted by mutableStateOf(granted)
    var calendarDenied by mutableStateOf(false)
    var appointments by mutableStateOf<List<Appointment>?>(null)

    // The appointment the user picked from the row below the tile; null for the next one.
    var appointmentPick by mutableStateOf<String?>(null)

    // Where the appointment in front lies and when to leave for it, once found.
    var appointmentLeave by mutableStateOf<AppointmentLeave?>(null)
}

// The connecting departures loaded for changes of the journeys of the open search, by offerKey,
// and the changes asked for tighter connecting departures (see loadTighterJourneys), by the key
// of their request, each with the job asking for them.
@Stable
private class OfferState {
    val transferOffers = mutableStateMapOf<String, Alternatives>()
    var offersJob by mutableStateOf<Job?>(null)
    val tightAsked = mutableSetOf<String>()
    var tightJob by mutableStateOf<Job?>(null)
    // The searches for feeders to long-distance trains made for the open search (#272), by key.
    val feederAsked = mutableSetOf<String>()
    var feederJob by mutableStateOf<Job?>(null)
}

// The whole trips of the rides of the followed journey, by their handle: all their stops for
// the cards, and as tracks for their vehicles on the map. The feeder maps hold the runs that
// bring the vehicle of a connecting ride to the stop where that ride begins, by the handle of
// the connecting ride, and feederTried the rides whose run before was looked for already.
@Stable
private class TripCourseCache {
    val tripCourses = mutableStateMapOf<String, TripCourse>()
    val tripTracks = mutableStateMapOf<String, TripTrack>()
    val feederCourses = mutableStateMapOf<String, TripCourse>()
    val feederTracks = mutableStateMapOf<String, TripTrack>()
    val feederTried = mutableSetOf<String>()
}

// Where an open line course came from: the camera before it ([0] of courseReturn), the row it
// came out of (null for a vehicle on the map), whether it may frame the map, and where the user was.
@Stable
private class CourseOpenedFrom {
    val courseReturn = arrayOfNulls<CameraPosition>(1)
    val courseOrigin = arrayOfNulls<Rect>(1)
    val courseFit = booleanArrayOf(true)
    val courseStart = arrayOfNulls<Pair<Double, Double>>(1)
}

// So many places picked in searches are remembered.
private const val MAX_SEARCH_HISTORY = 50

// How long a shared place stays with the closed search, while its panel leaves.
private const val SHARED_SEARCH_LINGER_MILLIS = 1_000L

// How long a point of a "navigate here" link without a name waits for one (from the device's
// geocoder or the nearest stop) before it is routed to as the plain chosen place.
private const val NAV_LABEL_WAIT_MILLIS = 2_500L

// Open connections load fresh live data this often, a few journeys per round and one
// request per journey with a pause in between: far below the 100 requests per minute allowed.
private const val JOURNEY_REFRESH_MILLIS = 30_000L
private const val JOURNEY_REFRESH_PAUSE_MILLIS = 400L
private const val MAX_REFRESH_PER_ROUND = 8

// Journeys that leave later than this have no live data yet: the refresh leaves them alone.
private const val LIVE_HORIZON_HOURS = 3L

private const val START_ZOOM = 14.5

// The chips and buttons on top of the map take about this much below the status bar.
private const val MAP_TOP_COVER_DP = 64f

// How long the frame of a line course waits for its list of stops to settle.
private const val COURSE_FIT_WAIT_MILLIS = 350L

// Around the equator, for the scale of the map.
private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
private const val METERS_PER_DEGREE = 111_320.0

// Close enough to see the entrances of a station.
private const val WALK_ZOOM = 17.0

// A walk on the big map: the room kept free for the "Back" on top and the card at the bottom;
// the time of a move.
private const val WALK_FRAME_TOP_DP = 76
private const val WALK_FRAME_BOTTOM_DP = 160
private const val WALK_FRAME_MILLIS = 600

// A stop shown on the map with the streets around it.
private const val STOP_ZOOM = 16.0

// A stop sign or stop position tapped on the map: how long the camera takes to centre it, and
// for how long after the tap it is aimed again while the card at the bottom still settles.
private const val TAP_FOCUS_MILLIS = 450
private const val TAP_FOCUS_SETTLE_MILLIS = 2_000L
// How far (in dp) the place may end up from where it was aimed before the camera goes again.
private const val TAP_FOCUS_SLACK_DP = 6f

// While the cards cover every way with chevrons, the flow looks again this often (ms) whether one shows.
private const val ARROW_FLOW_COVERED_POLL_MILLIS = 500L

// While a trip is followed, the position is asked for this often and after this many metres.
private const val LOCATION_INTERVAL_MILLIS = 5_000L
private const val LOCATION_MIN_METERS = 5f

// While the own position is on the map in front, the device reports it this often (with the
// battery saver at the slower pace) and the dot moves smoothly between the fixes (see OwnDot).
// The rest of the page still takes a position at the pace above (LOCATION_ / POSITION_).
private const val DOT_INTERVAL_MILLIS = 1_000L
private const val DOT_SAVER_INTERVAL_MILLIS = 3_000L

// A fix comes this much early and still counts for the pace of the page.
private const val PACE_SLACK_MILLIS = 500L

// While the camera follows the dot frame by frame, the map leaves targets this near alone.
private const val OWN_FOLLOW_SLACK_METRES = 150.0

// A fresh position is waited for this long per source; after that the last known one has to do.
private const val LOCATE_TIMEOUT_MILLIS = 10_000L

// How long a search waits for a fresh position of its start before the last known one stands in.
private const val START_WAIT_MILLIS = 5_000L

// A position this near to the one in use is not a new place: the selected stop and the map stay as they are.
private const val POSITION_SHIFT_METERS = 50

// While no trip is followed, the position is asked for at this calmer pace.
private const val POSITION_INTERVAL_MILLIS = 10_000L
private const val POSITION_MIN_METERS = 20f

// Faster than this, in metres per second, the user rides in a vehicle: while the map follows
// the user then, the position is asked for as often as on a followed trip. Below the lower
// speed the calm pace comes back (two speeds, so it does not flip at one).
private const val RIDING_SPEED = 4f
private const val WALKING_SPEED = 1.5f

// A position this young still says where the device is: asking for it anew would tell nothing.
private const val POSITION_FRESH_MILLIS = 30_000L

/** What stands in the way of the position of the device, as far as the app knows. */
private enum class LocationTrouble { None, NoPermission, SwitchedOff, Outside, NoFix }

/**
 * A way on the map in its colour; walks are drawn dotted, the way a vehicle still has to
 * come [dashed]. [stops] are marked on it as small dots. A ride ([arrows]) carries small
 * chevrons in its direction of travel (see LineArrows.kt); walks, the grey part behind the
 * traveller and the way a vehicle still has to come do not.
 */
private class MapRoute(
    val color: Color,
    val points: List<Pair<Double, Double>>,
    val dotted: Boolean,
    val dashed: Boolean = false,
    val stops: List<Pair<Double, Double>> = emptyList(),
    val arrows: Boolean = !dotted && color != TRAVELLED_COLOR && color != APPROACH_COLOR && color != BIKE_COLOR && color != WALK_COLOR,
)

// The ways on the map are thinned once per way (see thinnedRoute in RouteThinning.kt).
private fun thinned(path: List<Pair<Double, Double>>): List<Pair<Double, Double>> = thinnedRoute(path)

private val WALK_COLOR = Color(0xFF8D96A8)
// A ride by bike of the bike combo: a solid line in the green of cycle ways, not the dots of a walk.
private val BIKE_COLOR = Color(0xFF2E9E5B)
private val OTHER_OPTION_COLOR = Color(0xFFB4BBC9)

// The part of a followed trip that is behind the traveller.
private val TRAVELLED_COLOR = Color(0xFFB4BBC9)

// How often the way of a followed ride is split again into behind (grey) and ahead.
private const val ROUTE_SPLIT_MILLIS = 2_000L

// The way a vehicle still drives to pick the traveller up: grey and dashed, a little see-through.
private val APPROACH_COLOR = Color(0xFF8A93A3).copy(alpha = 0.6f)

/**
 * The ways of a followed [journey] at [now]: what is behind the traveller in grey, the ride
 * on board in its colour from where its vehicle is ([vehicleAt], else by the clock), and
 * for every ride still ahead the way its vehicle has yet to come to the boarding stop,
 * dashed in a quiet grey with small dots for the stops on it, taken from its whole trip in
 * [courses] (only with [approaches]). Then the rides themselves as in [journeyRoutes].
 */
private fun tripRoutes(
    journey: Journey,
    now: OffsetDateTime,
    courses: Map<String, TripCourse>,
    approaches: Boolean,
    glides: MutableList<GlideRide>? = null,
    trackOf: (Leg) -> TripTrack? = { null },
    state: RideState? = null,
    fix: PositionFix? = null,
    vehicleAt: (Leg) -> Pair<Double, Double>?,
): List<MapRoute> {
    // Only with the setting "Spätere Fahrzeuge zeigen" (off by default, see ApproachSetting).
    val approaches = approachRides(journey.rides, approaches) { now.isBefore(it.departure) }
        .mapNotNull { ride -> ride.tripId?.let { courses[it] }?.let { approachRoute(ride, it, now) } }
    val rides = journeyRoutes(journey) { leg, route ->
        val index = journey.legs.indexOfFirst { it === leg }
        // By the ride state (#170): on board by position past the live arrival the ride is still
        // ridden and the rides after it lie ahead, whatever the clock says.
        val phase = if (state != null && index >= 0) {
            routePhase(index, leg, now, state)
        } else {
            when {
                !now.isBefore(leg.arrival) -> RoutePhase.Behind
                !now.isBefore(leg.departure) -> RoutePhase.Riding
                else -> RoutePhase.Ahead
            }
        }
        // Past its live arrival its track puts the vehicle at the end: the split goes by the traveller's position.
        val overdueOnBoard = phase == RoutePhase.Riding && !now.isBefore(leg.arrival) && fix != null && fix.legIndex == index
        // A ride without a way of its own stays a dashed connector, also once it is split.
        when (phase) {
            RoutePhase.Behind -> listOf(MapRoute(TRAVELLED_COLOR, route.points, dotted = false, dashed = route.dashed))
            RoutePhase.Riding -> {
                val vehicle = if (overdueOnBoard) ridePositionOnWay(route.points, fix!!.alongMeters) else vehicleAt(leg)
                val at = DoubleArray(2)
                val (behind, ahead) = splitWay(route.points, vehicle, leg, now, at)
                // The end of the grey glides with the vehicle until the next split (see RouteSplitGlide):
                // only where a track places the vehicle (the same one the split went by) and the way is its own.
                val track = trackOf(leg)
                if (!overdueOnBoard && glides != null && vehicle != null && track != null && !route.dashed && route.points.size >= 2) {
                    val along = alongMetres(route.points)
                    val latLon = DoubleArray(route.points.size * 2) { if (it % 2 == 0) route.points[it / 2].first else route.points[it / 2].second }
                    glides.add(
                        GlideRide(
                            RouteGlide("${journey.id}|${leg.departure}|${leg.line}", TRAVELLED_COLOR.toArgb(), latLon, along, splitAlong(along, at[0].toInt(), at[1])),
                            track,
                        ),
                    )
                }
                listOfNotNull(
                    MapRoute(TRAVELLED_COLOR, behind, dotted = false, dashed = route.dashed).takeIf { behind.size >= 2 },
                    MapRoute(route.color, ahead, dotted = false, dashed = route.dashed).takeIf { ahead.size >= 2 },
                )
            }
            RoutePhase.Ahead -> listOf(route)
        }
    }
    // The ways still to come lie below the rides.
    return approaches + rides
}

/**
 * [path] of the ride [leg] on board split into the part behind and the part ahead, exactly at
 * the vehicle ([vehicle], else where the clock puts it): the split point is projected onto the
 * nearest stretch of the way and belongs to both parts, so the grey ends right at the vehicle
 * and does not jump back to the last corner of a long straight stretch.
 */
private fun splitWay(
    path: List<Pair<Double, Double>>,
    vehicle: Pair<Double, Double>?,
    leg: Leg,
    now: OffsetDateTime,
    splitAt: DoubleArray? = null,
): Pair<List<Pair<Double, Double>>, List<Pair<Double, Double>>> {
    if (path.size < 2) return emptyList<Pair<Double, Double>>() to path
    val at = vehicle ?: run {
        val total = Duration.between(leg.departure, leg.arrival).seconds.coerceAtLeast(1)
        val done = Duration.between(leg.departure, now).seconds
        pointAtShare(path, (done.toFloat() / total).coerceIn(0f, 1f))
    }
    val scale = cos(Math.toRadians(at.first))
    var bestIndex = 0
    var bestShare = 0.0
    var best = Double.MAX_VALUE
    for (index in 0 until path.size - 1) {
        val ax = (path[index].second - at.second) * scale
        val ay = path[index].first - at.first
        val dx = (path[index + 1].second - path[index].second) * scale
        val dy = path[index + 1].first - path[index].first
        val length = dx * dx + dy * dy
        val share = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        val px = ax + dx * share
        val py = ay + dy * share
        val distance = px * px + py * py
        if (distance < best) {
            best = distance
            bestIndex = index
            bestShare = share
        }
    }
    val a = path[bestIndex]
    val b = path[bestIndex + 1]
    val point = (a.first + (b.first - a.first) * bestShare) to (a.second + (b.second - a.second) * bestShare)
    if (splitAt != null) {
        splitAt[0] = bestIndex.toDouble()
        splitAt[1] = bestShare
    }
    return (path.subList(0, bestIndex + 1) + point) to (listOf(point) + path.subList(bestIndex + 1, path.size))
}

/**
 * The way the vehicle of [ride] has yet to come to the stop where the traveller boards it:
 * from the last stop of its whole trip ([course]) it has passed to the boarding stop, with
 * the stops in between. Null when it is there already or the trip does not show it.
 */
private fun approachRoute(ride: Leg, course: TripCourse, now: OffsetDateTime): MapRoute? {
    val stops = course.plausible().stops
    // On a ring the boarding stop comes round more than once: the call at the time of the ride (see TripMatch).
    val board = TripMatch.boardIndex(stops, ride)
    if (board <= 0) return null
    val passed = StopClock(stops).progress(now.toInstant().toEpochMilli()).toInt().coerceAtLeast(0)
    if (passed >= board) return null
    val positions = stops.subList(passed, board + 1).mapNotNull { it.place?.position }
    if (positions.size < 2) return null
    // The stretch of the trip's way from the last stop passed to the boarding stop, each on its
    // own pass of the way: on a ring the nearest point anywhere may lie a lap away.
    val points = TripMatch.cutBetween(course.path, stops.map { it.place?.position }, passed, board) ?: positions
    return MapRoute(
        // Neutral, so the traveller's own way (solid, in the colours of the lines) stands out.
        APPROACH_COLOR,
        thinned(points),
        dotted = false,
        dashed = true,
        stops = positions.dropLast(1),
    )
}

/**
 * The legs of a journey that have a way, each in the colour of its line or as a dotted walk.
 * The ways of two rides end at their platforms, and a walk is only known as the straight
 * line between two stations: so a walk is drawn from where the ride before it ends to where
 * the next one begins, and two rides that meet without a walk get a dotted link. [ride]
 * may draw the way of a ride differently, as one or more ways.
 */
/** A walk alone as a dotted way, to the stop position or entrance it leads to; empty without a way. */
private fun walkRoutes(walk: Leg): List<MapRoute> {
    val path = FootPaths.pathFor(walk)
    val target = walk.walkTarget?.position
    val way = when {
        target == null || path.lastOrNull() == target || path.isEmpty() -> path
        // Led on to the target from where it lies beside the way, not past it and back.
        path.size > 2 -> PathJoin.attachEnd(path.dropLast(1), target, always = true)
        else -> path.dropLast(1) + target
    }.let { PathJoin.smoothEnds(it) }
    val route = if (walk.cycling) MapRoute(BIKE_COLOR, thinned(way), dotted = false) else MapRoute(WALK_COLOR, thinned(way), dotted = true)
    return if (way.size >= 2) listOf(route) else emptyList()
}

/**
 * The walk [path] as drawn between the end of the ride before it ([start]) and the start of
 * the ride after it ([end]), where there are such rides. A way of its own runs between station
 * centres (or the two points themselves, see [FootPaths]): those ends are dropped and the way
 * is clipped to start beside [start] and end beside [end] (see [PathJoin.clipWalk]), so it
 * does not run on to a centre and back. A straight walk joins the two points straight.
 */
private fun walkBetween(
    path: List<Pair<Double, Double>>,
    start: Pair<Double, Double>?,
    end: Pair<Double, Double>?,
): List<Pair<Double, Double>> {
    if (path.size <= 2) return listOfNotNull(start ?: path.firstOrNull(), end ?: path.lastOrNull())
    val inner = path.subList(if (start != null) 1 else 0, if (end != null) path.size - 1 else path.size)
    return PathJoin.clipWalk(inner, start, end)
}

private fun journeyRoutes(
    journey: Journey?,
    ride: (Leg, MapRoute) -> List<MapRoute> = { _, route -> listOf(route) },
): List<MapRoute> {
    val legs = journey?.legs.orEmpty().filter { it.path.size >= 2 }
    val routes = mutableListOf<MapRoute>()
    legs.forEachIndexed { index, leg ->
        val before = legs.getOrNull(index - 1)
        if (leg.walking) {
            // A walk the provider knows only as a straight line follows the real way on foot once it is known.
            val path = FootPaths.pathFor(leg)
            val rideEnd = before?.takeIf { !it.walking }?.path?.last()
            val rideStart = legs.getOrNull(index + 1)?.takeIf { !it.walking }?.path?.first()
            // A walk that leaves its station by an exit (or goes in by an entrance) keeps that
            // end of its way, joined straight to the platform: clipped near the platform it
            // would cut the exit off.
            val exit = leg.walkExit?.position?.takeIf { it == path.firstOrNull() }
            val entrance = leg.walkTarget?.takeIf { it.kind == WalkTargetKind.Entrance }?.position?.takeIf { it == path.lastOrNull() }
            // A walk re-routed from where the traveller is starts there, not beside the ride before it.
            val start = if (exit != null || FootPaths.hasDetour(leg)) null else rideEnd
            val end = if (entrance != null) null else rideStart
            val way = walkBetween(path, start, end).let { way ->
                val head = if (exit != null && rideEnd != null && rideEnd != way.firstOrNull()) listOf(rideEnd) else emptyList()
                val tail = if (entrance != null && rideStart != null && rideStart != way.lastOrNull()) listOf(rideStart) else emptyList()
                head + way + tail
            }
            routes.add(
                if (leg.cycling) MapRoute(BIKE_COLOR, thinned(way), dotted = false) else MapRoute(WALK_COLOR, thinned(way), dotted = true),
            )
        } else {
            // The first ride from one of many stop positions: the way from the stop's point to its position.
            val spot = leg.boardingSpot
            val stopPoint = leg.fromPlace?.position
            if (before == null && index == 0 && spot != null && stopPoint != null && distanceMeters(stopPoint, spot.position) > 15 &&
                hasBoardingPositionMap(leg)
            ) {
                routes.add(MapRoute(WALK_COLOR, thinned(FootPaths.wayBetween(stopPoint, spot.position)), dotted = true))
            }
            if (before != null && !before.walking && before.path.last() != leg.path.first()) {
                val spots = stopSpotMeters(before, leg)
                when {
                    // Both rides stop at the same bus or tram stop: nothing to walk.
                    spots != null && spots <= SAME_SPOT_METERS -> Unit
                    // Between two stop positions of a bus or tram stop: the real way on foot once it is known.
                    spots != null -> routes.add(
                        MapRoute(
                            WALK_COLOR,
                            thinned(walkBetween(FootPaths.wayBetween(before.path.last(), leg.path.first()), before.path.last(), leg.path.first())),
                            dotted = true,
                        ),
                    )
                    else -> routes.add(MapRoute(WALK_COLOR, listOf(before.path.last(), leg.path.first()), dotted = true))
                }
            }
            // A bus without a way of its own (only the straight lines between its stops, as for a
            // rail replacement without a shape): along the streets through its stops once the car
            // router found them (see busRoad), until then a dashed connector, not a road through the fields.
            val stopPoints = legStopPoints(leg)
            val noWay = movesWithoutWay(leg.product, leg.path, stopPoints)
            val road = if (noWay) busRoad(stopPoints) else null
            routes.addAll(ride(leg, MapRoute(lineColor(leg), thinned(road ?: leg.path), dotted = false, dashed = noWay && road == null)))
        }
    }
    return routes
}

/**
 * The streets a bus without a way of its own drives through [stops], where the car router
 * already found them (see FootPaths.roadWay); else asks for them and is null meanwhile: the
 * maps draw again when they come in (FootPaths.version).
 */
private fun busRoad(stops: List<Pair<Double, Double>>): List<Pair<Double, Double>>? =
    FootPaths.roadWay(stops) ?: run {
        FootPaths.wantRoad(stops)
        null
    }

/** The positions of the stops of the ride [leg] in order: where it is boarded, the stops on the way, where it is left. */
private fun legStopPoints(leg: Leg): List<Pair<Double, Double>> =
    listOfNotNull(leg.fromPlace?.position) + leg.stopovers.mapNotNull { it.place?.position } + listOfNotNull(leg.toPlace?.position)

/**
 * The way of the trip [course] on the map: its own way, solid; where it has none, or only the
 * straight lines between its stops for a bus (see movesWithoutWay), a dashed connector through
 * its stops. Empty where not even two stops have a position.
 */
private fun courseRoutes(course: TripCourse): List<MapRoute> {
    val stops = course.stops.mapNotNull { it.place?.position }
    val color = lineColor(course)
    if (course.path.size >= 2 && !movesWithoutWay(course.product, course.path, stops)) return listOf(MapRoute(color, course.path, dotted = false))
    // A bus without a way of its own: along the streets through its stops once known (see busRoad).
    if (stops.size >= 2 && drivesOnRoads(course.product)) busRoad(stops)?.let { return listOf(MapRoute(color, thinned(it), dotted = false)) }
    return when {
        course.path.size >= 2 -> listOf(MapRoute(color, course.path, dotted = false, dashed = true))
        stops.size >= 2 -> listOf(MapRoute(color, stops, dotted = false, dashed = true))
        else -> emptyList()
    }
}

/** What a point of a journey on the map is: where it starts, a change, or where it ends. */
private enum class SpotKind { Start, Change, End }

/**
 * A point of a journey that is marked and named on the map. A [bare] one is only named: a
 * stop position of a bus or tram marks it already (see [journeyStopMarks]).
 */
private data class Spot(val kind: SpotKind, val position: Pair<Double, Double>, val name: String, val bare: Boolean = false)

/** Start, changes and destination of a journey, as far as their positions are known. */
private fun journeySpots(journey: Journey): List<Spot> {
    val spots = mutableListOf<Spot>()
    val first = journey.legs.first()
    val last = journey.legs.last()
    (first.path.firstOrNull() ?: first.fromPlace?.position)?.let {
        spots.add(Spot(SpotKind.Start, it, first.from.plainStopName()))
    }
    // A change is where a ride ends that has another ride after it, with or without a walk between them.
    journey.rides.dropLast(1).forEach { arriving ->
        (arriving.path.lastOrNull() ?: arriving.toPlace?.position)?.let {
            spots.add(Spot(SpotKind.Change, it, arriving.to.plainStopName(), bare = arriving.alightingSpot != null))
        }
    }
    (last.path.lastOrNull() ?: last.toPlace?.position)?.let {
        spots.add(Spot(SpotKind.End, it, last.to.plainStopName()))
    }
    return spots
}

/** Where the destination's badge of [journey] stands (#181): the end of its last leg, where known. */
private fun journeyEnd(journey: Journey?): Pair<Double, Double>? =
    journey?.takeIf { it.legs.isNotEmpty() }?.let { journeySpots(it).lastOrNull { spot -> spot.kind == SpotKind.End }?.position }

/**
 * A stop position of a bus or tram at a change of a journey on the map: where the arriving
 * ride stops and where the next one leaves, as a small mark in the colour of its line with
 * its "Pos." number ([label]) where it has one.
 */
private class StopMark(val position: Pair<Double, Double>, val label: String?, val color: Color, val faint: Boolean = false)

/**
 * The stop positions of the changes of [journey] (see [StopMark]): of the ride that arrives
 * and of the one that leaves, where OpenStreetMap knows them; one mark where both stop at
 * the same stop.
 */
private fun journeyStopMarks(journey: Journey): List<StopMark> {
    val marks = mutableListOf<StopMark>()
    // The first ride from a stop of many stop positions: the others faint, its own highlighted.
    journey.legs.firstOrNull()?.takeIf { !it.walking && hasBoardingPositionMap(it) }?.let { first ->
        val board = first.boardingSpot ?: return@let
        val anchor = first.fromPlace?.position ?: board.position
        OsmStations.areasOf(first.fromPlace?.id, first.fromPlace?.name ?: first.from, anchor)
            .flatMap { it.positions }
            .filter { it.position != board.position }
            .forEach { marks.add(StopMark(it.position, null, Color(0xFF8A9099), faint = true)) }
        marks.add(StopMark(board.position, board.label, lineColor(first)))
    }
    journey.rides.zipWithNext().forEach { (arriving, next) ->
        if (arriving.tripId != null && arriving.tripId == next.tripId) return@forEach
        val alight = arriving.alightingSpot
        val board = next.boardingSpot
        val same = stopSpotMeters(arriving, next)?.let { it <= SAME_SPOT_METERS } == true
        if (alight != null && !same) marks.add(StopMark(alight.position, alight.label, lineColor(arriving)))
        if (board != null) marks.add(StopMark(board.position, board.label ?: alight?.label?.takeIf { same }, lineColor(next)))
    }
    return marks
}

/** The next journey to a saved place or along a saved route, and when it was asked for. */
private data class PlaceTrip(val journey: Journey?, val loadedAt: OffsetDateTime)

// A journey on a tile is asked for again after this long, or once it has left. The tiles
// are looked through once per round, and only while they are on screen.
private const val TILE_FRESH_MINUTES = 5L
private const val TILE_ROUND_MILLIS = 60_000L

// The start has to stay the same for this long before the tiles ask for journeys from it.
private const val TILE_SETTLE_MILLIS = 800L

// Saved routes that get a tile next to the favourite places.
private const val MAX_ROUTE_TILES = 3

// Journeys asked for per tile of a remembered connection, and later pages a search asks for to find one of its journeys.
private const val PATTERN_TILE_RESULTS = 6
private const val PATTERN_MAX_PAGES = 3

/** The key of the tile of a remembered connection, apart from those of places and routes. */
private fun patternTileKey(pattern: SavedPattern): String = "pattern:" + pattern.id

// So many of the last searches are kept and offered on the start page.
private const val MAX_RECENT_ROUTES = 6

// A tap on the map picks the route option whose way lies within this many dp of it.
private const val PLAN_TAP_DP = 28f

// The connecting departures at a change are asked for from this many minutes before the
// traveller gets there: a ride that leaves just before can still be caught when the ride
// it is reached with is early or it is late itself.
private const val OFFER_EARLY_MINUTES = 6L

/**
 * From when connecting departures are asked for at the end of [arriving]: a little before it
 * gets there by the timetable or the live times, whichever is earlier, so departures that are
 * planned before the arrival but leave late enough to be caught (very risky) are among them.
 */
internal fun offerStart(arriving: Leg): OffsetDateTime {
    val planned = arriving.plannedArrival ?: arriving.arrival
    return minOf(planned, arriving.arrival).minusMinutes(OFFER_EARLY_MINUTES)
}
internal const val OFFER_RESULTS = 8

// While a trip is followed, the connecting departures at its next change are asked for again this often.
private const val OFFER_REFRESH_MILLIS = 45_000L

// The start page comes in after the map (see IntroStep): the sheet rises this far and the
// chips on top drop in from this high; the bar of the tabs rises from NAV_INTRO_RISE.
private val INTRO_RISE = 160.dp
private val NAV_INTRO_RISE = 28.dp
private val INTRO_DROP = 14.dp

// The cards of the start page (departures, places, appointment) rise this far as they come in
// one after the other.
private val INTRO_CARD_RISE = 18.dp
private const val INTRO_CARDS = 3

// What the departures card of the start page shows (see its cross-fade).
private const val DEPARTURES_ERROR = 0
private const val DEPARTURES_LOADING = 1
private const val DEPARTURES_NONE = 2
private const val DEPARTURES_LIST = 3

// The live hint pill grows from this size as it comes in.
private const val HINT_ENTER_SCALE = 0.82f

// How long the vehicle hint pill takes to fade in or out.
private const val HINT_FADE_MILLIS = 250

// The camera flies back to the user in this time.
private const val LOCATE_MILLIS = 700

// The quick picks of "Von wo?" need a position at most this old (#173).
private const val QUICK_FIX_MAX_AGE_MILLIS = 5 * 60_000L

// A tapped stop chip frames user and stop in this time; closer than this they share one view at the zoom of a stop.
private const val FRAME_MILLIS = 650
private const val FRAME_NEAR_METERS = 120

// The ways of route options draw themselves onto the map one after the other.
private const val ROUTE_DRAW_MILLIS = 650
private const val ROUTE_STAGGER_MILLIS = 90L

// A way that is no longer picked fades to grey in this time.
private const val ROUTE_FADE_MILLIS = 450

// The trips of the rides of a followed journey are asked for again this often.
private const val APPROACH_REFRESH_MILLIS = 30_000L

// The run that brings the vehicle of a connecting ride to its first stop is looked for
// among the arrivals there this long before that ride leaves.
private const val FEEDER_WINDOW_MINUTES = 45L

// The map of a followed trip frames what matters now no closer than this, and moves there in this time.
private const val TRIP_FRAME_MAX_ZOOM = 16.0

// Centring on the traveller (#273): the visible room starts this far from what covers the map,
// needs at least this much height (else the whole map counts), and the stop ahead keeps this
// margin from its edges.
private const val CENTRE_COVER_GAP_DP = 8f
private const val CENTRE_MIN_ROOM_DP = 160f
private const val CENTRE_MARGIN_DP = 40f
private const val TRIP_FRAME_MILLIS = 900

// A map just made gets its size within this long; the trip is framed after it (#122).
private const val MAP_SIZE_WAIT_MILLIS = 1_000L

// The device calendar is read again this often while the start page is in view.
private const val CALENDAR_REFRESH_MILLIS = 5 * 60_000L

// The times of an open line course are asked for again this often.
private const val COURSE_REFRESH_MILLIS = 30_000L

// The live vehicle of an open course is asked for this soon at first, then this often, in a
// box this many degrees of latitude around it; a report older than the last is dropped.
private const val COURSE_LIVE_FIRST_MILLIS = 1_500L
private const val COURSE_LIVE_MILLIS = 15_000L
private const val COURSE_LIVE_SPAN = 0.012
private const val COURSE_LIVE_STALE_MILLIS = 60_000L

/** One page of a search at one level of transfer comfort, or what went wrong asking for it. */
private class LevelPage(val level: TransferComfort, val page: JourneyPage?, val error: Exception?)

/** Asks for one page per level, all at the same time. A level that fails does not stop the others. */
private suspend fun fetchLevels(
    levels: Collection<TransferComfort>,
    fetch: suspend (TransferComfort) -> JourneyPage,
): List<LevelPage> = coroutineScope {
    levels.map { level ->
        async {
            try {
                LevelPage(level, fetch(level), null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LevelPage(level, null, e)
            }
        }
    }.awaitAll()
}

/**
 * [found] with the journeys of [pages] added: for every connection (by [journeyKey]) the
 * loosest level whose search found it, so a journey that only the search without any change
 * time found can be told from one the provider offers with its normal change times.
 */
private fun withFoundLevels(found: Map<String, TransferComfort>, pages: List<LevelPage>): Map<String, TransferComfort> {
    val result = found.toMutableMap()
    pages.forEach { part ->
        part.page?.journeys?.forEach { journey ->
            val key = journeyKey(journey)
            result[key] = loosestLevel(listOfNotNull(result[key], part.level)) ?: part.level
        }
    }
    return result
}

/** [journeys] each with the level of the loosest search that found it (see [withFoundLevels]). */
private fun withFound(journeys: List<Journey>, found: Map<String, TransferComfort>): List<Journey> =
    journeys.map { journey -> found[journeyKey(journey)]?.let { journey.copy(foundLevel = it) } ?: journey }

/**
 * The category of every journey of [journeys] by its id, always from the very journey that is
 * shown (see [journeyComfort]), so the filter, the tag and the changes of a journey agree.
 */
private fun levelsOf(journeys: List<Journey>?): Map<String, TransferComfort> =
    journeys.orEmpty().associate { it.id to journeyComfort(it) }

/** Whether the page [screen] shows the lists of the journeys found (see listsShown in AppRoot). */
private fun journeyListsShown(screen: Screen): Boolean = screen == Screen.Connections || screen == Screen.Plan

/** What [rememberWhileShown] holds: the keys it was worked out for, and the value. */
private class HeldValue<T>(var keys: Array<out Any?>? = null, var value: T? = null)

/**
 * Like remember with [keys], but worked out again only while [shown]: hidden, a change of the
 * keys keeps the last value (worked out once if there is none yet), and the first composition
 * with [shown] after a change works it out anew. For lists of a page that is not on screen.
 */
@Composable
private fun <T> rememberWhileShown(shown: Boolean, vararg keys: Any?, calculation: () -> T): T {
    val held = remember { HeldValue<T>() }
    val known = held.keys
    if (known == null || (shown && !known.contentEquals(keys))) {
        held.value = calculation()
        held.keys = keys.copyOf()
    }
    @Suppress("UNCHECKED_CAST")
    return held.value as T
}

// SeekableTransitionState drives the pages for the predictive back.
@OptIn(ExperimentalTransitionApi::class)
/** What the plan and the connections show of the journeys found (see the pages in AppRoot). */
private class PageLists(
    val selection: JourneySelection,
    val shownJourneys: List<Journey>?,
    val directIds: Set<String>,
    val patternNote: String?,
    val filteredOut: List<Journey>,
    val filteredLevels: Set<TransferComfort>,
    val showFiltered: () -> Unit,
    val productsOff: Set<String>,
    val useAllProducts: () -> Unit,
    val fromName: String,
    val toName: String,
)


// Back on screen, the extras of the page start this long after its first refreshes (see appSettled).
private const val REFRESH_SPREAD_MILLIS = 350L

@Composable
fun AppRoot() {
    AllocProbe.tick(AllocProbe.APP_ROOT)
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    // The providers are picked anew for every request from what the settings say right now.
    val source = remember {
        RoutingSource(choice = { prefs.sourceChoice }, userAgent = TransitousSource.userAgent(context), bvgUrl = { ownServerUrl(prefs.ownServer) })
    }
    var sourceChoice by remember { mutableStateOf(prefs.sourceChoice) }
    var ownServer by remember { mutableStateOf(prefs.ownServer) }
    var delayDisplay by remember { mutableStateOf(prefs.delayDisplay) }
    var options by remember { mutableStateOf(prefs.searchOptions) }
    // With an accessibility setting, the walks of changes count without steps and escalators.
    LaunchedEffect(options.accessibility) {
        stepFreeWalks = options.accessibility != Accessibility.None
        noteRatingSettings()
    }
    // "Langsamer unterwegs": every walk the app measures itself (changes, stop positions) takes longer.
    LaunchedEffect(options.walkFactorInUse) {
        walkSpeedFactor = options.walkFactorInUse
        noteRatingSettings()
    }
    var startVehicles by remember { mutableStateOf(prefs.startVehicles) }
    // False: the start page lies over a plain ground and the map rests until a page needs it.
    // As chosen ([startMapChosen]) and as in effect: the battery saver takes it away while active.
    var startMapChosen by remember { mutableStateOf(prefs.startMap) }
    val startMap = startMapChosen && !BatterySaver.active
    // The planning page lies on the plain ground too while the battery saver is on (#77).
    val planMap = !BatterySaver.active
    var startClassic by remember { mutableStateOf(prefs.startClassic) }
    var startCards by remember { mutableStateOf(prefs.startCards) }
    var startDepartures by remember { mutableStateOf(prefs.startDepartures) }
    // The card of the next appointment on the start page and the device calendar behind it.
    val calendarTile = remember { CalendarTile(prefs.startCalendar, hasCalendarPermission(context)) }
    // Says "trip ended" with a way back, on the start page.
    val snackbar = remember { SnackbarHostState() }
    var boardingHints by remember { mutableStateOf(prefs.boardingHints) }
    // False (the default): no selector of transfer levels, searches show normal and relaxed
    // journeys; a tap on "show the hidden ones" widens only the open search (comfortOverride).
    var transferChoice by remember { mutableStateOf(prefs.transferChoice) }
    var comfortOverride by remember { mutableStateOf<TransferComfort?>(null) }
    // The boarding hints read the setting wherever they are drawn.
    LaunchedEffect(boardingHints) { boardingHintsOn.value = boardingHints }
    // True: connection detail and companion ask which connecting departure to take.
    var connectionChoice by remember { mutableStateOf(prefs.connectionChoice) }
    LaunchedEffect(connectionChoice) { connectionChoiceOn.value = connectionChoice }
    // Changes on one platform need less time: the categories ask the dataset of change
    // positions for them, once it is loaded.
    LaunchedEffect(Unit) {
        samePlatformRecord = { arriving, next -> changePositionOf(arriving, next)?.samePlatform == true }
        // Changes between a train and a bus or tram count from the platform to the stop position.
        ridePlatforms = { ride, boarding -> OsmStations.ridePlatforms(ride, boarding) }
        // Changes count by their walk over the walking network of OpenStreetMap where it is known.
        transferWalkSeconds = { arriving, next -> OsmStations.transferWalkSeconds(arriving, next, stepFreeWalks) }
        ChangePositions.load(context)
    }
    // Stop positions, entrances and lifts from OpenStreetMap: for the map and for walks to a "Pos.".
    var osmLoaded by remember { mutableStateOf(OsmStations.loaded) }
    LaunchedEffect(Unit) {
        StationDetailSetting.load(context)
        EntranceSetting.load(context)
        StopNameSetting.load(context)
        MapNoticeSetting.load(context)
        HiddenNotices.load(context)
        OsmStations.load(context)
        // Build the ~10k detail features off the main thread, so the later calls hit the cache.
        prepareStationDetails()
        osmLoaded = true
    }
    // Watches a trip in the mode "Every minute counts" for faster ways, in the app and behind it.
    LaunchedEffect(Unit) { FasterWatch.ensure(context) }
    // True: a search first shows its options on the map; false: it opens the connections at once.
    var planFirst by remember { mutableStateOf(prefs.planFirst) }
    // True: the connections are a list instead of the chart.
    var listView by remember { mutableStateOf(prefs.connectionsList) }
    // True: the connections fill the screen and a picked journey opens on its own page.
    var connectionsFull by remember { mutableStateOf(prefs.connectionsFull) }
    // True: the chart names the stops of the changes between the rides.
    var chartChangeStops by remember { mutableStateOf(prefs.chartChangeStops) }
    var chartTotalTime by remember { mutableStateOf(prefs.chartTotalTime) }
    // The favourite places of the start page, and the one whose place the search is asking for.
    var favoritePlaces by remember { mutableStateOf(prefs.loadFavoritePlaces()) }
    var editingPlace by remember { mutableStateOf<Int?>(null) }
    // The saved places beyond the favourite places, and a place that waits in the dialog to be saved.
    var savedPlaces by remember { mutableStateOf(prefs.loadSavedPlaces()) }
    var placeToSave by remember { mutableStateOf<Stop?>(null) }
    // The last searches as pairs of start and destination, the newest first.
    var recentRoutes by remember { mutableStateOf(prefs.loadRecentRoutes()) }
    var favorites by remember { mutableStateOf(prefs.loadPlaces(AppPrefs.KEY_FAVORITES)) }
    // Favourites of several stops together (see StopGroup), and one that waits in the dialog for its name.
    var stopGroups by remember { mutableStateOf(prefs.loadStopGroups()) }
    var groupToSave by remember { mutableStateOf<StopGroup?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    // The settings were opened for the disruption watch: they scroll to its group once.
    var settingsFocusWatch by remember { mutableStateOf(false) }
    var vehicles by remember { mutableStateOf<List<Vehicle>>(emptyList()) }
    // When the vehicles came in (uptime): from then on they move along the ways they came with.
    var vehiclesSince by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    // True while start and destination are being planned over the open map.
    var planning by remember { mutableStateOf(false) }
    // The journey the trip companion is following, with live data refreshed in place.
    // A trip goes on while other pages are open; it ends only when the user says so. It is
    // shared with the service that keeps it fresh while the app is not on screen.
    val companionSeen = TripState.journey.collectAsState()
    var companion by remember { FlowVar(TripState.journey, companionSeen) }
    // True while the share sheet of the followed trip is open, from "Share" in the sheet of the trip banner.
    var tripShareOpen by remember { mutableStateOf(false) }
    // True while the service keeps the trip fresh and shows its notification.
    val tripServiceRunning by TripState.serviceRunning.collectAsState()
    // The mode "Every minute counts" of the followed trip, and a faster way it found.
    val tripEveryMinute by TripState.everyMinute.collectAsState()
    val tripShortcutAlert by TripState.shortcutAlert.collectAsState()
    val fasterOffer by TripState.faster.collectAsState()
    // True once the notification of a trip was refused: the trip page says so until tapped away.
    var notificationsOff by remember { mutableStateOf(false) }
    // True while the page of the companion is open; its card on the start page opens it again.
    var companionOpen by remember { mutableStateOf(false) }
    // Counts the trips that were started, so each one loads what belongs to it anew.
    var tripSerial by remember { mutableIntStateOf(0) }
    // The whole trips of the rides of the followed journey and the runs feeding its connecting rides.
    val tripCourseCache = remember { TripCourseCache() }
    // True once the user moved the map of a followed trip by hand: it then stays where it
    // was put until the user asks to frame the trip again.
    var tripFrameHeld by remember { mutableStateOf(false) }
    // A walk shown on the big map after a tap on its small map (see WalkFocus): the page it
    // came from slides away, the map frames the walk and follows the own position until the
    // user moves it by hand ([walkFrameHeld]) or goes back.
    var walkFocus by remember { mutableStateOf<WalkFocus?>(null) }
    var walkFrameHeld by remember { mutableStateOf(false) }
    // A walk re-routed from where the traveller left its way (see watchWalk): asked for, then
    // drawn and timed along the new way. [offRoute] watches the walk on screen; [watchedWalk]
    // names it, so another walk starts over.
    var walkDetour by remember { mutableStateOf<WalkDetourState?>(null) }
    val offRoute = remember { OffRouteWatch() }
    val watchedWalk = remember { arrayOfNulls<String>(1) }
    // The picture of the map behind the glass surfaces.
    val backdrop = remember { Backdrop() }
    TrackBackdropTone(backdrop)
    // The next journeys of the tiles, by the id of start and destination.
    val placeTrips = remember { mutableStateMapOf<String, PlaceTrip>() }
    // The journey whose stops on the way could not be loaded.
    var stopsFailedId by remember { mutableStateOf<String?>(null) }
    // The position of the device while a trip is followed; null when the device has none.
    var ownLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    // Counts the times the position of the device was asked for; every count starts a fresh request.
    var locateRound by remember { mutableIntStateOf(0) }
    // True while a request for the position is on its way.
    var locating by remember { mutableStateOf(false) }
    // Why the device has no position the app could use; the pages say so while the fallback is in use.
    var locationTrouble by remember { mutableStateOf(LocationTrouble.None) }
    // Where the device is, as exactly as it last said; null without a position in the Berlin area.
    var devicePosition by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // True while the device moves as fast as a vehicle (see RIDING_SPEED).
    var riding by remember { mutableStateOf(false) }
    // True while the user moves (see MovementWatch): then the map's dot gets a fix every second.
    var ownMoving by remember { mutableStateOf(false) }
    val movementWatch = remember { MovementWatch() }
    // When the device last said where it is, on the clock that runs since it was switched on; 0 for never.
    val lastFix = remember { longArrayOf(0L) }
    // True once the user moved the map or picked a stop by hand: the stops and the map then
    // stay where they were put, until the user asks for the own position again.
    var viewHeld by remember { mutableStateOf(false) }
    // True from picking "current location" as the start without a position until one is there.
    val originPending = remember { StartWait() }
    // Other connections from a transfer stop, while the companion offers them.
    var alternatives by remember { mutableStateOf<Alternatives?>(null) }
    // The connecting departures loaded for changes of the journeys of the open search, and the
    // changes asked for tighter connecting departures.
    val offerState = remember { OfferState() }
    // Journeys whose rest the user swapped at a change, each with the journey it was before.
    val adaptedFrom = remember { mutableStateMapOf<String, Journey>() }
    // The journeys whose stops on the way are being loaded right now.
    val stopsLoading = remember { mutableSetOf<String>() }
    // The trip whose line course is open, and what was loaded for it.
    var courseRequest by remember { mutableStateOf<CourseRequest?>(null) }
    var course by remember { mutableStateOf<TripCourse?>(null) }
    var courseFailed by remember { mutableStateOf(false) }
    // The vehicle of the open course where the provider reports it live, matched by its trip:
    // the course shows it where the map of all vehicles shows it, not where the timetable has it.
    var courseLive by remember { mutableStateOf<LiveVehicle?>(null) }
    // The track of the open course, for its vehicle on the map and in the list.
    val courseTrack = remember(course) { course?.let { TripTrack(it) } }
    val latestCourseTrack by rememberUpdatedState(courseTrack)
    // Where the map looked before a course was opened, where the course came from, and
    // whether it may frame the map.
    val courseFrame = remember { CourseOpenedFrom() }
    // The camera of a course opened from a vehicle: on the vehicle and its next stops, or the whole line.
    val courseCamera = remember { CourseCamera() }
    // Where the "Where to?" card sits on the screen, and the height the search field starts its way from.
    val whereToY = remember { floatArrayOf(Float.NaN) }
    var searchOrigin by remember { mutableStateOf<Float?>(null) }
    // Room the pages leave for the tab bar: its measured height and the gaps around it.
    var navInset by remember { mutableStateOf(98.dp) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // True while the app is on screen. Everything that asks the provider again and again
    // stops when it is not, and picks up where it was when the app comes back.
    // Back on screen, the page first shows what it had (its first frame, which takes touches)
    // and only then starts its refreshes: [appStarted] after the first frame, [appSettled] for
    // the extras (vehicles, tiles, offers, the calendar, the signs of disruptions) a moment
    // later, so their answers and the work on them do not all land on the main thread at once.
    var appStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var appSettled by remember { mutableStateOf(appStarted) }
    val startScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        var release: Job? = null
        val observer = LifecycleEventObserver { owner, _ ->
            val started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!started) {
                release?.cancel()
                release = null
                appStarted = false
                appSettled = false
            } else if (!appStarted && release?.isActive != true) {
                release = startScope.launch {
                    // The frame in the making and the one after it: the page is on screen again.
                    withFrameNanos { }
                    withFrameNanos { }
                    appStarted = true
                    ResumeLog.once("refreshes released")
                    delay(REFRESH_SPREAD_MILLIS)
                    appSettled = true
                    ResumeLog.once("extras released")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            release?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    var savedRoutes by remember { mutableStateOf(prefs.loadRoutes()) }
    // Disruptions on the favourites: the status of a saved route, and all current notices (see
    // Disruptions.kt). The watch of the routes with the bell on runs in the background.
    val disruptionAccess = remember(source) {
        DisruptionAccess(
            // The notices on its rides, and those within the radius of the setting around its
            // start, its end and its changes.
            checkRoute = { route -> routeStatus(source, route, prefs.searchOptionsInUse, DisruptionWatch.radiusMeters(context)) },
            loadAll = {
                val ends = prefs.loadRoutes().flatMap { listOf(it.from, it.to) }.filter { it.kind == PlaceKind.Stop }
                // Kept for the signs of disruptions on the maps too (see MapNoticePool). Away from
                // Berlin the large stations around the user and home are asked too, and the
                // notices of the journeys loaded lately count (see nationwideDisruptions).
                nationwideDisruptions(source, ends.map { it.id }, disruptionAnchors(prefs, source)).also { MapNoticePool.put("all", it) }
            },
            // Around a favourite or saved place: the notices within the radius of the setting.
            checkPlace = { place -> placeDisruptions(source, place, DisruptionWatch.radiusMeters(context)) },
        )
    }
    LaunchedEffect(Unit) { DisruptionWatch.schedule(context) }
    // The remembered connections (start, destination, lines), the one whose journeys the open
    // search shows, and one that waits in the dialog for its name.
    var savedPatterns by remember { mutableStateOf(prefs.loadPatterns()) }
    var patternFilter by remember { mutableStateOf<SavedPattern?>(null) }
    var patternToSave by remember { mutableStateOf<SavedPattern?>(null) }
    // A new destination for the followed trip: how far its search is, and whether the old one stays on the way.
    var retarget by remember { mutableStateOf<RetargetState?>(null) }
    var retargetJob by remember { mutableStateOf<Job?>(null) }
    var tripTargetAppend by remember { mutableStateOf(false) }
    // Pick, view and chart position of the connections screen; every search starts a new one.
    var connectionsState by remember { mutableStateOf(ConnectionsState()) }
    var fitLine by remember { mutableStateOf(prefs.fitLine) }
    // A place the user pinned with a long press on the map.
    var pinned by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // What lies next to the pinned place, once the provider has answered.
    var pinnedNeighbour by remember { mutableStateOf<String?>(null) }
    // The name of a place of the map that was tapped, which then is the pinned place.
    var pinnedPoi by remember { mutableStateOf<String?>(null) }
    // Name and address of the pinned place from the device's geocoder, once it has answered.
    var pinnedDetails by remember { mutableStateOf<PlaceDetails?>(null) }
    // True while the address of a pinned place is looked up: its card shows a shimmering line.
    var pinLookup by remember { mutableStateOf(false) }
    val pinnedFallback = stringResource(R.string.pinned_place)
    val pinnedName = pinnedNeighbour?.let { stringResource(R.string.near_place, it.shortStopName()) }
        ?: pinnedFallback
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    var tab by remember { mutableStateOf(Tab.Route) }
    // The map tab was opened from the departures: back leads to them again, as they were
    // (see showDepartureOnMap, showStationOnMap). Any other tab lets go of it.
    var mapFromDepartures by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != Tab.Map) mapFromDepartures = false }
    // "My routes" was opened from the map tab with a page of its own (all disruptions, from a
    // sign on the map): back from that page leads to the map again, not to the first page of
    // "My routes" and then the route tab. Any other tab, or the tab's own button, lets go of it.
    var favoritesFromMap by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { if (tab != Tab.Favorites) favoritesFromMap = false }
    // Each tab keeps its own pages: the pages opened on "My routes" (all disruptions, those
    // around a place), last on top, and the line course a tab had open when another tab was
    // picked; picking the tab again brings it back (see FloatingNav below).
    val favoritesPages = remember { mutableStateListOf<FavoritesPage>() }
    val courseByTab = remember { mutableStateMapOf<Tab, CourseRequest>() }
    // A tab reached another way than its button starts without the course it had kept.
    LaunchedEffect(tab) { courseByTab.remove(tab) }
    // Filters and scroll position of the departures, which outlast a visit to the map.
    val departuresView = remember { DeparturesView() }
    // What the search of the map tab found last; its pill names it while it is marked.
    var mapSearchPick by remember { mutableStateOf<Stop?>(null) }
    // Where the pill of that search sits on the screen: the search field springs up from there.
    val mapSearchY = remember { floatArrayOf(Float.NaN) }
    // The position of the user (or the fallback); the map starts here.
    var center by remember { mutableStateOf(FALLBACK_CENTER) }
    var usingFallback by remember { mutableStateOf(true) }
    // The search for places puts those near the user first; the fallback point says nothing.
    LaunchedEffect(center, usingFallback) {
        source.searchNear = if (usingFallback) null else center
        // The region of the search is where the user is, or else where the map is.
        source.searchRegion = center
    }
    // Where the map currently looks, and the point the shown stops were loaded for.
    var mapCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var queryCenter by remember { mutableStateOf(FALLBACK_CENTER) }
    var stops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    // Stops found by name on the departures tab: they stay in its list while the app runs.
    var foundStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    // The box the map shows (south, west, north, east) once it rests, and the stations and stop
    // areas of the offline data inside it (see OsmStations.mapStops): the signs cover the whole
    // screen, not only the provider's few stops near the centre.
    var mapBox by remember { mutableStateOf<List<Double>?>(null) }
    var osmMapStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var selectedStopId by remember { mutableStateOf<String?>(null) }
    // Whether the selected stop is marked on the map: its pin highlighted, its card on the map
    // tab. A tap on the bare map lets go of it; picking a stop, or another stop becoming the
    // selected one, marks it again. The stop stays the start of a search and of the departures.
    var stopMarked by remember { mutableStateOf(true) }
    // Another stop becoming the selected one by itself (the stops around a new map position)
    // does not push away the card of a pinned place: only one card shows at a time.
    LaunchedEffect(selectedStopId) { if (pinned == null) stopMarked = true }
    // Only one card at the bottom of the map: what was picked last replaces what was there.
    // A pinned place lets go of the marked stop, a stop marked again lets go of the place.
    LaunchedEffect(pinned) { if (pinned != null) stopMarked = false }
    LaunchedEffect(stopMarked) { if (stopMarked) pinned = null }
    // A stop tapped on the map of the start page: the start sheet shows its card (its
    // departures, from here, to here) until it is let go or another stop becomes the selected one.
    var mapPickedStopId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stopMarked) { if (!stopMarked) mapPickedStopId = null }
    // A long press on a stop sign of the map: its small menu at the point pressed (on the screen).
    var stopMenu by remember { mutableStateOf<Pair<Stop, PointF>?>(null) }
    val stopMenuHaptics = LocalHapticFeedback.current
    // A stop position of the marked stop tapped on the map: its card takes the place of the
    // stop's card; back or its close lead to the stop's card again.
    var pickedPosition by remember { mutableStateOf<MapPositionPick?>(null) }
    LaunchedEffect(selectedStopId, stopMarked) { if (!stopMarked) pickedPosition = null }
    var pickedPositionStop by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedStopId) { if (selectedStopId != pickedPositionStop) pickedPosition = null }
    var departures by remember { mutableStateOf<List<Departure>>(emptyList()) }
    // False until the provider has answered for the selected stop: an empty list then means "none".
    var departuresLoaded by remember { mutableStateOf(false) }
    // Counted up by a pull on the departures, which asks for them again at once; refreshing until they are back.
    var departuresPull by remember { mutableStateOf(0) }
    var departuresRefreshing by remember { mutableStateOf(false) }
    var departuresError by remember { mutableStateOf<String?>(null) }
    // Later pages of the board on the departures tab, each with the time it starts at; they
    // load at the end of the list and are asked for again with the first page.
    var morePages by remember { mutableStateOf<List<Pair<OffsetDateTime, List<Departure>>>>(emptyList()) }
    var moreState by remember { mutableStateOf(MoreDepartures()) }
    // After a page that brought nothing new, the next one starts this late.
    var moreAfter by remember { mutableStateOf<OffsetDateTime?>(null) }
    var moreJob by remember { mutableStateOf<Job?>(null) }
    // "Nearby" on the departures tab: the departures of every stop within the radius, merged.
    var nearbyOn by remember { mutableStateOf(prefs.nearbyDepartures) }
    var nearbyRadius by remember { mutableStateOf(prefs.nearbyRadius) }
    var nearbyDepartures by remember { mutableStateOf<List<Departure>>(emptyList()) }
    var nearbyLoaded by remember { mutableStateOf(false) }
    // "Wann los?" on the departures tab: the list by the latest time to set off on foot.
    var leaveOrderOn by remember { mutableStateOf(prefs.leaveOrder) }
    // More stops on the departures tab beside the selected one ([extraAnchor]), their departures
    // merged with its own. They belong to that selection: another selected stop (a tap on a
    // stop, the nearest one after a long way) lets go of them.
    var extraStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var extraAnchor by remember { mutableStateOf<String?>(null) }
    // The departures of each added stop by its id, and the question they answer (products, time).
    var extraBoards by remember { mutableStateOf<Map<String, List<Departure>>>(emptyMap()) }
    var extraBoardsFor by remember { mutableStateOf<Any?>(null) }
    // The lines of the stop on the card of the map, and the directions of the one tapped.
    var stopLines by remember { mutableStateOf<List<StopLine>>(emptyList()) }
    var lineDirections by remember { mutableStateOf<LineDirections?>(null) }
    var lineJob by remember { mutableStateOf<Job?>(null) }
    // Held as a state object too, so the map can be let go where it is destroyed (see ForgetMapWhenGone).
    val mapState = remember { mutableStateOf<MapLibreMap?>(null) }
    var map by mapState
    var cameraTick by remember { mutableIntStateOf(0) }
    // Goes up whenever a map style has loaded with the app's own layers in it (see installAppLayers).
    var styleTick by remember { mutableIntStateOf(0) }
    // How much the map shows by its zoom; only changes of the tier recompose.
    var tier by remember { mutableStateOf(MapTier.Neighbourhood) }
    // Close enough for the vehicles with the setting "Dynamic" (DYNAMIC_VEHICLE_MIN_ZOOM); only its changes recompose.
    var vehiclesClose by remember { mutableStateOf(true) }
    // The app's own S- and U-Bahn network for the city view, once built or read from the files.
    var rapidNetwork by remember { mutableStateOf<FeatureCollection?>(null) }
    LaunchedEffect(Unit) {
        // A little later than the first requests of the start page, which matter more, and only
        // once the map rests a moment: right after the start it loads its tiles while people
        // already pan it (see MapQuiet).
        MapQuiet.awaitRest(RAPID_NETWORK_DELAY_MILLIS)
        loadRapidNetwork(context, RAPID_LINE_NAMES) { lines ->
            // Made into map features once per network that comes in, off the main thread: the
            // ways are finer since 0.1.2 (see NETWORK_SIMPLIFY_METRES) and finding the lines that
            // share track goes over all of them.
            launch {
                val started = SystemClock.uptimeMillis()
                val features = withContext(Dispatchers.Default) {
                    AllocProbe.measureAny(AllocProbe.RAPID_NETWORK) {
                        rapidNetworkFeatures(lines) { name ->
                            lineColor(name, if (name.startsWith("U")) "subway" else "suburban", LineColors.BERLIN_CENTRE)
                        }
                    }
                }
                DiagLog.i(PERF_LOG_TAG) { "rapid network: ${lines.size} lines, ${lines.sumOf { it.points.size }} points in ${SystemClock.uptimeMillis() - started} ms" }
                rapidNetwork = features
            }
        }
    }

    // The app's own tram network for the overview (see loadTramNetwork), after the S- and U-Bahn.
    var tramNetwork by remember { mutableStateOf<FeatureCollection?>(null) }
    LaunchedEffect(Unit) {
        MapQuiet.awaitRest(TRAM_NETWORK_DELAY_MILLIS)
        loadTramNetwork(context) { lines ->
            launch {
                tramNetwork = withContext(Dispatchers.Default) {
                    AllocProbe.measureAny(AllocProbe.TRAM_NETWORK) { tramNetworkFeatures(lines) }
                }
            }
        }
    }

    // The long-distance railways for the map far out (see LongDistanceLayer.kt), after the trams.
    LongDistanceNetworkLayer(map = map, styleTick = styleTick)

    var searchMode by remember { mutableStateOf<SearchMode?>(null) }
    // A place shared by another app, waiting in the open search for a destination; see AppLinks.
    var sharedSearch by remember { mutableStateOf<SharedSearch?>(null) }
    val sharedSerial = remember { intArrayOf(0) }
    // A start the user picked by hand; null means "the selected nearby stop".
    var origin by remember { mutableStateOf<Stop?>(null) }
    var destination by remember { mutableStateOf<Stop?>(null) }
    // "Every minute counts" for the open search: it belongs to this destination and goes with
    // a trip started from it (see FasterWatch), never to the next search.
    var everyMinute by remember { mutableStateOf(false) }
    // A place the open search has to pass, with the time to stay there; it belongs to this search only.
    var via by remember { mutableStateOf<Via?>(null) }
    // A place picked for the way, waiting in the dialog for the time to stay there.
    var viaPending by remember { mutableStateOf<Via?>(null) }
    // A choice explained once, the first time it is made; null while none waits.
    var firstHint by remember { mutableStateOf<FirstHint?>(null) }
    // The places picked in searches, the newest first; kept on the device.
    var searchHistory by remember { mutableStateOf(prefs.loadSearchHistory()) }

    // A picked place goes to the top of the history, once; the own position is no place to remember.
    fun rememberSearch(place: Stop) {
        if (place.id == CURRENT_LOCATION_ID) return
        val entry = place.copy(distanceMeters = null)
        searchHistory = (listOf(entry) + searchHistory.filter { it.id != entry.id }).take(MAX_SEARCH_HISTORY)
        prefs.saveSearchHistory(searchHistory)
    }

    fun forgetSearch(place: Stop) {
        searchHistory = searchHistory.filter { it.id != place.id }
        prefs.saveSearchHistory(searchHistory)
    }
    var journeys by remember { mutableStateOf<List<Journey>?>(null) }
    // A long-distance train found at HAFAS under another name (#271), or with its live times
    // there (#274, see RideKindCheck): the connections, the board and the followed trip take
    // it in place, without a new request.
    LaunchedEffect(Unit) {
        RideKindFixes.version.drop(1).collect {
            journeys = journeys?.let { list -> list.map { withKnownRuns(it) }.takeIf { fixed -> fixed.indices.any { i -> fixed[i] !== list[i] } } ?: list }
            departures = withKnownBoardRuns(departures)
            val followed = TripState.journey.value
            val renamed = followed?.let { withKnownRuns(it) }
            if (followed != null && renamed !== followed) TripState.journey.compareAndSet(followed, renamed)
        }
    }
    // A track lent to a train after its journey was shown (#280: a foreign feed's copy of a
    // German train, looked up in the background): the connections and the followed trip take it in place.
    LaunchedEffect(Unit) {
        TrackCache.version.drop(1).collect {
            journeys = journeys?.let { list -> list.map { withLentTracks(it) }.takeIf { fixed -> fixed.indices.any { i -> fixed[i] !== list[i] } } ?: list }
            val followed = TripState.journey.value
            val tracked = followed?.let { withLentTracks(it) }
            if (followed != null && tracked !== followed) TripState.journey.compareAndSet(followed, tracked)
        }
    }
    var journeysLoading by remember { mutableStateOf(false) }
    var journeysError by remember { mutableStateOf<String?>(null) }
    // True when asking again for the journeys shown failed: they stay, with a note and a retry (#59).
    var journeysStale by remember { mutableStateOf(false) }
    // True while a search runs again in the background (pull to refresh, "Try again", or the
    // retries of a failed search): what is shown stays until the answer is there.
    var journeysRefreshing by remember { mutableStateOf(false) }
    // Whether the refresh that runs was asked for by hand (pull, "Try again"): only then the
    // pull indicator shows it, not for the retries of a failed search.
    var journeysAsked by remember { mutableStateOf(false) }
    // The pages before and after the loaded connections, and the jobs loading them.
    val paging = remember { JourneyPaging() }
    // The level each loaded journey was found with, by its id: the most relaxed one that found it.
    var journeyLevels by remember { mutableStateOf<Map<String, TransferComfort>>(emptyMap()) }
    // For every connection (by journeyKey) the loosest level whose search found it.
    var foundLevels by remember { mutableStateOf<Map<String, TransferComfort>>(emptyMap()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    // The time the user picked for the journey search and for the departures; null means now.
    var journeyTime by remember { mutableStateOf<TimeChoice?>(null) }
    var departuresTime by remember { mutableStateOf<TimeChoice?>(null) }
    var timeSheet by remember { mutableStateOf<TimeTarget?>(null) }
    // The options of the open search: kinds of transport, Deutschlandticket, bicycle.
    var searchOptionsOpen by remember { mutableStateOf(false) }
    val currentLocationName = stringResource(R.string.current_location)

    // The clock of the pages. It rests with the app and is right again the moment the app is back.
    // Read only where a page needs it (its own lambda, a derived state below): a read in the body
    // of AppRoot would compose all of it again every 15 seconds.
    val nowState = produceState(initialValue = OffsetDateTime.now(), key1 = appStarted) {
        if (!appStarted) return@produceState
        value = OffsetDateTime.now()
        while (true) {
            delay(15_000)
            AllocProbe.tick(AllocProbe.NOW_TICK)
            value = OffsetDateTime.now()
        }
    }
    val now by nowState

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Fine or approximate: either one is enough to ask for the position.
        locationGranted = hasLocationPermission(context)
        if (locationGranted) {
            locateRound++
        } else {
            if (originPending[0] && destination != null) originPending.notFound?.invoke()
            originPending[0] = false
            locationTrouble = LocationTrouble.NoPermission
        }
    }
    // The open journey page comes back after Android ended the process in the background
    // (see OpenJourneyKeeper): shown at once from the kept copy, then loaded fresh by the
    // refreshes of the trip and the connections, which take its refresh handles.
    val restoredTrip = remember {
        val kept = OpenJourneyKeeper.take() ?: return@remember false
        Tab.entries.firstOrNull { it.name == kept.tab }?.let { tab = it }
        origin = kept.origin
        destination = kept.destination
        via = kept.via
        journeyTime = kept.time?.let { TimeChoice(it, arrival = kept.timeArrival) }
        kept.journeys?.let { list ->
            journeys = list
            journeyLevels = levelsOf(list)
            foundLevels = list.mapNotNull { journey -> journey.foundLevel?.let { journeyKey(journey) to it } }.toMap()
            connectionsState = ConnectionsState().apply {
                selectedId = kept.selectedId?.takeIf { id -> list.any { it.id == id } }
                detailPage = kept.detailPage && selectedId != null
            }
            planning = kept.planning
        }
        val followed = kept.followed?.takeIf { companion == null } ?: return@remember false
        companion = followed
        companionOpen = kept.tripOpen
        true
    }
    // A restored trip goes on in the background as it did, where notifications are allowed.
    LaunchedEffect(restoredTrip) {
        if (restoredTrip && companion != null && !tripServiceRunning && tripNotificationsAllowed(context)) {
            notificationsOff = !startTripService(context)
        }
    }
    KeepOpenJourney(
        key = {
            listOf(
                tab, companionOpen, companion?.id, origin?.id, destination?.id, via, journeyTime,
                journeys?.size, journeys?.firstOrNull()?.id, journeys?.lastOrNull()?.id,
                connectionsState.selectedId, connectionsState.detailPage, planning,
            )
        },
        snapshot = {
            KeptPage(
                savedAt = System.currentTimeMillis(),
                tab = tab.name,
                tripOpen = companionOpen,
                followed = companion,
                origin = origin,
                destination = destination,
                via = via,
                time = journeyTime?.time,
                timeArrival = journeyTime?.arrival == true,
                journeys = journeys,
                selectedId = connectionsState.selectedId,
                detailPage = connectionsState.detailPage,
                planning = planning,
            )
        },
    )

    // With the permission the trip goes on in the background with its notification; without
    // it the app keeps the trip fresh only while it is open, and says so.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && companion != null && tripNotificationsAllowed(context)) {
            notificationsOff = !startTripService(context)
        } else if (companion != null) {
            notificationsOff = true
        }
    }

    // The device calendar, for the tile of the next appointment on the start page. Read
    // while that page is in view and never stored.
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        calendarTile.calendarGranted = granted || hasCalendarPermission(context)
        calendarTile.calendarDenied = !calendarTile.calendarGranted
    }

    // Asks the device where it is, or first the user for the permission to ask.
    fun requestPosition() {
        if (hasLocationPermission(context)) {
            locationGranted = true
            locateRound++
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
    // The last known position is empty until some app has asked for one: so this app asks
    // itself, right at its start. Back in front and still without a position, it asks again:
    // what stood in the way, a permission or a switch in the settings, may be gone by now.
    val askedAtStart = remember { booleanArrayOf(false) }
    LaunchedEffect(appStarted) {
        if (!appStarted) return@LaunchedEffect
        val granted = hasLocationPermission(context)
        locationGranted = granted
        if (!askedAtStart[0]) {
            askedAtStart[0] = true
            requestPosition()
        } else if (granted && usingFallback) {
            locateRound++
        }
    }

    // A new position of the user: the stops around it are loaded, and far from the last
    // ones the choice starts over. Not once the user has taken the view in hand.
    LaunchedEffect(center) {
        if (viewHeld) return@LaunchedEffect
        if (distanceMeters(center, queryCenter) > RELOAD_DISTANCE_METERS) selectedStopId = null
        queryCenter = center
    }

    // The user moved the map: once it rests, load the stops around the new centre.
    LaunchedEffect(mapCenter) {
        val moved = mapCenter ?: return@LaunchedEffect
        delay(350)
        if (distanceMeters(moved, queryCenter) > RELOAD_DISTANCE_METERS) queryCenter = moved
    }

    // Leaving the fallback loads them again as well: only then do the stops have distances.
    LaunchedEffect(queryCenter, usingFallback) {
        try {
            val found = source.nearbyStops(queryCenter.first, queryCenter.second, 10).map { stop ->
                val latitude = stop.latitude
                val longitude = stop.longitude
                // Distances count from the user, not from the map centre.
                val distance = if (usingFallback || latitude == null || longitude == null) {
                    null
                } else {
                    distanceMeters(center, latitude to longitude)
                }
                stop.copy(distanceMeters = distance)
            }.let { listed ->
                // The parts of a big station ("S+U Berlin Hauptbahnhof" and its "[Gleis 1-8]")
                // are one stop with the products of all: one sign, one choice, and departures
                // from all of them (see stationGroups, RoutingSource.joinComplex).
                stationGroups(listed, ::osmStationComplex).map { group ->
                    if (group.members.isNotEmpty()) source.joinComplex(group.main.id, group.members.map { it.id })
                    group.sign
                }
            }.let { signs ->
                // Nearest first by the distance the lists show: the provider sorts by its own
                // walking distance from where it was asked (the map centre, maybe), not by that.
                stopsByDistance(signs, if (usingFallback) null else center)
            }
            // The town these stops name is the one their names need not repeat (see LocalTown).
            LocalTown.noteStops(found.map { it.name })
            // Left alone, the choice follows the user: the nearest stop is the selected one.
            // A stop picked by hand stays, also where it is not among the nearest any more.
            val held = viewHeld || found.isEmpty()
            val kept = if (held) stops.firstOrNull { it.id == selectedStopId } else null
            stops = if (kept != null && found.none { it.id == kept.id }) listOf(kept) + found else found
            if (selectedStopId == null || !held) selectedStopId = found.firstOrNull()?.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            departuresError = errorText(context, e)
        }
    }

    // A picked time only counts on the departures tab; the start page and the map stay live.
    // Every round asks for the same picked time again, so the list stays where the user put it.
    val departuresFrom = if (tab == Tab.Departures) departuresTime?.time else null
    // Another stop or another question empties the list; coming back to the app does not.
    LaunchedEffect(selectedStopId) {
        if (selectedStopId != extraAnchor) {
            extraStops = emptyList()
            extraAnchor = null
        }
    }
    LaunchedEffect(selectedStopId, options.products, departuresFrom) {
        departures = emptyList()
        departuresLoaded = false
        moreJob?.cancel()
        morePages = emptyList()
        moreState = MoreDepartures()
        moreAfter = null
    }
    LaunchedEffect(selectedStopId, options.products, departuresFrom, appStarted, departuresPull) {
        val stopId = selectedStopId ?: run {
            departuresRefreshing = false
            return@LaunchedEffect
        }
        if (!appStarted) return@LaunchedEffect
        while (true) {
            // Where the board is: the colour of a line depends on where it runs (see LineColors.rideStyle).
            val boardPlace = (stops.firstOrNull { it.id == stopId } ?: foundStops.firstOrNull { it.id == stopId })?.position
            try {
                departures = source.departures(stopId, DEPARTURE_PAGE, options.products, departuresFrom).placedAt(boardPlace)
                departuresError = null
                departuresLoaded = true
                LiveFreshness.mark(LiveSource.Departures)
                // The later pages the user loaded, with fresh live times as well.
                val pages = morePages
                if (pages.isNotEmpty()) {
                    val fresh = coroutineScope {
                        pages.map { (start, old) ->
                            async {
                                try {
                                    start to source.departures(stopId, DEPARTURE_PAGE, options.products, start).placedAt(boardPlace)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    start to old
                                }
                            }
                        }.awaitAll()
                    }
                    // A page loaded meanwhile would be lost by writing the older list back.
                    if (morePages === pages) morePages = fresh
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                departuresError = errorText(context, e)
            }
            departuresRefreshing = false
            delay(30_000)
        }
    }

    // The departures of all stops within the radius around the user, while the tab shows them:
    // the stops around the position (nearest first), the next departures of each, merged and
    // sorted by time. A run that passes two of the stops is listed once, at the nearer one.
    val nearbyWanted = nearbyOn && tab == Tab.Departures && appStarted && !usingFallback
    // The position in steps of about 100 m: a walk across the street does not start over.
    val nearbyAnchor = (center.first * 1000).toInt() to (center.second * 1000).toInt()
    LaunchedEffect(nearbyWanted, nearbyRadius, options.products, departuresFrom, nearbyAnchor, departuresPull, leaveOrderOn) {
        if (!nearbyWanted) return@LaunchedEffect
        // A pull keeps the list in view while the new one loads.
        if (!departuresRefreshing) {
            nearbyLoaded = false
            nearbyDepartures = emptyList()
        }
        while (true) {
            try {
                val here = center
                val around = source.nearbyStops(here.first, here.second, nearbyStopsAsked(nearbyRadius))
                    .mapNotNull { stop ->
                        val latitude = stop.latitude ?: return@mapNotNull null
                        val longitude = stop.longitude ?: return@mapNotNull null
                        stop.copy(distanceMeters = distanceMeters(here, latitude to longitude))
                    }
                    .filter { (it.distanceMeters ?: Int.MAX_VALUE) <= nearbyRadius && it.kind == PlaceKind.Stop }
                    .sortedBy { it.distanceMeters }
                    .take(nearbyStopsKept(nearbyRadius))
                val lists = coroutineScope {
                    around.map { stop ->
                        async {
                            try {
                                // For "when to leave" each stop from when the user can be there.
                                val from = if (leaveOrderOn) {
                                    leaveDeparturesFrom(leaveStopWalkSeconds(here, stop), departuresFrom, OffsetDateTime.now())
                                } else {
                                    departuresFrom
                                }
                                Result.success(
                                    source.departures(stop.id, NEARBY_DEPARTURES_PER_STOP, options.products, from)
                                        .map { it.copy(stop = stop.copy(distanceMeters = null)) },
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Result.failure(e)
                            }
                        }
                    }.awaitAll()
                }
                // Every stop failed (no network, the sources down): that is no empty board (#196).
                failureOfAll(lists)?.let { throw it }
                val seenRuns = HashSet<String>()
                nearbyDepartures = lists.flatMap { it.getOrDefault(emptyList()) }
                    .filter { departure -> departure.tripId == null || seenRuns.add(departure.tripId) }
                    .let { inDepartureOrder(it) }
                departuresError = null
                nearbyLoaded = true
                LiveFreshness.mark(LiveSource.Departures)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                departuresError = errorText(context, e)
                nearbyLoaded = true
            }
            departuresRefreshing = false
            delay(30_000)
        }
    }

    // The departures tab: the first page and the later ones in one list, each ride once.
    val departureBoard = remember(departures, morePages) {
        if (morePages.isEmpty()) departures else mergeDepartures(listOf(departures) + morePages.map { it.second })
    }

    // The stops added to the selected one, while they belong to it and "nearby" is off.
    val nearbyShown = nearbyOn && !usingFallback
    val addedStops = if (extraAnchor == selectedStopId && !nearbyShown) extraStops else emptyList()
    // Their departures, one request per stop and round, while the tab shows them.
    val extraWanted = addedStops.isNotEmpty() && tab == Tab.Departures && appStarted
    // For "when to leave" the added stops are asked from when the user can be there (see leaveDeparturesFrom).
    val extraLeaveFrom = leaveOrderOn && !usingFallback
    LaunchedEffect(extraWanted, addedStops, options.products, departuresFrom, departuresPull, extraLeaveFrom) {
        if (!extraWanted) return@LaunchedEffect
        val question = listOf(options.products, departuresFrom, extraLeaveFrom)
        // Another question: the old answers go; a stop added or removed keeps the others'.
        extraBoards = if (extraBoardsFor == question) {
            extraBoards.filterKeys { id -> addedStops.any { it.id == id } }
        } else {
            emptyMap()
        }
        extraBoardsFor = question
        while (true) {
            val asked = addedStops
            val lists = coroutineScope {
                asked.map { stop ->
                    async {
                        try {
                            val from = if (extraLeaveFrom) {
                                leaveDeparturesFrom(leaveStopWalkSeconds(center, stop), departuresFrom, OffsetDateTime.now())
                            } else {
                                departuresFrom
                            }
                            stop.id to source.departures(stop.id, DEPARTURE_PAGE, options.products, from).placedAt(stop.position)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // One stop that fails keeps what it had; the others still show.
                            stop.id to extraBoards[stop.id]
                        }
                    }
                }.awaitAll()
            }
            extraBoards = lists.mapNotNull { (id, list) -> list?.let { id to it } }.toMap()
            delay(30_000)
        }
    }
    // The board of the selected stop with those added: by time, each row naming its stop.
    val selectedForBoard = stops.firstOrNull { it.id == selectedStopId } ?: foundStops.firstOrNull { it.id == selectedStopId }
    // For "when to leave" the boards end by the time to set off, each stop with its own walk.
    val leaveHere = if (extraLeaveFrom) center else null
    val shownBoard = remember(departureBoard, departures, extraBoards, addedStops, selectedForBoard, leaveHere) {
        if (addedStops.isEmpty() || selectedForBoard == null) {
            departureBoard
        } else {
            mergeStopBoards(
                listOf(StopDepartures(selectedForBoard, departures, departures.size >= DEPARTURE_PAGE)) +
                    addedStops.mapNotNull { stop ->
                        extraBoards[stop.id]?.let { list -> StopDepartures(stop, list, list.size >= DEPARTURE_PAGE) }
                    },
                walkSeconds = { stop -> leaveHere?.let { leaveStopWalkSeconds(it, stop) } },
            )
        }
    }

    // The next page of the board, from the planned time of its last departure on (or an hour
    // after a page that brought nothing new). It is kept only for the stop and question it
    // was asked for.
    fun loadMoreDepartures() {
        val stopId = selectedStopId ?: return
        if (moreJob?.isActive == true || !departuresLoaded) return
        val last = nextDeparturesStart(departureBoard) ?: return
        val after = moreAfter
        val start = if (after != null && after.isAfter(last)) after else last
        val products = options.products
        val from = departuresFrom
        fun stillAsked() = selectedStopId == stopId && options.products == products &&
            (if (tab == Tab.Departures) departuresTime?.time else null) == from
        moreState = MoreDepartures(loading = true)
        moreJob = scope.launch {
            try {
                val boardPlace = (stops.firstOrNull { it.id == stopId } ?: foundStops.firstOrNull { it.id == stopId })?.position
                val page = source.departures(stopId, DEPARTURE_PAGE, products, start).placedAt(boardPlace)
                if (!stillAsked()) return@launch
                val before = mergeDepartures(listOf(departures) + morePages.map { it.second })
                val pages = morePages + (start to page)
                val grown = mergeDepartures(listOf(departures) + pages.map { it.second }).size > before.size
                if (grown) {
                    morePages = pages
                    moreAfter = null
                    moreState = MoreDepartures()
                } else {
                    moreAfter = start.plusMinutes(MORE_DEPARTURES_SKIP_MINUTES)
                    moreState = MoreDepartures(none = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (stillAsked()) moreState = MoreDepartures(failed = true)
            }
        }
    }

    val selectedStop = stops.firstOrNull { it.id == selectedStopId } ?: foundStops.firstOrNull { it.id == selectedStopId }
    val startPlace = origin ?: selectedStop
    // The lines of the stop whose card the map shows; asked for once per stop.
    val cardStopId = if (stopMarked) selectedStopId else null
    LaunchedEffect(cardStopId) {
        lineJob?.cancel()
        lineDirections = null
        stopLines = emptyList()
        val id = cardStopId ?: return@LaunchedEffect
        try {
            stopLines = source.stopLines(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The lines are an extra: the card works without them.
        }
    }
    val showingConnections = journeys != null || journeysLoading || journeysError != null
    val tripOpen = companion != null && companionOpen
    // The course of a line lies above whatever page it was opened from.
    val courseView = courseRequest != null
    val onStart = tab == Tab.Route && searchMode == null && !showingConnections && !settingsOpen && !tripOpen &&
        !courseView
    // The search of the map tab lies over the map like every search: meanwhile the map is not "the map tab".
    val onMapTab = tab == Tab.Map && !courseView && searchMode == null

    // What covers the map, in pixels of the screen: [0] the top edge of the start page's
    // sheet or cards, [1] the height of the map, [2] the top edge of the stop card on the
    // map tab, [3] the system navigation at the bottom: the map reaches down under it, the pages
    // and their tab bar end above it (systemBarsPadding). Read when the map centres itself, so
    // it never makes the page compose again.
    val mapCover = remember { floatArrayOf(Float.NaN, Float.NaN, Float.NaN, 0f) }
    mapCover[3] = WindowInsets.navigationBars.getBottom(LocalDensity.current).toFloat()
    // The top edge of the start page's sheet as state, for the quiet logo on the plain ground
    // without the map (StartLogoMark); read only while placing, so it costs no composition.
    val startLogoCover = remember { mutableStateOf(Float.NaN) }
    // What the page in view covers of the map at its top and bottom, measured (see LocalMapCover).
    // A third entry: the right edge of a side panel over the left of the map (landscape).
    val frameCover = remember { floatArrayOf(Float.NaN, Float.NaN, Float.NaN) }
    // Set once the lower edge of the visible map is known, so a first fix taken before is
    // centred again.
    var coverKnown by remember { mutableStateOf(false) }
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)

    // The point to put the camera on so [position] shows in the middle of the part of the map
    // that is not covered: below the chips on top, above the sheet or card at the bottom.
    // [zoom] is the zoom the camera goes to; null keeps the current one.
    fun visibleTarget(position: Pair<Double, Double>, zoom: Double?): LatLng {
        val target = LatLng(position.first, position.second)
        val readyMap = map ?: return target
        val height = mapCover[1]
        val coverTop = when {
            onStart -> mapCover[0]
            onMapTab && stopMarked && selectedStop != null -> mapCover[2]
            // The top of the tab bar.
            else -> height - mapCover[3] - (navInset - SHEET_NAV_GAP).value * density
        }
        if (height.isNaN() || coverTop.isNaN() || coverTop > height) return target
        val visibleTop = statusTop + MAP_TOP_COVER_DP * density
        if (coverTop <= visibleTop + 40f * density) return target
        val shift = height / 2f - (visibleTop + coverTop) / 2f
        if (abs(shift) < 1f) return target
        val camera = readyMap.cameraPosition
        // A map not placed yet goes to the zoom of the start.
        val currentZoom = if (camera.zoom < 3.0) START_ZOOM else camera.zoom
        // Metres per screen pixel at the zoom the camera goes to, worked out directly: the map
        // has 512 dp tiles, so the world is 512 * 2^zoom dp wide; dp to pixels by the density.
        // (The projection's own figure counts per dp, not per pixel, which put the position
        // far too high on screens with a high density.)
        val metersPerPixel = EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(position.first)) /
            (512.0 * 2.0.pow(zoom ?: currentZoom) * density)
        // The camera goes that far towards the lower edge of the screen, whatever way the map is turned.
        val meters = shift * metersPerPixel
        val bearing = Math.toRadians(camera.bearing)
        val north = -meters * cos(bearing)
        val east = -meters * sin(bearing)
        return LatLng(
            position.first + north / METERS_PER_DEGREE,
            position.second + east / (METERS_PER_DEGREE * cos(Math.toRadians(position.first))),
        )
    }

    // The edge of the sheet or card the map last went along with (see followCover). What moves
    // the map right now: [0] a finger, [1] a camera animation of the app, [2] the move that
    // goes along with the sheet itself.
    val coverFollowed = remember { floatArrayOf(Float.NaN) }
    val mapGesture = remember { booleanArrayOf(false, false, false) }

    // How far the middle of the visible map lies above the middle of the screen with the
    // cover at [coverTop], in pixels; held at the edges so the camera goes along without a jump.
    fun coverShift(coverTop: Float): Float {
        val height = mapCover[1]
        if (height.isNaN() || coverTop.isNaN()) return Float.NaN
        val visibleTop = statusTop + MAP_TOP_COVER_DP * density
        val edge = coverTop.coerceIn(visibleTop + 40f * density, maxOf(height, visibleTop + 40f * density))
        return height / 2f - (visibleTop + edge) / 2f
    }

    // While the sheet or card at the bottom is dragged (or settles), the camera goes along frame
    // by frame, so what is in the middle of the visible map (the own position while it is
    // followed, else the place the user looks at) stays there. Not while a finger moves the map.
    fun followCover(coverTop: Float) {
        val before = coverFollowed[0]
        coverFollowed[0] = coverTop
        val readyMap = map ?: return
        // A finger on the map or a camera on its way (to a stop, to the position) is not overruled;
        // a walk on the map frames itself while its page is away.
        if (before.isNaN() || mapGesture[0] || mapGesture[1] || walkFocus != null) return
        // How far the middle of the visible map rose, in pixels (negative: it went down).
        val move = coverShift(coverTop) - coverShift(before)
        if (move.isNaN() || abs(move) < 0.5f) return
        // What lies [move] pixels below the camera target now comes to the target, so the map
        // content rises by [move] together with the middle of the visible map. A jump with
        // moveCamera, not scrollBy: scrollBy goes straight to the native map and tells no
        // listener, so the markers (cameraTick), the backdrop of the glass and the idle
        // callback that clears mapGesture would not hear of the move. moveCamera reports the
        // start (as an animation of the app, kept apart from others by mapGesture[2]), the
        // move and the idle, and does not animate. The listeners are called a little later
        // from the main looper, so mapGesture[2] stays set until the idle callback clears it.
        val projection = readyMap.projection
        val target = readyMap.cameraPosition.target ?: return
        val at = projection.toScreenLocation(target)
        val next = projection.fromScreenLocation(PointF(at.x, at.y + move))
        if (next.latitude.isNaN() || next.longitude.isNaN()) return
        mapGesture[2] = true
        readyMap.moveCameraSafely(CameraUpdateFactory.newLatLng(next))
    }

    // Where the map rests while it follows the user: the position in the middle of what is in view.
    // The camera follows every position the device reports, not only the steps of [center]
    // (which moves on after POSITION_SHIFT_METERS, for the stops around): else it stood still
    // between those steps and then jumped, which looked like a frozen position on the way.
    val followPosition = if (usingFallback) center else devicePosition ?: center
    var followTarget by remember { mutableStateOf(center) }
    LaunchedEffect(followPosition, map, coverKnown) {
        val shifted = visibleTarget(followPosition, null)
        followTarget = shifted.latitude to shifted.longitude
    }

    // The page of the companion is in view: the map behind it shows the trip.
    val tripView = tab == Tab.Route && searchMode == null && !settingsOpen && tripOpen && !courseView
    val planView = tab == Tab.Route && searchMode == null && !settingsOpen && showingConnections && planning &&
        !tripOpen && !courseView
    // With the map switched off for the start page, it only runs on the pages that are about it.
    val walkView = walkFocus != null
    val mapShown = startMap || onMapTab || (planView && planMap) || tripView || courseView || walkView
    // A map made while the view is held (a stop card, a pinned place) is not placed by
    // MapBackground and faced the whole world; it goes to that stop or place at once (#228).
    LaunchedEffect(map, viewHeld) {
        val readyMap = map ?: return@LaunchedEffect
        val place = mapStartCamera(
            zoom = readyMap.cameraPosition.zoom,
            follow = !(planView || tripView || courseView || walkView) && !viewHeld,
            framesItself = planView || tripView || courseView || walkView,
            markedStop = selectedStop?.position?.takeIf { stopMarked },
            pinned = pinned,
            own = followPosition,
        ) ?: return@LaunchedEffect
        val zoom = if (place.atStop) STOP_ZOOM else START_ZOOM
        readyMap.moveCameraSafely(CameraUpdateFactory.newLatLngZoom(visibleTarget(place.latitude to place.longitude, zoom), zoom))
    }
    val pageState = PageState(courseView, searchMode, tab, settingsOpen, tripOpen, showingConnections, planning, mapFromDepartures, favoritesFromMap)
    val screen = pageOf(pageState)
    // The jank meter of the developer options counts frames per screen (see JankMeter).
    JankScreenTag(if (screen == Screen.Start && onMapTab) "Map" else screen.name)
    // A change of page starts a run of the meter's transitions (see JankTransitions.kt).
    JankMeter.noteScreen(screen.name)
    // The pages follow [screen] through a seekable transition, so a back gesture can show the
    // page before while it goes on (see AppBackHandler below): [backFrom] is the page
    // the gesture started on while it runs, [backProgress] how far it went (0 to 1), from the
    // left edge or the right ([backFromLeft]); [backSwipe] picks the transition for it.
    val screenSeek = remember { SeekableTransitionState(screen) }
    val pageTransition = rememberTransition(screenSeek, label = "screen")
    JankTransitionProbe(pageTransition)
    var backFrom by remember { mutableStateOf<Screen?>(null) }
    var backSwipe by remember { mutableStateOf(false) }
    var backFromLeft by remember { mutableStateOf(true) }
    val backProgress = remember { Animatable(0f) }
    // The picture of the map the page a gesture leaves takes along (see the handler below);
    // where the pages lie on the screen and how large the map is, to lay it where the map is.
    var backShot by remember { mutableStateOf<ImageBitmap?>(null) }
    // The rest of what it takes along, and how it goes (see BackStage).
    val backStage = remember { BackStage() }
    backStage.marks = rememberGraphicsLayer()
    backStage.backdrop = backdrop
    backStage.progress = backProgress
    backStage.seek = screenSeek
    // Only for the back log of the performance lab, and let go with AppRoot.
    if (BackLog.on) {
        DisposableEffect(backStage) {
            BackProbe.stage = backStage
            onDispose { if (BackProbe.stage === backStage) BackProbe.stage = null }
        }
    }
    val pagesOrigin = remember { floatArrayOf(0f, 0f) }
    // The page that comes in after its first frame (see PageEntrance): while it fades in, it
    // brings its fog along and the fog under all pages waits for it.
    val pageEntering = remember { arrayOfNulls<PageEntrance>(1) }
    // The room below the pages to the bottom of the window (the navigation bar), in pixels (#194).
    val pagesBelow = remember { floatArrayOf(0f) }
    val mapSize = remember { intArrayOf(0, 0) }
    // Counts the back gestures: the end of one (its last animation) only clears the state when
    // no newer gesture has started in the meantime.
    val backGestures = remember { intArrayOf(0) }
    fun endBack() {
        backFrom = null
        backSwipe = false
        backShot = null
        backStage.marksShot = null
        backStage.mapStays = false
        backStage.mapCard = false
        backStage.plainAfter = false
        backStage.plainBefore = false
        backStage.fogBefore = false
        backStage.fogAfter = false
        scope.launch {
            backProgress.snapTo(0f)
            backStage.fade.snapTo(1f)
            backStage.shots.snapTo(1f)
        }
    }
    // Every other change of the page animates as it always did; also the page a back gesture
    // ended on, should it not be the one it showed.
    LaunchedEffect(screen, backFrom == null) {
        if (backFrom == null) screenSeek.animateTo(screen)
    }
    // A new page reports its own covers of the map; those of the page before no longer count.
    remember(screen) {
        frameCover.fill(Float.NaN)
        MapEdges.changed()
        // The map goes along with a sheet only while that page stays.
        coverFollowed[0] = Float.NaN
        screen
    }
    // What the pages say while the fallback is in use: that the position is still asked
    // for, or which of the reasons it is that there is none.
    val locationNote = when {
        !usingFallback -> null
        locating -> stringResource(R.string.location_searching)
        else -> when (locationTrouble) {
            LocationTrouble.None -> null
            LocationTrouble.NoPermission -> stringResource(R.string.location_no_permission)
            LocationTrouble.SwitchedOff -> stringResource(R.string.location_off)
            LocationTrouble.Outside -> stringResource(R.string.location_fallback)
            LocationTrouble.NoFix -> stringResource(R.string.location_unavailable)
        }
    }
    // The journeys the planning view offers, and the one of them that is in front. The pick
    // is the same one the connections show, so it is kept from one page to the other. They
    // come from the journeys the chosen level leaves visible, as in the chart and the list.
    // The tightest level the search shows: the chosen one, or without the setting normal.
    // "Every minute counts" takes Sprint changes for this trip, without changing the setting.
    val shownComfort = when {
        everyMinute -> TransferComfort.VeryRisky
        transferChoice -> options.comfort
        else -> comfortOverride ?: TransferComfort.Normal
    }
    // A remembered connection shows only the journeys that ride its lines, while one of them
    // leaves within the window; otherwise the normal ones, with a note.
    // Worked out again only when what they come from changes: AppRoot composes again for a pick
    // in the chart (just as the journey page grows out of it) and for much else, and rating every
    // journey anew each time cost the first frames of the opening (see the page lists below).
    val selectionRating = ratingSettingsShown.intValue
    // Only while a page that shows them is in front or coming (the connections, the plan):
    // a search kept behind the map tab was rated anew with every tick of the clock and every
    // pack of OpenStreetMap that came in, on the main thread, though nothing showed it.
    val listsShown = journeyListsShown(screen) || journeyListsShown(pageTransition.currentState) ||
        journeyListsShown(pageTransition.targetState)
    // The clock of the lists: read (and AppRoot composed again by it) only while they show;
    // hidden, they keep the time they were last shown with.
    val listClock = remember { arrayOf(OffsetDateTime.now()) }
    val listNow = if (listsShown) now.also { listClock[0] = it } else listClock[0]
    val patternFrom = (journeyTime?.takeIf { !it.arrival }?.time ?: listNow).minusMinutes(1)
    val visiblePattern = rememberWhileShown(listsShown, journeys, patternFilter?.lines, patternFrom) {
        AllocProbe.measure(AllocProbe.PAGE_LISTS) { patternView(journeys, patternFilter?.lines, patternFrom) }
    }
    val visibleSelection = rememberWhileShown(listsShown, journeys, shownComfort, journeyLevels, selectionRating, listNow) {
        AllocProbe.measure(AllocProbe.PAGE_LISTS) { journeySelection(journeys, shownComfort, journeyLevels) }
    }
    val visibleJourneys = visiblePattern?.takeIf { it.running }?.matching ?: visibleSelection.shown
    val planShown = planWindow(visibleJourneys.orEmpty(), journeyTime, connectionsState.selectedId, listNow)
    val planPicked = planShown.firstOrNull { it.id == connectionsState.selectedId }
        ?: defaultJourney(planShown, journeyTime, listNow)
    val planIds by rememberUpdatedState(planShown.map { it.id })

    // The room the page leaves free on the map, as padding in pixels on top and at the bottom:
    // measured from the cards over the map where they reported themselves, else [topDp] and
    // [bottomDp]. [margin] keeps what is framed clear of their edges.
    fun framePadding(topDp: Int, bottomDp: Int, margin: Float = 20f): Pair<Int, Int> {
        val height = mapCover[1]
        val coverTop = frameCover[0]
        val coverBottom = frameCover[1]
        var top = topDp * density
        var bottom = bottomDp * density
        if (!coverTop.isNaN() && coverTop > 0f && (height.isNaN() || coverTop < height)) top = coverTop + margin * density
        if (!coverBottom.isNaN() && !height.isNaN() && coverBottom > 0f && coverBottom < height) {
            bottom = height - coverBottom + margin * density
        }
        // The floating "centre on me" button stands above the bottom cover (#165): what is framed stays clear of it.
        if (MapEdges.locateShown) bottom += LOCATE_FRAME_ROOM_DP * density
        // What is left must still show something; else the room as designed.
        if (!height.isNaN() && height - top - bottom < 120f * density) {
            top = topDp * density
            bottom = bottomDp * density
        }
        return top.roundToInt() to bottom.roundToInt()
    }

    // Moves the map so the points fit between the cards on top and at the bottom.
    fun fitCamera(points: List<Pair<Double, Double>>, topDp: Int, bottomDp: Int) {
        val readyMap = map ?: return
        if (points.size < 2) return
        try {
            val bounds = LatLngBounds.Builder()
            points.forEach { bounds.include(LatLng(it.first, it.second)) }
            val side = (40 * density).roundToInt()
            val (top, bottom) = framePadding(topDp, bottomDp)
            // A panel at the side of the map (landscape) keeps the points to its right.
            val panel = frameCover[2]
            val start = if (!panel.isNaN() && panel > 0f) (panel + 20f * density).roundToInt() else side
            readyMap.animateCameraScaled(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), start, top, side, bottom),
            )
        } catch (e: Exception) {
            // Points without a real extent: leave the map where it is.
        }
    }

    // Moves the map gently so the points of a followed trip fit between the banner on top and
    // the sheet below, never closer than a street view.
    fun frameTrip(points: List<Pair<Double, Double>>) {
        val readyMap = map ?: return
        if (points.isEmpty()) return
        try {
            val first = points.first()
            val update = if (points.all { it == first }) {
                CameraUpdateFactory.newLatLngZoom(LatLng(first.first, first.second), TRIP_FRAME_MAX_ZOOM)
            } else {
                val bounds = LatLngBounds.Builder()
                points.forEach { bounds.include(LatLng(it.first, it.second)) }
                val side = (48 * density).roundToInt()
                val (top, bottom) = framePadding(230, 340, margin = 28f)
                val fitted = readyMap.getCameraForLatLngBounds(
                    bounds.build(),
                    intArrayOf(side, top, side, bottom),
                ) ?: return
                // A frame worked out for no room has no zoom (NaN): the map stays where it is (#284).
                if (!fitted.zoom.isFinite()) return
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(fitted).zoom(fitted.zoom.coerceAtMost(TRIP_FRAME_MAX_ZOOM)).build(),
                )
            }
            // A map just made faces the whole world: put on the trip at once, not flown in from space (#122).
            if (freshCamera(readyMap.cameraPosition.zoom)) readyMap.moveCameraSafely(update) else readyMap.animateCameraScaled(update, TRIP_FRAME_MILLIS)
        } catch (e: Exception) {
            // Points without a real extent: leave the map where it is.
        }
    }

    // Centres the map on [own] in the middle of what the page leaves visible (#273): below what
    // covers its top (the blue box, its chips), above the sheet, right of a side panel, as
    // measured right now; zoomed so [ahead] (the next stop) stays in view where it is near,
    // never closer than [maxZoom]. The shift lies in the target and the camera's padding is
    // cleared, so no padding left by an earlier framing puts the position elsewhere.
    fun centreOnTraveller(own: Pair<Double, Double>, ahead: Pair<Double, Double>?, maxZoom: Double, millis: Int) {
        val readyMap = map ?: return
        val camera = readyMap.cameraPosition
        val width = readyMap.width
        val height = readyMap.height
        val room = visibleRoom(
            width = width,
            height = height,
            topCover = frameCover[0] + CENTRE_COVER_GAP_DP * density,
            bottomCover = frameCover[1] - CENTRE_COVER_GAP_DP * density,
            startCover = frameCover[2],
            minHeight = CENTRE_MIN_ROOM_DP * density,
        )
        val update = if (room == null) {
            CameraUpdateFactory.newLatLngZoom(LatLng(own.first, own.second), maxZoom)
        } else {
            val zoom = centreZoom(own, ahead, room, density, camera.bearing, CENTRE_MARGIN_DP * density, CENTRE_MIN_ZOOM, maxZoom)
            val target = centreTarget(own, room, width, height, zoom, density, camera.bearing)
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(target.first, target.second))
                    .zoom(zoom)
                    .bearing(camera.bearing)
                    .tilt(camera.tilt)
                    .padding(0.0, 0.0, 0.0, 0.0)
                    .build(),
            )
        }
        if (freshCamera(camera.zoom)) readyMap.moveCameraSafely(update) else readyMap.animateCameraScaled(update, millis)
    }

    // The map looks at the surroundings of the user again, with the stops there.
    fun recenterMap() {
        map?.animateCameraScaled(CameraUpdateFactory.newLatLngZoom(visibleTarget(center, START_ZOOM), START_ZOOM))
        queryCenter = center
    }

    // Puts away what belongs to one followed trip: the offer of other connections and the
    // trips of its vehicles.
    fun clearTripExtras() {
        alternatives = null
        tripCourseCache.tripCourses.clear()
        tripCourseCache.tripTracks.clear()
        tripCourseCache.feederCourses.clear()
        tripCourseCache.feederTracks.clear()
        tripCourseCache.feederTried.clear()
    }

    fun endTrip() {
        // The same end as from the notification (statistics, mode, shortcut alert, the trip).
        endFollowedTrip(context)
        clearTripExtras()
        companionOpen = false
        recenterMap()
    }

    // The trip goes on in the background with its notification. That needs a permission of
    // its own from Android 13 on; without notifications the service is not started at all.
    fun followInBackground() {
        notificationsOff = false
        when {
            tripNotificationsAllowed(context) -> notificationsOff = !startTripService(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED ->
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> notificationsOff = true
        }
    }

    // Ends the trip from its banner on the start page, with "Undo" for a few seconds: the same
    // journey is followed again (its service and notification come back with it).
    fun endTripWithUndo() {
        val ended = companion ?: return
        val minuteCounts = TripState.everyMinute.value
        val alertOn = TripState.shortcutAlert.value
        endTrip()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.trip_ended),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && companion == null) {
                TripState.everyMinute.value = minuteCounts
                TripState.shortcutAlert.value = alertOn
                companion = ended
                followInBackground()
            }
        }
    }

    fun startTrip(journey: Journey) {
        // The connections the journey was picked from: the shortcut alert takes them as known.
        val pickedFrom = journeys?.takeIf { list -> list.any { it.id == journey.id } }
        clearTripExtras()
        tripFrameHeld = false
        tripSerial++
        // The mode of the search goes with the trip; what was put aside on the last trip is forgotten.
        FasterWatch.reset(context, journey, pickedFrom)
        TripState.everyMinute.value = everyMinute
        // The shortcut alert by the setting, and always with "Every minute counts".
        TripState.shortcutAlert.value = everyMinute || prefs.shortcutAlert
        companion = journey
        companionOpen = true
        followInBackground()
    }

    fun closeConnections() {
        patternFilter = null
        // Only after a journey was on the map; a map the user moved on the start page stays put.
        if (showingConnections) recenterMap()
        searchJob?.cancel()
        paging.laterJob?.cancel()
        paging.earlierJob?.cancel()
        offerState.offersJob?.cancel()
        offerState.tightJob?.cancel()
        offerState.tightAsked.clear()
        offerState.feederJob?.cancel()
        offerState.feederAsked.clear()
        offerState.transferOffers.clear()
        adaptedFrom.clear()
        paging.loadingLater = false
        paging.loadingEarlier = false
        paging.laterGen++
        paging.earlierGen++
        journeys = null
        LiveFreshness.clear(LiveSource.Journeys)
        journeysLoading = false
        journeysError = null
        paging.earlierRefs = emptyMap()
        paging.laterRefs = emptyMap()
        journeyLevels = emptyMap()
        foundLevels = emptyMap()
        destination = null
        everyMinute = false
        via = null
        // The next search from the start page leaves now again, and from where the user is
        // when its start came back with a page of an earlier run (see startAfterClose).
        journeyTime = null
        origin = OpenJourneyKeeper.startClosed(origin)
    }

    // The levels of transfer comfort a search asks for: all of them, so the chosen level only
    // filters (see journeySelection) and the journeys of a tighter level can be offered.
    // Levels that ask with the same change time (risky and very risky) are asked once.
    fun searchLevels(): List<TransferComfort> = TransferComfort.entries.distinctBy { it.minutes }

    // What the journeys of a search gathered: changes asked about, offers, adapted journeys.
    fun forgetJourneyExtras() {
        offerState.tightAsked.clear()
        offerState.feederAsked.clear()
        offerState.transferOffers.clear()
        adaptedFrom.clear()
    }

    // [keepView] stays on the page the search was started from instead of opening the first page of a search.
    // [quiet] asks again for the search that is shown (see refreshJourneys): the journeys, the
    // pick and a message stay until the answer is there, and a failure only shows where no
    // journey is left to show.
    fun searchJourneys(keepView: Boolean = false, quiet: Boolean = false) {
        val from = origin ?: selectedStop ?: return
        val to = destination ?: return
        searchJob?.cancel()
        paging.laterJob?.cancel()
        paging.earlierJob?.cancel()
        offerState.offersJob?.cancel()
        offerState.tightJob?.cancel()
        offerState.feederJob?.cancel()
        // A refresh keeps what belongs to the journeys shown until new ones are there.
        if (!quiet) forgetJourneyExtras()
        paging.loadingLater = false
        paging.loadingEarlier = false
        paging.laterGen++
        paging.earlierGen++
        if (!quiet) {
            journeys = null
            LiveFreshness.clear(LiveSource.Journeys)
            // A trip that is followed goes on; only its page makes room for the new search.
            companionOpen = false
            stopsFailedId = null
            connectionsState = ConnectionsState()
            // With the map step switched off, a search opens its connections at once.
            if (!keepView) planning = planFirst
            // The pair is kept, so the same search can be started again from the start page.
            val pair = SavedRoute(from.copy(distanceMeters = null), to.copy(distanceMeters = null))
            recentRoutes = (listOf(pair) + recentRoutes.filter { it.id != pair.id }).take(MAX_RECENT_ROUTES)
            prefs.saveRecentRoutes(recentRoutes)
            journeysError = null
            journeysStale = false
            journeyLevels = emptyMap()
            foundLevels = emptyMap()
            comfortOverride = null
            ViaDayNotice.clear()
            journeysLoading = true
            paging.earlierRefs = emptyMap()
            paging.laterRefs = emptyMap()
        }
        journeysRefreshing = quiet
        val time = journeyTime
        val levels = searchLevels()
        val askedVia = via
        searchJob = scope.launch {
            val pages = fetchLevels(levels) { level ->
                source.journeys(
                    from = from,
                    to = to,
                    options = options.copy(comfort = level, via = askedVia),
                    departure = time?.takeIf { !it.arrival }?.time,
                    arrival = time?.takeIf { it.arrival }?.time,
                )
            }
            val found = pages.filter { it.page != null }
            if (found.isNotEmpty()) {
                // An answer clears the message of a search that failed before, whichever way it was asked again.
                journeysError = null
                journeysStale = false
                if (quiet) forgetJourneyExtras()
                foundLevels = withFoundLevels(emptyMap(), found)
                journeys = withFound(mergeSearchPages(emptyList(), found.flatMap { it.page!!.journeys }, OffsetDateTime.now()), foundLevels)
                journeyLevels = levelsOf(journeys)
                // Through a place on the way with nothing soon: the list says so (#35a).
                ViaDayNotice.note(askedVia, time?.time, time?.arrival == true, journeys)
                LiveFreshness.mark(LiveSource.Journeys)
                paging.earlierRefs = found.mapNotNull { part -> part.page!!.earlierRef?.let { part.level to it } }.toMap()
                paging.laterRefs = found.mapNotNull { part -> part.page!!.laterRef?.let { part.level to it } }.toMap()
            } else {
                // The provider found no journey at all, e.g. to a place away from any way it knows:
                // that is an empty list with its own message, not a failed request.
                val failure = pages.mapNotNull { it.error }.firstOrNull { !(it is HttpStatusException && it.nothingFound) }
                if (failure == null) {
                    journeysError = null
                    journeysStale = false
                    journeys = emptyList()
                } else if (!quiet || journeys.isNullOrEmpty()) {
                    // A refresh that fails keeps the journeys it was asked over; with none to show it says why.
                    journeysError = errorText(context, failure)
                } else {
                    // The journeys asked over stay, with a note that they could not be updated (#59).
                    journeysStale = true
                }
            }
            journeysLoading = false
            journeysRefreshing = false
        }
        // Cancelled by another search: that one sets both flags again.
        searchJob?.invokeOnCompletion { if (it != null && searchJob?.isActive != true) journeysRefreshing = false }
    }

    // Asks again for the search that is shown, in the background: a pull to refresh, "Try again"
    // ([asked]), or a retry of a failed search. One that already runs is waited for, not started
    // anew. Asked for by hand, the backends that failed lately are tried again at once instead of
    // resting a while (see SourceHealth): the user asks because something may have come back.
    fun refreshJourneys(asked: Boolean): Job? {
        // Off screen nothing is asked again; the retries go on once the app is back.
        if (!appStarted) return null
        if (journeysRefreshing && searchJob?.isActive == true) {
            if (asked) journeysAsked = true
            return searchJob
        }
        if (journeysLoading) return searchJob
        if (asked) SourceHealth.shared.reset()
        journeysAsked = asked
        searchJourneys(keepView = true, quiet = true)
        return searchJob
    }

    fun loadLater() {
        val from = origin ?: selectedStop ?: return
        val to = destination ?: return
        if (paging.loadingLater) return
        val references = paging.laterRefs
        // Nothing left to ask for: the chart says so at its edge instead of doing nothing.
        if (references.isEmpty()) {
            paging.laterResult = paging.result(PageOutcome.End)
            return
        }
        paging.loadingLater = true
        val gen = ++paging.laterGen
        paging.laterJob = scope.launch {
            try {
                val pages = fetchLevels(references.keys) { level ->
                    source.laterJourneys(from, to, options.copy(comfort = level, via = via), references.getValue(level))
                }
                // Keep what is shown when nothing came; a level that failed keeps its reference and is asked again.
                val found = pages.filter { it.page != null }
                var added = 0
                if (found.isNotEmpty()) {
                    val before = journeys.orEmpty().map { it.id }
                    foundLevels = withFoundLevels(foundLevels, found)
                    journeys = withFound(mergeSearchPages(journeys.orEmpty(), found.flatMap { it.page!!.journeys }, OffsetDateTime.now()), foundLevels)
                    journeyLevels = levelsOf(journeys)
                    added = addedCount(before, journeys.orEmpty().map { it.id })
                    paging.laterRefs = paging.laterRefs - found.map { it.level }.toSet() +
                        found.mapNotNull { part -> part.page!!.laterRef?.let { part.level to it } }
                }
                paging.laterResult = paging.result(pageOutcome(found.size, failedLevels(pages), added, references, paging.laterRefs))
            } finally {
                // Also when it failed in a way nobody caught or was cancelled: never a flag that blocks the edge for good.
                if (paging.laterGen == gen) paging.loadingLater = false
            }
        }
    }

    fun loadEarlier() {
        val from = origin ?: selectedStop ?: return
        val to = destination ?: return
        if (paging.loadingEarlier) return
        val references = paging.earlierRefs
        if (references.isEmpty()) {
            paging.earlierResult = paging.result(PageOutcome.End)
            return
        }
        paging.loadingEarlier = true
        val gen = ++paging.earlierGen
        paging.earlierJob = scope.launch {
            try {
                val pages = fetchLevels(references.keys) { level ->
                    source.earlierJourneys(from, to, options.copy(comfort = level, via = via), references.getValue(level))
                }
                val found = pages.filter { it.page != null }
                var added = 0
                if (found.isNotEmpty()) {
                    val before = journeys.orEmpty().map { it.id }
                    foundLevels = withFoundLevels(foundLevels, found)
                    // The journeys shown win over the same ones on the new page (see mergeSearchPages).
                    journeys = withFound(mergeSearchPages(journeys.orEmpty(), found.flatMap { it.page!!.journeys }, OffsetDateTime.now()), foundLevels)
                    journeyLevels = levelsOf(journeys)
                    added = addedCount(before, journeys.orEmpty().map { it.id })
                    paging.earlierRefs = paging.earlierRefs - found.map { it.level }.toSet() +
                        found.mapNotNull { part -> part.page!!.earlierRef?.let { part.level to it } }
                }
                paging.earlierResult = paging.result(pageOutcome(found.size, failedLevels(pages), added, references, paging.earlierRefs))
            } finally {
                if (paging.earlierGen == gen) paging.loadingEarlier = false
            }
        }
    }

    // Puts fresh live data in the place of a loaded journey. The ways are not loaded again,
    // so the known ones are kept, and so are stops on the way that the fresh data leaves out.
    // A walk keeps its way with the target and exit it was led to, as one (see WalkAccess.keptWay).
    // A pick follows its journey should the id have changed. False when nothing was replaced.
    fun replaceJourney(old: Journey, fresh: Journey): Boolean {
        val list = journeys ?: return false
        if (fresh.legs.size != old.legs.size || list.none { it.id == old.id }) return false
        val updated = Journey(
            legs = fresh.legs.mapIndexed { index, leg ->
                val known = old.legs[index]
                WalkAccess.keptWay(leg, known).copy(stopovers = leg.stopovers.ifEmpty { known.stopovers })
            },
            refreshToken = fresh.refreshToken ?: old.refreshToken,
            split = old.split,
            via = old.via,
            foundLevel = old.foundLevel,
            // The other change points it offers stay on offer.
            altChanges = old.altChanges,
        )
        if (updated.id != old.id && list.any { it.id == updated.id }) return false
        // New live times can move it past its neighbours: the list keeps its order (see searchOrder),
        // and the views glide the journey to its new place.
        // The rides it shares with other journeys take its fresh live times too: one ride, one set
        // of times and one rating of its changes in every journey (see shareLiveTimes).
        journeys = shareLiveTimes(list.map { if (it.id == old.id) updated else it }, fresher = listOf(updated)).sortedWith(searchOrder)
        // The category of the journey follows it too.
        journeyLevels = levelsOf(journeys)
        if (connectionsState.selectedId == old.id) connectionsState.selectedId = updated.id
        // The way back from a swapped connection follows its journey too.
        if (updated.id != old.id) adaptedFrom.remove(old.id)?.let { adaptedFrom[updated.id] = it }
        return true
    }

    // The stops on the way of all rides of a journey: the search leaves them out, so they
    // are fetched when the user unfolds a ride. A request that is on its way is not sent a
    // second time.
    fun loadStops(journey: Journey) {
        if (!journey.refreshable) {
            stopsFailedId = journey.id
            return
        }
        if (!stopsLoading.add(journey.id)) return
        stopsFailedId = null
        scope.launch {
            try {
                val fresh = source.refreshed(journey, stopovers = true, polylines = false)
                if (fresh == null || !replaceJourney(journey, fresh)) stopsFailedId = journey.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stopsFailedId = journey.id
            } finally {
                stopsLoading.remove(journey.id)
            }
        }
    }

    // A missed ride or a change of plan on the way: the destination of the followed trip
    // again, now from the given place. The trip so far ends with it.
    fun searchFromStop(place: Stop) {
        // The search the trip came from may be closed by now: the trip itself knows where it leads.
        val end = companion?.legs?.lastOrNull()?.toPlace ?: destination ?: return
        // The new search keeps the mode of the trip it replaces, and its place on the way while that lies ahead.
        companion?.let { followed ->
            everyMinute = TripState.everyMinute.value
            via = replanPoint(followed, OffsetDateTime.now())?.let { point -> viaAhead(followed, point.kept) }
        }
        clearTripExtras()
        TripState.everyMinute.value = false
        TripState.shortcutAlert.value = false
        companion = null
        companionOpen = false
        origin = place.copy(distanceMeters = null)
        destination = end
        // The traveller stands there now, whatever time the first search was for.
        journeyTime = null
        searchJourneys()
    }

    // The start of an open search is the selected stop unless one was picked. Before the
    // selected stop follows the user to another place, that start is pinned.
    fun pinStart() {
        if (origin == null && (journeys != null || journeysLoading || journeysError != null)) {
            origin = stops.firstOrNull { it.id == selectedStopId }
        }
    }

    fun currentPlace(position: Pair<Double, Double>): Stop = Stop(
        id = CURRENT_LOCATION_ID,
        name = currentLocationName,
        latitude = position.first,
        longitude = position.second,
        distanceMeters = null,
        kind = PlaceKind.Address,
    )

    // Takes a position of the device as the place of the user. Only a position that a data
    // source in use covers counts: outside of them the app stays at the fallback and says so. True when
    // the place of the user changed. This runs long after the page was drawn, so it only
    // reads what is kept as state.
    fun usePosition(position: Pair<Double, Double>): Boolean {
        val inside = prefs.sourceChoice.covers(position.first, position.second)
        lastFix[0] = SystemClock.elapsedRealtime()
        locationTrouble = if (inside) LocationTrouble.None else LocationTrouble.Outside
        devicePosition = if (inside) position else null
        if (inside) io.github.jnbp.nextstop.ui.companion.RecentOwnPosition.update(position, lastFix[0])
        if (!inside && usingFallback) return false
        // A few steps are not a new place.
        if (inside && !usingFallback && distanceMeters(center, position) <= POSITION_SHIFT_METERS) return false
        // The start of an open search would otherwise follow the stop that gets selected at the new place.
        pinStart()
        usingFallback = !inside
        center = if (inside) position else FALLBACK_CENTER
        return inside
    }

    // The notice "Finding your location …" goes once the start is there.
    fun endLocatingNotice() {
        val shown = snackbar.currentSnackbarData ?: return
        if (shown.visuals.message == context.getString(R.string.start_locating)) shown.dismiss()
    }

    // "Current location" as the start: a position that was waited for becomes the start, and
    // with [moved] one that turns out to lie elsewhere runs the open search again from there.
    fun startFromPosition(moved: Boolean) {
        if (usingFallback) return
        if (originPending[0]) {
            originPending[0] = false
            origin = currentPlace(center)
            endLocatingNotice()
            if (destination != null) searchJourneys()
        } else if (moved && origin?.id == CURRENT_LOCATION_ID) {
            origin = currentPlace(center)
            val searchOpen = journeys != null || journeysLoading || journeysError != null
            if (searchOpen && destination != null) searchJourneys(keepView = true)
        }
    }

    // A new plan from where the traveller is: the position of the device, or without one
    // the stop the companion names.
    fun replanFromHere(fallback: Stop?) {
        val position = ownLocation ?: devicePosition ?: readLocation(context, prefs.sourceChoice)
        val place = if (position == null) fallback else currentPlace(position)
        if (place != null) searchFromStop(place)
    }

    // A bicycle option of the search options (see SearchOptionsSheet): stored as the default of
    // the next searches too (one source with the settings), and searches again.
    fun searchWithTickets(changed: SearchOptions) {
        options = changed
        prefs.bikeDefault = changed.bike
        prefs.bikeComboDefault = changed.bikeCombo
        searchJourneys(keepView = true)
    }

    fun updateOptions(changed: SearchOptions) {
        // The first pick of very risky changes says what they are.
        if (changed.comfort == TransferComfort.VeryRisky && options.comfort != TransferComfort.VeryRisky &&
            !prefs.veryRiskyHintSeen
        ) {
            prefs.veryRiskyHintSeen = true
            firstHint = FirstHint.VeryRisky
        }
        options = changed
        prefs.searchOptions = changed
        // The journeys on the tiles were found with the old options.
        placeTrips.clear()
    }

    // A chip of a ticket option above the connections switched off, and kept off for the next
    // searches: the Deutschlandticket is in the products and stored with them, the bicycle
    // options are stored as their defaults.
    fun ticketOff(filter: TicketFilter) {
        val changed = withoutTicketFilter(options, filter)
        if (filter == TicketFilter.LocalOnly) {
            updateOptions(changed)
            searchJourneys(keepView = true)
        } else {
            searchWithTickets(changed)
        }
    }

    // "Every minute counts" on a followed trip: Sprint changes and the shortcut alert with it;
    // switched off, the alert goes back to the setting.
    fun setTripEveryMinute(on: Boolean) {
        TripState.everyMinute.value = on
        TripState.shortcutAlert.value = on || prefs.shortcutAlert
    }

    // The first time the mode "Every minute counts" is switched on, it explains itself.
    fun everyMinuteHint(on: Boolean) {
        if (on && !prefs.everyMinuteHintSeen) {
            prefs.everyMinuteHintSeen = true
            firstHint = FirstHint.EveryMinute
        }
    }

    // A new choice of how tight changes may be is kept. Every search loads all levels, so
    // the choice only changes which of the loaded journeys are shown. The connections stay on
    // the connection they showed (see ConnectionsScreen); no toast or snackbar: the pills show
    // the level, and picking another is as quick as an "Undo" would be.
    fun pickComfort(comfort: TransferComfort) {
        if (comfort == options.comfort) return
        updateOptions(options.copy(comfort = comfort))
    }

    // A picked time closes the sheet. An open search runs again with it and stays on its page;
    // the departures load again by themselves.
    fun applyTime(choice: TimeChoice?) {
        when (timeSheet) {
            TimeTarget.Journey -> {
                journeyTime = choice
                if (showingConnections) searchJourneys(keepView = true)
            }

            TimeTarget.Departures -> departuresTime = choice
            null -> Unit
        }
        timeSheet = null
    }

    // The companion follows the trip so far plus the picked connection from the transfer stop on.
    fun switchRest(option: Journey) {
        val followed = companion ?: return
        val offer = alternatives ?: return
        companion = spliceJourney(followed, offer.keepLegs, option)
    }

    // The place on the way a new destination keeps: with "go on to" the old destination.
    fun retargetVia(followed: Journey, append: Boolean): Via? =
        if (append) followed.legs.last().toPlace?.let { Via(it.copy(distanceMeters = null), 0) } else null

    // A new destination for the followed trip, from where the traveller is now: on board from
    // each of the next stops of the vehicle, so the best place to get off is found; waiting at
    // the stop of the next ride; walking from the position of the device. The best way shows
    // in a card to confirm.
    fun startRetarget(target: Stop, append: Boolean) {
        val followed = companion ?: return
        val moment = OffsetDateTime.now()
        val riding = followed.legs.firstOrNull { !it.walking && !moment.isBefore(it.departure) && moment.isBefore(it.arrival) }
        var points = retargetPoints(followed, moment, riding?.tripId?.let { tripCourseCache.tripCourses[it] })
        val position = ownLocation ?: devicePosition
        val single = points.singleOrNull()
        if (position != null && single != null && single.stage.startsWith("walk")) {
            points = listOf(single.copy(place = currentPlace(position), time = moment))
        }
        retargetJob?.cancel()
        if (points.isEmpty()) {
            retarget = RetargetState.Failed(target, append)
            return
        }
        retarget = RetargetState.Searching(target, append)
        val searchOptions = prefs.searchOptionsInUse.copy(via = retargetVia(followed, append))
        retargetJob = scope.launch {
            val choice = searchRetarget(source, points, target, searchOptions)
            retarget = if (choice == null) {
                RetargetState.Failed(target, append)
            } else {
                RetargetState.Found(target, append, choice, retargeted(followed, choice), followed.id)
            }
        }
    }

    // Takes the found way: the trip goes on with it, the ride in progress stays as it is.
    fun applyRetarget(found: RetargetState.Found) {
        retarget = null
        val followed = companion ?: return
        // A trip changed in another way meanwhile is not overwritten.
        if (followed.id != found.basedOn) return
        FasterWatch.reset(context)
        companion = found.journey
        refreshTripNotification(context)
    }

    // "Other options": the normal search from where the new way would start to the new destination.
    fun retargetOthers(state: RetargetState) {
        val followed = companion
        retargetJob?.cancel()
        retarget = null
        val moment = OffsetDateTime.now()
        val point = (state as? RetargetState.Found)?.choice?.point
            ?: followed?.let { journey ->
                retargetPoints(journey, moment, null).firstOrNull()
            }
        val position = ownLocation ?: devicePosition
        origin = point?.place ?: position?.let { currentPlace(it) } ?: return
        via = followed?.let { retargetVia(it, state.append) }
        journeyTime = point?.time?.takeIf { it.isAfter(moment.plusMinutes(1)) }?.let { TimeChoice(it) }
        companionOpen = false
        destination = state.target
        searchJourneys()
    }

    // The connecting departures at the changes of a journey of the open search, one request
    // per change that has none yet, or with [again] per change: fresh live data can make
    // another departure the one to catch. What was loaded before stays on show until the
    // answer is there. Asking for another journey ends what is still on its way.
    fun loadTransferOffers(journey: Journey, again: Boolean) {
        if (!appStarted) return
        val to = journey.legs.last().toPlace ?: return
        val missing = journeyChanges(journey).filter { again || offerKey(journey.id, it.keepLegs) !in offerState.transferOffers }
        offerState.offersJob?.cancel()
        if (missing.isEmpty()) return
        offerState.offersJob = scope.launch {
            for (change in missing) {
                try {
                    val page = source.journeys(
                        from = change.stop.copy(distanceMeters = null),
                        to = to,
                        // A place on the way still ahead stays part of the way on.
                        options = options.copy(via = viaAhead(journey, journey.legs.take(change.keepLegs))),
                        results = OFFER_RESULTS,
                        departure = offerStart(change.arriving),
                    )
                    // The arrival by the plausible times: an estimate where the prognosis is not.
                    val arriving = change.arriving.plausible()
                    offerState.transferOffers[offerKey(journey.id, change.keepLegs)] = Alternatives(
                        stopId = change.stop.id,
                        stopName = change.stop.name,
                        keepLegs = change.keepLegs,
                        arrival = arriving.arrival,
                        arrivalEstimated = arriving.arrivalEstimated,
                        loading = false,
                        options = page.journeys.distinctBy { it.id }.sortedBy { it.departure },
                        current = Journey(legs = journey.legs.drop(change.keepLegs)),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Without an answer the change keeps what it offered before, or offers no choice.
                }
                delay(JOURNEY_REFRESH_PAUSE_MILLIS)
            }
        }
    }

    // The journeys of the chosen level that no search of the provider returns: the provider
    // keeps its own minimum change time at a station whatever change time is asked for, so a
    // change the app rates risky is often one it never offers (see TightJourneys.kt). For every
    // change of the journeys found (once per station and arriving ride) the connecting
    // departures are asked for from that station, as the connection chooser does, and those
    // that leave earlier and get there earlier are added. Journeys made that way are not asked
    // about again. Runs until no change is left to ask about, also for pages loaded later.
    fun loadTighterJourneys() {
        if (!appStarted || offerState.tightJob?.isActive == true) return
        val to = destination ?: return
        offerState.tightJob = scope.launch {
            while (true) {
                val list = journeys ?: break
                val requests = tightRequests(list.filter { it.split == null }, offerState.tightAsked)
                if (requests.isEmpty()) break
                for (request in requests) {
                    offerState.tightAsked += request.key
                    try {
                        val (sample, keep) = request.journeys.first()
                        val page = source.journeys(
                            from = request.stop.copy(distanceMeters = null),
                            to = to,
                            options = options.copy(comfort = TransferComfort.Risky, via = viaAhead(sample, sample.legs.take(keep))),
                            results = OFFER_RESULTS,
                            departure = offerStart(request.arriving),
                        )
                        val added = request.journeys.flatMap { (journey, legs) -> tighterAlternatives(journey, legs, page.journeys) }
                        val current = journeys
                        if (current != null && added.isNotEmpty()) {
                            val merged = withTighterJourneys(current, added)
                            if (merged !== current) {
                                journeys = merged
                                journeyLevels = levelsOf(merged)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Without an answer the journeys of the provider stay as they are.
                    }
                    delay(JOURNEY_REFRESH_PAUSE_MILLIS)
                }
            }
        }
    }

    // A tight level asks for the journeys the provider leaves out, for every page that comes in.
    LaunchedEffect(journeys, shownComfort) {
        if (journeys != null && shownComfort.ordinal >= TransferComfort.Risky.ordinal) loadTighterJourneys()
    }

    // #272: long-distance trains that only their local feeder (or last mile) makes tighter than
    // the chosen level get the same trains with a feeder at that level: an arrive-by search to
    // the station of the first train, or a search from the station of the last one (see
    // LongDistanceFeeders.kt). Only while the app is in the foreground, a few searches per
    // search at most (LONG_FEEDER_SEARCH_CAP), each train and level once; the journeys found
    // join the list in place, and the tighter original stays for the tighter levels.
    fun loadFeederJourneys() {
        if (!appStarted || offerState.feederJob?.isActive == true) return
        val from = origin ?: selectedStop ?: return
        val to = destination ?: return
        offerState.feederJob = scope.launch {
            while (true) {
                // The level chosen now: another chip while this runs is taken up on the next round.
                val level = shownComfort
                val list = journeys ?: break
                val left = LONG_FEEDER_SEARCH_CAP - offerState.feederAsked.size
                val requests = longFeederRequests(list, level, offerState.feederAsked, left)
                if (requests.isEmpty()) break
                for (request in requests) {
                    offerState.feederAsked += request.key
                    try {
                        val station = request.station.copy(distanceMeters = null)
                        val page = if (request.end == LongFeederEnd.First) {
                            source.journeys(from = from, to = station, options = options.copy(comfort = level, via = null), results = LONG_FEEDER_RESULTS, arrival = request.time)
                        } else {
                            source.journeys(from = station, to = to, options = options.copy(comfort = level, via = null), results = LONG_FEEDER_RESULTS, departure = request.time)
                        }
                        val added = longFeederVariants(request, page.journeys, level)
                        val current = journeys
                        if (current != null && added.isNotEmpty()) {
                            val merged = withTighterJourneys(current, added)
                            if (merged !== current) {
                                journeys = merged
                                journeyLevels = levelsOf(merged)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Without an answer the journeys stay as they are.
                    }
                    delay(JOURNEY_REFRESH_PAUSE_MILLIS)
                }
            }
        }
    }

    LaunchedEffect(journeys, shownComfort) {
        if (journeys != null && shownComfort != TransferComfort.VeryRisky) loadFeederJourneys()
    }

    // Swaps the rest of a journey of the open search, from one of its changes on, for another
    // connecting departure. The journey it was is kept, so the swap can be taken back.
    fun adaptJourney(journey: Journey, keepLegs: Int, option: Journey) {
        val list = journeys ?: return
        if (list.none { it.id == journey.id }) return
        // The same rides changed at another stop: that variant as a whole (see switchToAltChange).
        val switching = keepLegs == ALT_CHANGE_SWITCH
        val changed = if (switching) option else spliceJourney(journey, keepLegs, option)
        if (changed.id == journey.id) return
        if (list.any { it.id == changed.id }) {
            // The search found this very journey as well: it is picked instead of being made twice.
            connectionsState.selectedId = changed.id
            return
        }
        // Another rest arrives at another time, which can change its place among equal departures.
        journeys = list.map { if (it.id == journey.id) changed else it }.sortedWith(searchOrder)
        journeyLevels = levelsOf(journeys)
        if (!switching) {
            adaptedFrom[changed.id] = adaptedFrom.remove(journey.id) ?: journey
            // The choice at this change stays on offer, now with the other departure picked.
            offerState.transferOffers[offerKey(journey.id, keepLegs)]?.let { offerState.transferOffers[offerKey(changed.id, keepLegs)] = it }
        }
        if (stopsFailedId == journey.id) stopsFailedId = null
        connectionsState.selectedId = changed.id
    }

    // Brings back the journey as the search found it.
    fun undoAdapt(journey: Journey) {
        val list = journeys ?: return
        val before = adaptedFrom.remove(journey.id) ?: return
        journeys = if (list.any { it.id == before.id }) {
            list.filter { it.id != journey.id }
        } else {
            list.map { if (it.id == journey.id) before else it }.sortedWith(searchOrder)
        }
        journeyLevels = levelsOf(journeys)
        connectionsState.selectedId = before.id
    }

    // Opens the course of one trip above the page that is open, the same way from every
    // page and for a departure, a ride or a vehicle on the map. What is known of the trip
    // already shows at once; its stops and its way follow when they are loaded. With [fit]
    // the map moves to the whole way.
    fun openCourse(
        tripId: String?,
        line: String?,
        product: String,
        direction: String?,
        fromStop: String?,
        fit: Boolean = true,
        color: Int? = null,
        textColor: Int? = null,
        at: Pair<Double, Double>? = null,
        fromVehicle: Boolean = false,
    ) {
        if (tripId == null) return
        pinned = null
        if (courseRequest == null) {
            courseFrame.courseReturn[0] = map?.cameraPosition
            courseFrame.courseStart[0] = center
            // The row it was opened from, if any, for the course to grow out of.
            courseFrame.courseOrigin[0] = OpenOrigin.take()
        }
        courseFrame.courseFit[0] = fit
        courseRequest = CourseRequest(tripId, line, product, direction, fromStop, color, textColor, at, fromVehicle)
    }
    // A long press on a ride of the new companion views asks for its course (CourseRequests).
    val courseAsked by CourseRequests.leg.collectAsState()
    LaunchedEffect(courseAsked) {
        val leg = courseAsked ?: return@LaunchedEffect
        CourseRequests.leg.value = null
        openCourse(leg.tripId, leg.line, leg.product, leg.direction, leg.from, color = leg.providerColor, textColor = leg.providerTextColor, at = leg.ridePlace)
    }

    // Closes the course; the map goes back to where it looked before. Where it would have
    // followed the user to another place in the meantime, it goes there instead.
    fun closeCourse() {
        if (courseRequest == null) return
        courseRequest = null
        val before = courseFrame.courseStart[0]
        if (!viewHeld && before != null && before != center) {
            recenterMap()
        } else {
            courseFrame.courseReturn[0]?.let { map?.animateCameraScaled(CameraUpdateFactory.newCameraPosition(it)) }
        }
        courseFrame.courseReturn[0] = null
        courseFrame.courseStart[0] = null
    }

    // Back from a line course: where the map tab was opened from the departures, straight
    // back to them (see showDepartureOnMap).
    fun leaveCourse() {
        val toDepartures = mapFromDepartures && tab == Tab.Map
        closeCourse()
        if (toDepartures) tab = Tab.Departures
    }

    // "Show on map" of a departure: the map tab with the course of its trip, its vehicle live
    // where the map or the provider knows it (else where the timetable has it) and the stop it
    // leaves from marked on the way, as a tapped vehicle shows. From the departures, back leads there.
    fun showDepartureOnMap(departure: Departure, stop: Stop?) {
        val tripId = departure.tripId ?: return
        mapFromDepartures = tab == Tab.Departures || (tab == Tab.Map && mapFromDepartures)
        settingsOpen = false
        tab = Tab.Map
        vehicles.firstOrNull { it.tripId == tripId }?.let { courseLive = LiveVehicle(it, vehiclesSince) }
        // The whole way is framed: the map may look anywhere when it comes from the list.
        openCourse(
            tripId, departure.line, departure.product, departure.direction, stop?.name, fit = true,
            color = departure.providerColor, textColor = departure.providerTextColor, at = departure.ridePlace ?: stop?.position,
        )
    }

    // "Station on map" on the departures: the map tab with the selected stop marked (its ring,
    // stop positions and lines, as on the start page) and the camera on it. Back leads to the
    // departures as they were.
    fun showStationOnMap() {
        val stop = selectedStop ?: return
        mapFromDepartures = true
        settingsOpen = false
        pinned = null
        viewHeld = true
        stopMarked = true
        tab = Tab.Map
        val position = stop.position ?: return
        map?.animateCameraScaled(CameraUpdateFactory.newLatLngZoom(LatLng(position.first, position.second), STOP_ZOOM))
    }

    // What the menu of a departure leads to. Departures are always those of the selected stop.
    fun departureAction(departure: Departure, action: DepartureAction) {
        // A departure of the list of all stops nearby knows its own stop.
        val stop = departure.stop ?: selectedStop
        when (action) {
            DepartureAction.Course ->
                openCourse(
                    departure.tripId, departure.line, departure.product, departure.direction, stop?.name,
                    color = departure.providerColor, textColor = departure.providerTextColor, at = departure.ridePlace ?: stop?.position,
                )

            DepartureAction.ShowOnMap -> showDepartureOnMap(departure, stop)

            DepartureAction.FromHere -> if (stop != null) {
                settingsOpen = false
                tab = Tab.Route
                origin = stop.copy(distanceMeters = null)
                // With a destination the search runs at once; without one the app asks for it.
                if (destination != null) searchJourneys() else searchMode = SearchMode.Destination
            }

            DepartureAction.ToDestination -> departure.destination?.let { target ->
                settingsOpen = false
                tab = Tab.Route
                journeyTime = null
                destination = target
                rememberSearch(target)
                searchMode = null
                searchJourneys()
            }

            DepartureAction.Favorite -> if (stop != null) {
                favorites = if (favorites.any { it.id == stop.id }) {
                    favorites.filter { it.id != stop.id }
                } else {
                    favorites + stop.copy(distanceMeters = null)
                }
                prefs.savePlaces(AppPrefs.KEY_FAVORITES, favorites)
            }
        }
    }

    // Frames a walk on the big map (see focusWalk): its way, the stop position or entrance it
    // leads to, and the own position where it is near the walk, between the "Back" on top and
    // the card at the bottom. The pages are away meanwhile, so their covers do not count.
    // [own] is given only while the map follows the traveller on the walk (see walkFollowsOwn, #276).
    fun frameWalk(focus: WalkFocus, own: Pair<Double, Double>?) {
        val readyMap = map ?: return
        val points = (walkWay(focus.walk) + listOfNotNull(own)).distinct()
        if (points.isEmpty()) return
        try {
            val update = if (points.size == 1) {
                CameraUpdateFactory.newLatLngZoom(LatLng(points[0].first, points[0].second), WALK_ZOOM)
            } else {
                val bounds = LatLngBounds.Builder()
                points.forEach { bounds.include(LatLng(it.first, it.second)) }
                val side = (40 * density).roundToInt()
                val top = (statusTop + WALK_FRAME_TOP_DP * density).roundToInt()
                val bottom = (WALK_FRAME_BOTTOM_DP * density).roundToInt()
                CameraUpdateFactory.newLatLngBounds(bounds.build(), side, top, side, bottom)
            }
            readyMap.animateCameraScaled(update, WALK_FRAME_MILLIS)
        } catch (e: Exception) {
            // Points without a real extent: leave the map where it is.
        }
    }

    // Shows a walk on the big map with the own position: the page it was opened from (journey
    // detail or companion, given as [journey]'s page) slides away, "Back" brings it again.
    fun focusWalk(leg: Leg, journey: Journey?) {
        walkFrameHeld = false
        walkFocus = WalkFocus(leg, journey)
    }

    // No walk to watch (any more): its new way is forgotten and the map draws its own again.
    fun endWalkWatch() {
        if (watchedWalk[0] == null && walkDetour == null) return
        watchedWalk[0] = null
        walkDetour = null
        offRoute.reset()
        FootPaths.clearDetour()
    }

    // A live position while a walk is on screen: the walk on the big map, else the walk of the
    // followed trip right now. Off its way for long enough (see OffRouteWatch), a new way on
    // foot is asked for from here to where the walk leads, before every other way. Once a new
    // way is drawn, its remaining length gives the minutes still to walk.
    fun watchWalk(location: Location) {
        val now = OffsetDateTime.now()
        val followed = companion
        val focus = walkFocus
        val index: Int?
        val leg: Leg
        if (focus != null) {
            leg = focus.walk
            index = followed?.legs?.indexOfFirst { it == focus.walk }?.takeIf { it >= 0 }
        } else if (followed != null && tripView) {
            index = activeWalkIndex(followed, now) ?: return endWalkWatch()
            leg = followed.legs[index]
        } else {
            return endWalkWatch()
        }
        val target = leg.walkTarget?.position ?: leg.path.lastOrNull() ?: leg.toPlace?.position ?: return
        val key = "${leg.from}>${leg.to}|${leg.path.firstOrNull()}|${leg.path.lastOrNull()}"
        if (watchedWalk[0] != key) {
            endWalkWatch()
            watchedWalk[0] = key
        }
        // Only a real way says where the traveller should be: a straight line is no way to leave.
        if (FootPaths.status(leg) != FootWay.Real) {
            FootPaths.wantWalk(leg)
            return
        }
        val way = FootPaths.pathFor(leg).let { if (it.lastOrNull() == target) it else it + target }
        val position = location.latitude to location.longitude
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val off = offRoute.update(way, position, accuracy, location.elapsedRealtimeNanos / 1_000_000L)
        val state = walkDetour
        val fix = offRoute.lastFix
        if (state != null && state.path != null && !state.pending && fix != null) {
            walkDetour = state.copy(remainingMeters = fix.remainingMeters, at = now)
        }
        if (off) {
            FootPaths.wantWay(position, target)
            walkDetour = WalkDetourState(
                key = key,
                legIndex = index,
                leg = leg,
                from = position,
                to = target,
                pending = true,
                path = state?.path,
                remainingMeters = state?.remainingMeters,
                at = state?.at ?: now,
            )
        }
    }

    fun closeWalk() {
        if (walkFocus == null) return
        walkFocus = null
        walkFrameHeld = false
        // A walk shown from the calm companion showed the map only for it: calm again (#61).
        io.github.jnbp.nextstop.ui.companion.CompanionLayout.peekMap(false)
        // The followed trip is framed again as the page shows it.
        if (tripView) tripFrameHeld = false
    }

    // On the start page: the start becomes the destination, and the app asks for a new start.
    fun swapOnStart() {
        val start = origin ?: selectedStop ?: return
        destination = start
        searchMode = SearchMode.Origin
    }

    fun closeSearch() {
        // A swap that was not finished is undone; not while the start is chosen on the map instead.
        if (searchMode == SearchMode.Origin && journeys == null && !journeysLoading && journeysError == null && !MapPick.active) {
            destination = null
        }
        editingPlace = null
        searchMode = null
    }

    // A stop found by name on the departures tab: its departures show, and it joins the list there.
    fun pickDepartureStop(place: Stop) {
        val stop = place.copy(distanceMeters = null)
        foundStops = (listOf(stop) + foundStops.filter { it.id != stop.id }).take(8)
        viewHeld = true
        selectedStopId = stop.id
        stopMarked = true
        origin = null
        searchMode = null
    }

    // A place on the way with its time to stay there: the open search runs again with it and stays on its page.
    fun applyVia(picked: Via) {
        viaPending = null
        via = picked
        if (destination != null) searchJourneys(keepView = true)
    }

    fun removeVia() {
        if (via == null) return
        via = null
        if (destination != null) searchJourneys(keepView = true)
    }

    // No position for a start that was waited for: said, with the way to choose another start.
    fun startNotFound() {
        endLocatingNotice()
        scope.launch {
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.start_not_found),
                actionLabel = context.getString(R.string.choose_start),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) searchMode = SearchMode.Origin
        }
    }
    originPending.notFound = ::startNotFound

    // A destination is picked while the start at the position of the user is not known yet:
    // the search waits for a fresh position (at most START_WAIT_MILLIS, also taken by the round
    // of startFromPosition where that answers first) and says so; else the last known position
    // stands in, and without any the user is told and can choose a start.
    fun awaitStart() {
        originPending[0] = true
        // One wait at a time: a new one replaces the one before, with its request and notice.
        originPending.job?.cancel()
        endLocatingNotice()
        scope.launch {
            snackbar.showSnackbar(context.getString(R.string.start_locating), duration = SnackbarDuration.Long)
        }
        originPending.job = scope.launch {
            val fresh = withTimeoutOrNull(START_WAIT_MILLIS) { currentLocation(context) }
                ?.let { it.latitude to it.longitude }
                ?.takeIf { prefs.sourceChoice.covers(it.first, it.second) }
            // The position came in another way meanwhile and the search runs already.
            if (!originPending[0]) return@launch
            val place = fresh ?: readLocation(context, prefs.sourceChoice)
            originPending[0] = false
            if (place == null) {
                startNotFound()
                return@launch
            }
            origin = currentPlace(place)
            endLocatingNotice()
            if (destination != null) searchJourneys()
        }
    }

    fun pickDestination(target: Stop) {
        // A remembered connection is set again after this by the one who opens it.
        patternFilter = null
        // Another destination is another search: its mode starts off.
        if (target.id != destination?.id) everyMinute = false
        destination = target
        rememberSearch(target)
        searchMode = null
        // A start at the position of the user that is still awaited runs the search once it is
        // there; so does a start that is not known at all. Never nothing: the user is told.
        if (originPending[0] || (origin == null && selectedStop == null)) {
            awaitStart()
            return
        }
        searchJourneys()
    }

    fun pickOrigin(place: Stop) {
        patternFilter = null
        if (place.id != CURRENT_LOCATION_ID && originPending[0]) {
            // A start picked by hand ends the wait for the position and its notice.
            originPending[0] = false
            originPending.job?.cancel()
            originPending.job = null
            endLocatingNotice()
        }
        if (place.id == CURRENT_LOCATION_ID) {
            if (usingFallback) {
                // No position yet: it is asked for and becomes the start once it is there,
                // instead of routing from the fallback point. Without a destination the
                // question where to goes on meanwhile.
                originPending[0] = true
                requestPosition()
                if (destination == null) {
                    editingPlace = null
                    searchMode = SearchMode.Destination
                } else {
                    closeSearch()
                }
                return
            }
            // The known position starts the search at once; a fresh one that lies elsewhere runs it again.
            requestPosition()
        }
        origin = place
        // With a destination the search runs at once; without one the app asks for it next,
        // with the field ready for typing.
        if (destination != null) {
            searchMode = null
            searchJourneys()
        } else {
            searchMode = SearchMode.Destination
        }
    }

    // A favourite of several stops goes, with "Undo" for a few seconds (back in its place).
    fun removeStopGroup(group: StopGroup) {
        val index = stopGroups.indexOfFirst { it.id == group.id }
        if (index < 0) return
        stopGroups = stopGroups.filter { it.id != group.id }
        prefs.saveStopGroups(stopGroups)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.stop_group_removed),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                stopGroups = restoreStopGroup(stopGroups, group, index)
                prefs.saveStopGroups(stopGroups)
            }
        }
    }

    fun saveStopGroup(group: StopGroup) {
        stopGroups = withStopGroup(stopGroups, group)
        prefs.saveStopGroups(stopGroups)
        groupToSave = null
    }

    fun toggleFavorite() {
        val stop = selectedStop ?: return
        // Several stops shown together: the star keeps them as one favourite, with a name
        // asked for first, or lets that favourite go. One stop is a favourite stop as always.
        if (addedStops.isNotEmpty()) {
            val shownStops = listOf(stop) + addedStops
            val saved = findStopGroup(stopGroups, shownStops)
            if (saved != null) removeStopGroup(saved) else groupToSave = stopGroupOf(shownStops)
            return
        }
        favorites = if (favorites.any { it.id == stop.id }) {
            favorites.filter { it.id != stop.id }
        } else {
            favorites + stop.copy(distanceMeters = null)
        }
        prefs.savePlaces(AppPrefs.KEY_FAVORITES, favorites)
    }

    fun toggleRoute() {
        val from = origin ?: selectedStop ?: return
        val to = destination ?: return
        val id = routeId(from, to)
        savedRoutes = if (savedRoutes.any { it.id == id }) {
            savedRoutes.filter { it.id != id }
        } else {
            savedRoutes + SavedRoute(from.copy(distanceMeters = null), to.copy(distanceMeters = null))
        }
        prefs.saveRoutes(savedRoutes)
    }

    fun removePattern(pattern: SavedPattern) {
        savedPatterns = savedPatterns.filter { it.id != pattern.id }
        prefs.savePatterns(savedPatterns)
    }

    // "Remember connection" in the menu of a journey: its places and lines wait for a name.
    fun rememberPattern(journey: Journey) {
        val from = origin ?: selectedStop ?: return
        val to = destination ?: return
        val lines = patternLines(journey)
        if (lines.isEmpty()) return
        patternToSave = SavedPattern(from.copy(distanceMeters = null), to.copy(distanceMeters = null), lines)
    }

    fun savePattern(pattern: SavedPattern) {
        savedPatterns = savedPatterns.filter { it.id != pattern.id } + pattern
        prefs.savePatterns(savedPatterns)
        patternToSave = null
    }

    fun removeRoute(route: SavedRoute) {
        savedRoutes = savedRoutes.filter { it.id != route.id }
        prefs.saveRoutes(savedRoutes)
    }

    // Searches a saved or recent pair again, at the time that is picked on the start page.
    fun openRoute(route: SavedRoute) {
        origin = when {
            route.from.id != CURRENT_LOCATION_ID -> route.from
            // "Current location" means where the user is now, not where the route was saved.
            usingFallback -> null
            else -> route.from.copy(latitude = center.first, longitude = center.second)
        }
        settingsOpen = false
        tab = Tab.Route
        pickDestination(route.to)
    }

    // Opens a remembered connection: the search between its places, showing the journeys
    // that ride its lines, straight on the connections.
    fun openPattern(pattern: SavedPattern) {
        openRoute(SavedRoute(pattern.from, pattern.to))
        patternFilter = pattern
        planning = false
    }

    // A planned trip from its card (start page, "My routes"): its journey on its own page, as
    // the one connection between its ends at its time; the refreshes of the connections bring
    // its live data. A search that was open makes room for it.
    fun openPlannedJourney(journey: Journey) {
        val from = journey.legs.firstOrNull()?.fromPlace ?: return
        val to = journey.legs.lastOrNull()?.toPlace ?: return
        closeConnections()
        searchMode = null
        settingsOpen = false
        companionOpen = false
        tab = Tab.Route
        origin = from
        destination = to
        journeyTime = TimeChoice(journey.departure)
        journeys = listOf(journey)
        journeyLevels = levelsOf(listOf(journey))
        connectionsState = ConnectionsState().apply {
            selectedId = journey.id
            detailPage = true
        }
        planning = false
    }

    // What the cards of planned trips ask (see PlannedTripActions): open one, or "Undo" for
    // one just cancelled, for a few seconds.
    LaunchedEffect(Unit) {
        launch { snapshotFlow { PlannedTripActions.toOpen }.filterNotNull().collect { PlannedTripActions.takeOpen()?.let { openPlannedJourney(it) } } }
        snapshotFlow { PlannedTripActions.cancelled }.filterNotNull().collectLatest {
            val trip = PlannedTripActions.takeCancelled() ?: return@collectLatest
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = context.getString(R.string.planned_card_cancelled),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) PlannedTripAlarms.restore(context, trip)
        }
    }

    // Searches the way to an appointment, to be there by its start. Its place is the one its
    // location resolves to, as it was found for the tile or found now. When nothing is found,
    // the tile says so.
    fun openAppointment(appointment: Appointment) {
        val known = calendarTile.appointmentLeave?.takeIf { it.key == appointment.key }?.place
        scope.launch {
            val place = known ?: source.resolvePlace(appointment.location)
            if (place == null) {
                calendarTile.appointmentLeave = AppointmentLeave(appointment.key, startPlace?.id.orEmpty(), null, null)
                return@launch
            }
            journeyTime = TimeChoice(appointment.begin, arrival = true)
            settingsOpen = false
            tab = Tab.Route
            pickDestination(place.copy(distanceMeters = null))
        }
    }

    fun openFavorite(stop: Stop) {
        viewHeld = true
        if (stops.none { it.id == stop.id }) stops = listOf(stop) + stops
        selectedStopId = stop.id
        stopMarked = true
        origin = null
        tab = Tab.Departures
    }

    // A favourite of several stops: the departures tab with exactly those, its first one selected.
    fun openStopGroup(group: StopGroup) {
        val first = group.stops.firstOrNull() ?: return
        viewHeld = true
        if (stops.none { it.id == first.id }) stops = listOf(first) + stops
        // The stops chosen together, not all stops nearby.
        if (nearbyOn) {
            nearbyOn = false
            prefs.nearbyDepartures = false
        }
        selectedStopId = first.id
        extraStops = group.stops.drop(1)
        extraAnchor = first.id
        stopMarked = true
        origin = null
        tab = Tab.Departures
    }

    fun updatePlaces(changed: List<FavoritePlace>) {
        favoritePlaces = changed
        prefs.saveFavoritePlaces(changed)
    }

    fun updateSaved(changed: List<SavedPlace>) {
        savedPlaces = changed
        prefs.saveSavedPlaces(changed)
    }

    // The search found where the favourite place lies that it was opened for.
    fun pickFavoritePlace(place: Stop) {
        val index = editingPlace
        if (index != null) {
            updatePlaces(
                favoritePlaces.mapIndexed { at, favorite ->
                    if (at == index) favorite.copy(place = place.copy(distanceMeters = null)) else favorite
                },
            )
        }
        editingPlace = null
        searchMode = null
    }

    // A line badge on the card of a stop position: the course of its next run from there
    // (from the departures of the stop, else asked for the line), from this stop on.
    fun openPositionLine(line: OsmLine) {
        val pick = pickedPosition ?: return
        val stopId = selectedStopId ?: return
        val stopName = selectedStop?.name
        val key = lineKey(line.name)
        val next = positionDepartures(departures, pick.pos, pick.lines).firstOrNull { lineKey(it.line) == key && it.tripId != null }
        if (next != null) {
            openCourse(
                next.tripId, next.line, next.product, next.direction, stopName,
                color = next.providerColor, textColor = next.providerTextColor, at = next.ridePlace ?: selectedStop?.position,
            )
            return
        }
        lineJob?.cancel()
        lineJob = scope.launch {
            val found = try {
                source.lineDepartures(stopId, line.name, line.product)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val number = platformNumber(pick.pos)
            val run = found.filter { it.tripId != null }.let { runs ->
                runs.firstOrNull { number != null && platformNumber(it.platform) == number } ?: runs.firstOrNull()
            }
            if (run != null && pickedPosition == pick) {
                openCourse(
                    run.tripId, run.line, run.product, run.direction, stopName,
                    color = run.providerColor, textColor = run.providerTextColor, at = run.ridePlace ?: selectedStop?.position,
                )
            }
        }
    }

    // "Walk here" on the card of a stop position: the walk from the own position to it on the
    // big map, along a real way on foot once it came in.
    fun walkToPosition() {
        val pick = pickedPosition ?: return
        val own = ownLocation ?: devicePosition
        if (own == null) {
            Toast.makeText(context, R.string.position_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        val start = OffsetDateTime.now()
        val minutes = walkMinutesFor(distanceMeters(own, pick.position).toDouble())
        val stopName = selectedStop?.name?.shortStopName().orEmpty()
        val leg = Leg(
            line = null,
            product = "",
            walking = true,
            from = context.getString(R.string.my_location),
            to = pick.pos?.let { context.getString(R.string.position_card_title, it, stopName) } ?: stopName,
            departure = start,
            plannedDeparture = null,
            arrival = start.plusMinutes(minutes),
            plannedArrival = null,
            departurePlatform = null,
            arrivalPlatform = null,
            direction = null,
            cancelled = false,
            path = listOf(own, pick.position),
            toPlace = selectedStop,
            walkTarget = WalkTarget(WalkTargetKind.StopPosition, pick.pos, pick.latitude, pick.longitude, pick.lines),
        )
        FootPaths.wantWalk(leg)
        focusWalk(leg, null)
    }

    // A stop picked by hand stays the selected one, wherever the user goes from here.
    fun selectStop(stopId: String) {
        viewHeld = true
        // Only one card on the map: the stop's card replaces a place's (a search result, a pin).
        pinned = null
        selectedStopId = stopId
        stopMarked = true
        origin = null
    }

    // A place found by the search of the map tab: a stop is selected and marked with its stop
    // positions and its card, as a tapped stop sign is; any other place is pinned with the card
    // of a place. The map goes there either way.
    fun pickMapPlace(place: Stop) {
        searchMode = null
        val found = place.copy(distanceMeters = null)
        val position = found.position
        mapSearchPick = found
        viewHeld = true
        if (found.kind == PlaceKind.Stop) {
            pinned = null
            // Kept with the stops found by name, so it stays selected wherever the map loads stops.
            foundStops = (listOf(found) + foundStops.filter { it.id != found.id }).take(8)
            selectStop(found.id)
        } else if (position != null) {
            pinnedPoi = found.name
            pinned = position
            stopMarked = false
        }
        if (position != null) {
            map?.animateCameraScaled(CameraUpdateFactory.newLatLngZoom(LatLng(position.first, position.second), STOP_ZOOM))
        }
    }

    // The own position for a pick on the map (#205): the device's, else the last place known; null without any.
    fun pickOwn(): Pair<Double, Double>? = (ownLocation ?: devicePosition) ?: center.takeIf { !usingFallback }

    // Puts the pin of a pick on the map (the middle of the screen) on the own position.
    fun pickAtOwn(animated: Boolean) {
        val readyMap = map ?: return
        val camera = mapPickCamera(pickOwn(), ownIsReal = true, currentZoom = readyMap.cameraPosition.zoom) ?: return
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(camera.latitude, camera.longitude), camera.zoom)
        if (animated && !freshCamera(readyMap.cameraPosition.zoom)) readyMap.animateCameraScaled(update, LOCATE_MILLIS) else readyMap.moveCameraSafely(update)
    }

    // A start or destination chosen on the map (see MapPick.kt): the map tab shows the map with
    // the pin meanwhile; the place taken goes on as if the search had found it, and a pick called
    // off returns to the search it came from. Another tab ends the pick.
    LaunchedEffect(MapPick.target) { if (MapPick.target != null) tab = Tab.Map }
    // A pick starts with the pin on the own position where it is known, not where the map last
    // looked (#205); once per pick, as soon as there is a map.
    val pickPlaced = remember { booleanArrayOf(false) }
    LaunchedEffect(MapPick.target, map) {
        if (MapPick.target == null) {
            pickPlaced[0] = false
            return@LaunchedEffect
        }
        if (pickPlaced[0] || map == null) return@LaunchedEffect
        pickPlaced[0] = true
        // The pin is the place: the map does not follow the position under it meanwhile.
        viewHeld = true
        pickAtOwn(animated = false)
    }
    LaunchedEffect(tab) { if (tab != Tab.Map) MapPick.leave() }
    LaunchedEffect(MapPick.done) {
        val ended = MapPick.take() ?: return@LaunchedEffect
        tab = Tab.Route
        val place = ended.place
        when {
            place == null -> searchMode = if (ended.target == MapPickTarget.Origin) SearchMode.Origin else SearchMode.Destination
            ended.target == MapPickTarget.Origin -> pickOrigin(place)
            else -> pickDestination(place)
        }
    }

    // A stop chip of the start page: the stop is picked and the camera frames both the user
    // and the stop in the part of the map left free by the chips on top and the sheet below.
    // Close together, the camera goes to their middle at the zoom of a stop instead.
    fun selectStopFramed(stopId: String) {
        selectStop(stopId)
        val readyMap = map ?: return
        if (!mapShown || usingFallback) return
        val stopAt = stops.firstOrNull { it.id == stopId }?.position ?: return
        val here = center
        val middle = (here.first + stopAt.first) / 2 to (here.second + stopAt.second) / 2
        if (distanceMeters(here, stopAt) < FRAME_NEAR_METERS) {
            readyMap.animateCameraScaled(CameraUpdateFactory.newLatLngZoom(visibleTarget(middle, STOP_ZOOM), STOP_ZOOM), FRAME_MILLIS)
            return
        }
        val height = mapCover[1]
        val coverTop = if (onStart) mapCover[0] else mapCover[2]
        val margin = 36f * density
        val top = (statusTop + MAP_TOP_COVER_DP * density + margin).roundToInt()
        val bottom = if (height.isNaN() || coverTop.isNaN() || coverTop > height) {
            (margin * 3).roundToInt()
        } else {
            (height - coverTop + margin).roundToInt()
        }
        val side = (40 * density).roundToInt()
        try {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(here.first, here.second))
                .include(LatLng(stopAt.first, stopAt.second))
                .build()
            readyMap.animateCameraScaled(CameraUpdateFactory.newLatLngBounds(bounds, side, top, side, bottom), FRAME_MILLIS)
        } catch (e: Exception) {
            // Too little room left between the covers: the stop alone is picked, the map stays.
        }
    }

    // A stop sign, a stop of OpenStreetMap or a stop position tapped on the map (start page or
    // map tab): the camera glides there, so the place sits in the middle of the part of the map
    // left free above the sheet or card, at the current zoom or at least that of a stop. Only a
    // tap asks for it (focusTapped), never the nearest stop following the user. Counted per
    // tap, so the same place tapped again is centred again.
    var tapFocus by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var tapFocusSerial by remember { mutableIntStateOf(0) }
    fun focusTapped(position: Pair<Double, Double>?) {
        if (position == null) return
        tapFocus = position
        tapFocusSerial++
    }
    // The cover read by visibleTarget follows the card that opens with the tap (or, for a stop
    // of OpenStreetMap, once the provider's stop is found), so the effect asks the latest one.
    val latestVisibleTarget by rememberUpdatedState<(Pair<Double, Double>, Double?) -> LatLng>(
        { position, zoom -> visibleTarget(position, zoom) },
    )
    LaunchedEffect(tapFocusSerial) {
        if (tapFocusSerial == 0) return@LaunchedEffect
        val position = tapFocus ?: return@LaunchedEffect
        val readyMap = map ?: return@LaunchedEffect
        if (!mapShown || usingFallback) return@LaunchedEffect
        val current = readyMap.cameraPosition.zoom
        val zoom = if (current < STOP_ZOOM) STOP_ZOOM else current
        var aimed = latestVisibleTarget(position, zoom)
        readyMap.animateCameraScaled(CameraUpdateFactory.newLatLngZoom(aimed, zoom), TAP_FOCUS_MILLIS)
        // Pixels on screen per metre at that zoom (the map has 512 dp tiles, see visibleTarget).
        val pixelsPerMeter = 512.0 * 2.0.pow(zoom) * density /
            (EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(position.first)))
        fun pixelsBetween(a: LatLng, b: LatLng): Double =
            distanceMeters(a.latitude to a.longitude, b.latitude to b.longitude) * pixelsPerMeter
        // The card at the bottom may still be coming in or changing its height: once its edge
        // rests for a few frames and the place would end up elsewhere, the camera goes again.
        // A finger on the map ends this.
        val started = System.currentTimeMillis()
        var last = aimed
        var steady = 0
        while (System.currentTimeMillis() - started < TAP_FOCUS_SETTLE_MILLIS) {
            withFrameNanos { }
            if (mapGesture[0] || !MapAlive.alive(readyMap)) return@LaunchedEffect
            val next = latestVisibleTarget(position, zoom)
            steady = if (pixelsBetween(next, last) < 1.0) steady + 1 else 0
            last = next
            if (steady >= 4 && pixelsBetween(next, aimed) > TAP_FOCUS_SLACK_DP * density) {
                aimed = next
                readyMap.animateCameraScaled(CameraUpdateFactory.newLatLngZoom(aimed, zoom), TAP_FOCUS_MILLIS)
            }
        }
    }

    // Shows a stop of a journey under Departures or on the map. The start of an open search
    // is pinned first: it would otherwise follow the stop that gets selected here.
    fun showStop(place: Stop, target: Tab) {
        if (origin == null) origin = selectedStop
        viewHeld = true
        if (stops.none { it.id == place.id }) stops = listOf(place.copy(distanceMeters = null)) + stops
        selectedStopId = place.id
        stopMarked = true
        settingsOpen = false
        tab = target
        val position = place.position
        if (target == Tab.Map && position != null) {
            map?.animateCameraScaled(
                CameraUpdateFactory.newLatLngZoom(LatLng(position.first, position.second), STOP_ZOOM),
            )
        }
    }

    // The area of the map of "My routes" on the Map tab: all of [points] in view.
    fun showAreaOnMap(points: List<Pair<Double, Double>>) {
        viewHeld = true
        settingsOpen = false
        tab = Tab.Map
        val readyMap = map ?: return
        val side = (48 * density).roundToInt()
        try {
            if (points.size < 2) {
                val point = points.firstOrNull() ?: return
                readyMap.animateCameraScaled(CameraUpdateFactory.newLatLngZoom(LatLng(point.first, point.second), STOP_ZOOM))
            } else {
                val bounds = LatLngBounds.Builder()
                points.forEach { bounds.include(LatLng(it.first, it.second)) }
                readyMap.animateCameraScaled(CameraUpdateFactory.newLatLngBounds(bounds.build(), side, side, side, side * 4), FRAME_MILLIS)
            }
        } catch (e: Exception) {
            // All points on one spot, or no room yet: the map stays where it is.
        }
    }

    // "Als Lieblingshaltestelle" in the menu of any stop (#139): kept or let go, as the star does.
    fun toggleFavoriteStop(place: Stop) {
        favorites = if (favorites.any { it.id == place.id }) {
            favorites.filter { it.id != place.id }
        } else {
            favorites + place.copy(distanceMeters = null)
        }
        prefs.savePlaces(AppPrefs.KEY_FAVORITES, favorites)
    }

    // What the menu of a stop leads to, wherever it is opened (see StopMenu).
    fun stopAction(place: Stop, action: StopAction) {
        when (action) {
            StopAction.From -> pickOrigin(place.copy(distanceMeters = null))
            StopAction.To -> pickDestination(place.copy(distanceMeters = null))
            // From the search too (#139): it closes, and the stop shows on its tab.
            StopAction.Departures -> {
                searchMode = null
                showStop(place, Tab.Departures)
            }
            StopAction.Map -> {
                searchMode = null
                showStop(place, Tab.Map)
            }
            StopAction.Pin -> AppShortcuts.pinStop(context, place)
            StopAction.Favorite -> toggleFavoriteStop(place)
        }
    }

    // Live vehicles: loaded for the visible part of the map while the map is in view and
    // the app on screen; on the start page only when the user wants them there. With the
    // setting "Vehicles on the map" off none are asked for at all; with "Dynamic" only zoomed
    // in close (vehiclesClose), further out the list is emptied.
    val mapInView = appSettled && ((onStart && startVehicles && startMap) || onMapTab)
    // Vehicles show as dots down to VEHICLE_DOT_MIN_ZOOM; which ones are asked for at which
    // zoom, and which known ones stay, see loadMapVehicles.
    val vehiclesWanted = mapInView && mapVehiclesWanted(BatterySaver.mapVehicles(Appearance.mapVehicles), vehiclesClose)
    val seenVehicles = remember { SeenVehicles() }
    LaunchedEffect(map, vehiclesWanted, mapCenter, tier) {
        val readyMap = map
        if (readyMap == null || !vehiclesWanted) {
            // Kept in memory for a moment (a course opened and closed, the app left briefly):
            // back here, the same place shows them at once (see VehicleSnapshots).
            keepMapVehicles(mapCenter, tier, vehicles, vehiclesSince)
            vehicles = emptyList()
            return@LaunchedEffect
        }
        // At once, before any answer (#74): the recent vehicles kept for this part of the map at
        // any zoom (back from a course, back in the app, zoomed out again), and of those shown
        // only the ones in view (zoomed in); they glide along their paths, fresh ones replace them.
        vehiclesAtOnce(readyMap, tier, vehicles, seenVehicles)?.let { (atOnce, keptSince) ->
            if (atOnce !== vehicles) {
                if (vehicles.isEmpty() && keptSince != null) vehiclesSince = keptSince
                vehicles = atOnce
            }
        }
        // Wait until the map rests, so a series of moves costs one request, not many.
        delay(600)
        while (true) {
            try {
                val loaded = loadMapVehicles(source, readyMap, tier, vehicles, seenVehicles)
                // Zoomed out beyond the dots nothing is asked for: the last vehicles stay, so
                // zooming back in finds them at once instead of a map that fills up again.
                if (loaded != null) {
                    vehicles = loaded
                    vehiclesSince = SystemClock.uptimeMillis()
                    keepMapVehicles(mapCenter, tier, loaded, vehiclesSince)
                    if (loaded.size >= MAX_VEHICLES) DiagLog.d(PERF_LOG_TAG) { "${loaded.size} vehicles at tier $tier" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Vehicles are an extra: keep the last ones and try again.
            }
            delay(BatterySaver.pace(15_000))
        }
    }

    // The companion shows the whole way of its journey and keeps it fresh. The first
    // request comes at once: it brings the stops on the way of every ride, which the
    // search left out; an answer without them keeps the ones known.
    // While the trip service runs, it does this for the app, also with the screen off.
    // The watch gets the trip from the trip service. Where that does not run (notifications of
    // the phone off, or refused), the app mirrors it itself while it lives, so the watch still
    // shows the trip and can open its companion; the service takes over as soon as it runs.
    LaunchedEffect(tripServiceRunning) {
        if (tripServiceRunning) return@LaunchedEffect
        PhoneBridge.mirrorTrip(context, endWithNone = false)
    }
    LaunchedEffect(companion?.id, tripServiceRunning) {
        if (tripServiceRunning) return@LaunchedEffect
        val followed = companion ?: return@LaunchedEffect
        if (!followed.refreshable) return@LaunchedEffect
        // Back to the preferred provider, should the trip run on a stand-in (#185).
        val probe = io.github.jnbp.nextstop.data.ReturnProbe(System.currentTimeMillis())
        while (true) {
            try {
                // After a switch on the way each half is loaded by its own handle.
                val known = companion ?: followed
                val fresh = source.followedFresh(known, probe)
                // A trip that ended or changed in the meantime stays as it is now.
                // The same planned times as the service keeps (see withKnownPlannedTimes): a stable id.
                if (fresh != null && TripState.journey.compareAndSet(known, followedTripLive.steady(withKnownPlannedTimes(withKnownStops(withKnownSeating(keptKinds(fresh, known), known), known), known), known, OffsetDateTime.now()))) {
                    LiveFreshness.mark(LiveSource.Trip)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the last known times and try again.
            }
            // Sooner when the user asks for fresh times (a tap on the age of the data).
            // Every 20 s in the last minutes before a departure (see tripRefreshMillis), else every 30 s.
            LiveFreshness.awaitRefresh(LiveSource.Trip, tripRefreshMillis(companion ?: followed, OffsetDateTime.now(), 30_000))
        }
    }

    // The vehicles of the trip: the whole trip of every ride that is not over yet, with its
    // way once and its times again and again. The cards show from them how far away a
    // vehicle still is, the map where it is. Only asked for while the companion is in view.
    // A ride that begins where the traveller boards it, at the end of its line, gets the run
    // that brings its vehicle there as well: that vehicle is on the road long before the
    // ride leaves, under the handle of its run before.
    // Derived from the clock: AppRoot composes again only when the rides still to come change.
    val ridesOf = companion.takeIf { tripView && appStarted }
    val tripRides by remember(ridesOf) {
        derivedStateOf {
            // By the ride state: a ride still ridden by position past its live arrival (late, or
            // standing before the station) keeps its vehicle (#170).
            // The legs of the followed journey itself (not the retimed copies, whose times move with
            // the clock while waiting or getting off): the list changes only when the rides do.
            ridesOf?.let { followed ->
                val at = nowState.value
                val ridden = rideJourney(followed, at).legs
                followed.legs.filterIndexed { index, leg ->
                    !leg.walking && leg.tripId != null && at.isBefore((ridden.getOrNull(index) ?: leg).arrival)
                }
            }.orEmpty()
        }
    }
    val tripRideIds = tripRides.mapNotNull { it.tripId }
    LaunchedEffect(tripRideIds.joinToString("|")) {
        // What is known of the vehicles stays while the app or the page is away.
        if (tripRides.isEmpty()) return@LaunchedEffect
        while (true) {
            for (ride in tripRides) {
                val tripId = ride.tripId ?: continue
                // A ride joined from two runs (see mergeThroughRuns) follows the later run once
                // past the junction; it is kept under the handle of the ride all the same.
                val runId = ride.tripIdAt(OffsetDateTime.now()) ?: tripId
                try {
                    val known = tripCourseCache.tripCourses[tripId]?.takeIf { it.tripId == runId }
                    val fresh = if (known == null) {
                        val loaded = source.tripCourse(runId)
                        loaded.copy(path = thinned(loaded.path))
                    } else {
                        source.tripStopovers(runId).takeIf { it.isNotEmpty() }?.let { stops ->
                            // The air hops count by stop: with another number of stops they are found anew (see TripTrack).
                            val hops = if (stops.size == known.stops.size) {
                                known.airHops
                            } else {
                                io.github.jnbp.nextstop.data.airHops(known.path, stops.map { it.place?.position })
                            }
                            // A track lent since (#280) takes the place of the air lines (see withLentTrack).
                            withLentTrack(known.copy(stops = stops, airHops = hops))
                        }
                    }
                    if (fresh != null) {
                        tripCourseCache.tripCourses[tripId] = fresh
                        // The badge of the vehicle names the line as the journey (and so the step,
                        // "Your M48 comes") does. The trip of the provider names only the line it
                        // begins as: a vehicle that runs as a night line first and goes on as the
                        // day line (N18, then M48 from about 4:30) came back as "N18" and was drawn
                        // so, though it is the very trip of the ride, matched by its handle.
                        val part = ride.throughRuns.lastOrNull { !OffsetDateTime.now().isBefore(it.departure) }
                        val rideLine = part?.line ?: ride.line
                        // Its product and colour too: those of the ride, but for a later part under
                        // another name, whose colour the ride does not know.
                        val rideColor = if (part?.lineChanges == true) null else ride.providerColor
                        val labelled = if (rideLine.isNullOrBlank()) {
                            fresh
                        } else {
                            fresh.copy(
                                line = rideLine,
                                product = ride.product.ifEmpty { fresh.product },
                                providerColor = rideColor ?: fresh.providerColor,
                                providerTextColor = if (rideColor != null) ride.providerTextColor else fresh.providerTextColor,
                            )
                        }
                        // The run before, once looked for and from then on kept fresh.
                        val leaves = fresh.stops.firstOrNull()?.let { it.departure ?: it.arrival }
                        val waiting = leaves != null && OffsetDateTime.now().isBefore(leaves)
                        val knownFeeder = tripCourseCache.feederCourses[tripId]
                        if (waiting && knownFeeder != null) {
                            delay(JOURNEY_REFRESH_PAUSE_MILLIS)
                            source.tripStopovers(knownFeeder.tripId).takeIf { it.isNotEmpty() }?.let { stops ->
                                tripCourseCache.feederCourses[tripId] = knownFeeder.copy(stops = stops)
                            }
                        } else if (leaves != null && waiting && tripId !in tripCourseCache.feederTried &&
                            !OffsetDateTime.now().isBefore(leaves.minusMinutes(FEEDER_WINDOW_MINUTES))
                        ) {
                            tripCourseCache.feederTried.add(tripId)
                            delay(JOURNEY_REFRESH_PAUSE_MILLIS)
                            findFeeder(source, ride, fresh)?.let { tripCourseCache.feederCourses[tripId] = it }
                        }
                        val feeder = tripCourseCache.feederCourses[tripId]?.takeIf { waiting }
                        if (feeder == null) {
                            tripCourseCache.feederTracks.remove(tripId)
                            tripCourseCache.tripTracks[tripId] = TripTrack(labelled)
                        } else {
                            val feederTrack = TripTrack(feeder)
                            tripCourseCache.feederTracks[tripId] = feederTrack
                            // The vehicle stands at the first stop from the moment its run before ends there.
                            val handover = feederTrack.clock.lastArrival
                            val departs = StopClock(fresh.stops).firstDeparture
                            val lead = if (handover != null && departs != null) {
                                (departs - handover).coerceAtLeast(LEAD_MILLIS)
                            } else {
                                LEAD_MILLIS
                            }
                            tripCourseCache.tripTracks[tripId] = TripTrack(labelled, lead)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Without its trip a ride is still shown, only without its vehicle.
                }
                delay(JOURNEY_REFRESH_PAUSE_MILLIS)
            }
            delay(BatterySaver.pace(APPROACH_REFRESH_MILLIS))
        }
    }

    // The map of a followed trip frames what matters now: where the traveller is, the stop
    // of the next step and the vehicle to wait for or ride in. It moves again, gently, when
    // the step changes, and leaves the map alone once the user moved it by hand, until the
    // user asks for it again. Opening the trip page starts framing anew.
    LaunchedEffect(tripView) {
        if (tripView) {
            tripFrameHeld = false
            io.github.jnbp.nextstop.ui.companion.CompanionLayout.mapFocus = CompanionMapFocus.Trip
        }
    }
    // Where the traveller is shown (see PositionDot); also the fresh position the overview takes (#166).
    val ownDot = remember { OwnDot() }
    // The locate button and its chip (#104) and the chip of #91: the map is framed again for them.
    val mapFocus = io.github.jnbp.nextstop.ui.companion.CompanionLayout.mapFocus
    val frameAsks = io.github.jnbp.nextstop.ui.companion.CompanionLayout.frameAsks
    // The sheet over the map settled at another height (#137): the trip is framed into the room left.
    val coverAsks = io.github.jnbp.nextstop.ui.companion.CompanionLayout.coverAsks
    LaunchedEffect(mapFocus, frameAsks) {
        if (tripView) tripFrameHeld = false
    }
    // A map made anew (the companion's map button, #122) faces the whole world and has no view
    // moved by hand to keep: it frames the trip's focus at once, as a hand-moved map from before
    // its rest would else leave it on the world until the locate button.
    LaunchedEffect(map) {
        if (map != null && tripView) tripFrameHeld = false
    }
    // Derived from the clock and the companion's position: AppRoot composes again only when the
    // step changes (the states are read inside the derived one, never here).
    val stepOf = companion.takeIf { tripView }
    val companionFixState = CompanionPosition.fix.collectAsState()
    val rideEtaState = RideTracker.eta.collectAsState()
    val frameStep by remember(stepOf) {
        derivedStateOf {
            companionFixState.value
            rideEtaState.value
            stepOf?.let { tripStep(it, nowState.value) }
        }
    }
    val frameLeg = frameStep?.leg
    val frameKey = "${frameStep?.kind}|${frameLeg?.line}|${frameLeg?.from}|${frameLeg?.plannedDeparture}"
    val frameVehicleKnown = frameLeg?.tripId?.let { tripCourseCache.tripTracks[it] != null } == true
    // The new companion frames the vehicle to board only when asked ("Wo ist mein Zug?", #91).
    val frameVehicle = !io.github.jnbp.nextstop.ui.companion.CompanionLayout.modern ||
        io.github.jnbp.nextstop.ui.companion.CompanionLayout.vehicleShown
    LaunchedEffect(tripView, tripFrameHeld, walkView, frameKey, frameVehicleKnown, ownLocation != null, map != null, frameVehicle, mapFocus, frameAsks, coverAsks) {
        // A walk shown on the big map frames itself (#276): the trip's framing waits for its "Back".
        if (!tripView || tripFrameHeld || walkView) return@LaunchedEffect
        val followed = companion ?: return@LaunchedEffect
        // A map just made may not have its size yet: a frame worked out for no room is the whole world (#122).
        map?.let { readyMap ->
            kotlinx.coroutines.withTimeoutOrNull(MAP_SIZE_WAIT_MILLIS) {
                while (MapAlive.alive(readyMap) && (readyMap.width <= 0f || readyMap.height <= 0f)) withFrameNanos { }
            }
        }
        val at = OffsetDateTime.now()
        val own = ownLocation ?: devicePosition
        when {
            // The own position up close; the dot's frame loop follows it from there (OwnFollow).
            // In the middle of the visible map, between the blue box and the sheet, with the next stop in view (#273).
            // Where the dot (or the vehicle badge that carries it) is drawn: the camera follows that one from here.
            mapFocus == CompanionMapFocus.Own && own != null -> {
                val shown = DoubleArray(2)
                val spot = if (ownDot.shown(shown)) shown[0] to shown[1] else own
                centreOnTraveller(spot, centreAhead(tripStep(followed, at), followed, spot), OWN_FOCUS_ZOOM, TRIP_FRAME_MILLIS)
            }
            // The overview of the route still to go.
            // What is still ahead (#166): from the vehicle (or the fresh own position) along the ride under
            // way to the destination; a stale own position (the last one known, maybe far behind) is left out.
            mapFocus == CompanionMapFocus.Route -> {
                val fresh = ownDot.freshPosition(SystemClock.uptimeMillis())
                val riding = followed.legs.firstOrNull { !it.walking && !at.isBefore(it.departure) && at.isBefore(it.arrival) }
                val spot = DoubleArray(2)
                val vehicle = riding?.tripId?.let { id ->
                    val track = tripCourseCache.tripTracks[id] ?: tripCourseCache.feederTracks[id]
                    if (track?.locate(at.toInstant().toEpochMilli(), spot) == true) spot[0] to spot[1] else null
                }
                frameTrip(remainingRoute(followed, at, fresh, at = vehicle ?: fresh))
            }
            // The trip as it frames itself (#91); also without a position after the locate button: the vehicle, the next stop.
            else -> frameTrip(tripFocus(followed, at, ownLocation, tripCourseCache.tripTracks, tripCourseCache.feederTracks, frameVehicle))
        }
    }
    // And between framings the camera moves along with the traveller, until the map is moved by hand.
    // On the start page and the map tab the camera follows the user as well while it is not
    // moved by hand: frame by frame with the dot, no more in steps from fix to fix.
    val ownFollowsStart = mapShown && (onStart || onMapTab) && !viewHeld && !(planView || courseView || walkView) &&
        devicePosition != null
    OwnFollow(
        map,
        // Not over the route's overview: that one stays put.
        active = (tripView && !tripFrameHeld && !walkView && mapFocus != CompanionMapFocus.Route) || ownFollowsStart,
        dot = ownDot,
        frameKey = "$frameKey|$frameVehicleKnown|${ownLocation != null}|$mapFocus|$frameAsks",
    )

    // A walk on the big map frames itself and, while the traveller is on it, the own position,
    // moving along as the position changes, until the map is moved by hand. Another page ends it.
    // From a ride still under way or far from the walk, the map frames the walk alone and stands
    // still (#276): the position counts only once it follows (walkFollows).
    val walkOwn = if (walkView) ownLocation ?: devicePosition else null
    val walkFollows = walkFocus?.let { focus ->
        walkOwn != null && walkFollowsOwn(
            current = walkIsCurrent(focus.journey, focus.walk, OffsetDateTime.now()),
            metresToWalk = metresToWay(walkWay(focus.walk), walkOwn),
        )
    } == true
    val walkFollowed = walkOwn?.takeIf { walkFollows }
    LaunchedEffect(walkFocus, walkFrameHeld, walkFollowed, map != null) {
        val focus = walkFocus ?: return@LaunchedEffect
        if (walkFrameHeld) return@LaunchedEffect
        frameWalk(focus, walkFollowed)
    }
    LaunchedEffect(screen) {
        if (screen != Screen.Connections && screen != Screen.Trip) closeWalk()
    }

    // The change ahead: the connecting departures to choose from are loaded when the
    // companion first shows it, and again and again while it does, because a delay of the
    // ride the traveller is on or of a connecting ride changes which of them can be caught.
    // A switch to another departure keeps them; the next change or the next trip loads anew.
    // The next one, or in the new companion views a later one picked there (see CompanionOffers).
    // By the ride state, as the timeline shows the trip: a change broken by a delay is the same one here and there.
    // Derived from the clock: AppRoot composes again only when the change ahead changes.
    val changeOf = companion
    val changeState = remember(changeOf) {
        derivedStateOf {
            companionFixState.value
            rideEtaState.value
            changeOf?.let { CompanionOffers.changeFor(rideJourney(it, nowState.value), nowState.value) }
        }
    }
    val change = changeState.value
    val offersWanted = tripView && appSettled
    LaunchedEffect(tripSerial, change?.stop?.id, change?.keepLegs, offersWanted) {
        if (change == null) {
            alternatives = null
            ConnectionRescue.update(context, null)
            return@LaunchedEffect
        }
        if (!offersWanted) return@LaunchedEffect
        while (true) {
            // The trip as it is now: its times move with the live data.
            val followed = companion ?: break
            val ahead = OffsetDateTime.now().let { at -> CompanionOffers.changeFor(rideJourney(followed, at), at) }
            val to = followed.legs.last().toPlace
            if (ahead == null || ahead.keepLegs != change.keepLegs || to == null) break
            val fits = alternatives?.let { it.stopId == ahead.stop.id && it.keepLegs == ahead.keepLegs && !it.failed } == true
            if (!fits) {
                val arriving = ahead.arriving.plausible()
                alternatives = Alternatives(
                    stopId = ahead.stop.id,
                    stopName = ahead.stop.name,
                    keepLegs = ahead.keepLegs,
                    arrival = arriving.arrival,
                    arrivalEstimated = arriving.arrivalEstimated,
                )
            }
            try {
                val page = source.journeys(
                    from = ahead.stop.copy(distanceMeters = null),
                    to = to,
                    options = options.copy(via = viaAhead(followed, followed.legs.take(ahead.keepLegs))),
                    results = OFFER_RESULTS,
                    departure = offerStart(ahead.arriving),
                )
                val arriving = ahead.arriving.plausible()
                alternatives = Alternatives(
                    stopId = ahead.stop.id,
                    stopName = ahead.stop.name,
                    keepLegs = ahead.keepLegs,
                    arrival = arriving.arrival,
                    arrivalEstimated = arriving.arrivalEstimated,
                    loading = false,
                    options = page.journeys.distinctBy { it.id }.sortedBy { it.departure },
                    // The rest of the trip as followed now: always among the choices (#94).
                    current = Journey(legs = followed.legs.drop(ahead.keepLegs)),
                )
                // Kept for the trip service, so it does not search this change again in the background.
                RescueInBackground.searches.put(RescueSearches.changeKey(followed.id, ahead.keepLegs), alternatives?.options.orEmpty())
                // The chosen connection broken by a delay: the best one that works, in the hero
                // area and once as a notification (#98).
                val offered = connectionOptions(ahead, alternatives)
                val chosenIndex = offered.indexOfFirst { sameChoiceRide(it.rides.first(), ahead.next) }
                val rescue = rescueChoice(chosenIndex, optionTimes(ahead, offered))?.let { offered[it] }
                ConnectionRescue.update(
                    context,
                    rescue?.let { RescueSuggestion(followed.id, ahead.keepLegs, ahead.stop.name.plainStopName(), it) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // What was offered before stays; a first request that failed says so.
                if (!fits) alternatives = alternatives?.copy(loading = false, failed = true)
            }
            delay(BatterySaver.pace(OFFER_REFRESH_MILLIS))
        }
    }

    // The appointments for the tile on the start page, read again and again while it is in view.
    LaunchedEffect(calendarTile.calendarGranted, onStart, appSettled) {
        if (!calendarTile.calendarGranted) {
            calendarTile.appointments = null
            return@LaunchedEffect
        }
        if (!onStart || !appSettled) return@LaunchedEffect
        while (true) {
            // Also those that began a while ago (see UxPrefs.appointmentAfterMinutes).
            calendarTile.appointments = upcomingAppointments(context, afterMinutes = UxPrefs(context).appointmentAfterMinutes)
            delay(CALENDAR_REFRESH_MILLIS)
        }
    }
    val shownAppointment = calendarTile.appointments?.let { list ->
        list.firstOrNull { it.key == calendarTile.appointmentPick } ?: list.firstOrNull()
    }
    // When to leave for the appointment in front: its location as the search finds it, and
    // the last journey from the start of the page that is there by its start.
    LaunchedEffect(shownAppointment?.key, startPlace?.id, appStarted) {
        val appointment = shownAppointment ?: return@LaunchedEffect
        val from = startPlace ?: return@LaunchedEffect
        if (!appStarted) return@LaunchedEffect
        val done = calendarTile.appointmentLeave
        if (done != null && done.key == appointment.key && done.fromId == from.id) return@LaunchedEffect
        try {
            val place = if (done != null && done.key == appointment.key) {
                done.place
            } else {
                source.resolvePlace(appointment.location)
            }
            if (place == null) {
                calendarTile.appointmentLeave = AppointmentLeave(appointment.key, from.id, null, null)
                return@LaunchedEffect
            }
            val page = source.journeys(from = from, to = place, options = options.copy(comfort = shownComfort), results = 3, arrival = appointment.begin)
            val journey = page.journeys
                .filter { !it.arrival.isAfter(appointment.begin) }
                .maxByOrNull { it.departure }
            calendarTile.appointmentLeave = AppointmentLeave(appointment.key, from.id, place, journey?.departure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Without an answer the tile shows the appointment without a time to leave.
        }
    }

    // A trip ends by itself a few minutes after its arrival, when its notification goes too
    // (AFTER_ARRIVAL_MINUTES), with "Undo" for a few seconds. A trip taken back is left alone.
    var autoEndedId by remember { mutableStateOf<String?>(null) }
    val autoEndAt = companion?.takeIf { it.id != autoEndedId }?.arrival?.plusMinutes(AFTER_ARRIVAL_MINUTES)
    LaunchedEffect(autoEndAt) {
        val at = autoEndAt ?: return@LaunchedEffect
        val wait = Duration.between(OffsetDateTime.now(), at).toMillis()
        if (wait > 0) delay(wait)
        while (companion?.let { !tripOver(it, OffsetDateTime.now()) } == true) delay(TRIP_OVER_LOOK_MILLIS)
        val followed = companion ?: return@LaunchedEffect
        autoEndedId = followed.id
        endTripWithUndo()
    }

    // A trip can also end from its notification: what belongs to it goes away here too.
    val tripRunning = companion != null
    LaunchedEffect(tripRunning) {
        if (tripRunning) return@LaunchedEffect
        if (companionOpen) endTrip() else clearTripExtras()
    }
    // A tap on the notification of a trip opens its page.
    val openRequested by TripState.openRequested.collectAsState()
    LaunchedEffect(openRequested) {
        if (!openRequested) return@LaunchedEffect
        TripState.openRequested.value = false
        if (companion == null) return@LaunchedEffect
        tab = Tab.Route
        settingsOpen = false
        companionOpen = true
    }
    // A tap on a notification of the disruption watch: the first page of "My routes", whose
    // section of the disruptions shows what it reported (see DisruptionTap); when that is over,
    // a short note says so.
    val disruptionTap by DisruptionTap.pending.collectAsState()
    LaunchedEffect(disruptionTap) {
        if (disruptionTap == null) return@LaunchedEffect
        closeSearch()
        closeCourse()
        settingsOpen = false
        companionOpen = false
        favoritesPages.clear()
        tab = Tab.Favorites
    }
    val disruptionGone by DisruptionTap.gone.collectAsState()
    LaunchedEffect(disruptionGone) {
        if (!disruptionGone) return@LaunchedEffect
        DisruptionTap.gone.value = false
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(context.getString(R.string.r6notice_gone))
    }

    // A tap on the notification of a trip started on the watch that the phone could not take
    // over in the background: the app follows it now, from the front, with its service.
    val watchTrip by PhoneBridge.watchTripRequest.collectAsState()
    LaunchedEffect(watchTrip) {
        val token = watchTrip ?: return@LaunchedEffect
        // Cleared only after the load: clearing the key first would restart this effect and
        // cancel a load that has to suspend (the app was started from the notification).
        val journey = try {
            PhoneBridge.watchJourney(source, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        PhoneBridge.watchTripRequest.value = null
        if (journey == null) return@LaunchedEffect
        settingsOpen = false
        tab = Tab.Route
        startTrip(journey)
    }

    // A tap on a widget: the departures of its stop, or "Start" on a connection, which follows
    // the very journey the widget showed (found again by its key when the app was not running).
    val widgetStop by WidgetLaunch.openStop.collectAsState()
    LaunchedEffect(widgetStop) {
        val stop = widgetStop ?: return@LaunchedEffect
        WidgetLaunch.openStop.value = null
        settingsOpen = false
        companionOpen = false
        openFavorite(stop)
    }
    val widgetTrip by WidgetLaunch.startTrip.collectAsState()
    LaunchedEffect(widgetTrip) {
        val request = widgetTrip ?: return@LaunchedEffect
        WidgetLaunch.startTrip.value = null
        settingsOpen = false
        val journey = WidgetJourneys.get(request.key) ?: try {
            val leaves = OffsetDateTime.ofInstant(Instant.ofEpochMilli(request.departure), ZoneId.systemDefault())
            val found = source.journeys(
                from = request.from,
                to = request.to,
                options = prefs.searchOptionsInUse,
                results = 6,
                departure = leaves.minusMinutes(2),
            ).journeys
            found.firstOrNull { journeyKey(it) == request.key }
                ?: found.firstOrNull { !it.cancelled && !it.departure.isBefore(leaves.minusMinutes(1)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (journey != null) {
            tab = Tab.Route
            startTrip(journey)
        } else {
            // Without the journey the search between the two places opens instead.
            origin = request.from
            tab = Tab.Route
            pickDestination(request.to)
        }
    }
    // A tap on an appointment widget: the search to its place, to be there by its start, as
    // the appointment tile of the start page opens it. A place the widget did not find yet is
    // looked up now; without one the app just opens.
    val widgetSearch by WidgetLaunch.searchTo.collectAsState()
    LaunchedEffect(widgetSearch) {
        val request = widgetSearch ?: return@LaunchedEffect
        WidgetLaunch.searchTo.value = null
        settingsOpen = false
        companionOpen = false
        val place = request.to ?: try {
            source.resolvePlace(request.location)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (place != null) {
            journeyTime = TimeChoice(
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(request.arrival), ZoneId.systemDefault()),
                arrival = true,
            )
            tab = Tab.Route
            pickDestination(place.copy(distanceMeters = null))
        }
    }

    // A link of the app (a launcher shortcut, "In Next Stop öffnen" of a shared route): the
    // search to its destination, from its start or from where the user is.
    val linkRoute by AppLinks.route.collectAsState()
    LaunchedEffect(linkRoute) {
        val request = linkRoute ?: return@LaunchedEffect
        AppLinks.route.value = null
        settingsOpen = false
        companionOpen = false
        tab = Tab.Route
        val from = request.from
        if (from != null) {
            origin = from
            pickDestination(request.to)
        } else {
            // From the own position: the destination waits for it (see pickOrigin).
            patternFilter = null
            if (request.to.id != destination?.id) everyMinute = false
            destination = request.to
            rememberSearch(request.to)
            pickOrigin(currentPlace(center))
        }
    }
    // A place shared by another app: the search for a destination opens with it filled in, its
    // point (where the share had one) on top, and the user confirms the place.
    val linkShared by AppLinks.sharedPlace.collectAsState()
    LaunchedEffect(linkShared) {
        val shared = linkShared ?: return@LaunchedEffect
        AppLinks.sharedPlace.value = null
        settingsOpen = false
        companionOpen = false
        tab = Tab.Route
        editingPlace = null
        sharedSearch = SharedSearch(sharedSerial[0]++, shared.query, shared.asPlace())
        searchMode = SearchMode.Destination
    }
    // "Navigate here" of another app (see AppLinks.navigateTo): a point is the destination of a
    // connection from where the user is, named by the link or else after what lies there; a
    // place given only as a text opens the search for it, as a share does.
    val navChosenName = stringResource(R.string.nav_link_place)
    val linkNavigate by AppLinks.navigateTo.collectAsState()
    LaunchedEffect(linkNavigate) {
        val target = linkNavigate ?: return@LaunchedEffect
        AppLinks.navigateTo.value = null
        val at = target.position
        if (at == null) {
            AppLinks.sharedPlace.value = target
            return@LaunchedEffect
        }
        val label = target.name?.takeIf { it.isNotBlank() } ?: withTimeoutOrNull(NAV_LABEL_WAIT_MILLIS) {
            reverseGeocode(context, at.first, at.second)?.title?.takeIf { it.isNotBlank() } ?: try {
                source.nearestPlace(at.first, at.second)?.name?.let { context.getString(R.string.near_place, it.shortStopName()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } ?: navChosenName
        val place = target.copy(name = label).asPlace() ?: return@LaunchedEffect
        AppLinks.route.value = OpenLink.Route(null, place)
    }
    // A short link of Google Maps is followed first (one request to its host); a link that leads
    // nowhere readable opens the empty search for a destination.
    val linkShort by AppLinks.shortMapLink.collectAsState()
    LaunchedEffect(linkShort) {
        val link = linkShort ?: return@LaunchedEffect
        AppLinks.shortMapLink.value = null
        val target = followShortMapLink(link)?.let { parseNavLink(it) }
        AppLinks.navigateTo.value = target ?: SharedPlace("", null, null)
    }
    // The share belongs to that one search: let go of once it closed (after its exit, so the
    // closing panel keeps its text) or another search opened.
    LaunchedEffect(searchMode) {
        if (searchMode == SearchMode.Destination || sharedSearch == null) return@LaunchedEffect
        if (searchMode == null) delay(SHARED_SEARCH_LINGER_MILLIS)
        sharedSearch = null
    }
    // "Departures here" of the launcher: the departures tab at the stops where the user is.
    val linkHere by AppLinks.departuresHere.collectAsState()
    LaunchedEffect(linkHere) {
        if (!linkHere) return@LaunchedEffect
        AppLinks.departuresHere.value = false
        settingsOpen = false
        companionOpen = false
        viewHeld = false
        origin = null
        tab = Tab.Departures
        requestPosition()
    }
    // The dynamic shortcuts of the launcher follow home, work and the recent destinations.
    LaunchedEffect(favoritePlaces, searchHistory) {
        AppShortcuts.update(context, favoritePlaces, searchHistory)
    }

    // What the tiles lead to: the favourite places from the current start, saved routes as they are.
    val tileRoutes = buildList<SavedRoute> {
        if (startPlace != null) {
            favoritePlaces.mapNotNull { it.place }
                .filter { it.id != startPlace.id }
                .forEach { add(SavedRoute(startPlace, it)) }
        }
        savedRoutes.take(MAX_ROUTE_TILES).forEach { route ->
            if (route.from.id != CURRENT_LOCATION_ID) {
                add(route)
            } else if (!usingFallback) {
                // "Current location" means where the user is now, not where the route was saved.
                add(route.copy(from = route.from.copy(latitude = center.first, longitude = center.second)))
            }
        }
    }
    val iconLabels = PlaceIcon.entries.associateWith { placeIconLabel(it) }
    // Where the device is, for "You are here" on the tile of a place.
    val herePosition = ownLocation ?: devicePosition
    val tiles = buildList<PlaceTile> {
        favoritePlaces.forEachIndexed { index, favorite ->
            val place = favorite.place
            val known = if (place == null || startPlace == null) null else placeTrips[routeId(startPlace, place)]
            add(
                PlaceTile(
                    key = "place$index",
                    glyph = placeGlyph(favorite.icon),
                    title = favorite.name.ifBlank { iconLabels.getValue(favorite.icon) },
                    set = place != null,
                    journey = known?.journey,
                    // Who already is at the place has no journey to wait for.
                    loaded = known != null || place?.id == startPlace?.id,
                    // At the place already (see atSavedPlace): "You are here", not "No connection found".
                    here = place != null && atSavedPlace(
                        place = place.position,
                        placeId = place.id,
                        startId = startPlace?.id,
                        start = startPlace?.position,
                        device = herePosition,
                        noJourney = known != null && known.journey == null,
                    ),
                    onClick = {
                        if (place != null) {
                            pickDestination(place)
                        } else {
                            editingPlace = index
                            searchMode = SearchMode.Place
                        }
                    },
                ),
            )
        }
        savedRoutes.take(MAX_ROUTE_TILES).forEach { route ->
            val known = placeTrips[route.id]
            add(
                PlaceTile(
                    key = route.id,
                    glyph = Glyph.Route,
                    title = route.to.name.shortStopName(),
                    set = true,
                    journey = known?.journey,
                    // A route from the current location cannot be asked for without a position.
                    loaded = known != null || (route.from.id == CURRENT_LOCATION_ID && usingFallback),
                    onClick = { openRoute(route) },
                ),
            )
        }
        savedPatterns.take(MAX_ROUTE_TILES).forEach { pattern ->
            val known = placeTrips[patternTileKey(pattern)]
            add(
                PlaceTile(
                    key = patternTileKey(pattern),
                    glyph = Glyph.Route,
                    title = pattern.label,
                    set = true,
                    journey = known?.journey,
                    loaded = known != null || (pattern.from.id == CURRENT_LOCATION_ID && usingFallback),
                    onClick = { openPattern(pattern) },
                ),
            )
        }
    }
    val tilesInView = appSettled && (onStart || (tab == Tab.Favorites && !courseView))
    LaunchedEffect(tilesInView, tileRoutes.joinToString { it.id }, options) {
        if (!tilesInView) return@LaunchedEffect
        delay(TILE_SETTLE_MILLIS)
        while (true) {
            for (route in tileRoutes) {
                val known = placeTrips[route.id]
                val moment = OffsetDateTime.now()
                val fresh = known != null &&
                    known.loadedAt.isAfter(moment.minusMinutes(TILE_FRESH_MINUTES)) &&
                    (known.journey == null || known.journey.departure.isAfter(moment))
                if (fresh) continue
                try {
                    val found = source.journeys(route.from, route.to, options.copy(comfort = shownComfort), results = 1).journeys
                    placeTrips[route.id] = PlaceTrip(found.minByOrNull { it.departure }, OffsetDateTime.now())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The tile says that there is no journey; dated back, it is asked for again next round.
                    placeTrips[route.id] = PlaceTrip(null, moment.minusMinutes(TILE_FRESH_MINUTES))
                }
                delay(JOURNEY_REFRESH_PAUSE_MILLIS)
            }
            delay(TILE_ROUND_MILLIS)
        }
    }

    // The tiles of remembered connections show the next journey that rides their lines.
    val patternTiles = savedPatterns.take(MAX_ROUTE_TILES)
    LaunchedEffect(tilesInView, patternTiles.joinToString { it.id }, options) {
        if (!tilesInView) return@LaunchedEffect
        delay(TILE_SETTLE_MILLIS)
        while (true) {
            for (pattern in savedPatterns.take(MAX_ROUTE_TILES)) {
                val key = patternTileKey(pattern)
                val known = placeTrips[key]
                val moment = OffsetDateTime.now()
                val fresh = known != null &&
                    known.loadedAt.isAfter(moment.minusMinutes(TILE_FRESH_MINUTES)) &&
                    (known.journey == null || known.journey.departure.isAfter(moment))
                if (fresh) continue
                // "Current location" means where the user is now; without a position there is nothing to ask.
                val from = when {
                    pattern.from.id != CURRENT_LOCATION_ID -> pattern.from
                    usingFallback -> null
                    else -> pattern.from.copy(latitude = center.first, longitude = center.second)
                } ?: continue
                try {
                    val found = source.journeys(from, pattern.to, options.copy(comfort = shownComfort), results = PATTERN_TILE_RESULTS).journeys
                    placeTrips[key] = PlaceTrip(nextOfPattern(found, pattern.lines, moment.minusMinutes(1)), OffsetDateTime.now())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    placeTrips[key] = PlaceTrip(null, moment.minusMinutes(TILE_FRESH_MINUTES))
                }
                delay(JOURNEY_REFRESH_PAUSE_MILLIS)
            }
            delay(TILE_ROUND_MILLIS)
        }
    }

    // A remembered connection the loaded journeys do not show yet: later pages are asked for,
    // a few at most, until one of its journeys leaves within its window or the window is covered.
    val patternPages = remember(patternFilter?.id) { intArrayOf(0) }
    LaunchedEffect(patternFilter?.id, journeys?.size, journeysLoading, paging.loadingLater) {
        val pattern = patternFilter ?: return@LaunchedEffect
        val list = journeys ?: return@LaunchedEffect
        if (journeysLoading || paging.loadingLater || list.isEmpty() || patternPages[0] >= PATTERN_MAX_PAGES) return@LaunchedEffect
        val from = (journeyTime?.takeIf { !it.arrival }?.time ?: OffsetDateTime.now()).minusMinutes(1)
        if (patternView(list, pattern.lines, from)?.running == true) return@LaunchedEffect
        if (list.maxOf { it.departure }.isAfter(from.plusMinutes(PATTERN_WINDOW_MINUTES))) return@LaunchedEffect
        patternPages[0]++
        loadLater()
    }

    // One request for the position of the device, with the reason said where there is none
    // to be had. A position the device reported a moment ago still holds. Otherwise what it
    // knew last answers at once, and the fresh position follows and counts where it differs.
    // A new request ends the one before it.
    LaunchedEffect(locateRound) {
        val round = locateRound
        if (round == 0) return@LaunchedEffect
        locating = true
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val reported = lastFix[0]
            if (!hasLocationPermission(context)) {
                locationTrouble = LocationTrouble.NoPermission
            } else if (!manager.isLocationEnabled) {
                locationTrouble = LocationTrouble.SwitchedOff
            } else if (devicePosition != null && reported > 0L &&
                SystemClock.elapsedRealtime() - reported < POSITION_FRESH_MILLIS
            ) {
                startFromPosition(moved = false)
            } else {
                val known = lastKnownLocation(context)
                // Only a known position in the area answers early: whether the device has left
                // the area is for the fresh one to say.
                if (known != null && prefs.sourceChoice.covers(known.latitude, known.longitude)) {
                    startFromPosition(usePosition(known.latitude to known.longitude))
                }
                val fresh = currentLocation(context)?.also { recordWidgetPosition(context, it) } ?: known
                if (fresh != null) {
                    startFromPosition(usePosition(fresh.latitude to fresh.longitude))
                } else {
                    locationTrouble = LocationTrouble.NoFix
                }
            }
        } finally {
            // Ended by a newer request, this one leaves the state to it.
            if (locateRound == round) {
                locating = false
                // A start that waited for this position and got none: never silently nothing.
                if (originPending[0]) {
                    originPending[0] = false
                    if (destination != null) startNotFound()
                }
            }
        }
    }

    // While the app is in front and may ask, the device reports where it is: often while a
    // trip is followed, at a calmer pace otherwise. This one listener serves the start page,
    // the map and the companion. Leaving the app ends the updates.
    // The map follows the user while riding: then the position comes as often as on a trip.
    val mapFollowsUser = mapShown && (onStart || onMapTab) && !viewHeld
    val ridingFollowed = riding && mapFollowsUser
    // The own position is on the map: then fixes come every second (precise while the battery
    // saver is off) for the dot alone, and the rest of the page keeps its pace (see below). The
    // calm companion has no map, so no dot: its page takes a precise fix every few seconds (#71).
    val dotShown = walkView || (tripView && !companionCalmHere()) || (mapShown && (onStart || onMapTab))
    val savingBattery = BatterySaver.active
    // At "Extrem" the position comes only when asked for (the locate button, a walk shown on the map).
    val positionOnAsk = BatterySaver.extreme && !walkView
    // Standing on the start page or the map tab, the dot needs no fix every second (see dotLocationAsk).
    val dotOnly = mapShown && (onStart || onMapTab) && !(tripView || walkView || ridingFollowed)
    val dotMoving = dotOnly && ownMoving
    // Back to standing once no sign of moving came for a while, also when no fix comes at all.
    LaunchedEffect(ownMoving) {
        while (ownMoving) {
            delay(STANDING_AFTER_MILLIS / 2)
            if (!movementWatch.moving(SystemClock.elapsedRealtime())) ownMoving = false
        }
    }
    DisposableEffect(appStarted, locationGranted, tripView, walkView, ridingFollowed, dotShown, savingBattery, positionOnAsk, dotOnly, dotMoving) {
        if (!tripView || !locationGranted) ownLocation = null
        if (!appStarted || !locationGranted || !hasLocationPermission(context) || positionOnAsk) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val following = tripView
        // A walk on the map follows the position as closely as a trip does, and so does the map
        // that follows a user who rides.
        val often = following || walkView || ridingFollowed
        val paceMillis = if (often) LOCATION_INTERVAL_MILLIS else POSITION_INTERVAL_MILLIS
        val paceMeters = if (often) LOCATION_MIN_METERS else POSITION_MIN_METERS
        // The last fix the page took (elapsed realtime ms, 0 for none) and where.
        val paced = LongArray(1)
        val pacedAt = DoubleArray(2)
        val listener = LocationListener { location ->
            if (location.hasSpeed()) {
                if (!riding && location.speed > RIDING_SPEED) riding = true
                if (riding && location.speed < WALKING_SPEED) riding = false
            }
            // A sign of moving switches the dot's fixes to every second at once.
            val moves = movementWatch.fix(
                SystemClock.elapsedRealtime(),
                location.latitude,
                location.longitude,
                if (location.hasSpeed()) location.speed else Float.NaN,
                if (location.hasAccuracy()) location.accuracy else 0f,
            )
            if (moves != ownMoving) ownMoving = moves
            // Every fix moves the dot; the page takes one at its own pace only, so the many
            // fixes for the dot do not build the page again each second.
            ownDot.deviceFix(location)
            val at = SystemClock.elapsedRealtime()
            if (paced[0] != 0L && (at - paced[0] < paceMillis - PACE_SLACK_MILLIS ||
                    metresApart(pacedAt[0], pacedAt[1], location.latitude, location.longitude) < paceMeters)
            ) {
                return@LocationListener
            }
            paced[0] = at
            pacedAt[0] = location.latitude
            pacedAt[1] = location.longitude
            val place = location.latitude to location.longitude
            // The widgets get no position of their own in the background: they take this one.
            recordWidgetPosition(context, location)
            // On a walk the traveller may leave its way: then a new one is asked for.
            if (often) watchWalk(location)
            if (following && prefs.sourceChoice.covers(place.first, place.second)) ownLocation = place
            val moved = usePosition(place)
            // A start that waits for the position gets it from here as well.
            if (originPending[0]) startFromPosition(moved)
        }
        // What is known already bridges the time until the first update.
        if (following) ownLocation = devicePosition ?: readLocation(context, prefs.sourceChoice)
        val ask = dotLocationAsk(
            dotOnly = dotOnly,
            moving = dotMoving,
            dotMillis = if (savingBattery) DOT_SAVER_INTERVAL_MILLIS else DOT_INTERVAL_MILLIS,
            precise = !savingBattery,
            base = LocationAsk(
                intervalMillis = when {
                    !dotShown -> paceMillis
                    savingBattery -> DOT_SAVER_INTERVAL_MILLIS
                    else -> DOT_INTERVAL_MILLIS
                },
                minMeters = if (dotShown) 0f else paceMeters,
                precise = often || (dotShown && !savingBattery),
            ),
        )
        startLocationUpdates(
            manager = manager,
            listener = listener,
            intervalMillis = ask.intervalMillis,
            minMeters = ask.minMeters,
            precise = ask.precise,
            executor = context.mainExecutor,
        )
        onDispose { stopLocationUpdates(manager, listener) }
    }

    // The new way of a re-routed walk came in: drawn from now on, and its length gives the
    // minutes still to walk. Without one (no way found, no answer) the walk keeps what it had.
    // Waits on the ways that come in here, not by a state of the page: that would draw it all again for every way.
    LaunchedEffect(walkDetour?.pending, walkDetour?.from) {
        if (walkDetour?.pending != true) return@LaunchedEffect
        FootPaths.version.collect {
            val state = walkDetour ?: return@collect
            if (!state.pending) return@collect
            when (FootPaths.statusBetween(state.from, state.to)) {
                FootWay.Real -> {
                    val path = FootPaths.wayBetween(state.from, state.to)
                    walkDetour = state.copy(pending = false, path = path, remainingMeters = routeMeters(path), at = OffsetDateTime.now())
                    offRoute.reset()
                    FootPaths.setDetour(state.leg, path)
                }
                FootWay.Straight, FootWay.Short -> walkDetour = state.copy(pending = false)
                FootWay.Pending -> Unit
            }
        }
    }
    // The trip ended or another one is followed: its walk is no longer watched.
    LaunchedEffect(companion?.id) {
        if (walkFocus == null) endWalkWatch()
    }

    // A closed search forgets where it was opened from.
    LaunchedEffect(searchMode) {
        if (searchMode == null) searchOrigin = null
    }

    // Open connections follow the live data. The picked journey comes first, then the ones
    // in view, then the rest in order; journeys that have arrived are left alone.
    val connectionsOpen = appStarted && tab == Tab.Route && journeys != null && !tripOpen && !courseView
    LaunchedEffect(connectionsOpen) {
        if (!connectionsOpen) return@LaunchedEffect
        while (true) {
            delay(BatterySaver.pace(JOURNEY_REFRESH_MILLIS))
            val loaded = journeys.orEmpty()
            val picked = connectionsState.selectedId
            val inView = if (planning) {
                planIds
            } else {
                connectionsState.chartState.layoutInfo.visibleItemsInfo.map { it.key }
            }
            val moment = OffsetDateTime.now()
            val round = loaded
                .filter { it.refreshable && it.arrival.isAfter(moment) }
                .filter { it.departure.isBefore(moment.plusHours(LIVE_HORIZON_HOURS)) }
                .sortedBy { journey ->
                    when (journey.id) {
                        picked -> 0
                        in inView -> 1
                        else -> 2
                    }
                }
                .take(MAX_REFRESH_PER_ROUND)
            for (old in round) {
                try {
                    // A journey that shows its stops on the way gets them with fresh times.
                    val withStops = old.legs.any { it.stopovers.isNotEmpty() }
                    source.refreshed(old, stopovers = withStops, polylines = false)
                        ?.let { fresh -> if (replaceJourney(old, fresh)) LiveFreshness.mark(LiveSource.Journeys) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Silent: the journey keeps its last known times until the next round.
                }
                delay(JOURNEY_REFRESH_PAUSE_MILLIS)
            }
        }
    }

    // A pinned place is named after what lies next to it.
    LaunchedEffect(pinned) {
        pinnedNeighbour = null
        pinnedDetails = null
        pinLookup = true
        val pin = pinned ?: return@LaunchedEffect
        // Its full name and address, where the device can tell them.
        launch {
            pinnedDetails = reverseGeocode(context, pin.first, pin.second)
            pinLookup = false
        }
        try {
            pinnedNeighbour = source.nearestPlace(pin.first, pin.second)?.name
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Without an answer the place keeps its plain name.
        }
    }

    // The open line course: all of it once, then only its times again while it stays open
    // and the app is on screen.
    val courseId = courseRequest?.tripId
    LaunchedEffect(courseId, appStarted) {
        if (courseId == null) {
            course = null
            courseFailed = false
            return@LaunchedEffect
        }
        if (!appStarted) return@LaunchedEffect
        if (course?.tripId != courseId) {
            course = null
            courseFailed = false
            // A new course looks at its vehicle again; where the line is not to be framed, the map stays.
            courseCamera.open(follow = courseFrame.courseFit[0])
            try {
                val loaded = source.tripCourse(courseId)
                val path = thinned(loaded.path)
                // Named as the ride, departure or vehicle it was opened from (see CourseRequest).
                course = courseAsRequested(loaded.copy(path = path), courseRequest)
                if (courseFrame.courseFit[0]) {
                    // The list of stops comes in with the course: the frame waits until it stands,
                    // so the room between header and list is measured as it is.
                    delay(COURSE_FIT_WAIT_MILLIS)
                    // From a tapped vehicle the camera follows it with its next stops (see CourseFollow).
                    val request = courseRequest
                    if (request?.fromVehicle != true || request.tripId != courseId) {
                        fitCamera(path.ifEmpty { loaded.stops.mapNotNull { it.place?.position } }, topDp = 130, bottomDp = 360)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                courseFailed = true
                return@LaunchedEffect
            }
        }
        while (true) {
            delay(BatterySaver.pace(COURSE_REFRESH_MILLIS))
            try {
                val stops = source.tripStopovers(courseId)
                if (stops.isNotEmpty()) course = course?.copy(stops = stops)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The course keeps its last known times.
            }
        }
    }

    // The live position of the vehicle of the open course: taken over from the map of all
    // vehicles where it was there, then asked for around where it is (or, without a report
    // yet, where the timetable has it) while the course stays open. A report that is not
    // renewed for a while gives way to the timetable again.
    LaunchedEffect(courseId, appStarted) {
        if (courseId == null) {
            courseLive = null
            return@LaunchedEffect
        }
        if (courseLive?.vehicle?.tripId != courseId) {
            courseLive = vehicles.firstOrNull { it.tripId == courseId }?.let { LiveVehicle(it, vehiclesSince) }
        }
        if (!appStarted) return@LaunchedEffect
        val spot = DoubleArray(2)
        while (true) {
            delay(if (courseLive == null) COURSE_LIVE_FIRST_MILLIS else BatterySaver.pace(COURSE_LIVE_MILLIS))
            val known = courseLive
            val around = when {
                known != null && known.locate(SystemClock.uptimeMillis(), spot) -> spot[0] to spot[1]
                latestCourseTrack?.takeIf { it.tripId == courseId }?.locate(System.currentTimeMillis(), spot) == true -> spot[0] to spot[1]
                else -> null
            } ?: continue
            try {
                val found = source.vehicles(
                    north = around.first + COURSE_LIVE_SPAN,
                    west = around.second - COURSE_LIVE_SPAN * 1.6,
                    south = around.first - COURSE_LIVE_SPAN,
                    east = around.second + COURSE_LIVE_SPAN * 1.6,
                    results = MAX_VEHICLES,
                ).firstOrNull { it.tripId == courseId }
                val nowMillis = SystemClock.uptimeMillis()
                courseLive = when {
                    found != null -> LiveVehicle(found, nowMillis)
                    known != null && nowMillis - known.since < COURSE_LIVE_STALE_MILLIS -> known
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The last report stays until it is stale.
            }
        }
    }

    // The walks on the map follow real ways on foot: asked for the journeys on screen, that is
    // the options of the plan, the journey detail and the followed trip (again whenever one of
    // them changes, a trip refreshed with a new walk say), and the walk on the big map before
    // all others. The ways load on in the background when the page changes (see FootPaths).
    val footJourneys = buildList {
        if (planView) addAll(planShown)
        if (showingConnections && !planning) journeys?.firstOrNull { it.id == connectionsState.selectedId }?.let { add(it) }
        if (tripView) companion?.let { add(it) }
    }
    LaunchedEffect(footJourneys) {
        FootPaths.want(footJourneys)
    }
    val footFocus = walkFocus
    LaunchedEffect(footFocus) {
        val focus = footFocus ?: return@LaunchedEffect
        focus.journey?.let { FootPaths.want(listOf(it)) }
        FootPaths.wantWalk(focus.walk)
    }

    // While planning, the map shows all route options at once.
    val planKey = if (planning && journeys != null) planShown.joinToString { it.id } else null
    LaunchedEffect(map, planKey) {
        if (planKey == null) return@LaunchedEffect
        val points = planShown.flatMap { journey -> journey.legs.flatMap { it.path } }
        fitCamera(points, topDp = 170, bottomDp = 190)
    }

    // Another route option picked while planning (in the list or on the map): the camera frames
    // the whole of that route, even after the user zoomed in. The first pick of a search is left
    // to the frame of all options above; this one is declared after it, so its move wins when a
    // pick also shifts the options shown.
    val planPickKey = if (planning && journeys != null) planPicked?.id else null
    val lastPlanPick = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(map, planPickKey) {
        val previous = lastPlanPick[0]
        lastPlanPick[0] = planPickKey
        if (planPickKey == null || previous == null || previous == planPickKey) return@LaunchedEffect
        // A new search brings other options: the option before is not among them, and the
        // frame of all options stays.
        if (visibleJourneys.orEmpty().none { it.id == previous }) return@LaunchedEffect
        val picked = planPicked?.takeIf { it.id == planPickKey } ?: return@LaunchedEffect
        val points = picked.legs.flatMap { leg ->
            leg.path.ifEmpty { listOfNotNull(leg.fromPlace?.position, leg.toPlace?.position) }
        }
        fitCamera(points, topDp = 170, bottomDp = 190)
    }

    fun swapEnds() {
        val start = origin ?: selectedStop ?: return
        val end = destination ?: return
        patternFilter = null
        origin = end
        destination = start
        searchJourneys()
    }

    fun pinnedPlace(position: Pair<Double, Double>, name: String): Stop = Stop(
        id = "pin:${position.first},${position.second}",
        name = name,
        latitude = position.first,
        longitude = position.second,
        distanceMeters = null,
        kind = PlaceKind.Address,
    )

    // Back is taken by the app wherever it leads somewhere inside it; on the start page it is
    // left to the system (back to the home screen, with its own animation). What one back does
    // comes from one table (see backStep), shared by goBack and its preview below.
    // A page of "My routes" over its first page: back stays in the tab (the screen stays the same).
    // Opened from the map tab, its first page is the one back leads from to the map.
    val favoritesSubOpen = tab == Tab.Favorites && favoritesPages.size > (if (favoritesFromMap) 1 else 0) && !courseView &&
        searchMode == null
    // The card of a stop tapped on the map of the start page (see startPickedStop): back lets go of it first.
    val startStopCard = mapShown && onStart && stopMarked && mapPickedStopId != null && mapPickedStopId == selectedStopId
    val backFacts = BackFacts(
        page = pageState,
        walk = walkView,
        pin = pinned != null && (onStart || onMapTab),
        pickedStop = startStopCard,
        positionCard = pickedPosition != null && onMapTab,
        stopCard = stopMarked && selectedStop != null && onMapTab && !mapFromDepartures,
        favoritesSub = favoritesSubOpen,
    )
    val step = backStep(backFacts, planFirst)

    fun goBack() {
        when (backStep(backFacts, planFirst)) {
            // A walk on the big map goes back to the page it was opened from.
            BackStep.CloseWalk -> closeWalk()
            // A marked place is let go first: the start sheet shows its own content again.
            BackStep.Unpin -> pinned = null
            // The card of a stop tapped on the start page's map, as its close button does.
            BackStep.ClosePick -> mapPickedStopId = null
            // On the map tab the card on top goes first: a stop position's, then the stop's.
            BackStep.ClosePosition -> pickedPosition = null
            BackStep.UnmarkStop -> stopMarked = false
            BackStep.LeaveCourse -> leaveCourse()
            BackStep.CloseSearch -> closeSearch()
            // A page opened on "My routes" goes back to the one below it, within the tab.
            BackStep.FavoritesPage -> if (favoritesPages.isNotEmpty()) favoritesPages.removeAt(favoritesPages.lastIndex)
            BackStep.CloseSettings -> settingsOpen = false
            BackStep.MapToDepartures -> tab = Tab.Departures
            // The page stays with the tab, as a tab keeps what it had open.
            BackStep.FavoritesToMap -> tab = Tab.Map
            BackStep.RouteTab -> tab = Tab.Route
            // The trip goes on; its card on the start page leads back to it.
            BackStep.CloseTrip -> companionOpen = false
            // From the connections back to the map with the options, where that step is
            // switched on, and from there back to the start.
            BackStep.ToPlan -> planning = true
            BackStep.CloseConnections -> closeConnections()
            BackStep.System -> tab = Tab.Route
        }
    }

    // Every back that changes what is on screen is predictive: the gesture shows where it
    // leads. Back to another page: the page in front shrinks with the swipe and rounds its
    // corners, the page before it lies underneath; let go, it goes; called off, it springs
    // back. Back within the page (see BackStep.inPage) runs [inPageBack], and the part of the
    // page that goes shows the same: a card or a page of "My routes" shrinks over what is
    // underneath, the start sheet swaps back to its content, the page of a walk rises again.
    // [inPageStep] is the step that preview is about while it runs.
    val backPage = if (step.inPage || step == BackStep.System) null else pageAfterBack(pageState, planFirst)
    val inPageBack = remember { BackPreview() }
    var inPageStep by remember { mutableStateOf(BackStep.System) }
    val backAnimate = rememberAnimationsOn()
    // This is the one back handler of the app. A back inside the page in front (the page of
    // one journey, "Advanced" of the settings, see PredictiveNestedBackHandler) is a layer of
    // it, not a handler of its own: where back goes is decided at the start of each gesture
    // from the state as it is then, never from the order in which handlers were added or
    // switched on, so back can neither reach a page underneath nor fall through to the
    // system (and close the app) while something in it is open.
    val backLayers = remember { BackLayers() }
    val topLayer = backLayers.top()
    // Once a gesture has started the handler stays on until it ends: switched off halfway the
    // system would call the gesture off.
    var backGesture by remember { mutableStateOf(false) }
    val backEnabled = backGesture || topLayer != null || step != BackStep.System
    val backActivity = remember(context) { context.backActivity() }
    SideEffect { BackLog.state(backEnabled, step, topLayer?.label, screen) }
    if (BackLog.on) SideEffect { BackProbe.front = if (topLayer != null) "layer:${topLayer.label}" else "page:$screen" }
    AppBackHandler(enabled = backEnabled) { events ->
        backGesture = true
        val milestones = BackMilestones()
        try {
            // A layer inside the page in front goes first; its page shows the preview.
            val layer = backLayers.top()
            if (layer != null) {
                BackLog.log("start handler=layer:${layer.label} screen=$screen pages=${BackLog.pages()}")
                layer.preview.follow(
                    events = events,
                    scope = scope,
                    animate = backAnimate,
                    onSeek = { shown ->
                        milestones.at(shown)
                        layer.onSeek(shown)
                    },
                    onCancel = {
                        BackLog.log("cancel layer:${layer.label}")
                        layer.onCancel()
                    },
                ) {
                    BackLog.log("commit layer:${layer.label}")
                    layer.onBack()
                }
                return@AppBackHandler
            }
            if (step == BackStep.System) {
                // On a moment too long (back leads out of the app now): back does what the
                // system does on the first page of an app.
                BackLog.log("start handler=root step=System (late) screen=$screen")
                events.collect { }
                // As the system does: the task goes to the back from its root activity, any
                // other activity (opened on top of another app's task) simply finishes.
                val activity = backActivity
                if (activity == null || activity.isTaskRoot) {
                    BackLog.log("commit System: task to back")
                    activity?.moveTaskToBack(true)
                } else {
                    BackLog.log("commit System: finish")
                    activity.finish()
                }
                return@AppBackHandler
            }
            // The page under the gesture as it is when the gesture starts.
            val target = backPage
            BackLog.log("start handler=root step=$step from=$screen target=${target ?: "-"} animate=$backAnimate pages=${BackLog.pages()}")
            if (target == null || !backAnimate) {
                // Back within the page (or without animations): the page shows it itself.
                inPageStep = step
                inPageBack.follow(
                    events = events,
                    scope = scope,
                    animate = backAnimate && step.inPage,
                    onSeek = { shown -> milestones.at(shown) },
                    onCancel = { BackLog.log("cancel $step") },
                ) {
                    BackLog.log("commit $step")
                    goBack()
                }
                return@AppBackHandler
            }
            val from = screen
            val gesture = ++backGestures[0]
            var committed = false
            try {
                // How the page goes (see BackStage): a page on the map back to a page on the
                // map leaves the map where it is and only its own UI shrinks; any other page
                // shrinks whole as a card over the page back leads to, a page on the map with
                // the picture of its map, so nothing of the page underneath shows through it.
                val fromGround = pageGround(from, startMap, planMap)
                val toGround = pageGround(target, startMap, planMap)
                // Left over from a gesture whose end was cut short by this one: reset before the
                // flags of this gesture are set, so the bars never show the old values with them.
                backStage.fade.snapTo(1f)
                backProgress.snapTo(0f)
                backStage.mapShot = false
                backStage.mapStays = mapShown && fromGround == PageGround.Map && toGround == PageGround.Map
                backStage.mapCard = mapShown && fromGround == PageGround.Map && !backStage.mapStays
                // The row of the status bar shows what the page back leads to shows there:
                // the plain ground where its map is off, which comes with the gesture.
                backStage.plainAfter = mapShown && toGround == PageGround.Plain
                // Off the plain ground onto the map: the ground gives way once let go.
                backStage.plainBefore = !mapShown && toGround != PageGround.Plain
                // Between a page on fog and one that is not: the fog in the rows of the bars
                // goes or comes with the gesture (see BackStage.barFog).
                backStage.fogBefore = fromGround == PageGround.Fog && toGround != PageGround.Fog
                backStage.fogAfter = fromGround != PageGround.Fog && toGround == PageGround.Fog
                // The map is no part of the page (one map lies under all of them, a surface of
                // its own that no transformation of the page reaches): a page that shows it
                // takes a picture of it along, which shrinks with the page, so the page in
                // front is whole and the page back leads to lies under it on its own ground.
                val shotMap = map
                if (backStage.mapCard && shotMap != null) {
                    val shot = withTimeoutOrNull(BACK_SHOT_MILLIS) { shotMap.awaitBackShot() }
                    BackLog.log(if (shot != null) "map picture ${shot.width}x${shot.height}" else "map picture missing")
                    backStage.mapShot = shot != null
                    backShot = shot?.asImageBitmap()
                    // The ways and marks are no part of the map's picture (they are drawn over
                    // it): their picture goes along too, so the page keeps its route.
                    backStage.marksShot = backStage.marks?.let { layer ->
                        try {
                            layer.toImageBitmap()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                    BackLog.log(if (backStage.marksShot != null) "marks picture" else "marks picture missing")
                }
                // Left over from a gesture whose end was cut short by this one.
                backStage.shots.snapTo(1f)
                backFrom = from
                backSwipe = true
                events.collect { event ->
                    backFromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                    backProgress.snapTo(event.progress)
                    screenSeek.seekTo(event.progress.coerceIn(0f, 1f), targetState = target)
                    milestones.at(event.progress)
                }
                committed = true
                BackLog.log("commit $step: $from -> $target")
                goBack()
            } finally {
                if (committed) {
                    // The rest of the way runs in the scope of AppRoot: going back may switch this
                    // handler off (back on the start page is the system's), which would cancel it
                    // halfway and leave the page shrunk.
                    scope.launch {
                        try {
                            // The page in front fades as it goes, from wherever the finger let
                            // go: it stays whole and opaque while the finger is on it.
                            coroutineScope {
                                launch { backStage.fade.animateTo(0f, tween(BACK_COMMIT_FADE_MILLIS, easing = LinearEasing)) }
                                screenSeek.animateTo(target, tween(BACK_PAGE_MILLIS))
                            }
                            BackLog.log("settled on $target (screen=${screenSeek.currentState})")
                        } finally {
                            if (backGestures[0] == gesture) endBack()
                        }
                    }
                } else {
                    BackLog.log("cancel $step: back to $from")
                    // Called off: the page grows back and the one underneath goes again. The
                    // change of the pages is not animated back by itself (its own animations
                    // and the spring of the page ran apart, and where they handed over the rows
                    // of the bars, which follow the change, see BackStage.toward, changed in one
                    // frame): it follows the page as it grows back, frame by frame, and settles
                    // on the page in front once that is whole, where the page underneath is gone.
                    scope.launch {
                        try {
                            coroutineScope {
                                val follow = launch {
                                    snapshotFlow { backProgress.value }.collect { shown ->
                                        screenSeek.seekTo(shown.coerceIn(0f, 1f), targetState = target)
                                    }
                                }
                                backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                follow.cancel()
                            }
                            screenSeek.seekTo(0f, targetState = target)
                            screenSeek.snapTo(from)
                            BackLog.log("called off: settled on $from")
                            // The pictures the page took along give way to the live map softly:
                            // a map that moved meanwhile (a followed trip) does not jump.
                            if (backShot != null || backStage.marksShot != null) {
                                backStage.shots.animateTo(0f, tween(BACK_SHOT_FADE_MILLIS))
                            }
                        } finally {
                            if (backGestures[0] == gesture) endBack()
                        }
                    }
                }
            }
        } finally {
            milestones.done()
            backGesture = false
        }
    }
    // The preview within the page, for the part of it that [inPageStep] is about.
    fun inPagePreview(vararg steps: BackStep): BackPreview? = inPageBack.takeIf { inPageStep in steps }

    // A tap on the map while it shows the route options: the option whose way lies nearest
    // to the tap is picked, if one lies near enough. The option in front is left out, so a
    // tap where ways share a stretch reaches the one below.
    val pickOnMap by rememberUpdatedState<(MapLibreMap, LatLng) -> Boolean>(
        { readyMap, point ->
            var nearest: String? = null
            if (planView) {
                val tap = readyMap.projection.toScreenLocation(point)
                var reach = PLAN_TAP_DP * density
                planShown.forEach { journey ->
                    if (journey.id != planPicked?.id) {
                        val distance = screenDistance(readyMap, journey, tap)
                        if (distance < reach) {
                            reach = distance
                            nearest = journey.id
                        }
                    }
                }
            }
            val found = nearest
            if (found != null) connectionsState.selectedId = found
            found != null
        },
    )

    // The station of the journey or line on the map (not the free map) at a point on the
    // screen, if one lies within reach of the finger: a tap or a long press on it opens the
    // menu of the stop, as on the free map. The same journey or line as the ways drawn (see mapMarks).
    val routeStationAt by rememberUpdatedState<(MapLibreMap, PointF) -> Stop?>(
        { readyMap, screen ->
            val focus = walkFocus
            val shownCourse = course
            val stations = when {
                walkView && !tripView -> focus?.journey?.let { journeyStations(it) }.orEmpty()
                courseView -> shownCourse?.let { courseStations(it.stops) }.orEmpty()
                tripView -> companion?.let { journeyStations(it) }.orEmpty()
                planView -> planPicked?.let { journeyStations(it) }.orEmpty()
                else -> emptyList()
            }
            if (stations.isEmpty()) {
                null
            } else {
                val xs = FloatArray(stations.size)
                val ys = FloatArray(stations.size)
                stations.forEachIndexed { index, station ->
                    val at = readyMap.projection.toScreenLocation(LatLng(station.latitude!!, station.longitude!!))
                    xs[index] = at.x
                    ys[index] = at.y
                }
                stations.getOrNull(nearestStationIndex(xs, ys, screen.x, screen.y, ROUTE_STATION_TAP_DP * density))
            }
        },
    )

    // True while the map shows the surroundings and nothing of its own, on the start page and
    // on the map tab alike: only then do moving it and a long press on it count. The
    // listeners of the map were made once and read it from here.
    val mapFree by rememberUpdatedState(onStart || onMapTab)
    val onMapTabNow by rememberUpdatedState(onMapTab)
    val tripViewNow by rememberUpdatedState(tripView)
    val walkViewNow by rememberUpdatedState(walkView)

    // The vehicles of the map named and coloured as the rides the app shows (the followed trip,
    // the journey in view, the open course), where one is the run of such a ride: the
    // provider's vehicles may name a run by the line it began as. All others keep their own.
    val openCourseRequest = courseRequest
    val shownRideNames = buildList {
        companion?.let { addAll(rideNames(it.rides)) }
        journeys?.firstOrNull { it.id == connectionsState.selectedId }?.let { addAll(rideNames(it.rides)) }
        if (openCourseRequest != null) {
            val requested = openCourseRequest.line?.takeIf { it.isNotBlank() && it != "?" }
            if (requested != null) {
                add(RideName(openCourseRequest.tripId, requested, openCourseRequest.product, openCourseRequest.color, openCourseRequest.textColor))
            }
        }
    }
    val shownVehicles = remember(vehicles, shownRideNames) { withRideLines(vehicles, shownRideNames) }

    // A tapped vehicle opens the course of its trip, like a tapped departure does; false when
    // the vehicle is no longer known.
    val pickVehicle by rememberUpdatedState<(String) -> Boolean>(
        { tripId ->
            val vehicle = shownVehicles.firstOrNull { it.tripId == tripId }
            if (vehicle != null) {
                // The course goes on from the very position the map shows.
                courseLive = LiveVehicle(vehicle, vehiclesSince)
                openCourse(
                    vehicle.tripId, vehicle.line, vehicle.product, vehicle.direction, fromStop = null, fit = fitLine,
                    color = vehicle.providerColor, textColor = vehicle.providerTextColor, at = vehicle.ridePlace,
                    fromVehicle = true,
                )
            }
            vehicle != null
        },
    )

    // A tapped stop sign on the map selects that stop, as its pin did.
    // A sign the map knows only from OpenStreetMap is first resolved to the provider's stop
    // nearby (by name, else the nearest), whose departures the card then loads.
    val pickStop by rememberUpdatedState<(String) -> Unit>({ stopId ->
        val osmStop = if (isOsmOnlyStop(stopId)) osmMapStops.firstOrNull { it.id == stopId } else null
        val at = osmStop?.position
        if (osmStop == null || at == null) {
            if (!isOsmOnlyStop(stopId)) {
                selectStop(stopId)
                mapPickedStopId = stopId
                val tapped = stops.firstOrNull { it.id == stopId } ?: foundStops.firstOrNull { it.id == stopId }
                focusTapped(tapped?.position)
            }
        } else {
            // The camera goes to the sign at once; the card follows once its stop is found.
            focusTapped(at)
            scope.launch {
                val nearby = try {
                    source.nearbyStops(at.first, at.second, 8)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                // The parts of a station as one stop, as on the map (see stationGroups).
                val stations = stationGroups(nearby, ::osmStationComplex).map { group ->
                    if (group.members.isNotEmpty()) source.joinComplex(group.main.id, group.members.map { it.id })
                    group.sign
                }
                val found = resolveOsmStop(osmStop, stations)
                if (found == null) {
                    Toast.makeText(context, R.string.map_stop_unresolved, Toast.LENGTH_SHORT).show()
                } else {
                    val picked = found.copy(distanceMeters = null)
                    // Kept with the stops found by name, so it stays selected wherever the map loads stops.
                    foundStops = (listOf(picked) + foundStops.filter { it.id != picked.id }).take(8)
                    selectStop(picked.id)
                    mapPickedStopId = picked.id
                }
            }
        }
    })

    // The app's own layers (network, vehicles, stops) belong to the surroundings: hidden while a
    // journey or a line is on the map.
    val ownLayersShown = mapShown && (onStart || onMapTab)
    LaunchedEffect(map, styleTick, ownLayersShown, StationDetailSetting.shown, EntranceSetting.mode, PerfLab.hiddenLayers) {
        map?.style?.takeIf { it.isFullyLoaded }?.let {
            showAppLayers(it, ownLayersShown)
            // Debug and staging builds: the layer groups switched off in the performance lab.
            if (PerfLab.available) applyPerfLabLayers(it)
        }
    }
    // The packs of other German states, where the app has them: those where the map is.
    // Whoever added a pack (this map, a journey search, a call cut short by a newer map
    // position), the details of all stations are made anew from it, so entrances and lifts
    // show the same way in every state as in Berlin.
    val osmGeneration by OsmStations.generations.collectAsState()
    // With journeys to rate, the change walks are read off the main thread before the first
    // rating asks for them there (see OsmStations.warmWalks).
    val journeysToRate = journeys != null
    LaunchedEffect(journeysToRate, osmLoaded, osmGeneration) {
        if (journeysToRate && osmLoaded) OsmStations.warmWalks()
    }
    LaunchedEffect(osmLoaded, mapCenter) {
        val center = mapCenter ?: return@LaunchedEffect
        if (!osmLoaded) return@LaunchedEffect
        // Zoomed far out the map shows nothing of these packs (no signs below the district
        // tier), but every rest of the camera over another part of the country read more of
        // them: the data grew with each, everything made from it (station details, signs,
        // notices) was made anew over all of it, and the app stuttered until it was closed.
        val zoom = map?.cameraPosition?.zoom ?: return@LaunchedEffect
        if (zoom < OSM_REGIONS_MIN_ZOOM) return@LaunchedEffect
        // A new pack is read whole and makes the details, signs and notices anew: in the first
        // seconds of the map that waits until it rests a moment (see MapQuiet).
        if (MapQuiet.fresh()) MapQuiet.awaitRest(OSM_PACKS_AFTER_SHOWN_MILLIS)
        OsmStations.ensureRegions(listOf(center))
    }
    LaunchedEffect(map, styleTick, osmLoaded, osmGeneration) {
        if (!osmLoaded) return@LaunchedEffect
        // Built off the main thread (cached per generation), then handed to the map while it
        // rests: some ten thousand features into one source, cut into tiles anew.
        prepareStationDetails()
        MapQuiet.awaitRest(0L)
        map?.style?.takeIf { it.isFullyLoaded }?.let { style ->
            val started = SystemClock.uptimeMillis()
            setStationDetails(style)
            val took = SystemClock.uptimeMillis() - started
            if (took > PERF_SLOW_MAIN_MILLIS) DiagLog.w(PERF_LOG_TAG) { "station details generation $osmGeneration set in $took ms on the main thread" }
        }
    }
    // The colours of the stop signs follow the look of the app.
    val signColors = StopSignColors(
        ground = AppColors.Card.toArgb(),
        poi = AppColors.Blue.toArgb(),
        address = AppColors.Muted.toArgb(),
    )
    val currentSignColors by rememberUpdatedState(signColors)
    LaunchedEffect(map, styleTick, signColors) {
        map?.style?.takeIf { it.isFullyLoaded }?.let { setStopSignColors(it, density, signColors) }
    }
    // The stops of the surroundings as signs in the map's own layer, by the zoom tier: none
    // for the whole city, only stations of the rapid network for a district, all of them
    // nearby. The marked stop is drawn apart, in a layer of its own (see setSelectedStop).
    val markedStopId = if (stopMarked) selectedStopId else null
    // The stations and stop areas of the offline data on the screen (and a margin), once the map
    // rests: only stations for a district, all of them nearby, none for the whole city. Built
    // off the main thread from the cells OsmStations keeps anyway.
    LaunchedEffect(mapBox, tier, osmLoaded, osmGeneration) {
        val box = mapBox
        // Zoomed out to the city the signs are not drawn; the stops found so far stay, so
        // zooming back in to the same place needs no new ones (and no new signs).
        if (tier == MapTier.City) return@LaunchedEffect
        if (box == null || !osmLoaded) {
            if (osmMapStops.isNotEmpty()) osmMapStops = emptyList()
            return@LaunchedEffect
        }
        delay(120)
        val grown = grownBox(box)
        osmMapStops = withContext(Dispatchers.Default) {
            AllocProbe.measureAny(AllocProbe.MAP_STOPS, { it.size }) {
                OsmStations.mapStops(grown[0], grown[1], grown[2], grown[3], stationsOnly = tier == MapTier.District)
            }
        }
    }
    val markedForSigns = selectedStop?.takeIf { it.id == markedStopId }
    // Merging the stops and grouping them into stations takes a while with many stops: it runs
    // off the main thread, so a map that comes to rest or the map tab that opens does not
    // stutter meanwhile. The signs shown so far stay until the new ones are ready.
    val signsInBackground = PerfLab.signsInBackground
    var signStops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    LaunchedEffect(stops, osmMapStops, tier, markedStopId, markedForSigns, ownLayersShown, osmGeneration, signsInBackground) {
        val providerStops = stops
        val offlineStops = osmMapStops
        val signTier = tier
        val marked = markedForSigns
        val markedId = markedStopId
        val shown = ownLayersShown
        // Zoomed out to the city the sign layer draws nothing (it starts at CITY_ZOOM): the
        // signs stay as they are, so zooming back in does not build and put them anew.
        if (shown && signTier == MapTier.City) return@LaunchedEffect
        fun buildSigns(): List<Stop> = if (!shown) {
            emptyList<Stop>()
        } else {
            // The provider's stops with their ids, and the offline ones no provider stop stands for;
            // one sign per station, none beside the marked one (see stationSigns).
            val all = stationSigns(mergeMapStops(providerStops, offlineStops, marked), marked, ::osmStationComplex)
            when (signTier) {
                MapTier.City -> emptyList<Stop>()
                MapTier.District -> all.filter { stop -> stop.id != markedId && stop.products.any(::isMajorProduct) }
                MapTier.Neighbourhood -> all.filter { stop -> stop.id != markedId }
            }
        }
        fun build(): List<Stop> = AllocProbe.measureAny(AllocProbe.SIGNS_BUILD, { it.size }) { buildSigns() }
        val built = if (!shown || signTier == MapTier.City || !signsInBackground) build() else withContext(Dispatchers.Default) { build() }
        if (built.size > PERF_MANY_SIGNS) DiagLog.w(PERF_LOG_TAG) { "${built.size} stop signs at tier $signTier (${offlineStops.size} offline stops)" }
        if (built != signStops) signStops = built
    }
    // At the start the signs fade in once they are there, a step after the map (see StopSignsIntro).
    StopSignsIntro(map = map, styleTick = styleTick, hasSigns = signStops.isNotEmpty())
    // With the setting on, bus and tram stops show their name in green close up instead of the "H".
    val stopsNamed = StopNameSetting.named
    LaunchedEffect(map, styleTick, signStops, stopsNamed) {
        map?.style?.takeIf { it.isFullyLoaded }?.let { style ->
            setStopSigns(style, signStops, stopsNamed)
            // The marked stop's pills keep clear of the station signs now on the map.
            map?.cameraPosition?.let { camera -> declutterSelectedPositions(style, camera.zoom, camera.bearing) }
        }
    }
    // The marked stop at every tier: its sign with a green ring, its name in green below it and,
    // close up, its stop positions with the lines that leave from each; all still below the
    // vehicles. Only this stop shows its positions. Marking it springs the sign in and fades its
    // name and positions in; letting go (a tap on the bare map, the card's close or a swipe)
    // clears them.
    val markedStop = if (ownLayersShown) selectedStop?.takeIf { it.id == markedStopId } else null
    val animationsOn = rememberAnimationsOn()
    // The bar of the tabs comes in at the start after the cards (see IntroStep.NAV): it rises
    // and fades with the spring of the app. With animations off it is there at once.
    val navIntro = remember { Animatable(if (Startup.navIn || !animationsOn) 1f else 0f) }
    LaunchedEffect(navIntro) {
        if (navIntro.value >= 1f) return@LaunchedEffect
        try {
            awaitIntroStep(IntroStep.NAV)
            navIntro.animateTo(1f, Motion.settle())
        } finally {
            // Also when cut short: a page built again shows the bar at once.
            Startup.navIn = true
        }
    }
    val currentMarkedStop by rememberUpdatedState(markedStop)
    LaunchedEffect(map, styleTick, markedStop?.id, markedStop != null, osmLoaded, osmGeneration) {
        val style = map?.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val stop = currentMarkedStop
        setSelectedStop(style, stop)
        val positions = setSelectedStopPositions(style, stop)
        map?.cameraPosition?.let { camera -> declutterSelectedPositions(style, camera.zoom, camera.bearing) }
        if (stop == null) return@LaunchedEffect
        if (!animationsOn) {
            setSelectedStopAppear(style, stopHop(STOP_HOP_AT_REST))
            return@LaunchedEffect
        }
        // The sign hops up and lands, its name fades in, its positions pop in one by one.
        val total = stopHopMillis(positions)
        val clock = Animatable(0f)
        setSelectedStopAppear(style, stopHop(0f))
        clock.animateTo(total.toFloat(), tween(total.toInt(), easing = LinearEasing)) {
            setSelectedStopAppear(style, stopHop(value))
        }
        setSelectedStopAppear(style, stopHop(STOP_HOP_AT_REST))
    }
    // Disruptions on the map (see MapNoticeLayer, MapNoticeUi): a sign at the stops the notices
    // the app knows are about, from the city tier on, unless switched off in the settings. The
    // departures loaded anyway add theirs; an area the map rests in close up adds those of a
    // few big stops in view, at most once in a few minutes.
    val noticesOn = MapNoticeSetting.shown && ownLayersShown && tier != MapTier.City
    LaunchedEffect(departures) {
        if (departures.isNotEmpty()) MapNoticePool.putDepartures("stop", departures, selectedStop)
    }
    LaunchedEffect(nearbyDepartures) {
        if (nearbyDepartures.isNotEmpty()) MapNoticePool.putDepartures("nearby", nearbyDepartures, null)
    }
    LaunchedEffect(mapBox, noticesOn, appSettled) {
        val box = mapBox ?: return@LaunchedEffect
        if (!noticesOn || !appSettled) return@LaunchedEffect
        val zoom = map?.cameraPosition?.zoom ?: return@LaunchedEffect
        if (zoom < NOTICE_REFRESH_ZOOM) return@LaunchedEffect
        // Only once the map rests there a moment.
        delay(NOTICE_REFRESH_DELAY_MILLIS)
        MapNoticePool.refreshArea(source, box, stops)
    }
    var mapNoticeMarks by remember { mutableStateOf<List<NoticeMarker>>(emptyList()) }
    // Whether signs were on the map before: only signs that come where there were none fade in.
    val noticesWereShown = remember { BooleanArray(1) }
    // The sign tapped on the map: its notices in a sheet.
    var openNotice by remember { mutableStateOf<NoticeMarker?>(null) }
    val noticeVersion = MapNoticePool.version
    val hiddenNotices = HiddenNotices.list
    val noticeRouteIds = remember(savedRoutes, savedPatterns) {
        savedRoutes.map { it.id } + savedPatterns.map { SavedRoute(it.from, it.to).id }
    }
    LaunchedEffect(noticesOn, noticeVersion, hiddenNotices, osmLoaded, osmGeneration, noticeRouteIds, stops) {
        if (!noticesOn) {
            mapNoticeMarks = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        val hiddenIds = hiddenNotices.mapTo(HashSet()) { it.id }
        val known = stops
        val (seen, markers) = withContext(Dispatchers.Default) {
            AllocProbe.measureAny(AllocProbe.NOTICE_MARKS, { it.second.size }) {
                val items = MapNoticePool.known(context, noticeRouteIds)
                val names = MapNoticePool.namesFor(items, known, osmGeneration)
                items.mapTo(HashSet()) { it.id } to noticeMarkers(items, names, hiddenIds)
            }
        }
        HiddenNotices.prune(context, seen)
        mapNoticeMarks = markers
    }
    val noticeColors = noticeMarkColors()
    // Again when the signs change: each sits on the corner of its stop's sign (see NoticeSignSpots).
    LaunchedEffect(map, styleTick, mapNoticeMarks, noticeColors, signStops, markedStop?.id, stopsNamed) {
        val style = map?.style?.takeIf { it.isFullyLoaded } ?: return@LaunchedEffect
        val wasEmpty = !noticesWereShown[0]
        noticesWereShown[0] = mapNoticeMarks.isNotEmpty()
        // At the start the signs are held unseen until their step (after the map, its stop
        // signs and vehicles, see IntroStep), then fade in.
        val introHeld = animationsOn && !Startup.noticesIn
        if (introHeld) setNoticeAppear(style, 0f)
        setNoticeMarkers(style, mapNoticeMarks, density, noticeColors)
        if (introHeld) {
            awaitIntroStep(IntroStep.NOTICES)
            if (mapNoticeMarks.isNotEmpty()) Startup.noticesIn = true
        }
        // Signs that come where there were none fade in, unless animations are off.
        if (!animationsOn || (!wasEmpty && !introHeld) || mapNoticeMarks.isEmpty()) {
            setNoticeAppear(style, 1f)
            return@LaunchedEffect
        }
        val appear = Animatable(0f)
        setNoticeAppear(style, 0f)
        appear.animateTo(1f, tween(NOTICE_APPEAR_MILLIS, easing = LinearEasing)) { setNoticeAppear(style, value) }
        setNoticeAppear(style, 1f)
    }
    openNotice?.let { marker ->
        MapNoticeSheet(
            marker = marker,
            onAll = {
                favoritesPages.clear()
                favoritesPages.add(FavoritesPage.AllDisruptions)
                favoritesFromMap = tab == Tab.Map
                tab = Tab.Favorites
            },
            onDismiss = { openNotice = null },
        )
    }
    // Into the map while it rests: a new source of thousands of points is cut into tiles anew
    // and competes with the tiles of a moving map.
    LaunchedEffect(map, styleTick, rapidNetwork) {
        val network = rapidNetwork ?: return@LaunchedEffect
        MapQuiet.awaitRest(0L)
        map?.style?.takeIf { it.isFullyLoaded }?.let { AllocProbe.measure(AllocProbe.NETWORK_PUSH) { setRapidNetwork(it, network) } }
    }
    LaunchedEffect(map, styleTick, tramNetwork) {
        val network = tramNetwork ?: return@LaunchedEffect
        MapQuiet.awaitRest(0L)
        map?.style?.takeIf { it.isFullyLoaded }?.let { AllocProbe.measure(AllocProbe.NETWORK_PUSH) { setTramNetwork(it, network) } }
    }

    // What the card of a pinned place does, on the map tab and in the start sheet alike.
    val pinTitle = pinnedPoi ?: pinnedDetails?.title ?: pinnedName
    val placeFrom: () -> Unit = {
        val pin = pinned
        if (pin != null) {
            pinned = null
            tab = Tab.Route
            pickOrigin(pinnedPlace(pin, pinTitle))
        }
    }
    val placeTo: () -> Unit = {
        val pin = pinned
        if (pin != null) {
            pinned = null
            tab = Tab.Route
            pickDestination(pinnedPlace(pin, pinTitle))
        }
    }
    // "Save" asks in a dialog how to keep the place: among the saved places, or as a favourite place.
    val placeSave: () -> Unit = {
        val pin = pinned
        if (pin != null) placeToSave = pinnedPlace(pin, pinTitle)
    }
    val saveAsSaved: (Stop, String, PlaceIcon) -> Unit = { place, name, icon ->
        // The same place saved again moves to the top with its new name and icon.
        val entry = SavedPlace(id = UUID.randomUUID().toString(), icon = icon, name = name, place = place)
        updateSaved(listOf(entry) + savedPlaces.filter { it.place.id != place.id })
        placeToSave = null
        pinned = null
        Toast.makeText(context, R.string.saved_place_done, Toast.LENGTH_LONG).show()
    }
    val saveAsFavorite: (Stop, String, PlaceIcon) -> Unit = { place, name, icon ->
        placeToSave = null
        if (favoritePlaces.size >= MAX_FAVORITE_PLACES) {
            Toast.makeText(context, R.string.places_full, Toast.LENGTH_LONG).show()
        } else {
            // Icon and name can be changed in the settings, where the favourite places are kept.
            updatePlaces(favoritePlaces + FavoritePlace(icon, name = name, place = place))
            pinned = null
            tab = Tab.Route
            settingsOpen = true
            Toast.makeText(context, R.string.place_saved, Toast.LENGTH_LONG).show()
        }
    }
    // The content of the start sheet while a place is marked. Its values are taken now, so the
    // sheet still shows them while it swaps back.
    val startPin = if (mapShown && onStart) pinned else null
    // Else a stop tapped on its map (see mapPickedStopId), with its departures a tap away.
    val startPickedStop = if (startStopCard) {
        selectedStop
    } else {
        null
    }
    val startPinContent: (@Composable () -> Unit)? = startPin?.let { pin ->
        val shownTitle = pinTitle
        val shownAddress = pinnedDetails?.address
        val own = devicePosition
        val addressLoading = pinLookup && pinnedPoi == null
        val content: @Composable () -> Unit = {
            PlaceContent(
                position = pin,
                title = shownTitle,
                address = shownAddress,
                own = own,
                onFrom = placeFrom,
                onTo = placeTo,
                onSave = placeSave,
                onClose = { pinned = null },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
                loading = addressLoading,
            )
        }
        content
    } ?: startPickedStop?.let { picked ->
        val content: @Composable () -> Unit = {
            StopPickContent(
                stop = picked,
                onDepartures = {
                    mapPickedStopId = null
                    stopAction(picked, StopAction.Departures)
                },
                onFrom = {
                    mapPickedStopId = null
                    stopAction(picked, StopAction.From)
                },
                onTo = {
                    mapPickedStopId = null
                    stopAction(picked, StopAction.To)
                },
                onClose = { mapPickedStopId = null },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
            )
        }
        content
    }

    // The parts of the screen below are lambdas of their own, each taking along only what it
    // uses. Written inline, the content of the CompositionLocalProvider took along well over
    // 200 values of AppRoot in one method, and the dex verifier on the device refused the
    // whole of AppRoot for it (VerifyError, D8 runs out of registers). Keep each part small.
    // The map and its plain ground, the menu of a stop pressed long, the fog.
    val mapLayer: @Composable BoxScope.() -> Unit = {
        // The map switched off: while the pages change between a page on the map and one on the
        // plain ground, the map stays until the change is over, so it never goes in one frame;
        // the plain ground comes over it in the rows of the bars as the change goes (below),
        // the page on the plain ground brings its own in its room.
        val groundFrom = pageGround(pageTransition.currentState, startMap, planMap)
        val groundTo = pageGround(pageTransition.targetState, startMap, planMap)
        val mapHeld = !mapShown && groundFrom != groundTo && (groundFrom == PageGround.Map || groundTo == PageGround.Map)
        // The calm companion (no map, see ui/companion) lies on its own plain ground: while it
        // stands alone on screen there is no map at all (no view, no loading, no camera). Any
        // change of page (a back gesture too), the map button or a walk shown on the map makes
        // it anew, and it fades in once it has drawn its place.
        val calmRest = tripView && !walkView && companionCalmHere() &&
            pageTransition.currentState == Screen.Trip && pageTransition.targetState == Screen.Trip
        // Once not wanted the map stays a frame longer, hidden, so it rests before its view goes (#284).
        if (rememberMapKept(mapComposed(calmAlone = calmRest))) {
        ForgetMapWhenGone(mapState)
        MapBackground(
            latitude = followTarget.first,
            longitude = followTarget.second,
            interactive = onStart || onMapTab || planView || tripView || courseView || walkView,
            modifier = Modifier.fillMaxSize(),
            backdrop = backdrop,
            visible = (mapShown || mapHeld) && !calmRest,
            // A journey or a line on the map keeps it: a new position does not pull it away.
            // Neither does it once the user has moved the map by hand.
            follow = !(planView || tripView || courseView || walkView) && !viewHeld,
            // The camera follows the dot frame by frame then (OwnFollow): a near target is no step to take.
            followSlackMetres = if (devicePosition != null) OWN_FOLLOW_SLACK_METRES else 0.0,
            styleUrl = mapStyleUrl(Appearance.mapStyle, AppColors.palette.dark),
            // The big logo while the map loads only on the start page: over a page opened by a
            // link (a route from the website) it would lie in the middle of it.
            logo = onStart,
            onStyleLoaded = { style ->
                installAppLayers(style, density, currentSignColors)
                showAppLayers(style, mapFree)
                styleTick++
            },
            onMapReady = { readyMap ->
                map = readyMap
                // Where the map faces, for the compass and the pick on the map (see MapCompass.kt).
                MapCamera.attach(readyMap)
                // Heavy work that is no hurry waits for a quiet map (see MapQuiet).
                MapQuiet.mapReady()
                tier = mapTier(readyMap.cameraPosition.zoom)
                vehiclesClose = closeForVehicles(readyMap.cameraPosition.zoom)
                readyMap.addOnCameraMoveListener {
                    AllocProbe.begin(AllocProbe.CAMERA_MOVE)
                    MapQuiet.moved()
                    cameraTick++
                    // One camera position per move (each call makes a new one).
                    val camera = readyMap.cameraPosition
                    MapCamera.moved(camera.bearing)
                    val now = mapTier(camera.zoom)
                    if (now != tier) tier = now
                    val close = closeForVehicles(camera.zoom)
                    if (close != vehiclesClose) vehiclesClose = close
                    // The pills of the marked stop give way to each other as the zoom changes.
                    readyMap.style?.takeIf { it.isFullyLoaded }?.let { style ->
                        declutterSelectedPositions(style, camera.zoom, camera.bearing)
                    }
                    AllocProbe.end(AllocProbe.CAMERA_MOVE)
                }
                // A move by the finger takes the view in hand: positions that follow leave it alone.
                readyMap.addOnCameraMoveStartedListener { reason ->
                    MapQuiet.moveStarted()
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        mapGesture[0] = true
                        // A course stops following its vehicle until asked to again.
                        courseCamera.held = true
                    }
                    if (reason != MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && !mapGesture[2]) {
                        mapGesture[1] = true
                    }
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && mapFree) {
                        viewHeld = true
                    }
                    // On a followed trip the map stops framing it until the user asks for that again.
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && tripViewNow) {
                        tripFrameHeld = true
                    }
                    // A walk on the map stops following the position once it is moved by hand.
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && walkViewNow) {
                        walkFrameHeld = true
                    }
                }
                // A tap on the bare map lets go of a pinned place and of the marked stop, closes
                // an open line course, and picks a route option where it hits the way of one.
                readyMap.addOnMapClickListener { point ->
                    // A sign of disruptions sits on the corner of its stop's sign: a tap right on it
                    // opens its notices (drawn over signs and vehicles, so checked before both, with a small room only, so a tap
                    // on the stop's sign still selects the stop).
                    if (mapFree) {
                        val exactNotice = noticeAt(readyMap, readyMap.projection.toScreenLocation(point), NOTICE_TAP_DP * density)
                            ?.let { key -> mapNoticeMarks.firstOrNull { it.key == key } }
                        if (exactNotice != null) {
                            openNotice = exactNotice
                            return@addOnMapClickListener true
                        }
                    }
                    // A vehicle drawn by the map itself: a tap opens the course of its trip. The
                    // vehicles lie over the stop signs and positions, so a tap right on one
                    // hits it first; the wider room around a vehicle counts after the stops.
                    if (mapFree) {
                        val hit = vehicleAt(readyMap, readyMap.projection.toScreenLocation(point), STOP_TAP_DP * density)
                        if (hit != null && pickVehicle(hit)) return@addOnMapClickListener true
                    }
                    // A stop position pill of the marked stop on the map tab: its own card, with
                    // the departures from there, its lines and the way there on foot.
                    if (onMapTabNow) {
                        val positionHit = stopPositionAt(readyMap, readyMap.projection.toScreenLocation(point), STOP_TAP_DP * density)
                        if (positionHit != null) {
                            pickedPositionStop = selectedStopId
                            pickedPosition = positionHit
                            focusTapped(positionHit.latitude to positionHit.longitude)
                            return@addOnMapClickListener true
                        }
                    }
                    // A stop sign drawn by the map itself ("H" included): a tap selects the stop,
                    // whose card lists its lines.
                    if (mapFree) {
                        val stopHit = stopAt(readyMap, readyMap.projection.toScreenLocation(point), STOP_TAP_DP * density)
                        if (stopHit != null) {
                            pickStop(stopHit)
                            return@addOnMapClickListener true
                        }
                        // A sign of disruptions: the notices at its stop in a sheet.
                        val noticeHit = noticeAt(readyMap, readyMap.projection.toScreenLocation(point), STOP_TAP_DP * density)
                        val tappedNotice = noticeHit?.let { key -> mapNoticeMarks.firstOrNull { it.key == key } }
                        if (tappedNotice != null) {
                            openNotice = tappedNotice
                            return@addOnMapClickListener true
                        }
                        // An entrance, stop position or lift of OpenStreetMap: its name, said briefly.
                        val detail = stationDetailAt(context, readyMap, readyMap.projection.toScreenLocation(point), STOP_TAP_DP * density)
                        if (detail != null) {
                            Toast.makeText(context, detail, Toast.LENGTH_SHORT).show()
                            return@addOnMapClickListener true
                        }
                    }
                    if (mapFree) {
                        val hit = vehicleAt(readyMap, readyMap.projection.toScreenLocation(point), VEHICLE_TAP_DP * density)
                        if (hit != null && pickVehicle(hit)) return@addOnMapClickListener true
                    }
                    // A named place of the map (a shop, a park) is marked with its name.
                    if (mapFree) {
                        val poi = placeAt(readyMap, readyMap.projection.toScreenLocation(point), VEHICLE_TAP_DP * density)
                        if (poi != null) {
                            pinnedPoi = poi.first
                            pinned = poi.second
                            stopMarked = false
                            return@addOnMapClickListener true
                        }
                    }
                    // A station of the journey or line on the map: the menu of the stop, a
                    // tap away (no action of it only by a long press).
                    if (!mapFree) {
                        val screen = readyMap.projection.toScreenLocation(point)
                        val station = routeStationAt(readyMap, screen)
                        if (station != null) {
                            stopMenu = station.copy(distanceMeters = null) to PointF(screen.x, screen.y)
                            return@addOnMapClickListener true
                        }
                    }
                    pinned = null
                    if (mapFree) stopMarked = false
                    if (courseRequest != null) {
                        closeCourse()
                        true
                    } else {
                        pickOnMap(readyMap, point)
                    }
                }
                readyMap.addOnMapLongClickListener { point ->
                    // On the map of a journey or a line: a long press on one of its stations
                    // opens the menu of the stop too (a tap does the same, see above).
                    if (!mapFree) {
                        val screen = readyMap.projection.toScreenLocation(point)
                        val station = routeStationAt(readyMap, screen)
                        if (station != null) {
                            stopMenuHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            stopMenu = station.copy(distanceMeters = null) to PointF(screen.x, screen.y)
                            return@addOnMapLongClickListener true
                        }
                    }
                    // A long press on a stop sign opens the stop's small menu (its departures,
                    // from here, to here) instead of dropping a pin there.
                    if (mapFree) {
                        val screen = readyMap.projection.toScreenLocation(point)
                        val stopHit = stopAt(readyMap, screen, STOP_TAP_DP * density)
                        if (stopHit != null) {
                            val known = stops.firstOrNull { it.id == stopHit } ?: foundStops.firstOrNull { it.id == stopHit }
                            if (known != null && !isOsmOnlyStop(stopHit)) {
                                stopMenuHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                stopMenu = known.copy(distanceMeters = null) to PointF(screen.x, screen.y)
                            } else {
                                // A stop known only from OpenStreetMap is found first: it is selected, with its card.
                                pickStop(stopHit)
                            }
                            return@addOnMapLongClickListener true
                        }
                    }
                    // Not while a journey or a line is shown: the map belongs to them then.
                    if (mapFree) {
                        pinnedPoi = null
                        pinned = point.latitude to point.longitude
                        // The card of the place takes the place of the card of a stop.
                        stopMarked = false
                    }
                    true
                }
                readyMap.addOnCameraIdleListener {
                    MapQuiet.idle()
                    mapGesture.fill(false)
                    cameraTick++
                    val restedAt = readyMap.cameraPosition
                    MapCamera.rested(restedAt.bearing)
                    val target = restedAt.target
                    // While a journey or a line is on screen the map moves by itself: keep the stops where they are.
                    if (target != null && mapFree) mapCenter = target.latitude to target.longitude
                    if (mapFree) visibleBox(readyMap)?.let { if (it != mapBox) mapBox = it }
                }
            },
        )
        } else {
            NoMapStart()
        } // The map exists.

        // The map switched off: a calm plain ground takes its place, on the start page with
        // the quiet mark of the app in the free part above the sheet.
        // A back gesture from it onto a page on the map: it gives way once let go (see BackStage.plainBefore).
        if ((!mapShown && !mapHeld) || backStage.plainBefore || calmRest) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { if (backStage.plainBefore) alpha = backStage.fade.value }
                    .background(AppColors.PlainGround),
            )
        } else if (mapHeld || backStage.plainAfter) {
            // Between a page on the map and one on the plain ground (a change of page, or a back
            // gesture onto the plain ground and the way after it): the rows of the bars take the
            // plain ground as far as the change went (see BackStage.barPlain). Under a back
            // gesture onto the plain ground the rows are drawn over the ways (see BackBarRows).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Under a back gesture onto the plain ground the rows are BackBarRows'.
                        if (backStage.plainAfter) return@drawBehind
                        val shown = backStage.barPlain(groundTo == PageGround.Plain)
                        if (shown <= 0f) return@drawBehind
                        val top = backStage.pagesBox[0]
                        val bottom = backStage.pagesBox[1]
                        if (top > 0f) {
                            drawRect(AppColors.PlainGround, size = Size(size.width, top), alpha = shown)
                        }
                        if (bottom > 0f && bottom < size.height) {
                            drawRect(
                                AppColors.PlainGround,
                                topLeft = Offset(0f, bottom),
                                size = Size(size.width, size.height - bottom),
                                alpha = shown,
                            )
                        }
                    },
            )
        }
        if (!mapShown) {
            if (onStart) {
                StartLogoMark(
                    coverTop = { startLogoCover.value },
                    top = statusTop + MAP_TOP_COVER_DP * density,
                )
            }
        }

        // The menu of a stop pressed long on the map, at the point pressed.
        stopMenu?.let { (menuStop, at) ->
            Box(modifier = Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)) {
                StopMenu(
                    open = true,
                    onDismiss = { stopMenu = null },
                    onAction = { action -> stopAction(menuStop, action) },
                    // It is on the map already.
                    omit = setOf(StopAction.Map),
                    place = menuStop,
                )
            }
        }

        // Pages that do not need the map lie on fog (see pageGround). While the page stays on
        // fog it lies here, under every page; while a page changes to or from one on the map,
        // each page brings its own fog along (see the pages below), so that a page a back
        // gesture shrinks takes its fog with it and the page underneath lies on its own ground.
        if (startMap) {
            // The fog is the map itself, blurred, under a light veil. While the page changes
            // between a page on fog and one that is not, the pages bring their fog only into
            // their own room: the rows of the status bar and the gesture bar take it here, as
            // far as the change went (see BackStage.barFog), never in one frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val fogBefore = pageGround(pageTransition.currentState, startMap, planMap) == PageGround.Fog
                        val fogAfter = pageGround(pageTransition.targetState, startMap, planMap) == PageGround.Fog
                        // What lies in the rows of the bars from here, for the back probe.
                        backStage.rowMap = -1f
                        if (walkView || !(fogBefore || fogAfter)) return@drawWithContent
                        if (fogBefore && fogAfter) {
                            backStage.rowMap = 1f
                            // A page coming in after its first frame fades in on its own fog.
                            val entering = pageEntering[0]
                            if (entering != null && entering.alpha.value < 1f) return@drawWithContent
                            drawContent()
                            return@drawWithContent
                        }
                        // Under a back gesture onto fog the rows are drawn over the ways (see BackBarRows).
                        if (backStage.fogAfter) return@drawWithContent
                        val shown = backStage.barFog(fogAfter)
                        backStage.rowMap = shown
                        if (shown <= 0f) return@drawWithContent
                        // The float bounds of the platform canvas: no Rect made each frame.
                        val paint = backStage.fogPaint
                        paint.alpha = shown
                        val top = backStage.pagesBox[0]
                        val bottom = backStage.pagesBox[1]
                        if (top > 0f) {
                            clipRect(bottom = top) {
                                drawContext.canvas.nativeCanvas.saveLayer(0f, 0f, size.width, top, paint.asFrameworkPaint())
                                this@drawWithContent.drawContent()
                                drawContext.canvas.nativeCanvas.restore()
                            }
                        }
                        if (bottom > 0f && bottom < size.height) {
                            clipRect(top = bottom) {
                                drawContext.canvas.nativeCanvas.saveLayer(0f, bottom, size.width, size.height, paint.asFrameworkPaint())
                                this@drawWithContent.drawContent()
                                drawContext.canvas.nativeCanvas.restore()
                            }
                        }
                    }
                    .then(glassGround(tint = AppColors.FogTint, fallback = AppColors.Fog, highlight = Color.Transparent)),
            )
        }
    }
    // Ways, markers and the position on the map.
    val mapMarks: @Composable BoxScope.() -> Unit = {
        // Ways lie above the fog, so a journey stays readable behind its cards. They draw
        // themselves onto the map when they appear.
        val shownCourse = course
        val readyMap = map
        // Counts up when a real way on foot came in: the walks are drawn again along it.
        val footPaths by FootPaths.version.collectAsState()
        // Counts up when colours of lines came in later (a pack of OpenStreetMap, a provider's
        // colour): the ways drawn with them are drawn again. Read only by the ways below.
        val lineColorsVersion by LineColors.version.collectAsState()
        if (readyMap != null) {
            when {
                // A walk from the journey detail: its journey around it, else the walk alone.
                // (From the companion the followed trip below draws it.)
                walkView && !tripView -> key(walkFocus) {
                    val focus = walkFocus
                    val routes = remember(focus, footPaths, lineColorsVersion) {
                        when {
                            focus == null -> emptyList()
                            focus.journey != null && focus.journey.legs.any { it === focus.walk } -> journeyRoutes(focus.journey)
                            else -> journeyRoutes(focus.journey) + walkRoutes(focus.walk)
                        }
                    }
                    val badgeAt = remember(planView, planPicked) { if (planView) journeyEnd(planPicked) else null }
                    RouteLayer(map = readyMap, cameraTick = { cameraTick }, routes = routes, inFront = mapShown, badgeAt = badgeAt)
                }

                courseView -> if (shownCourse != null) {
                    key(shownCourse.tripId) {
                        // Drawn anew once colours of lines come in (a pack of OpenStreetMap loaded later).
                        // Again when the streets of a bus without a way of its own came in (see busRoad).
                        val routes = remember(shownCourse.path, shownCourse.stops, lineColorsVersion, footPaths) { courseRoutes(shownCourse) }
                        if (routes.isNotEmpty()) RouteLayer(map = readyMap, cameraTick = { cameraTick }, routes = routes, inFront = mapShown)
                    }
                }

                tripView -> key(companion?.id) {
                    val followed = companion
                    // Worked out again every two seconds (so the grey behind the vehicle keeps up
                    // with its mark, which moves by the same track) and whenever a trip of a ride was loaded.
                    val routeClock by produceState(OffsetDateTime.now()) {
                        while (true) {
                            delay(ROUTE_SPLIT_MILLIS)
                            value = OffsetDateTime.now()
                        }
                    }
                    val rideCourses = tripRideIds.map { tripCourseCache.tripCourses[it] }
                    val approachesShown = ApproachSetting.shown(context)
                    val glides = remember(followed, routeClock, rideCourses, footPaths, approachesShown) { ArrayList<GlideRide>() }
                    val routes = remember(followed, routeClock, rideCourses, footPaths, approachesShown) {
                        if (followed == null) {
                            emptyList()
                        } else {
                            val spot = DoubleArray(2)
                            tripRoutes(
                                followed,
                                routeClock,
                                tripCourseCache.tripCourses.toMap(),
                                approachesShown,
                                glides = glides,
                                trackOf = { leg -> leg.tripId?.let { tripCourseCache.tripTracks[it] } },
                                state = currentRideState(followed, routeClock),
                                fix = CompanionPosition.fixFor(followed, routeClock.toInstant().toEpochMilli()),
                            ) { leg ->
                                val track = leg.tripId?.let { tripCourseCache.tripTracks[it] }
                                // Split where its marker is drawn, else where the track puts it: the grey never runs ahead of the marker (#119).
                                if (track != null && (DrawnVehicles.locate(track.tripId, spot) || track.locate(routeClock.toInstant().toEpochMilli(), spot))) spot[0] to spot[1] else null
                            }
                        }
                    }
                    val badgeAt = remember(followed) { journeyEnd(followed) }
                    RouteLayer(map = readyMap, cameraTick = { cameraTick }, routes = routes, inFront = mapShown, badgeAt = badgeAt)
                    // The end of the grey glides with the vehicle between two splits (#105).
                    RouteSplitGlide(
                        map = readyMap,
                        cameraTick = { cameraTick },
                        rides = glides,
                        active = mapShown && !companionCalmHere() && rememberAnimationsOn(),
                    )
                }

                // Every option has a layer of its own: the picked one comes to the front in its
                // line colours and grows thicker, the others stay grey below it.
                planView -> Box(modifier = Modifier.fillMaxSize()) {
                    planShown.forEachIndexed { index, journey ->
                        key(journey.id) {
                            val routes = remember(journey, footPaths, lineColorsVersion) { journeyRoutes(journey) }
                            val badgeAt = remember(journey) { journeyEnd(journey) }
                            RouteLayer(
                                map = readyMap,
                                cameraTick = { cameraTick },
                                routes = routes,
                                badgeAt = badgeAt,
                                picked = journey.id == planPicked?.id,
                                order = index,
                                layered = true,
                                snake = true,
                                inFront = mapShown,
                            )
                        }
                    }
                }
            }
        }
        // The walk in focus: what OSM knows along it, over its way, at any zoom: the other stop
        // positions faint, the one it leads to ringed, entrances, lifts and escalators on it.
        val focusedWalk = walkFocus
        if (readyMap != null && focusedWalk != null) {
            key(focusedWalk) {
                val details = remember(focusedWalk, footPaths) { walkFocusDetails(focusedWalk.walk) }
                // The last walk of the journey on the map ends at its destination's badge.
                val badgeAt = remember(focusedWalk, planView, tripView) {
                    focusedWalk.journey?.takeIf { (planView || tripView) && it.legs.lastOrNull() === focusedWalk.walk }?.let { journeyEnd(it) }
                }
                WalkDetailsOverlay(map = readyMap, cameraTick = { cameraTick }, details = details, brokenLifts = rememberBrokenLifts(details), badgeAt = badgeAt)
            }
        }
        // Everything that marks a point comes after the ways, so no way is ever drawn across
        // a badge, a sign or a name. First the surroundings: vehicles, stops and a pinned
        // place. A journey or a line on the map shows its own markers instead.
        // Vehicles are drawn by the map in its own layers (see VehicleLayer), tapped via the map's click listener.
        VehicleLayer(
            map = map,
            styleTick = styleTick,
            vehicles = shownVehicles,
            moving = appStarted,
            // Off in the settings: no vehicle layers and no glide loop at all.
            shown = ownLayersShown && Appearance.mapVehicles != MapVehiclesChoice.Off,
            direction = if (Appearance.vehicleDirection == VehicleDirectionChoice.On) DEFAULT_DIRECTION_STYLE else null,
        )
        if (mapShown && (onStart || onMapTab)) {
            // Saved places stay on the map with their icon at every zoom, above the stops; a
            // tap opens their card, named as the user named them.
            run {
                // The saved places a size smaller, below the favourite places.
                FavoriteMarkers(
                    map = map,
                    cameraTick = { cameraTick },
                    places = savedPlaces.map { it.asFavorite() },
                    size = 24.dp,
                    keyPrefix = "saved",
                    onOpen = { favorite ->
                        val place = favorite.place
                        val lat = place?.latitude
                        val lon = place?.longitude
                        if (place != null && lat != null && lon != null) {
                            pinnedPoi = favorite.name.ifBlank { place.name.shortStopName() }
                            pinned = lat to lon
                            stopMarked = false
                        }
                    },
                )
                FavoriteMarkers(
                    map = map,
                    cameraTick = { cameraTick },
                    places = favoritePlaces,
                    onOpen = { favorite ->
                        val place = favorite.place
                        val lat = place?.latitude
                        val lon = place?.longitude
                        if (place != null && lat != null && lon != null) {
                            pinnedPoi = favorite.name.ifBlank { iconLabels.getValue(favorite.icon) }
                            pinned = lat to lon
                            stopMarked = false
                        }
                    },
                )
            }
            PinMarker(map = map, cameraTick = { cameraTick }, position = pinned)
        }
        // The stops of the open line course on its way, and its vehicle where it is right now.
        if (courseView && shownCourse != null) {
            val track = courseTrack?.takeIf { it.tripId == shownCourse.tripId } ?: TripTrack(shownCourse)
            // A report placed on an air line between stations (see Vehicle.onAirLine) gives way to the
            // track: on the real tracks where the course has them, else at a station.
            val live = courseLive?.takeIf { it.vehicle.tripId == shownCourse.tripId && (!it.vehicle.onAirLine || !track.usable) }
            // Where the vehicle is right now: the live report first, else the timetable.
            val vehicleAt: (DoubleArray) -> Boolean = { out ->
                if (live != null) live.locate(SystemClock.uptimeMillis(), out) else track.locate(System.currentTimeMillis(), out)
            }
            CourseMarkers(
                map = map,
                cameraTick = { cameraTick },
                course = shownCourse,
                fromStop = courseRequest?.fromStop,
                // The names of its ends stay whole between the header and the list.
                freeTop = { frameCover[0] },
                freeBottom = { frameCover[1] },
                // Into an array of CourseMarkers' own: no Pair for each frame.
                vehicleAt = vehicleAt,
            )
            // Opened from a vehicle: the camera stays on it (and its next stops) as it moves.
            if (courseRequest?.fromVehicle == true) {
                CourseFollow(
                    map = map,
                    camera = courseCamera,
                    course = shownCourse,
                    track = track,
                    vehicleAt = vehicleAt,
                    cover = frameCover,
                )
            }
            TrackMarkers(
                map = map,
                cameraTick = { cameraTick },
                tracks = listOf(track),
                live = if (live == null) emptyMap() else mapOf(live.vehicle.tripId to live),
                realtime = if (live != null && live.vehicle.realtime) setOf(live.vehicle.tripId) else emptySet(),
            )
        }
        // The stations of the journey on the map: start, changes and destination always named, stops on the way where there is room.
        if (planView || tripView) {
            JourneyLabels(
                map = map,
                cameraTick = { cameraTick },
                journey = if (tripView) companion else planPicked,
            )
        }
        // The vehicles of the lines of the followed trip: the one the traveller rides and
        // those to change to, also while they are still stations away or on the run that
        // brings them to where their ride begins. No other traffic here.
        if (tripView) {
            // Later rides' vehicles only with "Spätere Fahrzeuge zeigen"; else the traveller's own (#160).
            val markerRideIds = vehicleRides(
                tripRideIds.distinct(),
                ApproachSetting.shown(context),
                frameStep?.leg?.takeIf { !it.walking }?.tripId,
            )
            val tripTracks = markerRideIds
                .flatMap { listOfNotNull(tripCourseCache.feederTracks[it], tripCourseCache.tripTracks[it]) }
                .distinctBy { it.tripId }
            // A vehicle of the trip the map reports live (the same trip, by its handle) goes by its
            // reported position, as when its course is opened: the timetable only stands in without one.
            val trackIds = tripTracks.joinToString("|") { it.tripId }
            val tripLive = remember(vehicles, vehiclesSince, trackIds) {
                if (vehicles.isEmpty()) {
                    emptyMap<String, LiveVehicle>()
                } else {
                    val ids = tripTracks.mapTo(HashSet()) { it.tripId }
                    vehicles.filter { it.tripId in ids }.associate { it.tripId to LiveVehicle(it, vehiclesSince) }
                }
            }
            // The vehicle the traveller waits for stays at their stop until it shows it left (#184).
            val boardHolds by remember(companion) {
                derivedStateOf {
                    companion?.let { followed ->
                        boardingHolds(followed, currentRideState(followed, nowState.value)) { id -> tripCourseCache.tripCourses[id]?.stops }
                    }.orEmpty()
                }
            }
            TrackMarkers(
                map = map,
                cameraTick = { cameraTick },
                tracks = tripTracks,
                live = tripLive,
                holds = boardHolds,
                // A ride whose times come from live data (leg.realtime) carries the live dot, as does a vehicle reported live.
                realtime = tripRides.filter { it.realtime }.mapNotNull { it.tripId }.toSet() +
                    tripLive.values.filter { it.vehicle.realtime }.map { it.vehicle.tripId },
                // The traveller in or at one of them rides on its badge.
                own = ownDot,
            )
        }

        // The destination's badge over every way, mark, name and vehicle of the journey (#181c);
        // only the traveller's own dot goes over it.
        if (readyMap != null && (planView || tripView)) {
            val badgeJourney = if (tripView) companion else planPicked
            val badgeAt = remember(badgeJourney) { journeyEnd(badgeJourney) }
            // Arrived (the trip at its destination, or the own position there): the badge fades out (#222).
            val badgeArrived by remember(tripView, companion, badgeAt) {
                derivedStateOf {
                    tripView && companion?.let { followed ->
                        destinationReached(currentRideState(followed, nowState.value).stage == RideStage.Arrived, ownLocation, badgeAt)
                    } == true
                }
            }
            JourneyDestinationBadge(map = readyMap, cameraTick = { cameraTick }, at = badgeAt, appearMillis = ROUTE_DRAW_MILLIS, arrived = badgeArrived)
        }

        // The traveller: the real position if the device knows one, else where the timetable
        // says. On the start page and the map tab only the device can say where the user is.
        val ownPosition = when {
            // A walk on the map shows only where the device really is.
            walkView -> ownLocation ?: devicePosition
            tripView -> ownLocation ?: companion?.let { positionOnJourney(it, now) }
            mapShown && (onStart || onMapTab) -> devicePosition
            else -> null
        }
        PositionDot(
            map = map,
            cameraTick = { cameraTick },
            position = ownPosition,
            dot = ownDot,
            // The device's own position (not the timetable's guess): its fixes move the dot.
            live = ownPosition != null && (ownPosition === ownLocation || ownPosition === devicePosition),
            journey = if (tripView) companion else null,
        )

        // Where the vehicle positions come from: a small pill at the lower left of the map
        // that is not covered, while vehicles are drawn (see VehicleHint).
        // Steady: it shows once vehicles have been there a moment, stays at least two seconds
        // and outlasts a reload of the vehicles (see rememberSteadyHint); it fades in and out.
        val hintSteady by rememberSteadyHint(
            vehicleHintWanted(VehicleHintSetting.shown(context), ownLayersShown, vehicles.size),
            mapLeft = !ownLayersShown,
        )
        val hintFade = remember { Animatable(0f) }
        LaunchedEffect(hintSteady) {
            // Off the map's pages (another tab) it is gone at once, not faded over the next page (#141).
            hintFade.animateTo(if (hintSteady) 1f else 0f, tween(if (animationsOn && (hintSteady || ownLayersShown)) HINT_FADE_MILLIS else 0))
        }
        val hintFading by remember { derivedStateOf { hintFade.value > 0f } }
        if (hintSteady || hintFading) {
            val hintOpen = remember { mutableStateOf(false) }
            val hintBeat = rememberBeat()
            // The pill comes in last at the start, after the vehicles and the cards, with a
            // small pop from its corner; later each time the vehicles come back. It is placed
            // above the map, so it takes no room from anything.
            val hintIn = remember { Animatable(if (animationsOn) 0f else 1f) }
            LaunchedEffect(hintIn) {
                if (hintIn.value >= 1f) return@LaunchedEffect
                awaitIntroStep(IntroStep.LIVE)
                hintIn.animateTo(1f, Motion.pop())
            }
            VehicleHint(
                open = hintOpen,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        // Read while placing: the sheet or card moves without a recomposition,
                        // so the beat and the camera have the pill placed again.
                        val tick = hintBeat.intValue + cameraTick
                        val height = mapCover[1]
                        val coverTop = when {
                            onStart -> mapCover[0]
                            onMapTab && stopMarked && selectedStop != null -> mapCover[2]
                            // The top of the tab bar, above the system navigation.
                            else -> height - mapCover[3] - (navInset - SHEET_NAV_GAP).value * density
                        }
                        val panel = frameCover[2]
                        val left = (if (!panel.isNaN() && panel > 0f) panel else 0f) + 12f * density
                        val visibleTop = statusTop + MAP_TOP_COVER_DP * density
                        // Too little map left above the cover: the pill steps aside.
                        if (tick < 0 || height.isNaN() || coverTop.isNaN() || coverTop - visibleTop < 120f * density) {
                            placeable.place(-10_000, -10_000)
                        } else {
                            val bottom = minOf(coverTop, height) - 8f * density
                            placeable.place(left.roundToInt(), (bottom - placeable.height).roundToInt())
                        }
                    }
                }.graphicsLayer {
                    val shown = hintIn.value
                    alpha = shown.coerceIn(0f, 1f) * hintFade.value
                    scaleX = HINT_ENTER_SCALE + (1f - HINT_ENTER_SCALE) * shown
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 1f)
                },
            )
        }
    }
    // The pages (see the AnimatedContent below), one lambda each.
    val coursePage: @Composable (CourseRequest?) -> Unit = { shownRequest ->
        if (shownRequest != null) {
            CourseScreen(
                request = shownRequest,
                // What was loaded for another trip is not shown for this one.
                course = course?.takeIf { it.tripId == shownRequest.tripId },
                failed = courseFailed,
                now = now,
                // The vehicle in the list where the map has it live.
                liveProgress = { millis ->
                    val live = courseLive?.takeIf { it.vehicle.tripId == shownRequest.tripId }
                    val track = courseTrack?.takeIf { it.tripId == shownRequest.tripId }
                    val spot = DoubleArray(2)
                    // The timetable picks the lap where a ring line passes the place more than once.
                    if (live != null && track != null && live.locate(millis, spot)) {
                        track.progressAt(spot[0], spot[1], near = track.clock.progress(System.currentTimeMillis()))
                    } else {
                        null
                    }
                },
                onBack = { leaveCourse() },
                // The whole line and back to the vehicle and its next stops (see CourseFollow).
                onWholeLine = if (shownRequest.fromVehicle) courseCamera::toggleWhole else null,
                wholeShown = courseCamera.whole,
                onShowStop = { place ->
                    val position = place.position
                    if (position != null) {
                        // The stop is looked at: the camera no longer follows the vehicle.
                        courseCamera.held = true
                        map?.animateCameraScaled(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(position.first, position.second),
                                STOP_ZOOM,
                            ),
                        )
                    }
                },
                onStopAction = { place, action ->
                    closeCourse()
                    stopAction(place, action)
                },
            )
        }
    }
    // "Von wo?": up to four stops near the user as quick picks (#173), worked out once as the
    // search for the start opens, only with the position allowed and fresh; empty otherwise.
    var nearbyQuick by remember { mutableStateOf<List<QuickStop>>(emptyList()) }
    val quickWanted = searchMode == SearchMode.Origin && locationGranted && devicePosition != null
    LaunchedEffect(quickWanted) {
        nearbyQuick = emptyList()
        if (!quickWanted) return@LaunchedEffect
        val position = devicePosition ?: return@LaunchedEffect
        if (lastFix[0] == 0L || SystemClock.elapsedRealtime() - lastFix[0] > QUICK_FIX_MAX_AGE_MILLIS) return@LaunchedEffect
        nearbyQuick = try {
            NearbyQuickCache.load(source, position, stops, nearbyDepartures)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    val searchPage: @Composable (SearchMode?, String) -> Unit = { shownMode, placeLabel ->
        if (shownMode != null) {
            // A new share opens the search afresh, with its text in the field.
            val shared = sharedSearch?.takeIf { shownMode == SearchMode.Destination }
            key(shownMode, shared?.serial) {
                val pickFound: (Stop) -> Unit = { place ->
                    when (shownMode) {
                        SearchMode.Origin -> pickOrigin(place)
                        SearchMode.Destination -> pickDestination(place)
                        SearchMode.Place -> pickFavoritePlace(place)
                        SearchMode.DepartureStop -> pickDepartureStop(place)
                        // The time to stay there is asked next.
                        SearchMode.Via -> {
                            viaPending = Via(place.copy(distanceMeters = null), via?.minutes ?: 0)
                            searchMode = null
                        }
                        SearchMode.TripTarget -> {
                            searchMode = null
                            startRetarget(place.copy(distanceMeters = null), tripTargetAppend)
                        }
                        SearchMode.MapPlace -> pickMapPlace(place)
                    }
                }
                SearchPanel(
                    source = source,
                    placeholder = when (shownMode) {
                        SearchMode.Origin -> stringResource(R.string.where_from)
                        SearchMode.Destination -> stringResource(R.string.where_to)
                        SearchMode.Place -> placeLabel
                        SearchMode.DepartureStop -> stringResource(R.string.choose_stop)
                        SearchMode.Via -> stringResource(R.string.via_search)
                        SearchMode.TripTarget -> stringResource(
                            if (tripTargetAppend) R.string.retarget_continue_search else R.string.retarget_change_search,
                        )
                        SearchMode.MapPlace -> stringResource(R.string.map_search_hint)
                    },
                    // The places picked before come from the history (see below).
                    suggestions = when {
                        shownMode == SearchMode.Destination -> emptyList()
                        shownMode == SearchMode.DepartureStop -> foundStops
                        shownMode != SearchMode.Origin -> emptyList()
                        // The position of the user always comes first.
                        else -> listOf(currentPlace(center)) + stops.filter { it.id != destination?.id }
                    },
                    expandFromY = searchOrigin,
                    onPick = pickFound,
                    // An appointment as the destination: be there by its start.
                    onPickAppointment = { appointment, place ->
                        if (shownMode == SearchMode.Destination) {
                            journeyTime = TimeChoice(appointment.begin, arrival = true)
                        }
                        pickFound(place)
                    },
                    onCalendarGranted = {
                        calendarTile.calendarGranted = true
                        calendarTile.calendarDenied = false
                    },
                    onClose = { closeSearch() },
                    stopsOnly = shownMode == SearchMode.DepartureStop,
                    // Choosing a favourite place itself offers no favourite places.
                    favorites = if (shownMode == SearchMode.Place) emptyList() else favoritePlaces,
                    saved = if (shownMode == SearchMode.Place) emptyList() else savedPlaces,
                    history = searchHistory,
                    onRemember = { rememberSearch(it) },
                    onForget = { forgetSearch(it) },
                    initialQuery = shared?.query.orEmpty(),
                    offered = shared?.place,
                    nearbyQuick = if (shownMode == SearchMode.Origin) nearbyQuick else emptyList(),
                    // Start and destination can be chosen on the map, too.
                    mapPick = when (shownMode) {
                        SearchMode.Origin -> MapPickTarget.Origin
                        SearchMode.Destination -> MapPickTarget.Destination
                        else -> null
                    },
                )
            }
        }
    }
    val settingsPage: @Composable () -> Unit = {
        SettingsScreen(
            options = options,
            onOptions = { updateOptions(it) },
            startVehicles = startVehicles,
            onStartVehicles = { value ->
                startVehicles = value
                prefs.startVehicles = value
            },
            startMap = startMapChosen,
            onStartMap = { value ->
                // What only shows on the map is let go of with it.
                if (!value) pinned = null
                startMapChosen = value
                prefs.startMap = value
            },
            startClassic = startClassic,
            onStartClassic = { value ->
                startClassic = value
                prefs.startClassic = value
            },
            startCards = startCards,
            onStartCards = { value ->
                startCards = value
                prefs.startCards = value
            },
            startDepartures = startDepartures,
            nearbyRadius = nearbyRadius,
            onNearbyRadius = { value ->
                nearbyRadius = value
                prefs.nearbyRadius = value
            },
            startCalendar = calendarTile.startCalendar,
            onStartCalendar = { value ->
                calendarTile.startCalendar = value
                prefs.startCalendar = value
                calendarTile.calendarLater = false
            },
            onStartDepartures = { value ->
                startDepartures = value
                prefs.startDepartures = value
            },
            boardingHints = boardingHints,
            transferChoice = transferChoice,
            onTransferChoice = { value ->
                transferChoice = value
                prefs.transferChoice = value
                comfortOverride = null
            },
            onBoardingHints = { value ->
                boardingHints = value
                prefs.boardingHints = value
                // The notification of a trip says where to board, or no longer does.
                refreshTripNotification(context)
            },
            delayDisplay = delayDisplay,
            fitLine = fitLine,
            onFitLine = { value ->
                fitLine = value
                prefs.fitLine = value
            },
            onDelayDisplay = { value ->
                delayDisplay = value
                prefs.delayDisplay = value
                // The notification of a trip and the widgets show times as well.
                refreshTripNotification(context)
                WidgetUpdates.redrawAll(context)
            },
            planFirst = planFirst,
            onPlanFirst = { value ->
                planFirst = value
                prefs.planFirst = value
            },
            listView = listView,
            onListView = { value ->
                listView = value
                prefs.connectionsList = value
            },
            onClearHistory = {
                searchHistory = emptyList()
                prefs.saveSearchHistory(emptyList())
            },
            connectionsFull = connectionsFull,
            onConnectionsFull = { value ->
                connectionsFull = value
                prefs.connectionsFull = value
            },
            chartChangeStops = chartChangeStops,
            onChartChangeStops = { value ->
                chartChangeStops = value
                prefs.chartChangeStops = value
            },
            chartTotalTime = chartTotalTime,
            onChartTotalTime = { value ->
                chartTotalTime = value
                prefs.chartTotalTime = value
            },
            connectionChoice = connectionChoice,
            onConnectionChoice = { value ->
                connectionChoice = value
                prefs.connectionChoice = value
            },
            ownServer = ownServer,
            onOwnServer = { value ->
                ownServer = value
                prefs.ownServer = value
                // Journeys on the tiles may have come from the other server.
                placeTrips.clear()
            },
            sourceChoice = sourceChoice,
            onSourceChoice = { value ->
                sourceChoice = value
                prefs.sourceChoice = value
                // A changed order, switch or key: every backend gets a fresh chance.
                SourceHealth.shared.reset()
                // The journeys on the tiles may have come from another provider.
                placeTrips.clear()
            },
            onBack = { settingsOpen = false },
            focusWatch = settingsFocusWatch,
            onFocusWatchDone = { settingsFocusWatch = false },
            // "My routes", where the bells of the routes are.
            onOpenWays = {
                favoritesPages.clear()
                tab = Tab.Favorites
            },
        )
    }
    val tripPage: @Composable (Journey?) -> Unit = { shownCompanion ->
        if (shownCompanion != null) {
            CompanionScreen(
                journey = shownCompanion,
                now = now,
                alternatives = alternatives,
                courses = tripCourseCache.tripCourses,
                feeders = tripCourseCache.feederCourses,
                onMinimize = { companionOpen = false },
                onEnd = {
                    AppHaptics.confirm()
                    endTrip()
                },
                onMissed = { searchFromStop(it) },
                onReplan = { replanFromHere(it) },
                onFocusWalk = {
                    // Showing a walk takes the map out of the trip's framing until "Back".
                    tripFrameHeld = true
                    focusWalk(it, shownCompanion)
                },
                onSwitch = { switchRest(it) },
                everyMinute = tripEveryMinute,
                onEveryMinute = {
                everyMinuteHint(it)
                setTripEveryMinute(it)
            },
                shortcutAlert = tripShortcutAlert,
                onShortcutAlert = { TripState.shortcutAlert.value = it },
                faster = fasterOffer,
                onTakeFaster = { FasterWatch.take(context, it.key) },
                onIgnoreFaster = { FasterWatch.ignore(context, it.key) },
                // The locate button (#104): to the own position up close where one is known (the
                // vehicle, the next stop otherwise); offered while the map was moved by hand or
                // still frames the trip.
                onRecentre = run {
                    val ownKnown = (ownLocation ?: devicePosition) != null
                    val locate: () -> Unit = {
                        tripFrameHeld = false
                        io.github.jnbp.nextstop.ui.companion.CompanionLayout.mapFocus = focusOnLocate((ownLocation ?: devicePosition) != null)
                    }
                    locate.takeIf { tripFrameHeld || (io.github.jnbp.nextstop.ui.companion.CompanionLayout.mapFocus == CompanionMapFocus.Trip && ownKnown) }
                },
                notificationsOff = notificationsOff,
                onNotificationsNoteSeen = { notificationsOff = false },
                onChangeDestination = {
                    tripTargetAppend = false
                    searchMode = SearchMode.TripTarget
                },
                onContinueTo = {
                    tripTargetAppend = true
                    searchMode = SearchMode.TripTarget
                },
                // A walk of the trip re-routed on the way: its new minutes, and whether the ride after it is lost.
                walkDetour = walkDetour?.let { state ->
                    val index = state.legIndex ?: return@let null
                    val (minutes, arrival) = state.remaining() ?: return@let null
                    WalkDetour(
                        index, minutes, arrival, late = walkMissesRide(shownCompanion, index, arrival),
                        nextProduct = shownCompanion.legs.drop(index + 1).firstOrNull { !it.walking }?.product,
                    )
                },
            )
        }
    }
    val planPage: @Composable (PageLists) -> Unit = { lists ->
        val selection = lists.selection
        val shownJourneys = lists.shownJourneys
        val filteredOut = lists.filteredOut
        val filteredLevels = lists.filteredLevels
        val showFiltered = lists.showFiltered
        val productsOff = lists.productsOff
        val useAllProducts = lists.useAllProducts
        val fromName = lists.fromName
        val toName = lists.toName
        PlanScreen(
            fromName = fromName,
            toName = toName,
            loading = journeysLoading,
            error = journeysError,
            stale = journeysStale,
            onRetry = { asked -> refreshJourneys(asked) },
            active = appStarted,
            count = shownJourneys?.size ?: 0,
            filteredCount = filteredOut.size,
            filteredLevels = filteredLevels,
            tighterCount = selection.nextCount,
            tighterLevel = selection.nextLevel,
            onShowFiltered = showFiltered,
            // A page on its way out shows the options it had.
            options = if (showingConnections) {
                planShown
            } else {
                planWindow(shownJourneys.orEmpty(), journeyTime, connectionsState.selectedId, now)
            },
            selectedId = planPicked?.id,
            onSelect = { connectionsState.selectedId = it },
            time = journeyTime,
            onTime = { timeSheet = TimeTarget.Journey },
            comfort = shownComfort,
            levelChoice = transferChoice,
            onComfort = { pickComfort(it) },
            everyMinute = everyMinute,
            onEveryMinute = {
                everyMinuteHint(it)
                everyMinute = it
            },
            via = via,
            onAddVia = { searchMode = SearchMode.Via },
            onEditVia = { viaPending = via },
            onRemoveVia = { removeVia() },
            routeSaved = savedRoutes.any { it.from.id == startPlace?.id && it.to.id == destination?.id },
            onToggleRoute = { toggleRoute() },
            onBack = { closeConnections() },
            onPickOrigin = { searchMode = SearchMode.Origin },
            onPickDestination = { searchMode = SearchMode.Destination },
            onSwap = { swapEnds() },
            onConfirm = { planning = false },
            productsOff = productsOff,
            onAllProducts = useAllProducts,
            searchOptions = options,
            onSearchOptions = { searchOptionsOpen = true },
            onTicketOff = { filter -> ticketOff(filter) },
        )
    }
    val connectionsPage: @Composable (PageLists) -> Unit = { lists ->
        val selection = lists.selection
        val shownJourneys = lists.shownJourneys
        val directIds = lists.directIds
        val patternNote = lists.patternNote
        val filteredOut = lists.filteredOut
        val filteredLevels = lists.filteredLevels
        val showFiltered = lists.showFiltered
        val productsOff = lists.productsOff
        val useAllProducts = lists.useAllProducts
        val fromName = lists.fromName
        val toName = lists.toName
        ConnectionsScreen(
            fromName = fromName,
            toName = toName,
            journeys = shownJourneys,
            loading = journeysLoading,
            error = journeysError,
            stale = journeysStale,
            refreshing = journeysRefreshing && journeysAsked,
            onRefresh = { asked -> refreshJourneys(asked) },
            active = appStarted,
            now = now,
            state = connectionsState,
            time = journeyTime,
            onTime = { timeSheet = TimeTarget.Journey },
            comfort = shownComfort,
            levelChoice = transferChoice,
            onComfort = { pickComfort(it) },
            everyMinute = everyMinute,
            onEveryMinute = {
                everyMinuteHint(it)
                everyMinute = it
            },
            via = via,
            onAddVia = { searchMode = SearchMode.Via },
            onEditVia = { viaPending = via },
            onRemoveVia = { removeVia() },
            levels = remember(journeyLevels, directIds) { journeyLevels - directIds },
            filteredCount = filteredOut.size,
            filteredLevels = filteredLevels,
            tighterCount = selection.nextCount,
            tighterLevel = selection.nextLevel,
            onShowFiltered = showFiltered,
            productsOff = productsOff,
            onAllProducts = useAllProducts,
            searchOptions = options,
            onSearchOptions = { searchOptionsOpen = true },
            onTicketOff = { filter -> ticketOff(filter) },
            // Back leads to the map with the options, or without that step to the start.
            onBack = { if (planFirst) planning = true else closeConnections() },
            onPickOrigin = { searchMode = SearchMode.Origin },
            onLoadEarlier = { loadEarlier() },
            onLoadLater = { loadLater() },
            edges = EdgeStatus(paging.loadingEarlier, paging.loadingLater, paging.earlierResult, paging.laterResult),
            routeSaved = savedRoutes.any { it.from.id == startPlace?.id && it.to.id == destination?.id },
            onToggleRoute = { toggleRoute() },
            stopsFailedId = stopsFailedId,
            onLoadStops = { loadStops(it) },
            onStopAction = { place, action -> stopAction(place, action) },
            onStartTrip = {
                AppHaptics.confirm()
                startTrip(it)
            },
            fullScreen = connectionsFull,
            listView = listView,
            offers = offerState.transferOffers,
            onLoadOffers = { journey, again -> loadTransferOffers(journey, again) },
            onAdapt = { journey, keepLegs, option -> adaptJourney(journey, keepLegs, option) },
            adaptedIds = adaptedFrom.keys,
            onUndo = { undoAdapt(it) },
            onCourse = { leg ->
                openCourse(
                    leg.tripId, leg.line, leg.product, leg.direction, leg.from,
                    color = leg.providerColor, textColor = leg.providerTextColor, at = leg.ridePlace,
                )
            },
            onFocusWalk = { walk ->
                focusWalk(walk, journeys?.firstOrNull { it.id == connectionsState.selectedId })
            },
            onRememberPattern = { rememberPattern(it) },
            patternTitle = patternFilter?.label,
            patternNote = patternNote,
            onClearPattern = { patternFilter = null },
        )
    }
    val startPage: @Composable (String) -> Unit = { fromName ->
        StartScreen(
            classic = startClassic,
            showCards = startCards,
            showDepartures = startDepartures,
            // The chips in the order of the distance they show (see stopsByDistance).
            stops = remember(stops, center, usingFallback) { stopsByDistance(stops, if (usingFallback) null else center) },
            selectedStopId = selectedStopId,
            onSelectStop = { selectStopFramed(it) },
            departures = departures,
            departuresLoaded = departuresLoaded,
            error = departuresError,
            stopFavorite = favorites.any { it.id == selectedStopId },
            onDepartureAction = { departure, action -> departureAction(departure, action) },
            // All departures of the stop the card shows, under Departures.
            onAllDepartures = {
                settingsOpen = false
                tab = Tab.Departures
            },
            locationNote = locationNote,
            recentRoutes = recentRoutes,
            onOpenRoute = { openRoute(it) },
            time = journeyTime,
            onTime = { timeSheet = TimeTarget.Journey },
            now = now,
            fromName = fromName,
            onPickOrigin = { searchMode = SearchMode.Origin },
            onSwap = { swapOnStart() },
            tiles = startPageTiles(tiles, favoritePlaces),
            trip = companion,
            onOpenTrip = { companionOpen = true },
            // The same sheet as the companion's "Change" button, from the ⋮ of the trip banner.
            tripActions = companion?.let { followed ->
                TripActions(
                    onReplan = if (tripStep(followed, now).kind == StepKind.Arrived) {
                        null
                    } else {
                        ({ replanFromHere(replanFallback(followed, now)) })
                    },
                    onChangeDestination = {
                        tripTargetAppend = false
                        searchMode = SearchMode.TripTarget
                    },
                    onContinueTo = {
                        tripTargetAppend = true
                        searchMode = SearchMode.TripTarget
                    },
                    onShare = { tripShareOpen = true },
                    everyMinute = tripEveryMinute,
                    onEveryMinute = {
                        everyMinuteHint(it)
                        setTripEveryMinute(it)
                    },
                    shortcutAlert = tripShortcutAlert,
                    onShortcutAlert = { TripState.shortcutAlert.value = it },
                    shortcutMinutes = prefs.shortcutMinutes,
                    onEnd = {
                        AppHaptics.confirm()
                        endTripWithUndo()
                    },
                )
            },
            showAppointment = calendarTile.startCalendar && !calendarTile.calendarLater,
            onRemoveAppointment = {
                calendarTile.startCalendar = false
                prefs.startCalendar = false
            },
            onLaterAppointment = { calendarTile.calendarLater = true },
            appointment = AppointmentTileState(
                granted = calendarTile.calendarGranted,
                denied = calendarTile.calendarDenied,
                appointments = calendarTile.appointments,
                shown = shownAppointment,
                leaveAt = calendarTile.appointmentLeave
                    ?.takeIf { it.key == shownAppointment?.key && it.fromId == startPlace?.id }
                    ?.leaveAt,
                placeMissing = calendarTile.appointmentLeave
                    ?.takeIf { it.key == shownAppointment?.key }
                    ?.let { it.place == null } == true,
            ),
            onEnableCalendar = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
            onOpenAppointment = { openAppointment(it) },
            onPickAppointment = { calendarTile.appointmentPick = it.key },
            onSettings = { settingsOpen = true },
            onSearch = {
                searchOrigin = whereToY[0].takeIf { y -> !y.isNaN() }
                searchMode = SearchMode.Destination
            },
            onWhereToPlaced = { y -> whereToY[0] = y },
            place = startPinContent,
            placeBack = inPagePreview(BackStep.Unpin, BackStep.ClosePick),
            onCoverTop = { y ->
                MapEdges.report(mapCover, 0, y)
                startLogoCover.value = y
                if (!coverKnown) coverKnown = true
                if (startMap) followCover(y)
            },
        )
    }
    val departuresPage: @Composable () -> Unit = {
        DeparturesScreen(
            stops = stops + foundStops.filter { found -> stops.none { it.id == found.id } },
            selectedStopId = selectedStopId,
            onSelectStop = { id ->
                // A stop tapped in the list is shown on its own again.
                extraStops = emptyList()
                extraAnchor = null
                selectStop(id)
            },
            addedStops = addedStops,
            onToggleAdded = { stop ->
                val anchor = selectedStopId
                if (anchor != null && stop.id != anchor) {
                    // Several stops by choice instead of all stops nearby.
                    if (nearbyOn) {
                        nearbyOn = false
                        prefs.nearbyDepartures = false
                    }
                    val current = if (extraAnchor == anchor) extraStops else emptyList()
                    extraStops = if (current.any { it.id == stop.id }) {
                        current.filter { it.id != stop.id }
                    } else {
                        current + stop
                    }
                    extraAnchor = anchor
                }
            },
            onStopAction = { stop, action -> stopAction(stop, action) },
            departures = if (nearbyShown) nearbyDepartures else shownBoard,
            loaded = if (nearbyOn && !usingFallback) nearbyLoaded else departuresLoaded,
            error = departuresError,
            now = now,
            time = departuresTime,
            onTime = { timeSheet = TimeTarget.Departures },
            // With stops added, the star is that of their combination.
            isFavorite = if (addedStops.isNotEmpty()) {
                findStopGroup(stopGroups, listOfNotNull(selectedForBoard) + addedStops) != null
            } else {
                favorites.any { it.id == selectedStopId }
            },
            onToggleFavorite = { toggleFavorite() },
            onDepartureAction = { departure, action -> departureAction(departure, action) },
            stopFavorite = favorites.any { it.id == selectedStopId },
            onSearchStop = {
                searchOrigin = null
                searchMode = SearchMode.DepartureStop
            },
            nearby = nearbyOn && !usingFallback,
            onNearby = { value ->
                nearbyOn = value
                prefs.nearbyDepartures = value
            },
            nearbyRadius = nearbyRadius,
            // Later departures for one stop; the lists of several stops have none.
            more = if (nearbyShown || addedStops.isNotEmpty()) null else moreState,
            onLoadMore = { loadMoreDepartures() },
            onShowStation = { showStationOnMap() },
            view = departuresView,
            refreshing = departuresRefreshing,
            onRefresh = {
                departuresRefreshing = true
                departuresPull++
            },
            here = if (usingFallback) null else center,
            leaveOrder = leaveOrderOn,
            onLeaveOrder = { value ->
                leaveOrderOn = value
                prefs.leaveOrder = value
            },
        )
    }
    val mapSheetPage: @Composable () -> Unit = {
        MapStopSheet(
            stop = if (stopMarked) selectedStop else null,
            // Back from the card of the stop or of a stop position shows where it leads.
            cardBack = inPagePreview(BackStep.UnmarkStop),
            positionBack = inPagePreview(BackStep.ClosePosition),
            // The card's close and a swipe down let go of the stop, as a tap on the bare map does.
            onClose = { stopMarked = false },
            departures = departures,
            now = now,
            isFavorite = favorites.any { it.id == selectedStopId },
            onDepartureAction = { departure, action -> departureAction(departure, action) },
            onRouteHere = { tab = Tab.Route },
            onDepartures = { tab = Tab.Departures },
            lines = stopLines,
            lineDirections = lineDirections,
            onLine = { line ->
                lineJob?.cancel()
                val stopId = selectedStopId
                if (lineDirections?.line == line || stopId == null) {
                    lineDirections = null
                } else {
                    lineDirections = LineDirections(line, null)
                    lineJob = scope.launch {
                        val found = try {
                            source.lineDepartures(stopId, line.line, line.product)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                        if (lineDirections?.line == line) lineDirections = LineDirections(line, found)
                    }
                }
            },
            onDirection = { departure ->
                openCourse(
                    departure.tripId, departure.line, departure.product, departure.direction, selectedStop?.name,
                    color = departure.providerColor, textColor = departure.providerTextColor,
                    at = departure.ridePlace ?: selectedStop?.position,
                )
            },
            onCardTop = { y ->
                MapEdges.report(mapCover, 2, y)
                if (stopMarked && selectedStop != null) followCover(y)
            },
            position = pickedPosition,
            positionDepartures = remember(departures, pickedPosition) {
                pickedPosition?.let { pick -> positionDepartures(departures, pick.pos, pick.lines) }.orEmpty()
            },
            onPositionClose = { pickedPosition = null },
            onPositionLine = { line -> openPositionLine(line) },
            onWalkHere = { walkToPosition() },
        )
    }
    val favoritesPage: @Composable () -> Unit = {
        FavoritesScreen(
            tiles = tiles,
            routes = savedRoutes,
            favorites = favorites,
            onOpenRoute = { openRoute(it) },
            onRemoveRoute = { removeRoute(it) },
            onOpen = { openFavorite(it) },
            stopGroups = stopGroups,
            onOpenGroup = { openStopGroup(it) },
            savedPlaces = savedPlaces,
            onOpenSaved = { saved -> pickDestination(saved.place) },
            onSavedChange = { updateSaved(it) },
            places = favoritePlaces,
            onPlaceChange = { index, changed ->
                updatePlaces(favoritePlaces.mapIndexed { at, favorite -> if (at == index) changed else favorite })
            },
            onPlaceChoose = { index ->
                editingPlace = index
                searchMode = SearchMode.Place
            },
            onPlaceRemove = { index -> updatePlaces(favoritePlaces.filterIndexed { at, _ -> at != index }) },
            onPlaceAdd = {
                // The first icon that no favourite place has yet.
                val icon = PlaceIcon.entries.firstOrNull { icon -> favoritePlaces.none { it.icon == icon } }
                    ?: PlaceIcon.Star
                if (favoritePlaces.size < MAX_FAVORITE_PLACES) updatePlaces(favoritePlaces + FavoritePlace(icon))
            },
            onPlacesOrder = { updatePlaces(it) },
            patterns = savedPatterns,
            onOpenPattern = { openPattern(it) },
            onRemovePattern = { removePattern(it) },
            disruptions = disruptionAccess,
            page = favoritesPages.lastOrNull(),
            pageBefore = favoritesPages.getOrNull(favoritesPages.lastIndex - 1),
            back = inPagePreview(BackStep.FavoritesPage),
            onPage = { favoritesPages.add(it) },
            onPageBack = { if (favoritesPages.isNotEmpty()) favoritesPages.removeAt(favoritesPages.lastIndex) },
            // A disruption's "Map": the stop it is about on the main map.
            onShowStopOnMap = { stop -> showStop(stop, Tab.Map) },
            // The map at the top: where the user is, and only where the map is wanted.
            here = if (usingFallback) null else center,
            showMap = startMap,
            onShowArea = { points -> showAreaOnMap(points) },
            // The bell's "Set times": the settings of the start, at the disruption watch.
            onWatchSettings = {
                settingsFocusWatch = true
                tab = Tab.Route
                settingsOpen = true
            },
        )
    }
    // What lies above the pages: the walk and map search bars, notes, cards and dialogs.
    // The button "my location" and a second tap on the tab "Route" on its start page (#136): the
    // map flies back to the user (scaled with the animator scale), and the position is asked anew.
    fun locateMe() {
        // The view follows the user again. The known place shows at once, with
        // the stops around it; the position is asked for anew, and one that
        // lies elsewhere moves the map again.
        viewHeld = false
        if (!usingFallback) {
            // Back to the user, facing north and seen from straight above.
            map?.animateCameraScaled(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(visibleTarget(center, 15.0))
                        .zoom(15.0)
                        .bearing(0.0)
                        .tilt(0.0)
                        .build(),
                ),
                LOCATE_MILLIS,
            )
            pinStart()
            if (queryCenter == center) {
                // The stops around the user are on show: the nearest of them is the selected one again.
                stops.filter { it.distanceMeters != null }
                    .minByOrNull { it.distanceMeters ?: 0 }
                    ?.let { nearest -> selectedStopId = nearest.id }
            } else {
                if (distanceMeters(center, queryCenter) > RELOAD_DISTANCE_METERS) selectedStopId = null
                queryCenter = center
            }
        }
        requestPosition()
    }

    val pageOverlays: @Composable BoxScope.() -> Unit = {
        // A walk on the big map: "Back" on top, where it leads at the bottom (see WalkFocus).
        val shownWalk = lastNonNull(walkFocus)
        AnimatedVisibility(
            visible = walkView,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
        ) {
            if (shownWalk != null) {
                WalkFocusOverlay(
                    focus = shownWalk,
                    following = !walkFrameHeld && walkFollows,
                    hasPosition = (ownLocation ?: devicePosition) != null,
                    rerouteMinutes = walkDetour?.takeIf { it.leg == shownWalk.walk }?.remaining()?.first,
                    onBack = { closeWalk() },
                    onFollow = { walkFrameHeld = false },
                )
            }
        }

        // "Centre on me" on every map page (#165): bottom right, floating just above what covers
        // the bottom of the map and moving with it; gone under a sheet pulled fully up. The
        // classic companion keeps its own buttons; without the map there is none.
        val ownKnown = (ownLocation ?: devicePosition) != null
        val modernTrip = tripView && companionModernHere()
        val floatingLocate: (() -> Unit)? = when {
            // Not over the loading ground of the map: only once the map itself shows (#165).
            !mapShown || MapPick.active || MapCover.covered -> null
            onStart || onMapTab -> ({ locateMe() })
            walkView -> if (walkFrameHeld && ownKnown) ({ walkFrameHeld = false }) else null
            modernTrip -> {
                val locate: () -> Unit = {
                    tripFrameHeld = false
                    CompanionLayout.mapFocus = focusOnLocate((ownLocation ?: devicePosition) != null)
                }
                locate.takeIf { tripFrameHeld || (CompanionLayout.mapFocus == CompanionMapFocus.Trip && ownKnown) }
            }
            (planView || courseView) && ownKnown -> ({
                val own = ownLocation ?: devicePosition
                if (own != null) {
                    // A course stops following its vehicle, as after a move by hand.
                    if (courseView) courseCamera.held = true
                    // In the middle of what the page leaves visible, not of the screen (#273).
                    centreOnTraveller(own, null, 15.0, LOCATE_MILLIS)
                }
            })
            else -> null
        }
        FloatingLocateButton(
            onLocate = floatingLocate,
            description = stringResource(
                when {
                    walkView -> if (walkFollows) R.string.walk_focus_follow else R.string.r9center_walk_reframe
                    modernTrip -> R.string.recentre_trip
                    locating && (onStart || onMapTab) -> R.string.location_searching
                    else -> R.string.my_location
                },
            ),
            busy = locating && (onStart || onMapTab),
            bottomEdge = {
                val height = mapCover[1]
                val bars = height - mapCover[3] - (navInset - SHEET_NAV_GAP).value * density
                when {
                    onStart -> mapCover[0]
                    onMapTab -> if (stopMarked && selectedStop != null) mapCover[2] else bars
                    walkView -> MapEdges.walkCard[0]
                    else -> frameCover[1].takeIf { it.isFinite() } ?: (height - mapCover[3] - navInset.value * density)
                }
            },
            topCover = {
                if (onStart || onMapTab) statusTop + MAP_BUTTONS_TOP.value * density else frameCover[0]
            },
            height = { mapCover[1] },
        )

        // The search of the map tab: a glass pill on top, over the "my location" button.
        // It names the place it found while that is marked; its ✕ lets go of it.
        val mapSearchShown = mapSearchPick?.takeIf { pick ->
            if (pick.kind == PlaceKind.Stop) stopMarked && selectedStopId == pick.id else pinned != null && pinned == pick.position
        }
        AnimatedVisibility(
            visible = mapShown && onMapTab && !walkView && !MapPick.active,
            enter = fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { -it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = SEARCH_FIELD_INSET, top = MAP_SEARCH_TOP, end = SEARCH_FIELD_INSET),
        ) {
            MapSearchPill(
                label = mapSearchShown?.let { pick -> if (pick.kind == PlaceKind.Stop) pick.name.shortStopName() else pick.name },
                onOpen = {
                    searchOrigin = mapSearchY[0].takeIf { y -> !y.isNaN() }
                    searchMode = SearchMode.MapPlace
                },
                onClear = {
                    if (mapSearchShown?.kind == PlaceKind.Stop) stopMarked = false else pinned = null
                    mapSearchPick = null
                },
                onPlaced = { y -> mapSearchY[0] = y },
            )
        }

        // The start page says on its own what is known of the position; the map tab has no page on top that could.
        if (mapShown && onMapTab && locationNote != null && !MapPick.active) {
            LocationNote(
                text = locationNote,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Below the search pill, beside the "my location" button.
                    .padding(start = SEARCH_FIELD_INSET, top = MAP_BUTTONS_TOP, end = 72.dp),
            )
        }

        if (mapShown && (onStart || onMapTab)) {
            MapButtons(
                // Right under the settings button at the top right (8 dp from the top, 48 high).
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // On the map tab below the search field, which is as tall as "Where to?".
                    .padding(top = if (onMapTab) MAP_BUTTONS_TOP else 64.dp, end = 12.dp),
            )
        }

        // A pinned place on the map tab: a card at the bottom, like the card of a stop, with
        // its name, address and way on foot, and what to do with it. It springs in and out.
        // On the start page the sheet itself shows the place instead (see StartScreen).
        PlaceCard(
            position = if (mapShown && onMapTab && !MapPick.active) pinned else null,
            title = pinTitle,
            address = pinnedDetails?.address,
            own = devicePosition,
            onFrom = placeFrom,
            onTo = placeTo,
            onSave = placeSave,
            onClose = { pinned = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = navInset)
                // Back shrinks the card over the map it leaves.
                .backFront(inPagePreview(BackStep.Unpin)?.takeIf { it.active }),
            loading = pinLookup && pinnedPoi == null,
        )
        retarget?.let { state ->
            RetargetDialog(
                state = state,
                onApply = { applyRetarget(it) },
                onOthers = { retargetOthers(state) },
                onDismiss = {
                    retargetJob?.cancel()
                    retarget = null
                },
            )
        }
        patternToSave?.let { pattern ->
            key(pattern.id) {
                RememberPatternDialog(
                    pattern = pattern,
                    onSave = { name -> savePattern(pattern.copy(name = name)) },
                    onDismiss = { patternToSave = null },
                )
            }
        }
        placeToSave?.let { place ->
            key(place.id) {
                SavePlaceDialog(
                    initialName = (pinnedPoi ?: pinnedDetails?.title).orEmpty(),
                    placeName = place.name.shortStopName(),
                    canFavorite = favoritePlaces.size < MAX_FAVORITE_PLACES,
                    onSave = { name, icon -> saveAsSaved(place, name, icon) },
                    onFavorite = { name, icon -> saveAsFavorite(place, name, icon) },
                    onDismiss = { placeToSave = null },
                )
            }
        }
        groupToSave?.let { group ->
            key(group.id) {
                val defaultName = stopGroupDefaultName(group)
                SaveStopGroupDialog(
                    initialName = defaultName,
                    stopNames = group.stops.joinToString(" · ") { it.name.shortStopName() },
                    // The name as offered is no name of its own: the list then names it by its stops.
                    onSave = { name -> saveStopGroup(group.copy(name = if (name == defaultName) "" else name)) },
                    onDismiss = { groupToSave = null },
                )
            }
        }
    }
    // The tab bar and the bars at the bottom, sheets and dialogs.
    val pageControls: @Composable BoxScope.(String, String) -> Unit = { fromName, toName ->
        // A walk on the big map has its own "Back"; the tabs come back with the page.
        // The new companion views (see ui/companion) have no tab bar during a ride: it sinks away.
        // Read by the drawing only, so the bar does not compose anew for every frame of it.
        // A back gesture from the companion brings them back as it begins (and away again if it is let go).
        val tabsAway = animateFloatAsState(
            if (CompanionLayout.onScreen && backFrom != Screen.Trip) 1f else 0f,
            tween(TABS_AWAY_MILLIS),
            label = "tabsAway",
        )
        val tabsGone by remember { derivedStateOf { tabsAway.value >= 1f } }
        if (searchMode == null && !walkView && !tabsGone) {
            FloatingNav(
                current = tab,
                // Tapping the tab that is already open leads back to its first page (the map: to the user).
                onSelect = { picked ->
                    // An open line course makes room first; the same tab then just shows its page again.
                    val hadCourse = courseView
                    if (picked != tab) {
                        // Another tab: what this tab has open (its course, its pages, the
                        // settings of the start) stays with it and is there again when it
                        // is picked again; the other tab shows what it had open.
                        courseRequest?.let { open -> courseByTab[tab] = open }
                        closeCourse()
                        tab = picked
                        courseByTab.remove(picked)?.let { kept ->
                            openCourse(
                                kept.tripId, kept.line, kept.product, kept.direction, kept.fromStop,
                                color = kept.color, textColor = kept.textColor, at = kept.at,
                            )
                        }
                    } else if (hadCourse) {
                        closeCourse()
                    } else if (picked == Tab.Favorites) {
                        favoritesPages.clear()
                        favoritesFromMap = false
                    } else if (picked == Tab.Route) {
                        // Already on the start page with its map: a second tap brings the map back to the user (#136).
                        if (onStart && mapShown) locateMe()
                        // A followed trip goes on; its card on the start page leads back to it.
                        settingsOpen = false
                        companionOpen = false
                        closeConnections()
                    } else if (picked == Tab.Map) {
                        // Already on the map tab: a second tap brings the map back to the user, like
                        // its locate button (#256); the one-shot position request of that button, no polling.
                        if (onMapTab && mapShown && !MapPick.active) locateMe()
                    }
                },

                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = NAV_BAR_BOTTOM)
                    // The bar, the gap below it and the gap that keeps sheets clear of it.
                    .onSizeChanged { size -> navInset = (size.height / density).dp + NAV_BAR_BOTTOM + SHEET_NAV_GAP }
                    // Its entrance at the start: drawn only, so its room is known at once.
                    .graphicsLayer {
                        val shown = navIntro.value * (1f - tabsAway.value)
                        translationY = (1f - shown) * NAV_INTRO_RISE.toPx()
                        alpha = shown.coerceIn(0f, 1f)
                    },
            )
        }

        // The one host of all snackbars ("Undo" for another transfer level, a trip ended, a stop
        // combination removed), on every page: drawn after the tab bar so it is never under it,
        // and above it; at the bottom where the bar is away (search, a walk on the big map).
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = if (searchMode == null && !walkView) navInset + 12.dp else 12.dp),
        )

        // Which backend answers while the first choice fails ("Daten: Transitous"); nothing otherwise.
        SourceHint(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navInset),
        )

        val sheetFor = timeSheet
        if (sheetFor != null) {
            TimeSheet(
                current = if (sheetFor == TimeTarget.Journey) journeyTime else departuresTime,
                // Departures have no arrival: only the journey search can ask to be there by a time.
                allowArrival = sheetFor == TimeTarget.Journey,
                onApply = { applyTime(it) },
                onDismiss = { timeSheet = null },
            )
        }
        val sharedTrip = companion
        if (tripShareOpen && sharedTrip != null) {
            ShareSheet(
                journey = sharedTrip,
                fromName = sharedTrip.legs.first().from.plainStopName(),
                toName = sharedTrip.legs.last().to.plainStopName(),
                now = now,
                following = true,
                onDismiss = { tripShareOpen = false },
            )
        }
        if (searchOptionsOpen) {
            SearchOptionsSheet(
                options = options,
                onProducts = { products ->
                    updateOptions(options.copy(products = products))
                    searchJourneys(keepView = true)
                },
                onLocalOnly = { on ->
                    // A shorthand for the long-distance products: stored as they are.
                    updateOptions(options.withLocalOnly(on))
                    searchJourneys(keepView = true)
                },
                onBike = { searchWithTickets(options.copy(bike = it)) },
                onBikeCombo = { searchWithTickets(options.copy(bikeCombo = it)) },
                onDismiss = { searchOptionsOpen = false },
                // Only below the chart: the list has no columns to show it in.
                totalTime = chartTotalTime.takeIf { !listView },
                onTotalTime = { value ->
                    chartTotalTime = value
                    prefs.chartTotalTime = value
                },
            )
        }
        firstHint?.let { hint -> FirstHintDialog(hint = hint, onDismiss = { firstHint = null }) }
        val viaAsked = viaPending
        if (viaAsked != null) {
            ViaStayDialog(
                via = viaAsked,
                onApply = { applyVia(it) },
                onDismiss = { viaPending = null },
            )
        }
    }

    // The step-free setting, live for every reader (see rememberStepFree).
    val stepFreeNow by stepFreeSetting.collectAsState()
    CompositionLocalProvider(
        LocalStepFree provides stepFreeNow,
        LocalDelayDisplay provides delayDisplay,
        LocalChartChangeStops provides chartChangeStops,
        LocalChartTotalTime provides chartTotalTime,
        LocalNavInset provides navInset,
        LocalMapCover provides frameCover,
        // Without the map there is no picture behind the glass: it turns nearly opaque.
        LocalBackdrop provides (if (mapShown) backdrop else null),
        LocalBackLayers provides backLayers,
        // The same menu for every stop that is tapped (#139).
        LocalStopMenuHost provides remember(favorites) { StopMenuHost(favorites.mapTo(HashSet()) { it.id }) { place, action -> stopAction(place, action) } },
    ) {
    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged {
            mapCover[1] = it.height.toFloat()
            mapSize[0] = it.width
            mapSize[1] = it.height
        },
    ) {
        mapLayer()

        // The ways and marks are recorded as they are drawn (see BackStage.marks), so a page a
        // back gesture leaves takes them along. Under that page they give way with the gesture
        // (the page shows them, shrinking), and come back as it is called off; once let go
        // they are the next page's.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (backStage.marksShot != null || backStage.mapStays) alpha = 1f - backProgress.value * backStage.fade.value
                }
                .drawWithContent {
                    val layer = backStage.marks
                    if (layer == null) {
                        drawContent()
                    } else {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                },
        ) {
            mapMarks()
        }

        // The rows of the bars under a back gesture onto fog or the plain ground: over the ways,
        // under the pages, as the page back leads to shows its ground (see BackBarRows).
        BackBarRows(backStage)

        // How far the page is away while a walk is on the big map: 0 in place, 1 out of view.
        val pageAway by animateFloatAsState(
            if (walkView) 1f else 0f,
            spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
            label = "pageAway",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .onGloballyPositioned { coordinates ->
                    val origin = coordinates.positionInRoot()
                    pagesOrigin[0] = origin.x
                    pagesOrigin[1] = origin.y
                    backStage.pagesBox[0] = origin.y
                    backStage.pagesBox[1] = origin.y + coordinates.size.height
                    pagesBelow[0] = coordinates.findRootCoordinates().size.height - (origin.y + coordinates.size.height)
                },
        ) {
            // A page on its way out keeps showing what it had, also after its state was cleared.
            val shownMode = lastNonNull(searchMode)
            val shownCompanion = lastNonNull(companion)
            val heldJourneys = lastNonNull(journeys)
            // Journeys tighter than the chosen level are left out (see journeySelection);
            // every view of the search takes these same lists.
            val allJourneys = if (showingConnections) journeys else heldJourneys
            // Worked out again only when the journeys, the level or the rating change (and with the
            // clock, which rates estimated arrivals): every recomposition of AppRoot rated every
            // journey here anew, and handed the pages new lists, so nothing in them could skip.
            val ratingVersion = ratingSettingsShown.intValue
            // Only while the pages that show them are in front or coming (see listsShown); the
            // same lists as above where they come from the same journeys, not worked out twice.
            val directIds = rememberWhileShown(listsShown, allJourneys) {
                AllocProbe.measure(AllocProbe.PAGE_LISTS) {
                    allJourneys.orEmpty().filter { journeyChangesOf(it).isEmpty() }.map { it.id }.toSet()
                }
            }
            val sameJourneys = allJourneys === journeys && ratingVersion == selectionRating
            val selection = rememberWhileShown(listsShown, allJourneys, shownComfort, journeyLevels, ratingVersion, now, sameJourneys) {
                if (sameJourneys) {
                    visibleSelection
                } else {
                    AllocProbe.measure(AllocProbe.PAGE_LISTS) { journeySelection(allJourneys, shownComfort, journeyLevels) }
                }
            }
            val shownPattern = rememberWhileShown(listsShown, allJourneys, patternFilter?.lines, patternFrom, sameJourneys) {
                if (sameJourneys) visiblePattern else AllocProbe.measure(AllocProbe.PAGE_LISTS) { patternView(allJourneys, patternFilter?.lines, patternFrom) }
            }
            val shownJourneys = shownPattern?.takeIf { it.running }?.matching ?: selection.shown
            val patternNote = when {
                shownPattern == null -> null
                !shownPattern.running -> stringResource(R.string.pattern_not_running)
                shownPattern.fasterMinutes != null -> stringResource(R.string.pattern_faster, shownPattern.fasterMinutes)
                else -> null
            }
            // What the chosen level hides right now; one tap shows the next tighter level.
            val filteredOut = selection.filtered
            val filteredLevels = selection.filteredLevels
            val showFiltered: () -> Unit = {
                selection.nextLevel?.let { next -> if (transferChoice) pickComfort(next) else comfortOverride = next }
            }
            // Kinds of transport switched off in the settings; one tap searches with all again.
            // Long-distance trains and coaches off are the Deutschlandticket, which has a chip of its own.
            val localOnlyOff = if (options.localOnly) LONG_DISTANCE_PRODUCTS else emptySet()
            val productsOff = remember(options.products, localOnlyOff) { PRODUCT_KEYS.toSet() - options.products - localOnlyOff }
            val useAllProducts: () -> Unit = {
                updateOptions(options.copy(products = PRODUCT_KEYS.toSet() - localOnlyOff))
                searchJourneys(keepView = true)
            }
            // "Aktueller Standort" shows its address once it is found (#207, see HereAddress.kt).
            val shownDestination = lastNonNull(destination)
            val hereFrom = hereLabel(startPlace, CURRENT_LOCATION_ID, !usingFallback, HereAddress.fromResolving)
            val hereTo = hereLabel(shownDestination, CURRENT_LOCATION_ID, !usingFallback, HereAddress.toResolving)
            val fromName = hereFrom ?: startPlace?.name?.shortStopName() ?: ""
            val toName = hereTo ?: shownDestination?.name?.shortStopName() ?: ""
            val shownRequest = lastNonNull(courseRequest)
            // The favourite place the search is asking for, by the name it shows on its tile.
            val placeLabel = favoritePlaces.getOrNull(editingPlace ?: -1)
                ?.let { favorite -> favorite.name.ifBlank { iconLabels.getValue(favorite.icon) } }
                ?: stringResource(R.string.saved_places)
            // The same holder as long as what it holds is the same, so the pages that take it see no
            // change; its actions always call the latest ones.
            val showFilteredNow = rememberUpdatedState(showFiltered)
            val useAllProductsNow = rememberUpdatedState(useAllProducts)
            val pageLists = remember(selection, shownJourneys, directIds, patternNote, productsOff, fromName, toName) {
                PageLists(
                    selection = selection,
                    shownJourneys = shownJourneys,
                    directIds = directIds,
                    patternNote = patternNote,
                    filteredOut = filteredOut,
                    filteredLevels = filteredLevels,
                    showFiltered = { showFilteredNow.value() },
                    productsOff = productsOff,
                    useAllProducts = { useAllProductsNow.value() },
                    fromName = fromName,
                    toName = toName,
                )
            }
            pageTransition.AnimatedContent(
                modifier = Modifier
                    .fillMaxSize()
                    // While a walk is on the big map the page slides down out of view and keeps its state.
                    // A back gesture from the walk lifts it as far as the finger went; let go it
                    // carries on with the spring above, called off it sinks again.
                    .graphicsLayer {
                        val walkBack = inPagePreview(BackStep.CloseWalk)?.takeIf { it.active }
                        val away = if (walkBack != null) minOf(pageAway, 1f - walkBack.progress.value) else pageAway
                        // Also past the navigation bar and the shadow of its top edge: nothing of it peeks (#194).
                        translationY = pageAwayOffset(size.height, pagesBelow[0], PAGE_AWAY_SHADOW.toPx(), away)
                    },
                // The new page fades in while it rises a little and settles with a spring; the old one just fades.
                transitionSpec = {
                    if (backSwipe) {
                        // Predictive back: the page before is there underneath almost at once,
                        // always below the page in front, also below a page that came by an
                        // earlier back; the page in front shrinks with the swipe and stays
                        // opaque, it fades by itself once let go (see BackStage.fade).
                        (
                            fadeIn(tween(BACK_PAGE_MILLIS, easing = if (backStage.mapStays) LinearEasing else BackUnderEasing)) togetherWith
                                fadeOut(tween(BACK_PAGE_MILLIS, easing = BackHoldEasing))
                            ).apply { targetContentZIndex = backUnderZ(backGestures[0]) }
                    } else if (tabSlideOf(initialState, targetState) != 0) {
                        // Another tab: the pages slide along the tab bar, in the direction of
                        // the tab picked (drawn moves only; the map underneath stays as it is).
                        val direction = tabSlideOf(initialState, targetState)
                        if (animationsOn) {
                            (
                                slideInHorizontally(tween(TAB_SLIDE_MILLIS, easing = TabEnterEasing)) { width ->
                                    (direction * width * TAB_SLIDE_SHARE).roundToInt()
                                } + fadeIn(tween(TAB_SLIDE_MILLIS / 2, delayMillis = TAB_SLIDE_MILLIS / 5))
                                ) togetherWith (
                                slideOutHorizontally(tween(TAB_SLIDE_MILLIS, easing = TabExitEasing)) { width ->
                                    (-direction * width * TAB_SLIDE_SHARE).roundToInt()
                                } + fadeOut(tween(TAB_SLIDE_MILLIS / 2))
                                )
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    } else if (targetState == Screen.Search || initialState == Screen.Search) {
                        // The search field travels by itself; the pages only fade over.
                        fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                    } else if (targetState == Screen.Course && courseFrame.courseOrigin[0] != null) {
                        // The course grows out of the departure row (see growFrom); the page
                        // underneath stays a moment longer.
                        fadeIn(tween(120)) togetherWith fadeOut(tween(260, delayMillis = 140))
                    } else if (initialState == Screen.Course && courseFrame.courseOrigin[0] != null) {
                        // Closing, the course shrinks back into its row above the page underneath.
                        (fadeIn(tween(160)) togetherWith fadeOut(tween(220, delayMillis = 180))).apply { targetContentZIndex = -1f }
                    } else {
                        val rise = slideInVertically(
                            spring(
                                dampingRatio = 0.72f,
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            ),
                        ) { height -> height / 18 }
                        // From a page on its own ground onto the map (the ride view from the page
                        // of a journey): the page's content goes first and its ground only then
                        // (see groundLast below), so the map never shows through its text.
                        if (animationsOn && entersAfterFirstFrame(initialState, targetState)) {
                            // The overview of connections rises and fades by itself once it has
                            // drawn its first frame (see pageEntrance below): the first frame of
                            // the chart is long, and a fade started with it was over before it
                            // was ever seen (#110). The page before fades as always.
                            EnterTransition.None togetherWith fadeOut(tween(160))
                        } else if (groundLast(initialState, targetState, startMap, planMap)) {
                            (fadeIn(tween(220)) + rise) togetherWith
                                fadeOut(tween(GROUND_OUT_MILLIS, delayMillis = CONTENT_OUT_MILLIS))
                        } else {
                            (fadeIn(tween(220)) + rise) togetherWith fadeOut(tween(160))
                        }
                    }
                },
            ) { page ->
                val pageScope = this
                // A page that enters after its first frame (see entersAfterFirstFrame): it lies
                // hidden and a little low until it has drawn once, then fades in and rises with
                // the spring of the other pages. Read only while drawing; settled it costs nothing.
                val entrance = remember {
                    val gated = animationsOn && !backSwipe && page == pageTransition.targetState &&
                        entersAfterFirstFrame(pageTransition.currentState, page)
                    PageEntrance(if (gated) 0f else 1f).also { if (gated) pageEntering[0] = it }
                }
                LaunchedEffect(entrance) { entrance.run() }
                // A page that leaves before its entrance is over (back at once) must not keep the fog
                // under all pages waiting for it.
                DisposableEffect(entrance) {
                    onDispose { if (pageEntering[0] === entrance) pageEntering[0] = null }
                }
                if (BackLog.on) {
                    DisposableEffect(page) {
                        BackLog.pageIn(page)
                        onDispose { BackLog.pageOut(page) }
                    }
                }
                // What the page lies on while it changes to or from a page on another ground
                // (see the fog above): a page a back gesture shrinks keeps its ground, and the
                // page underneath has its own.
                val ground = pageGround(page, startMap, planMap)
                val ownGround: () -> Boolean = {
                    pageGround(pageTransition.currentState, startMap, planMap) != ground ||
                        pageGround(pageTransition.targetState, startMap, planMap) != ground ||
                        entrance.alpha.value < 1f
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Where the page lies, before any shrinking (see BackProbe).
                        .then(if (BackLog.on) Modifier.onGloballyPositioned { BackProbe.top("page:$page", it) } else Modifier)
                        // The course grows out of the row it was opened from, and back into it.
                        .then(if (page == Screen.Course && !backSwipe) Modifier.growFrom(pageScope, courseFrame.courseOrigin[0]) else Modifier)
                        .graphicsLayer {
                            // The page a back gesture leaves shrinks and rounds with it, moves a
                            // little away from the edge it came from and sinks a little, as a sheet does.
                            val leaving = page == backFrom
                            val progress = if (leaving) backProgress.value else 0f
                            // Once let go it fades as it goes (the transition leaves it opaque).
                            val fade = (if (leaving) backStage.fade.value else 1f) * entrance.alpha.value
                            if (fade < 1f) alpha = fade
                            val rise = entrance.rise.value
                            if (rise != 0f) translationY = size.height * PAGE_RISE_SHARE * rise
                            if (progress > 0f) {
                                val scale = 1f - (1f - BACK_MIN_SCALE) * progress
                                scaleX = scale
                                scaleY = scale
                                translationX = (if (backFromLeft) 1f else -1f) * BACK_SHIFT_DP.dp.toPx() * progress
                                translationY = size.height * BACK_SINK * progress
                                // A card: what the page draws into the row of the status bar (the
                                // map's picture) shrinks with it instead of being cut off. Over
                                // a map that stays, only the UI moves, as one group, unclipped.
                                if (!backStage.mapStays) {
                                    shape = BackPageShape((BACK_CORNER_DP * progress).dp.toPx(), pagesOrigin[1])
                                    clip = true
                                }
                            }
                        }
                        // A page on the map that a back gesture leaves shows the picture of
                        // the map it took along (see the back handler), where the map lies.
                        .drawBehind {
                            if (page == backFrom && backStage.mapCard) {
                                val at = IntOffset(-pagesOrigin[0].roundToInt(), -pagesOrigin[1].roundToInt())
                                val room = IntSize(mapSize[0], mapSize[1])
                                val shown = backStage.shots.value
                                val shot = backShot
                                if (shot != null) {
                                    drawImage(shot, dstOffset = at, dstSize = room, alpha = shown)
                                } else {
                                    // No picture of the map came: the card stays opaque all the
                                    // same, on the blurred picture behind the glass (or the plain ground).
                                    drawRect(
                                        AppColors.PlainGround,
                                        topLeft = Offset(at.x.toFloat(), at.y.toFloat()),
                                        size = Size(room.width.toFloat(), room.height.toFloat()),
                                        alpha = shown,
                                    )
                                    val glass = backStage.backdrop
                                    val blurred = glass?.image
                                    if (glass != null && blurred != null) {
                                        val ratio = glass.scale * glass.grow
                                        withTransform({
                                            translate(left = glass.shiftX - pagesOrigin[0], top = glass.shiftY - pagesOrigin[1])
                                            scale(ratio, ratio, Offset.Zero)
                                        }) {
                                            drawImage(blurred, alpha = shown)
                                        }
                                    }
                                }
                                backStage.marksShot?.let { marks -> drawImage(marks, dstOffset = at, dstSize = room, alpha = shown) }
                            }
                        },
                ) {
                if (ground == PageGround.Fog) {
                    val fog = glassGround(tint = AppColors.FogTint, fallback = AppColors.Fog, highlight = Color.Transparent)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            // The page a back gesture leaves is a card on its own ground.
                            .drawWithContent { if (ownGround() || page == backFrom) drawContent() }
                            .then(fog),
                    )
                } else if (ground == PageGround.Plain) {
                    Box(modifier = Modifier.matchParentSize().drawBehind { if (ownGround() || page == backFrom) drawRect(AppColors.PlainGround) })
                }
                // Leaving its ground for the map, the content of the page fades before its
                // ground does (see the transition above).
                val contentShown = pageScope.transition.animateFloat(
                    transitionSpec = { tween(CONTENT_OUT_MILLIS) },
                    label = "pageContent",
                ) { state ->
                    if (state == EnterExitState.PostExit && !backSwipe && groundLast(page, pageTransition.targetState, startMap, planMap)) 0f else 1f
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentShown.value }.jankPageDrawn(page.name)) {
                // Only the page in front takes back inside it (see PredictiveNestedBackHandler).
                CompositionLocalProvider(LocalPageInFront provides (page == screen && !walkView)) {
                when (page) {
                    Screen.Course -> coursePage(shownRequest)
                    Screen.Search -> searchPage(shownMode, placeLabel)
                    Screen.Settings -> settingsPage()
                    Screen.Trip -> tripPage(shownCompanion)
                    Screen.Plan -> planPage(pageLists)
                    Screen.Connections -> connectionsPage(pageLists)
                    Screen.Start -> startPage(fromName)
                    Screen.Departures -> departuresPage()
                    Screen.MapSheet -> mapSheetPage()
                    Screen.Favorites -> favoritesPage()
                }
                } // The page in front.
                } // Its content.
                } // The Box of the page.
            }

            pageOverlays()
            pageControls(fromName, toName)
        }
        // Choosing a start or destination on the map: the pin, its bar and its card (see MapPick.kt),
        // with the "centre on me" button where the own position is known (#205).
        MapPickOverlay(onLocate = if (mapShown && pickOwn() != null) ({ pickAtOwn(animated = true) }) else null)
    }
    }
}


/**
 * The tiles of the start page: with more than two places saved, the tile of the place the user
 * is at is left out (see hideHereTile), so the row shows where one can go; with one or two it
 * stays and says "You are here". The favourites page always shows every place.
 */
private fun startPageTiles(tiles: List<PlaceTile>, places: List<FavoritePlace>): List<PlaceTile> {
    if (!hideHereTile(places.count { it.place != null })) return tiles
    return tiles.filter { tile -> !(tile.here && tile.key.startsWith("place")) }
}

private fun distanceMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Int {
    val result = FloatArray(1)
    Location.distanceBetween(from.first, from.second, to.first, to.second, result)
    return result[0].roundToInt()
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The newest position the device still knows, wherever it lies, or null without permission
 * or when no app has asked for a position in a while.
 */
private fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.getProviders(true).mapNotNull { provider ->
        try {
            manager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        }
    }.maxByOrNull { it.time }
}

/** Last known position, or null without permission, without a fix, or where no source in use has data. */
private fun readLocation(context: Context, choice: SourceChoice): Pair<Double, Double>? {
    val location = lastKnownLocation(context) ?: return null
    return if (choice.covers(location.latitude, location.longitude)) {
        location.latitude to location.longitude
    } else {
        null
    }
}

/** The sources of positions that are switched on, best first: the fused one combines the others. */
private fun enabledProviders(manager: LocationManager): List<String> {
    val enabled = manager.getProviders(true)
    return listOf(
        LocationManager.FUSED_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    ).filter { it in enabled }
}

/**
 * The sources that report positions again and again. The fused one combines the others, so
 * it stands alone. Without it satellites and network both report, also when they are
 * switched off right now: they start to report as soon as they are switched on.
 */
private fun updateProviders(manager: LocationManager): List<String> {
    if (LocationManager.FUSED_PROVIDER in manager.getProviders(true)) return listOf(LocationManager.FUSED_PROVIDER)
    val known = manager.allProviders
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in known }
}

/**
 * Asks the device where it is right now, one source after the other until one answers.
 * Null without permission, with every source switched off, or when none answers in time.
 */
private suspend fun currentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    for (provider in enabledProviders(manager)) {
        val found = withTimeoutOrNull(LOCATE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                // Giving up, by the timeout or because the request was ended, also ends the search of the device.
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } catch (e: SecurityException) {
                    // With the approximate permission only, a precise source may refuse; the next one is asked.
                    continuation.resume(null)
                } catch (e: IllegalArgumentException) {
                    // A source that was switched off between asking and using it.
                    continuation.resume(null)
                }
            }
        }
        if (found != null) return found
    }
    return null
}

/**
 * Asks for position updates, one every [intervalMillis] at most and only after [minMeters] of
 * way. With [precise] the fused source is asked for its most exact positions (satellites
 * included), as needed to follow a moving user; else for balanced ones, which spare the
 * battery but may come from the mobile network and stay put for a while.
 */
private fun startLocationUpdates(
    manager: LocationManager,
    listener: LocationListener,
    intervalMillis: Long,
    minMeters: Float,
    precise: Boolean,
    executor: Executor,
) {
    val request = LocationRequest.Builder(intervalMillis)
        .setMinUpdateDistanceMeters(minMeters)
        .setQuality(if (precise) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
        .build()
    DiagLog.d("Location") { "updates every $intervalMillis ms, ${if (precise) "precise" else "balanced"}" }
    updateProviders(manager).forEach { provider ->
        try {
            manager.requestLocationUpdates(provider, request, executor, listener)
        } catch (e: SecurityException) {
            // With the approximate permission only, the precise source refuses; the others still report.
        } catch (e: IllegalArgumentException) {
            // A source that was switched off between asking and using it.
        }
    }
}

/** Ends the position updates of [listener]. */
private fun stopLocationUpdates(manager: LocationManager, listener: LocationListener) {
    try {
        manager.removeUpdates(listener)
    } catch (e: SecurityException) {
        // The permission was taken away in the meantime: the updates have ended with it.
    }
}

/**
 * The last value that was not null. A card or page that is animating out reads its
 * content from here, because the state it showed is already cleared by then.
 */
@Suppress("UNCHECKED_CAST")
@Composable
private fun <T : Any> lastNonNull(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    return holder[0] as T?
}

/**
 * A walk re-routed from where the traveller left its way: [legIndex] its leg in the followed
 * journey (null for a walk of another journey), the new way from [from] to [to] once it is
 * there ([path]; [pending] while it is asked for), and [remainingMeters] along it at [at].
 */
private data class WalkDetourState(
    val key: String,
    val legIndex: Int?,
    val leg: Leg,
    val from: Pair<Double, Double>,
    val to: Pair<Double, Double>,
    val pending: Boolean,
    val path: List<Pair<Double, Double>>?,
    val remainingMeters: Double?,
    val at: OffsetDateTime,
) {
    /** The minutes still to walk and when the walk ends, once a new way is drawn; null before. */
    fun remaining(): Pair<Long, OffsetDateTime>? {
        if (path == null) return null
        val meters = remainingMeters ?: return null
        val minutes = walkMinutesFor(meters)
        return minutes to at.plusMinutes(minutes)
    }
}

/**
 * Where an appointment lies and when to leave from the start [fromId] to be there in time;
 * null where unknown. It is only kept after its location was looked up, so a null [place]
 * means that it was not found.
 */
private data class AppointmentLeave(val key: String, val fromId: String, val place: Stop?, val leaveAt: OffsetDateTime?)

/**
 * The run that brings the vehicle of [course] to its first stop, where the traveller boards
 * [ride]: the last run of the same line that ends there before [course] leaves. Null when
 * the ride does not begin at the first stop of its trip, or no such run is found.
 */
private suspend fun findFeeder(source: TransitSource, ride: Leg, course: TripCourse): TripCourse? {
    val first = course.stops.firstOrNull() ?: return null
    val firstId = first.place?.id ?: return null
    val boarding = ride.fromPlace?.id
    val startsHere = (boarding != null && StationIdentity.sameStopPlaceId(boarding, firstId)) ||
        first.name.plainStopName() == ride.from.plainStopName()
    if (!startsHere) return null
    val leaves = first.departure ?: first.arrival ?: return null
    val line = course.line ?: ride.line ?: return null
    val products = if (course.product in PRODUCT_KEYS) setOf(course.product) else PRODUCT_KEYS.toSet()
    // The vehicle needs a moment to turn: a run that arrives later than this is another one.
    val latest = leaves.minusMinutes(1)
    val candidates = source.arrivals(
        stopId = firstId,
        products = products,
        time = leaves.minusMinutes(FEEDER_WINDOW_MINUTES),
        durationMinutes = FEEDER_WINDOW_MINUTES.toInt(),
    ).mapNotNull { arrival ->
        val tripId = arrival.tripId
        val time = arrival.time
        if (tripId == null || time == null || arrival.line != line || tripId == course.tripId || time.isAfter(latest)) {
            null
        } else {
            tripId to time
        }
    }.sortedByDescending { it.second }
    for ((tripId, _) in candidates.take(2)) {
        val loaded = source.tripCourse(tripId)
        val last = loaded.stops.lastOrNull() ?: continue
        val lastId = last.place?.id
        // Only a run that ends here hands its vehicle over.
        val endsHere = (lastId != null && StationIdentity.sameStopPlaceId(lastId, firstId)) ||
            last.name.plainStopName() == first.name.plainStopName()
        if (endsHere) return loaded.copy(path = thinned(loaded.path))
    }
    return null
}

/**
 * What the map of a followed trip frames at [now]: where the traveller is (the device
 * position [own], else where the timetable says), the stop of the next step, and the
 * vehicle the traveller waits for or rides in, where it is known.
 */
private fun tripFocus(
    journey: Journey,
    now: OffsetDateTime,
    own: Pair<Double, Double>?,
    tracks: Map<String, TripTrack>,
    feeders: Map<String, TripTrack>,
    boardingVehicle: Boolean = true,
): List<Pair<Double, Double>> {
    val step = tripStep(journey, now)
    val points = mutableListOf<Pair<Double, Double>>()
    (own ?: positionOnJourney(journey, now))?.let { points.add(it) }
    val leg = step.leg
    val place = when (step.kind) {
        // The stop position of a bus or tram where known: the walk to it is framed with the traveller.
        StepKind.Board -> leg?.boardingSpot?.position ?: leg?.fromPlace?.position ?: leg?.path?.firstOrNull()
        StepKind.GetOff -> leg?.toPlace?.position ?: leg?.path?.lastOrNull()
        StepKind.WalkToEnd, StepKind.Arrived -> journey.legs.last().let { it.toPlace?.position ?: it.path.lastOrNull() }
    }
    if (place != null) points.add(place)
    // Riding towards a change (#137): its walk and where the next ride is boarded are in view too.
    if (step.kind == StepKind.GetOff && leg != null) {
        points.addAll(changeFramePoints(journey.legs, journey.legs.indexOfFirst { it === leg }) { it.boardingSpot?.position })
    }
    // Before boarding without the vehicle (#91): the platform of the ride, where OpenStreetMap knows it.
    if (step.kind == StepKind.Board && !boardingVehicle && leg != null) {
        OsmStations.ridePlatforms(leg, boarding = true).firstOrNull()?.let { points.add((it.a.first + it.b.first) / 2 to (it.a.second + it.b.second) / 2) }
    }
    val tripId = leg?.tripId?.takeIf { step.kind != StepKind.Board || boardingVehicle }
    if (tripId != null) {
        val millis = now.toInstant().toEpochMilli()
        val spot = DoubleArray(2)
        val found = tracks[tripId]?.locate(millis, spot) == true || feeders[tripId]?.locate(millis, spot) == true
        if (found) points.add(spot[0] to spot[1])
    }
    return points
}

/** The way of [walk] on the map: its way on foot (else its two ends) and the stop position it leads to. */
private fun walkWay(walk: Leg): List<Pair<Double, Double>> =
    FootPaths.pathFor(walk).ifEmpty { listOfNotNull(walk.fromPlace?.position, walk.toPlace?.position) } +
        listOfNotNull(walk.walkTarget?.position)

/**
 * Where a traveller on [journey] should be at [now] by the timetable: each leg is
 * covered at an even pace along its way, and waiting happens at the start of the next leg.
 */
private fun positionOnJourney(journey: Journey, now: OffsetDateTime): Pair<Double, Double>? {
    val legs = journey.legs.filter { it.path.size >= 2 }
    if (legs.isEmpty()) return null
    val current = legs.firstOrNull { now.isBefore(it.arrival) } ?: return legs.last().path.last()
    if (now.isBefore(current.departure)) return current.path.first()
    val total = Duration.between(current.departure, current.arrival).seconds.coerceAtLeast(1)
    val done = Duration.between(current.departure, now).seconds
    return pointAtShare(current.path, done.toFloat() / total)
}

/** The point after [share] (0 to 1) of the length of [path], measured in metres. */
private fun pointAtShare(path: List<Pair<Double, Double>>, share: Float): Pair<Double, Double> {
    val steps = path.zipWithNext { start, end ->
        val result = FloatArray(1)
        Location.distanceBetween(start.first, start.second, end.first, end.second, result)
        result[0]
    }
    var left = steps.sum() * share.coerceIn(0f, 1f)
    steps.forEachIndexed { index, step ->
        if (step > 0f && left <= step) {
            val part = left / step
            val start = path[index]
            val end = path[index + 1]
            return Pair(
                start.first + (end.first - start.first) * part,
                start.second + (end.second - start.second) * part,
            )
        }
        left -= step
    }
    return path.last()
}

/**
 * The saved [places] on the map: a small white disc with the icon of each, standing on its
 * position. A tap hands the place to [onOpen].
 */
@Composable
private fun FavoriteMarkers(
    map: MapLibreMap?,
    cameraTick: () -> Int,
    places: List<FavoritePlace>,
    onOpen: (FavoritePlace) -> Unit,
    size: Dp = 30.dp,
    keyPrefix: String = "favorite",
) {
    val shown by rememberMapInAlpha()
    if (map == null) return
    places.forEachIndexed { index, favorite ->
        val place = favorite.place ?: return@forEachIndexed
        val lat = place.latitude ?: return@forEachIndexed
        val lon = place.longitude ?: return@forEachIndexed
        key("$keyPrefix$index") {
            Box(
                modifier = Modifier
                    .offset {
                        cameraTick()
                        if (!MapAlive.alive(map)) return@offset IntOffset(MAP_GONE_OFFSET, MAP_GONE_OFFSET)
                        val point = map.projection.toScreenLocation(LatLng(lat, lon))
                        IntOffset(point.x.roundToInt(), point.y.roundToInt())
                    }
                    .graphicsLayer {
                        translationX = -this.size.width / 2f
                        translationY = -this.size.height / 2f
                        alpha = shown
                    }
                    .shadow(5.dp, CircleShape)
                    .size(size)
                    .clip(CircleShape)
                    .background(AppColors.Card)
                    .clickable { onOpen(favorite) }
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(AppColors.Blue),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(placeGlyph(favorite.icon), color = AppColors.OnBlue, size = size / 2)
            }
        }
    }
}

/**
 * The dot of a place pinned with a long press. It drops in from a little above with a small
 * bounce and sends a soft ring out over the map where it lands; a pin moved to a new spot
 * lands again.
 */
@Composable
private fun PinMarker(map: MapLibreMap?, cameraTick: () -> Int, position: Pair<Double, Double>?) {
    if (map == null || position == null) return
    val drop = remember(position) { Animatable(0f) }
    val ring = remember(position) { Animatable(0f) }
    LaunchedEffect(position) {
        launch { ring.animateTo(1f, tween(PIN_RING_MILLIS)) }
        drop.animateTo(1f, Motion.pop())
    }
    val ringColor = AppColors.Blue
    Box(
        modifier = Modifier
            .offset {
                cameraTick()
                if (!MapAlive.alive(map)) return@offset IntOffset(MAP_GONE_OFFSET, MAP_GONE_OFFSET)
                val point = map.projection.toScreenLocation(LatLng(position.first, position.second))
                IntOffset(point.x.roundToInt(), point.y.roundToInt())
            }
            .graphicsLayer {
                translationX = -size.width / 2f
                translationY = -size.height / 2f
            }
            .size(26.dp)
            // The ring: from the size of the pin out to three times its size, fading away.
            .drawBehind {
                val grown = ring.value
                if (grown < 1f) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.35f * (1f - grown)),
                        radius = size.minDimension / 2f * (1f + 2f * grown),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            .graphicsLayer {
                val landed = drop.value
                scaleX = landed
                scaleY = landed
                translationY = (1f - landed) * -PIN_DROP.toPx()
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .shadow(6.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(AppColors.Card)
            .padding(6.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(AppColors.Blue),
    )
}

// A pin drops in from this high; its ring spreads over this time.
private val PIN_DROP = 22.dp
private const val PIN_RING_MILLIS = 650

/** [rows] placeholder rows of departures (a badge, a line of text, the minutes) that shimmer while the list loads. */
@Composable
private fun DepartureSkeleton(rows: Int) {
    val label = stringResource(R.string.loading_departures)
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonLine(modifier = Modifier.size(width = 46.dp, height = 24.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonLine(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp))
                    SkeletonLine(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp))
                }
                SkeletonLine(modifier = Modifier.size(width = 44.dp, height = 24.dp))
            }
        }
    }
}

/**
 * The small "i" of the map's attribution at the edge of the map, under the settings button, and
 * the compass below it; "my location" floats at the bottom right (#165). Zooming is left to the fingers: the map has no + and − buttons.
 */
@Composable
private fun MapButtons(
    modifier: Modifier = Modifier,
) {
    // A button of the map: it comes in with the map, not on the loading ground before it.
    val shown by rememberMapInAlpha()
    Column(
        modifier = modifier.graphicsLayer {
            alpha = shown
            val grow = 0.85f + 0.15f * shown
            scaleX = grow
            scaleY = grow
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // "My location" floats at the bottom right above what covers the map (#165, see FloatingLocateButton).
        // The attribution of the map and the other data sources, one tap away (see MapInfoButton).
        // Both stand at the right edge, not centred: the column is as wide as its widest button,
        // so a centred "i" (32 dp) slid sideways each time the compass (48 dp) came or went.
        MapInfoButton(modifier = Modifier.align(Alignment.End))
        // Once the map is turned: the compass, which turns it back to north (see MapCompass.kt).
        // Below the others, so they keep their places when it comes and goes.
        CompassButton(modifier = Modifier.align(Alignment.End))
    }
}

/**
 * The search fields on top of the map share one shape: the "Where to?" row of the start sheet
 * and the search of the map tab are as tall, as round at their ends, and as far from the edges
 * of the screen.
 */
internal val SEARCH_FIELD_HEIGHT = 60.dp
internal val SEARCH_FIELD_CORNER = 30.dp
internal val SEARCH_FIELD_INSET = 12.dp

/** Where the search field of the map tab starts, from the top. */
private val MAP_SEARCH_TOP = 8.dp

/** The "my location" button and the location note of the map tab: below the search field. */
private val MAP_BUTTONS_TOP = MAP_SEARCH_TOP + SEARCH_FIELD_HEIGHT + 8.dp

/**
 * The search of the map tab: a glass pill with the hint, or the name of the place it found
 * while that is marked, with a ✕ to let go of it. A tap opens the search (see SearchPanel);
 * [onPlaced] tells where the pill sits, for the search field to spring up from there.
 */
@Composable
private fun MapSearchPill(
    label: String?,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    onPlaced: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.map_search_hint)
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPlaced(it.positionInRoot().y) },
        shape = RoundedCornerShape(SEARCH_FIELD_CORNER),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = hint, onClick = onOpen)
                // Always as tall as the "Where to?" field (a Material 3 search bar and a little
                // more): heightIn holds in every layout, defaultMinSize only where the parent
                // sets no minimum of its own.
                .heightIn(min = SEARCH_FIELD_HEIGHT)
                .padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlyphIcon(Glyph.Search, color = AppColors.Muted)
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f),
                label = "mapSearchLabel",
            ) { shown ->
                Text(
                    text = shown ?: hint,
                    color = if (shown == null) AppColors.Muted else AppColors.Ink,
                    // The idle hint as firm as the hint of "Where to?", only smaller.
                    fontWeight = if (shown == null) FontWeight.Medium else FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = label != null,
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f),
            ) {
                RoundButton(
                    glyph = Glyph.Close,
                    description = stringResource(R.string.map_search_clear),
                    onClick = onClear,
                    size = 36.dp,
                    solid = true,
                )
            }
            // Keeps the pill as high as with the ✕ when there is none.
            if (label == null) Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

/** A quiet line on glass that says why the app is not at the place of the user. */
@Composable
private fun LocationNote(text: String, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = AppColors.Muted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * A named point of a journey on the map. [kind] is set for start, changes and destination,
 * which are always labelled; null for a stop on the way, labelled only where there is room.
 */
private class MapLabel(val position: Pair<Double, Double>, val name: String, val kind: SpotKind?, val bare: Boolean = false)

// Two points of a journey closer than this, in degrees, are the same place on the map.
private const val SAME_LABEL_DEGREES = 0.0002

/** Start, changes and destination of a journey, then the stops on the way of its rides. */
private fun journeyLabels(journey: Journey): List<MapLabel> {
    val labels = journeySpots(journey).map { MapLabel(it.position, it.name, it.kind, it.bare) }.toMutableList()
    journey.legs.filter { !it.walking }.forEach { leg ->
        leg.stopovers.drop(1).dropLast(1).forEach { stop ->
            val position = stop.place?.position
            val taken = position == null || labels.any { label ->
                abs(label.position.first - position.first) < SAME_LABEL_DEGREES &&
                    abs(label.position.second - position.second) < SAME_LABEL_DEGREES
            }
            if (!taken && position != null) labels.add(MapLabel(position, stop.name.plainStopName(), null))
        }
    }
    return labels
}

/**
 * The stations of a journey on the map: a small dot on the way for each, and its name next
 * to it in plain text with a halo, so the map stays readable. Start, changes and destination
 * have a sign of their own; start and destination always carry their names, a change where its
 * name finds room (zoomed far out the route shows its ends alone), the stops on the way only
 * where the route has room around them (see namedRouteStops: zoomed out the important stations
 * alone, zoomed in the stops between) and a name finds room beside its dot without covering
 * another one. Each name tries the right, the left, above and below its dot, in that order.
 * The names fade in once; a name that comes or goes as the map moves fades, one that changes
 * its side glides there, and so do the pills of the stop positions ("3", "A") when they move
 * from their position to above or beside it (see placeMarkPills).
 *
 * Moving the map makes nothing: the places are projected at once into arrays kept for the
 * journey, the boxes taken are kept in one array too.
 */
// The text of the names along a journey (start, changes, end; the stops between) and of the
// pills of stop positions: made once, not in each composition.
private val JOURNEY_KEY_STYLE = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)
private val JOURNEY_STOP_STYLE = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
private val JOURNEY_MARK_STYLE = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)

/** How far the mark of [label] reaches from its point: the destination's badge, a key spot's ring or a stop's dot. */
private fun spotReach(label: MapLabel, keyRadius: Float, stopRadius: Float, badgeReach: Float): Float = when {
    label.kind == SpotKind.End && !label.bare -> badgeReach
    label.kind != null -> keyRadius
    else -> stopRadius
}

@Composable
private fun JourneyLabels(map: MapLibreMap?, cameraTick: () -> Int, journey: Journey?) {
    if (map == null || journey == null) return
    val labels = remember(journey) { journeyLabels(journey) }
    val measurer = rememberTextMeasurer()
    val ink = AppColors.Ink
    val halo = AppColors.Card
    // The destination's badge (#181) is drawn by JourneyDestinationBadge, over everything here;
    // here it only keeps its room, and the stop positions on it give way.
    val endIndex = remember(labels) { labels.indexOfFirst { it.kind == SpotKind.End && !it.bare } }
    val keyStyle = JOURNEY_KEY_STYLE
    val stopStyle = JOURNEY_STOP_STYLE
    val layouts = remember(labels, measurer) {
        labels.map { label -> measurer.measure(label.name, if (label.kind != null) keyStyle else stopStyle, maxLines = 1) }
    }
    // The stop positions of buses and trams at the changes, with their numbers.
    val marks = remember(journey) { journeyStopMarks(journey) }
    val markStyle = JOURNEY_MARK_STYLE
    val markLayouts = remember(marks, measurer) {
        marks.map { mark -> mark.label?.let { measurer.measure(it, markStyle, maxLines = 1) } }
    }
    // The edge of each pill and dot only where its colour melts into the map (see LineCasing.kt).
    val markGround = currentMapGround()
    val markCasings = remember(marks, markGround) { IntArray(marks.size) { LineCasings.of(marks[it].color.toArgb(), markGround) } }
    val appear = remember(journey) { Animatable(0f) }
    LaunchedEffect(appear) { appear.animateTo(1f, tween(ROUTE_DRAW_MILLIS, easing = FastOutSlowInEasing)) }
    // The places as latitude and longitude in turn, their places on the screen, and the camera
    // move they were worked out for.
    val labelGeo = remember(labels) { DoubleArray(labels.size * 2) { if (it % 2 == 0) labels[it / 2].position.first else labels[it / 2].position.second } }
    val labelOut = remember(labels) { DoubleArray(labels.size * 2) }
    val xs = remember(labels) { FloatArray(labels.size) }
    val ys = remember(labels) { FloatArray(labels.size) }
    val markGeo = remember(marks) { DoubleArray(marks.size * 2) { if (it % 2 == 0) marks[it / 2].position.first else marks[it / 2].position.second } }
    val markOut = remember(marks) { DoubleArray(marks.size * 2) }
    val markXs = remember(marks) { FloatArray(marks.size) }
    val markYs = remember(marks) { FloatArray(marks.size) }
    val pillHalfWidths = remember(marks) { FloatArray(marks.size) }
    val pillScratch = remember(marks) { FloatArray(marks.size * 4) }
    // Where each pill of a stop position stands, moved off its position where pills crowd (see
    // placeMarkPills), and where it is drawn while it glides there, with its strength.
    val markShift = remember(marks) { FloatArray(marks.size * 2) }
    val markDrawn = remember(marks) { FloatArray(marks.size * 2) { Float.NaN } }
    val markAlpha = remember(marks) { FloatArray(marks.size) }
    val projected = remember(labels) { intArrayOf(-1) }
    // Which names show: start and destination always, the rest where there is room (see
    // namedRouteStops), worked out with the places on the screen; how strongly each is drawn
    // and where beside its dot (relative to it), as it fades and glides.
    val keys = remember(labels) { BooleanArray(labels.size) { labels[it].kind != null } }
    val named = remember(labels) { BooleanArray(labels.size) { true } }
    val nameAlpha = remember(labels) { FloatArray(labels.size) }
    val nameAt = remember(labels) { FloatArray(labels.size * 2) }
    // The boxes taken on the screen (left, top, right, bottom in turn): the dots, the flag of the
    // destination (its cloth and its pole), the pills, the names placed.
    val taken = remember(labels, marks) { FloatArray((labels.size * 2 + 2 + marks.size * 2) * 4) }
    // The important names first.
    val order = remember(labels) { labels.indices.sortedBy { if (labels[it].kind != null) 0 else 1 }.toIntArray() }
    // While names and pills still glide or fade, the canvas is drawn again in every frame; at
    // rest nothing runs. [settling] is written only when it changes (its mirror [settlingNow] is
    // what the drawing compares with, so the drawing does not read the state it writes).
    val easedAt = remember(journey) { longArrayOf(0L) }
    val settling = remember(journey) { mutableStateOf(false) }
    val settlingNow = remember(journey) { booleanArrayOf(false) }
    val settleTick = remember(journey) { mutableIntStateOf(0) }
    LaunchedEffect(journey) {
        snapshotFlow { settling.value }.collectLatest { active ->
            if (active) while (true) withFrameMillis { settleTick.intValue++ }
        }
    }
    // Where the names and pills were placed for (see LabelPlacement), and one point of the
    // journey (latitude, longitude) with its place on the screen, to follow a pan by.
    val placement = remember(labels, marks) { LabelPlacement() }
    val anchorGeo = remember(labels, marks) {
        when {
            labelGeo.isNotEmpty() -> doubleArrayOf(labelGeo[0], labelGeo[1])
            markGeo.isNotEmpty() -> doubleArrayOf(markGeo[0], markGeo[1])
            else -> DoubleArray(0)
        }
    }
    val anchorOut = remember(labels, marks) { DoubleArray(2) }
    val anchorWas = remember(labels, marks) { FloatArray(2) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val tick = cameraTick()
        // A map released in this frame (its view destroyed) is not read any more (#284).
        if (!MapAlive.alive(map)) return@Canvas
        // Read here, so a camera that comes to rest places the names and pills anew.
        val rests = MapCamera.rests
        settleTick.intValue
        if (tick != projected[0] || rests != placement.rests) {
            projected[0] = tick
            val camera = map.cameraPosition
            if (anchorGeo.isNotEmpty()) map.projection.toScreenLocations(anchorGeo, anchorOut)
            if (anchorGeo.isNotEmpty() && placement.panOnly(camera.zoom, camera.bearing, camera.tilt, rests)) {
                // The camera only panned: everything moves by the way it went, nothing is placed anew.
                val dx = anchorOut[0].toFloat() - anchorWas[0]
                val dy = anchorOut[1].toFloat() - anchorWas[1]
                shiftPlaces(xs, ys, labels.size, dx, dy)
                shiftPlaces(markXs, markYs, marks.size, dx, dy)
            } else {
                placement.placed(camera.zoom, camera.bearing, camera.tilt, rests)
                if (labelGeo.isNotEmpty()) map.projection.toScreenLocations(labelGeo, labelOut)
                for (index in labels.indices) {
                    xs[index] = labelOut[index * 2].toFloat()
                    ys[index] = labelOut[index * 2 + 1].toFloat()
                }
                namedRouteStopsInto(xs, ys, keys, WAY_STOP_NAME_SPACING_DP.dp.toPx(), named)
                if (markGeo.isNotEmpty()) map.projection.toScreenLocations(markGeo, markOut)
                for (index in marks.indices) {
                    markXs[index] = markOut[index * 2].toFloat()
                    markYs[index] = markOut[index * 2 + 1].toFloat()
                    val layout = markLayouts[index]
                    pillHalfWidths[index] = if (marks[index].faint || layout == null) 0f else maxOf(layout.size.width / 2f + 5.dp.toPx(), 8.dp.toPx())
                }
                placeMarkPills(markXs, markYs, pillHalfWidths, 8.dp.toPx(), 6.dp.toPx(), 2.dp.toPx(), markShift, pillScratch)
            }
            anchorWas[0] = anchorOut[0].toFloat()
            anchorWas[1] = anchorOut[1].toFloat()
        }
        val now = SystemClock.uptimeMillis()
        // The step of a frame, at most LABEL_MAX_STEP_MILLIS: after a rest a name glides from
        // where it was instead of snapping (only the very first frame places everything at once).
        val step = if (easedAt[0] == 0L) Long.MAX_VALUE else (now - easedAt[0]).coerceAtMost(LABEL_MAX_STEP_MILLIS)
        easedAt[0] = now
        // The share of the way to its target each glide and fade goes in this frame.
        val ease = if (step == Long.MAX_VALUE) 1f else 1f - exp(-step / LABEL_EASE_MILLIS).toFloat()
        var moving = false
        val shown = appear.value
        val keyRadius = 7.dp.toPx()
        val stopRadius = 3.5.dp.toPx()
        val gap = 4.dp.toPx()
        val haloWidth = 1.5.dp.toPx()
        // What is taken on the screen: the dots first, then every pill and name that was placed.
        var takenCount = 0
        // The destination's round badge (#181) takes the room of its circle; no name goes over it.
        val badgeReach = (badgeReachDp(DESTINATION_BADGE_RADIUS_DP) + BADGE_DARK_HALO_DP).dp.toPx()
        val badgeMargin = BADGE_CLEAR_MARGIN_DP.dp.toPx()
        // A stop position on the destination's badge is not drawn (#181c): the badge marks the end.
        fun onBadge(index: Int): Boolean =
            endIndex >= 0 && coversBadge(markXs[index] - xs[endIndex], markYs[index] - ys[endIndex], badgeReach, badgeMargin)
        for (index in labels.indices) {
            val radius = spotReach(labels[index], keyRadius, stopRadius, badgeReach)
            takenCount = putBox(taken, takenCount, xs[index] - radius, ys[index] - radius, xs[index] + radius, ys[index] + radius)
        }
        val markHalfHeight = 8.dp.toPx()
        for (index in marks.indices) {
            if (marks[index].faint || onBadge(index)) continue
            val x = markXs[index]
            val y = markYs[index]
            val dotRadius = 6.dp.toPx()
            takenCount = putBox(taken, takenCount, x - dotRadius, y - dotRadius, x + dotRadius, y + dotRadius)
            val layout = markLayouts[index]
            val dx = markShift[index * 2]
            if (layout != null && !dx.isNaN()) {
                val halfWidth = maxOf(layout.size.width / 2f + 5.dp.toPx(), markHalfHeight)
                val px = x + dx
                val py = y + markShift[index * 2 + 1]
                takenCount = putBox(taken, takenCount, px - halfWidth, py - markHalfHeight, px + halfWidth, py + markHalfHeight)
            }
        }
        // Stops on the way below, start, changes and destination on top.
        for (index in labels.indices) {
            if (labels[index].kind != null) continue
            val center = Offset(xs[index], ys[index])
            drawCircle(halo, radius = stopRadius, center = center, alpha = shown)
            drawCircle(ink, radius = stopRadius, center = center, style = Stroke(1.5.dp.toPx()), alpha = shown)
        }
        for (index in labels.indices) {
            val label = labels[index]
            val kind = label.kind ?: continue
            // A change at a bus or tram stop is marked by its stop positions instead.
            if (label.bare) continue
            val center = Offset(xs[index], ys[index])
            when (kind) {
                // A ring with a dot in the ink of the map, never blue: a blue dot with a white rim is
                // the traveller's own position, and the start of a trip long left looked like a second one (#166).
                SpotKind.Start -> {
                    drawCircle(halo, radius = keyRadius, center = center, alpha = shown)
                    drawCircle(ink, radius = keyRadius - 2.dp.toPx(), center = center, style = Stroke(2.5.dp.toPx()), alpha = shown)
                    drawCircle(ink, radius = 3.dp.toPx(), center = center, alpha = shown)
                }
                SpotKind.Change -> {
                    drawCircle(halo, radius = keyRadius, center = center, alpha = shown)
                    drawCircle(ink, radius = keyRadius - 2.dp.toPx(), center = center, style = Stroke(2.5.dp.toPx()), alpha = shown)
                }
                // The destination's badge is drawn over everything else, see JourneyDestinationBadge (#181c).
                SpotKind.End -> Unit
            }
        }
        // The stop positions at changes: a pill with the number in the colour of the line, or a dot.
        // A pill glides to a new side (on its position, above, below, beside) and fades where it finds no room.
        val markOutline = 1.dp.toPx()
        for (index in marks.indices) {
            val mark = marks[index]
            val center = Offset(markXs[index], markYs[index])
            val layout = markLayouts[index]
            if (onBadge(index)) continue
            if (mark.faint) {
                // Another stop position of the stop: a small pale dot, so the one to go to stands out.
                drawCircle(halo, radius = 4.dp.toPx(), center = center, alpha = shown * 0.8f)
                drawCircle(mark.color, radius = 2.8.dp.toPx(), center = center, alpha = shown * 0.6f)
                continue
            }
            val targetX = markShift[index * 2]
            val targetY = markShift[index * 2 + 1]
            val room = layout != null && !targetX.isNaN()
            if (room) {
                if (markDrawn[index * 2].isNaN() || markAlpha[index] < 0.01f) {
                    markDrawn[index * 2] = targetX
                    markDrawn[index * 2 + 1] = targetY
                } else {
                    markDrawn[index * 2] += (targetX - markDrawn[index * 2]) * ease
                    markDrawn[index * 2 + 1] += (targetY - markDrawn[index * 2 + 1]) * ease
                    if (abs(targetX - markDrawn[index * 2]) < 0.5f && abs(targetY - markDrawn[index * 2 + 1]) < 0.5f) {
                        markDrawn[index * 2] = targetX
                        markDrawn[index * 2 + 1] = targetY
                    } else {
                        moving = true
                    }
                }
            }
            markAlpha[index] = settle(markAlpha[index], if (room) 1f else 0f, ease)
            if (markAlpha[index] != if (room) 1f else 0f) moving = true
            val pillAlpha = markAlpha[index]
            val dx = markDrawn[index * 2]
            val dy = markDrawn[index * 2 + 1]
            val shifted = !dx.isNaN() && (dx != 0f || dy != 0f)
            val casingArgb = markCasings[index]
            val edge = if (casingArgb != 0) Color(casingArgb) else null
            // The dot of the position wherever the pill is not on it.
            if (layout == null || dx.isNaN() || shifted || pillAlpha < 1f) {
                if (edge != null) drawCircle(edge, radius = 5.5.dp.toPx(), center = center, alpha = shown)
                drawCircle(mark.color, radius = 4.5.dp.toPx(), center = center, alpha = shown)
            }
            if (layout == null || dx.isNaN() || pillAlpha <= 0f) continue
            val alpha = shown * pillAlpha
            val pill = center + Offset(dx, dy)
            if (shifted) {
                // Moved aside so it does not cover another pill: a leader from its dot.
                if (edge != null) drawLine(edge, center, pill, strokeWidth = 2.5.dp.toPx(), alpha = alpha)
                drawLine(mark.color, center, pill, strokeWidth = 1.5.dp.toPx(), alpha = alpha)
                if (edge != null) drawCircle(edge, radius = 5.5.dp.toPx(), center = center, alpha = shown)
                drawCircle(mark.color, radius = 4.5.dp.toPx(), center = center, alpha = shown)
            }
            val halfWidth = maxOf(layout.size.width / 2f + 5.dp.toPx(), markHalfHeight)
            // A thin edge only where the pill's colour melts into the map.
            if (edge != null) {
                drawRoundRect(
                    edge,
                    topLeft = Offset(pill.x - halfWidth - markOutline, pill.y - markHalfHeight - markOutline),
                    size = Size(2f * (halfWidth + markOutline), 2f * (markHalfHeight + markOutline)),
                    cornerRadius = CornerRadius(markHalfHeight + markOutline),
                    alpha = alpha,
                )
            }
            drawRoundRect(
                mark.color,
                topLeft = Offset(pill.x - halfWidth, pill.y - markHalfHeight),
                size = Size(2f * halfWidth, 2f * markHalfHeight),
                cornerRadius = CornerRadius(markHalfHeight),
                alpha = alpha,
            )
            drawText(
                layout,
                color = onLineColor(mark.color),
                topLeft = Offset(pill.x - layout.size.width / 2f, pill.y - layout.size.height / 2f),
                alpha = alpha,
            )
        }
        // Names: the important ones first; start and destination always placed, the others only where they fit.
        for (index in order) {
            val label = labels[index]
            val layout = layouts[index]
            val width = layout.size.width.toFloat()
            val height = layout.size.height.toFloat()
            val radius = spotReach(label, keyRadius, stopRadius, badgeReach)
            val x = xs[index]
            val y = ys[index]
            // A stop on the way where the route is crowded: its dot alone, its name once zoomed in.
            var placed = -1
            if (named[index]) {
                for (side in 0 until 4) {
                    val left = labelSideX(side, x, radius, gap, width)
                    val top = labelSideY(side, y, radius, gap, height)
                    if (left + width < 0f || left > size.width || top + height < 0f || top > size.height) continue
                    var free = true
                    for (other in 0 until takenCount) {
                        // Its own dot (the first boxes are the dots, by index) does not count.
                        if (other == index) continue
                        if (left < taken[other * 4 + 2] && left + width > taken[other * 4] &&
                            top < taken[other * 4 + 3] && top + height > taken[other * 4 + 1]
                        ) {
                            free = false
                            break
                        }
                    }
                    if (free) {
                        placed = side
                        break
                    }
                }
                // Start and destination keep their names even where the map is crowded.
                if (placed < 0 && (label.kind == SpotKind.Start || label.kind == SpotKind.End)) placed = 0
            }
            if (placed >= 0) {
                val left = labelSideX(placed, x, radius, gap, width)
                val top = labelSideY(placed, y, radius, gap, height)
                takenCount = putBox(taken, takenCount, left, top, left + width, top + height)
                val relX = left - x
                val relY = top - y
                if (nameAlpha[index] < 0.01f) {
                    nameAt[index * 2] = relX
                    nameAt[index * 2 + 1] = relY
                } else if (nameAt[index * 2] != relX || nameAt[index * 2 + 1] != relY) {
                    nameAt[index * 2] += (relX - nameAt[index * 2]) * ease
                    nameAt[index * 2 + 1] += (relY - nameAt[index * 2 + 1]) * ease
                    if (abs(relX - nameAt[index * 2]) < 0.5f && abs(relY - nameAt[index * 2 + 1]) < 0.5f) {
                        nameAt[index * 2] = relX
                        nameAt[index * 2 + 1] = relY
                    } else {
                        moving = true
                    }
                }
            }
            val target = if (placed >= 0) 1f else 0f
            nameAlpha[index] = settle(nameAlpha[index], target, ease)
            if (nameAlpha[index] != target) moving = true
            val alpha = shown * nameAlpha[index]
            if (alpha <= 0f) continue
            val corner = Offset(x + nameAt[index * 2], y + nameAt[index * 2 + 1])
            val stepOut = haloWidth
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx != 0 || dy != 0) drawText(layout, color = halo, topLeft = corner + Offset(dx * stepOut, dy * stepOut), alpha = alpha)
                }
            }
            drawText(layout, color = ink, topLeft = corner, alpha = alpha)
        }
        if (settlingNow[0] != moving) {
            settlingNow[0] = moving
            settling.value = moving
        }
    }
}

// How quickly names and pills of a journey glide to a new side and fade: the time constant, in ms.
private const val LABEL_EASE_MILLIS = 90.0

// The longest step of the glide in one frame: two frames at 60 Hz.
private const val LABEL_MAX_STEP_MILLIS = 32L

/** Writes the box [left], [top], [right], [bottom] into [boxes] as its box number [count]; the count after it. */
private fun putBox(boxes: FloatArray, count: Int, left: Float, top: Float, right: Float, bottom: Float): Int {
    boxes[count * 4] = left
    boxes[count * 4 + 1] = top
    boxes[count * 4 + 2] = right
    boxes[count * 4 + 3] = bottom
    return count + 1
}

/** [value] moved [ease] of the way toward [target], snapped to it when it is that close. */
private fun settle(value: Float, target: Float, ease: Float): Float {
    val next = value + (target - value) * ease
    return if (abs(target - next) < 0.01f) target else next
}

/** The left edge of a name [width] wide on [side] (0 right, 1 left, 2 above, 3 below) of a dot at [x] of [radius]. */
private fun labelSideX(side: Int, x: Float, radius: Float, gap: Float, width: Float): Float = when (side) {
    0 -> x + radius + gap
    1 -> x - radius - gap - width
    else -> x - width / 2f
}

/** The top edge of a name [height] high on [side] (see labelSideX) of a dot at [y] of [radius]. */
private fun labelSideY(side: Int, y: Float, radius: Float, gap: Float, height: Float): Float = when (side) {
    2 -> y - radius - gap - height
    3 -> y + radius + gap
    else -> y - height / 2f
}

/**
 * Ways on the map: a white casing with the line colour on top; walks as a row of dots.
 * When the layer appears the ways draw themselves from their start to their end, the
 * layers of several options one after the other by their [order]; with [snake] the ways of
 * one journey draw as one path, leg after leg in their order (see RouteSnake.kt), else all at
 * once. A layer that is not
 * [picked] is grey and thin; picking it lets it take its colours and grow thicker with a
 * spring, and with [layered] brings it in front of the layers next to it.
 *
 * The points are only turned into places on the screen when the map has moved, and paths
 * and paints are made once: drawing a frame of the animation creates nothing.
 */
/** A ride whose grey end glides (see RouteSplitGlide): its way and split, and the track that places its vehicle. */
private class GlideRide(val glide: RouteGlide, val track: TripTrack)

// The end of the grey is moved on this often at most (ms): about 15 times a second.
private const val GLIDE_FRAME_MILLIS = 66L

// The vehicle is looked for this far beyond the split (metres): more than two seconds of any train.
private const val GLIDE_REACH_METRES = 400.0

// The most points of the piece drawn over the grey (two seconds of a ride are a few).
private const val GLIDE_MAX_POINTS = 32

// Without a visible step of the end for this long (ms) the glide rests until the next split.
private const val GLIDE_REST_MILLIS = 1_000L

/**
 * The end of the grey of the ride on board moves with its vehicle (#105): between two splits of
 * the way (every ROUTE_SPLIT_MILLIS) the short piece from the split to where the same track puts
 * the vehicle now is drawn in grey over the way, on a small canvas of its own: no way is split or
 * drawn again for it, and the next split takes over exactly where the piece ended. At most about
 * 15 updates a second, and only while [active] (the companion's map in front, animations on), a
 * step of at least a pixel is due and the vehicle moves; else nothing runs, and the grey steps
 * at each split as before.
 */
@Composable
private fun RouteSplitGlide(map: MapLibreMap, cameraTick: () -> Int, rides: List<GlideRide>, active: Boolean) {
    if (rides.isEmpty()) return
    val density = LocalDensity.current
    // Where the grey ends now on each ride (metres along its way): the split until the vehicle moved on.
    val shown = remember(rides) { DoubleArray(rides.size) { rides[it].glide.split } }
    val glideTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(rides, active) {
        if (!active) {
            if (rides.indices.any { shown[it] != rides[it].glide.split }) {
                for (index in rides.indices) shown[index] = rides[index].glide.split
                glideTick.intValue++
            }
            return@LaunchedEffect
        }
        val spot = DoubleArray(2)
        var lastStep = SystemClock.uptimeMillis()
        while (true) {
            delay(GLIDE_FRAME_MILLIS)
            withFrameMillis { }
            // A map released meanwhile (its view destroyed): the loop ends with it (#284).
            if (!MapAlive.alive(map)) break
            val now = SystemClock.uptimeMillis()
            val millis = System.currentTimeMillis()
            var stepped = false
            for (index in rides.indices) {
                val ride = rides[index]
                val glide = ride.glide
                // Where its marker is drawn (a live report, the traveller on board): never ahead of it (#119).
                if (!DrawnVehicles.locate(ride.track.tripId, spot) && !ride.track.locate(millis, spot)) continue
                val along = alongNear(glide.latLon, glide.along, spot[0], spot[1], glide.split, GLIDE_REACH_METRES)
                if (along.isNaN() || along <= shown[index]) continue
                // A step of less than a pixel is not drawn (zoomed out, a slow vehicle).
                val perPixel = map.projection.getMetersPerPixelAtLatitude(spot[0])
                if (perPixel <= 0.0 || (along - shown[index]) / perPixel < 1.0) continue
                shown[index] = along
                stepped = true
            }
            if (stepped) {
                lastStep = now
                glideTick.intValue++
            } else if (now - lastStep > GLIDE_REST_MILLIS) {
                // The vehicle stands (or the map is far out): rest until the next split.
                break
            }
        }
    }
    val ground = currentMapGround()
    val casingArgb = remember(ground) { LineCasings.of(TRAVELLED_COLOR.toArgb(), ground) }
    val way = remember { Path() }
    val casing = remember { strokePaint() }
    val ink = remember { strokePaint() }
    val piece = remember { DoubleArray(GLIDE_MAX_POINTS * 2) }
    // Arrays of the size of each piece, made once per size: the projection takes whole arrays.
    val sized = remember { arrayOfNulls<DoubleArray>(GLIDE_MAX_POINTS + 1) }
    val sizedOut = remember { arrayOfNulls<DoubleArray>(GLIDE_MAX_POINTS + 1) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        cameraTick()
        glideTick.intValue
        if (!MapAlive.alive(map)) return@Canvas
        // As the way of a picked ride (see RouteLayer): 6 dp, a casing 2 dp wider where needed.
        val width = 6.dp.toPx()
        val casingWidth = width + 2.dp.toPx()
        drawIntoCanvas { canvas ->
            for (index in rides.indices) {
                val glide = rides[index].glide
                if (shown[index] <= glide.split) continue
                val count = glidePiece(glide.latLon, glide.along, glide.split, shown[index], piece)
                if (count < 2) continue
                val geo = sized[count] ?: DoubleArray(count * 2).also { sized[count] = it }
                val out = sizedOut[count] ?: DoubleArray(count * 2).also { sizedOut[count] = it }
                piece.copyInto(geo, 0, 0, count * 2)
                map.projection.toScreenLocations(geo, out)
                way.rewind()
                way.moveTo(out[0].toFloat(), out[1].toFloat())
                for (point in 1 until count) way.lineTo(out[point * 2].toFloat(), out[point * 2 + 1].toFloat())
                if (casingArgb != 0) {
                    casing.color = Color(casingArgb)
                    casing.strokeWidth = casingWidth
                    canvas.drawPath(way, casing)
                }
                ink.color = Color(glide.argb)
                ink.strokeWidth = width
                canvas.drawPath(way, ink)
            }
        }
    }
}

@Composable
private fun RouteLayer(
    map: MapLibreMap,
    cameraTick: () -> Int,
    routes: List<MapRoute>,
    picked: Boolean = true,
    order: Int = 0,
    layered: Boolean = false,
    snake: Boolean = false,
    inFront: Boolean = true,
    badgeAt: Pair<Double, Double>? = null,
) {
    // The destination's badge (#181c), where the journey has one: the stop dots on it are left out.
    val badgeGeo = remember(badgeAt) { badgeAt?.let { doubleArrayOf(it.first, it.second) } }
    val badgeScreen = remember(badgeAt) { DoubleArray(2) }
    // Where each leg starts and ends within the draw-in (#69); null: all legs at once.
    val snakeAt = remember(routes, snake) {
        if (snake && routes.size > 1) snakeBounds(DoubleArray(routes.size) { wayMeters(routes[it].points) }) else null
    }
    val drawMillis = if (snakeAt != null) snakeMillis(ROUTE_DRAW_MILLIS, routes.size) else ROUTE_DRAW_MILLIS
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(reveal) {
        animationDelay(order * ROUTE_STAGGER_MILLIS)
        reveal.animateTo(1f, tween(drawMillis, easing = FastOutSlowInEasing))
    }
    // A way that is no longer the picked one fades to grey.
    val strength by animateFloatAsState(
        if (picked) 1f else 0f,
        tween(ROUTE_FADE_MILLIS, easing = FastOutSlowInEasing),
        label = "routeStrength",
    )
    // A way that becomes the picked one draws its colours anew over the grey, like it drew
    // itself when it appeared.
    val recolour = remember { Animatable(1f) }
    val wasPicked = remember { booleanArrayOf(picked) }
    LaunchedEffect(picked) {
        val before = wasPicked[0]
        wasPicked[0] = picked
        if (picked && !before) {
            recolour.snapTo(0f)
            recolour.animateTo(1f, tween(drawMillis, easing = FastOutSlowInEasing))
        }
    }
    val density = LocalDensity.current
    // A dash of almost no length with round ends draws as a dot.
    val dots = remember(density) { PathEffect.dashPathEffect(floatArrayOf(0.1f, with(density) { 10.dp.toPx() }), 0f) }
    val dashes = remember(density) {
        PathEffect.dashPathEffect(floatArrayOf(with(density) { 9.dp.toPx() }, with(density) { 9.dp.toPx() }), 0f)
    }
    val way = remember { Path() }
    val casing = remember { strokePaint() }
    val ink = remember { strokePaint() }
    // The chevrons of the rides (see LineArrows.kt): one small open arrow, made once, turned and
    // moved for each; its paint takes the ink of each line.
    val chevron = remember(density) { routeChevron(density.density) }
    val chevronPaint = remember(density) { strokePaint().apply { strokeWidth = 1.7f * density.density } }
    // Where the points lie on the screen (x and y in turn), and the camera move they were worked out for.
    val screen = remember(routes) { routes.map { FloatArray(it.points.size * 2) } }
    val stopScreen = remember(routes) { routes.map { FloatArray(it.stops.size * 2) } }
    // The places of the points and stops as latitude and longitude in turn, and room for their
    // places on the screen: projected all at once per route and camera move, no LatLng or
    // PointF per point and frame.
    val geo = remember(routes) { routes.map { route -> flatPlaces(route.points) } }
    val stopGeo = remember(routes) { routes.map { route -> flatPlaces(route.stops) } }
    val geoScreen = remember(routes) { routes.map { DoubleArray(it.points.size * 2) } }
    val stopGeoScreen = remember(routes) { routes.map { DoubleArray(it.stops.size * 2) } }
    val projected = remember(routes) { intArrayOf(-1) }
    // Ways of the journey on the same course (a way in and back on one line, a walk along the
    // tracks): each drawn a little to its right, so they stand side by side (see RouteOverlap.kt).
    val shifted = remember(routes) { overlappingWays(routes.map { it.points }) }
    val shiftScratch = remember(routes) { routes.map { route -> FloatArray(route.points.size * 2) } }
    // The casing under a way only where its colour melts into the map (see LineCasing.kt),
    // worked out once per colour and map: 0 for none.
    val ground = currentMapGround()
    val casings = remember(routes, ground) { IntArray(routes.size) { LineCasings.of(routes[it].color.toArgb(), ground) } }
    val greyCasing = remember(ground) { LineCasings.of(OTHER_OPTION_COLOR.toArgb(), ground) }
    // The chevrons flow along the ways in their direction (see ArrowFlowRun): a slow drift,
    // moved on about 30 times a second, only while idle animations may run (not with the battery
    // saver or reduced motion), only on the picked option, only while the map is in front
    // ([inFront]), the app resumed and some of the way is not covered by the cards. Eight
    // seconds after the last camera move or pick the flow slows to rest, and it picks up again
    // with the next one. Only the canvas of the chevrons reads it: the ways are not drawn again for it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val flowOn = rememberIdleAnimationsOn() && picked && routes.any { it.arrows } && inFront && resumed
    val flow = remember { mutableFloatStateOf(0f) }
    val flowRun = remember(density) { ArrowFlowRun(ARROW_SPACING_DP * density.density, ARROW_FLOW_DP_PER_SECOND * density.density) }
    // The size of the canvas (px), for whether the cards cover the way.
    val canvasSize = remember { FloatArray(2) }
    val covers = LocalMapCover.current
    // Some of a way with chevrons lies in the part of the map the cards leave free.
    fun arrowsUncovered(): Boolean {
        val width = canvasSize[0]
        val height = canvasSize[1]
        if (width <= 0f || height <= 0f) return true
        val top = covers?.get(0)?.takeIf { !it.isNaN() && it in 0f..height } ?: 0f
        val bottom = covers?.get(1)?.takeIf { !it.isNaN() && it > 0f && it < height } ?: height
        val left = covers?.getOrNull(2)?.takeIf { !it.isNaN() && it > 0f } ?: 0f
        for (index in routes.indices) {
            if (!routes[index].arrows) continue
            if (wayInRoom(screen[index], screen[index].size / 2, left, top, width, bottom)) return true
        }
        return false
    }
    // The camera moves and the pick wake the flow (a new pick restarts this effect).
    LaunchedEffect(flowOn, flowRun) {
        if (!flowOn) return@LaunchedEffect
        flowRun.pause()
        flowRun.active(SystemClock.uptimeMillis())
        var seenTick = cameraTick()
        while (true) {
            delay(ARROW_FLOW_FRAME_MILLIS)
            val tick = cameraTick()
            val now = SystemClock.uptimeMillis()
            if (tick != seenTick) {
                seenTick = tick
                flowRun.active(now)
            }
            if (flowRun.resting(now)) {
                // At rest: no frames until the camera moves again.
                flowRun.pause()
                seenTick = snapshotFlow { cameraTick() }.first { it != seenTick }
                flowRun.active(SystemClock.uptimeMillis())
                continue
            }
            if (!arrowsUncovered()) {
                // Hidden under the cards: looked at again now and then, without frames.
                flowRun.pause()
                delay(ARROW_FLOW_COVERED_POLL_MILLIS)
                continue
            }
            val millis = withFrameMillis { it }
            // On the app's animation clock: slower with a larger animator duration scale.
            flow.floatValue = flowRun.step(now, SystemAnimation.clockMillis(millis))
        }
    }
    // Among the layers of several options the picked one lies on top.
    Box(modifier = Modifier.fillMaxSize().zIndex(if (layered && picked) 1f else 0f)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Read here, so the ways are drawn again whenever the map moves.
        val tick = cameraTick()
        if (!MapAlive.alive(map)) return@Canvas
        if (tick != projected[0]) {
            projected[0] = tick
            for (index in routes.indices) {
                projectInto(map, geo[index], geoScreen[index], screen[index])
                projectInto(map, stopGeo[index], stopGeoScreen[index], stopScreen[index])
                if (shifted[index]) {
                    val places = screen[index]
                    shiftRight(places, places.size / 2, OVERLAP_SHIFT_DP.dp.toPx(), shiftScratch[index])
                    shiftScratch[index].copyInto(places)
                }
            }
        }
        val shown = reveal.value
        val coloured = if (picked) recolour.value else 1f
        val strong = strength
        val width = 4.dp.toPx() + 2.dp.toPx() * strong
        // A thin casing, where one is needed at all.
        val casingWidth = width + 2.dp.toPx()
        drawIntoCanvas { canvas ->
            for (index in routes.indices) {
                val route = routes[index]
                val places = screen[index]
                val count = places.size / 2
                // How far this leg has drawn itself, and taken its colours, in the draw-in of the journey.
                val legShown = if (snakeAt != null) snakeShare(snakeAt, index, shown) else shown
                val legColoured = if (snakeAt != null && coloured < 1f) snakeShare(snakeAt, index, coloured) else coloured
                if (count >= 2 && legShown > 0f) {
                    traceWay(way, places, legShown)
                    val effect = when {
                        route.dotted -> dots
                        route.dashed -> dashes
                        else -> null
                    }
                    // The casing follows the colour shown: the grey of another option, else the line's.
                    val casingArgb = if (legColoured < 1f || strong <= 0.5f) greyCasing else casings[index]
                    if (casingArgb != 0) {
                        casing.color = Color(casingArgb)
                        casing.strokeWidth = casingWidth
                        casing.pathEffect = effect
                        canvas.drawPath(way, casing)
                    }
                    ink.color = when {
                        // The newly picked way is grey where its colours have not come yet.
                        legColoured < 1f -> OTHER_OPTION_COLOR
                        strong >= 1f -> route.color
                        strong <= 0f -> OTHER_OPTION_COLOR
                        else -> lerp(OTHER_OPTION_COLOR, route.color, strong)
                    }
                    ink.strokeWidth = width
                    ink.pathEffect = effect
                    canvas.drawPath(way, ink)
                    if (legColoured < 1f && legColoured > 0f) {
                        traceWay(way, places, minOf(legShown, legColoured))
                        ink.color = route.color
                        canvas.drawPath(way, ink)
                    }
                }
            }
        }
        // The stops on a way still to come, once it has drawn itself: small white dots in its colour.
        if (shown >= 1f) {
            if (badgeGeo != null) map.projection.toScreenLocations(badgeGeo, badgeScreen)
            val badgeReach = (badgeReachDp(DESTINATION_BADGE_RADIUS_DP) + BADGE_DARK_HALO_DP).dp.toPx()
            val badgeMargin = BADGE_CLEAR_MARGIN_DP.dp.toPx()
            for (index in routes.indices) {
                val color = routes[index].color
                val stopPlaces = stopScreen[index]
                for (stop in 0 until stopPlaces.size / 2) {
                    // The last stop's dot under the destination's badge: the badge marks the end.
                    if (badgeGeo != null &&
                        coversBadge(stopPlaces[stop * 2] - badgeScreen[0].toFloat(), stopPlaces[stop * 2 + 1] - badgeScreen[1].toFloat(), badgeReach, badgeMargin)
                    ) continue
                    val center = Offset(stopPlaces[stop * 2], stopPlaces[stop * 2 + 1])
                    drawCircle(AppColors.Card, radius = 4.5.dp.toPx(), center = center)
                    drawCircle(color, radius = 3.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
    // The chevrons on a canvas of their own above the ways, drawn again for the flow alone; the
    // places on the screen are those the canvas of the ways worked out for this camera (it is
    // drawn first, in the same frame).
    Canvas(modifier = Modifier.fillMaxSize()) {
        cameraTick()
        canvasSize[0] = size.width
        canvasSize[1] = size.height
        val phase = flow.floatValue
        val shown = reveal.value
        val coloured = if (picked) recolour.value else 1f
        val strong = strength
        drawIntoCanvas { canvas ->
            for (index in routes.indices) {
                val route = routes[index]
                if (!route.arrows) continue
                val places = screen[index]
                val count = places.size / 2
                if (count < 2) continue
                // Chevrons in the direction of travel, as far as the line has drawn itself; they
                // fade in with its end, and only on the picked option.
                val legShown = if (snakeAt != null) snakeShare(snakeAt, index, shown) else shown
                val legColoured = if (snakeAt != null && coloured < 1f) snakeShare(snakeAt, index, coloured) else coloured
                val arrowShare = arrowAppear(legShown) * strong * legColoured
                if (arrowShare <= 0.01f) continue
                chevronPaint.color = Color(arrowInkArgb(route.color.toArgb()))
                chevronPaint.alpha = chevronPaint.alpha * arrowShare
                walkArrows(
                    places, count, legShown * (count - 1), ARROW_SPACING_DP.dp.toPx(),
                    size.width, size.height, margin = 8.dp.toPx(), phase = phase,
                ) { x, y, dx, dy ->
                    canvas.save()
                    canvas.translate(x, y)
                    canvas.rotate(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat())
                    canvas.drawPath(chevron, chevronPaint)
                    canvas.restore()
                }
            }
        }
    }
    }
}

/**
 * Puts into [way] the line through the points in [places] (x and y in turn) as far as
 * [share] of it: whole stretches, and a part of the next one.
 */
private fun traceWay(way: Path, places: FloatArray, share: Float) {
    val count = places.size / 2
    val reach = share.coerceIn(0f, 1f) * (count - 1)
    val whole = reach.toInt().coerceAtMost(count - 1)
    way.rewind()
    way.moveTo(places[0], places[1])
    for (point in 1..whole) way.lineTo(places[point * 2], places[point * 2 + 1])
    if (whole < count - 1) {
        val part = reach - whole
        way.lineTo(
            places[whole * 2] + (places[whole * 2 + 2] - places[whole * 2]) * part,
            places[whole * 2 + 1] + (places[whole * 2 + 3] - places[whole * 2 + 1]) * part,
        )
    }
}

/**
 * The chevron of a ride: an open arrow pointing right (to +x), centred on 0, 0, small enough
 * to sit inside the line; [density] is that of the screen.
 */
private fun routeChevron(density: Float): Path = Path().apply {
    val back = 1.3f * density
    val span = 2.1f * density
    moveTo(-back, -span)
    lineTo(back, 0f)
    lineTo(-back, span)
}

/** The places of [points] as latitude and longitude in turn, for projecting them all at once. */
private fun flatPlaces(points: List<Pair<Double, Double>>): DoubleArray {
    val flat = DoubleArray(points.size * 2)
    for (index in points.indices) {
        flat[index * 2] = points[index].first
        flat[index * 2 + 1] = points[index].second
    }
    return flat
}

/**
 * Projects the places [geo] (latitude and longitude in turn) onto the screen of [map] at once,
 * through [scratch] (as long as [geo]), into [into] as x and y in turn.
 */
private fun projectInto(map: MapLibreMap, geo: DoubleArray, scratch: DoubleArray, into: FloatArray) {
    if (geo.isEmpty() || !MapAlive.alive(map)) return
    map.projection.toScreenLocations(geo, scratch)
    for (index in geo.indices) into[index] = scratch[index].toFloat()
}

/** A paint for a line with round ends and round corners. */
private fun strokePaint(): Paint = Paint().apply {
    style = PaintingStyle.Stroke
    strokeCap = StrokeCap.Round
    strokeJoin = StrokeJoin.Round
}

/** How far [tap] lies from the way of [journey] on the screen, in pixels. */
private fun screenDistance(map: MapLibreMap, journey: Journey, tap: PointF): Float {
    if (!MapAlive.alive(map)) return Float.MAX_VALUE
    var nearest = Float.MAX_VALUE
    journey.legs.forEach { leg ->
        val path = thinned(leg.path)
        var before: PointF? = null
        path.forEach { point ->
            val place = map.projection.toScreenLocation(LatLng(point.first, point.second))
            val from = before
            if (from != null) {
                // The nearest point of the stretch from one point of the way to the next.
                val dx = place.x - from.x
                val dy = place.y - from.y
                val length = dx * dx + dy * dy
                val share = if (length <= 0f) 0f else (((tap.x - from.x) * dx + (tap.y - from.y) * dy) / length).coerceIn(0f, 1f)
                val distance = hypot(tap.x - (from.x + dx * share), tap.y - (from.y + dy * share))
                if (distance < nearest) nearest = distance
            }
            before = place
        }
    }
    return nearest
}

// How long the arrow of a vehicle stays after it last moved: a stop or a late update, not a long stand.
internal const val VEHICLE_HEADING_KEEP_MILLIS = 3 * 60_000L

/**
 * The direction a vehicle at [progress] of its [path] moves on the ground, as a share to the
 * north and to the east (the east in the same measure as the north): the compass bearing
 * from where it is to a point a little further along its way (the path starts at the
 * vehicle and runs in its direction of travel, see [wayAhead]); at the end of its glide that
 * of the last part of the way. Null where the way does not move: a single point, or the same
 * point over that part.
 */
internal fun groundDirection(path: List<Pair<Double, Double>>, progress: Float): Pair<Double, Double>? {
    if (path.size < 2) return null
    val ahead = (progress.coerceIn(0f, 1f) + 0.1f).coerceAtMost(1f)
    val from = pointOnPath(path, (ahead - 0.1f).coerceAtLeast(0f))
    val to = pointOnPath(path, ahead)
    if (abs(to.first - from.first) < 1e-7 && abs(to.second - from.second) < 1e-7) return null
    val bearing = bearingDegrees(from, to)
    if (bearing.isNaN()) return null
    val angle = Math.toRadians(bearing)
    return cos(angle) to sin(angle)
}

/** The position after [progress] (0 to 1) of the way along [path]. */
internal fun pointOnPath(path: List<Pair<Double, Double>>, progress: Float): Pair<Double, Double> {
    if (path.size < 2) return path.first()
    val scaled = progress.coerceIn(0f, 1f) * (path.size - 1)
    val index = scaled.toInt().coerceAtMost(path.size - 2)
    val part = scaled - index
    val from = path[index]
    val to = path[index + 1]
    return (from.first + (to.first - from.first) * part) to (from.second + (to.second - from.second) * part)
}

@Composable
fun StopChips(
    stops: List<Stop>,
    selectedStopId: String?,
    onSelectStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fadeEdges(scroll, horizontal = true)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        stops.forEach { stop ->
            val distance = stop.distanceMeters
            val label = if (distance != null) {
                stringResource(R.string.stop_with_distance, stop.name.shortStopName(), formatDistance(distance))
            } else {
                stop.name.shortStopName()
            }
            ChoiceChip(text = label, active = stop.id == selectedStopId, onClick = { onSelectStop(stop.id) })
        }
    }
}

/**
 * The start page: nearby stops and the settings on top, the map free above a sheet that
 * holds the rest. The sheet is pulled between a low peek, the middle (where it starts) and
 * full height. A followed trip is one slim band at its top; then, scrolling, the next
 * departures, the favourite places as tiles and the next appointment. At its bottom edge,
 * in every stage, the time to travel at with the last searches next to it, and the row that
 * reads like a journey: from where, where to, and the swap of the two.
 *
 * [classic] shows the page as it was before the sheet: the same parts as floating cards that
 * stand on the bottom edge over the map, with a followed trip as a card on top of them.
 */
@Composable
private fun StartScreen(
    classic: Boolean,
    showCards: Boolean,
    showDepartures: Boolean,
    stops: List<Stop>,
    selectedStopId: String?,
    onSelectStop: (String) -> Unit,
    departures: List<Departure>,
    departuresLoaded: Boolean,
    error: String?,
    stopFavorite: Boolean,
    onDepartureAction: (Departure, DepartureAction) -> Unit,
    onAllDepartures: () -> Unit,
    locationNote: String?,
    recentRoutes: List<SavedRoute>,
    onOpenRoute: (SavedRoute) -> Unit,
    time: TimeChoice?,
    onTime: () -> Unit,
    now: OffsetDateTime,
    fromName: String,
    onPickOrigin: () -> Unit,
    onSwap: () -> Unit,
    tiles: List<PlaceTile>,
    trip: Journey?,
    onOpenTrip: () -> Unit,
    tripActions: TripActions?,
    showAppointment: Boolean,
    onRemoveAppointment: () -> Unit,
    onLaterAppointment: () -> Unit,
    appointment: AppointmentTileState,
    onEnableCalendar: () -> Unit,
    onOpenAppointment: (Appointment) -> Unit,
    onPickAppointment: (Appointment) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onWhereToPlaced: (Float) -> Unit,
    onCoverTop: (Float) -> Unit,
    place: (@Composable () -> Unit)? = null,
    // A back gesture that lets go of the place: the content of the sheet shows under it.
    placeBack: BackPreview? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A wide screen has the sheet as a panel at the side of the map; a small one (the cover
        // screen of a flip phone) keeps only the essentials: the trip, the time and where to.
        val room = screenRoom(maxWidth, maxHeight)
        val wide = room == ScreenRoom.Wide
        val cardsShown = showCards && room != ScreenRoom.Small
        // On a low screen the departures card shows fewer rows; on the classic page also with a
        // followed trip, and there the last searches give way on a low screen, so the map keeps some room.
        val departureRows = if (maxHeight < 720.dp || (classic && trip != null)) 2 else 3
        // A minimal start page (cards switched off) keeps only the trip, the time and where to.
        val showRecent = cardsShown && recentRoutes.isNotEmpty() && (!classic || maxHeight >= 620.dp)
        // The stage of the sheet stays as the user left it, also across restarts of the app.
        val stageContext = LocalContext.current
        val stagePrefs = remember { AppPrefs(stageContext) }
        var stage by remember {
            mutableStateOf(SheetStage.entries.firstOrNull { it.name == stagePrefs.startSheetStage } ?: SheetStage.Half)
        }
        LaunchedEffect(stage) { stagePrefs.startSheetStage = stage.name }
        // The first start of the page after the splash screen: the sheet (or the cards) rises
        // with the spring of the app, the chips and buttons on top follow a moment later.
        // With animations off all of it is there at once. The cards in the sheet follow the map,
        // its stop signs and vehicles (see IntroStep), one after the other.
        val introAnimate = rememberAnimationsOn()
        val introStart = if (Startup.introPlayed || !introAnimate) 1f else 0f
        val introSheet = remember { Animatable(introStart) }
        val introTop = remember { Animatable(introStart) }
        val introCards = remember { List(INTRO_CARDS) { Animatable(introStart) } }
        LaunchedEffect(Unit) {
            if (Startup.introPlayed) return@LaunchedEffect
            Startup.introPlayed = true
            if (!introAnimate) return@LaunchedEffect
            // Nothing of the sheet before the map, its stop signs, vehicles and signs of
            // disruptions: the sheet rises at its step, the chips on top and then the cards in
            // it follow one after the other.
            introCards.forEachIndexed { index, card ->
                launch {
                    awaitIntroStep(IntroStep.CARDS)
                    animationDelay(Motion.STAGGER_MILLIS * (index + 1))
                    card.animateTo(1f, Motion.settle())
                }
            }
            awaitIntroStep(IntroStep.CARDS)
            launch { introSheet.animateTo(1f, Motion.settle()) }
            animationDelay(Motion.STAGGER_MILLIS)
            introTop.animateTo(1f, Motion.settle())
        }
        // A card of the sheet (by its place from the top) as far as it has come in: it rises and
        // fades, drawn only, so the sheet keeps its room and nothing below moves.
        val cardIntro: (Int) -> Modifier = { index ->
            val card = introCards[index.coerceIn(0, INTRO_CARDS - 1)]
            Modifier.graphicsLayer {
                val shown = card.value
                alpha = shown.coerceIn(0f, 1f)
                translationY = (1f - shown) * INTRO_CARD_RISE.toPx()
            }
        }
        val sheetIntro = Modifier.graphicsLayer {
            translationY = (1f - introSheet.value) * INTRO_RISE.toPx()
            alpha = introSheet.value.coerceIn(0f, 1f)
        }
        val topIntro = Modifier.graphicsLayer {
            translationY = (1f - introTop.value) * -INTRO_DROP.toPx()
            alpha = introTop.value.coerceIn(0f, 1f)
        }
        // A place marked on the map takes the place of the content of the sheet: the last one
        // is kept, so it still shows while the sheet swaps back.
        val heldPlace = remember { arrayOfNulls<@Composable () -> Unit>(1) }
        if (place != null) heldPlace[0] = place
        val placeShown = place != null
        // A sheet that was put away comes up for the place.
        LaunchedEffect(placeShown) { if (placeShown && stage == SheetStage.Away) stage = SheetStage.Half }
        // The content of the page, or the place in its stead, swapped with a short slide and fade.
        // A back gesture seeks the swap as it goes: the place shrinks over the content.
        val swapped: @Composable (@Composable () -> Unit) -> Unit = { page ->
            rememberBackSeek(placeShown, placeBack, backTarget = false, label = "startSheetPlace").AnimatedContent(
                transitionSpec = {
                    if (placeBack?.active == true) {
                        backContentTransform(placeBack)
                    } else {
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 6 }) togetherWith
                            (fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 6 })
                    }
                },
            ) { showing ->
                if (showing) {
                    Box(modifier = Modifier.backShrink(placeBack?.takeIf { it.active })) { heldPlace[0]?.invoke() }
                } else {
                    page()
                }
            }
        }

        // The parts both layouts share: the time and the last searches, the row that reads like a
        // journey, and the cards with departures, places and the next appointment.
        val timeRow: @Composable () -> Unit = {
            // When to travel, chosen before the search, and next to it the last
            // searches: one tap starts one of them again.
            val recentScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fadeEdges(recentScroll, horizontal = true)
                    .horizontalScroll(recentScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TimeChip(choice = time, onClick = onTime)
                if (showRecent) {
                    recentRoutes.forEachIndexed { index, route ->
                        key(route.id) {
                            // Short on the chip ("Mainstr. 12 → Central"); the whole names for screen readers.
                            ChoiceChip(
                                // Both ends stay visible: each name is cut in its middle where the
                                // whole label is wider than the chip (260 dp less its padding).
                                text = rememberRoutePair(route.from, route.to, 228.dp, 14.sp, FontWeight.SemiBold),
                                description = stringResource(
                                    R.string.route_pair,
                                    route.from.name.shortStopName(),
                                    route.to.name.shortStopName(),
                                ),
                                active = false,
                                onClick = { onOpenRoute(route) },
                                modifier = appearModifier(index),
                            )
                        }
                    }
                }
            }
        }
        val journeyRow: @Composable () -> Unit = {
            // The row reads like a journey: from where, where to, and the swap of the two.
            // The question where to go is the large part. Its place on the screen is
            // reported, so the search can open out of it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onWhereToPlaced(it.positionInRoot().y) },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlassSurface(shape = RoundedCornerShape(SEARCH_FIELD_CORNER, 12.dp, 12.dp, SEARCH_FIELD_CORNER)) {
                    Column(
                        modifier = Modifier
                            .clickable(onClick = onPickOrigin)
                            .height(SEARCH_FIELD_HEIGHT)
                            .widthIn(max = 128.dp)
                            .padding(start = 18.dp, end = 14.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (HereAddress.fromResolving.value) {
                            // The address of "Aktueller Standort" is looked up (#207): the train of the search.
                            TrainLoader(width = 72.dp, description = stringResource(R.string.r5search_contact_resolving))
                        } else if (fromName.isEmpty()) {
                            Text(
                                text = stringResource(R.string.where_from),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.from_short),
                                color = AppColors.Muted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                            Text(
                                text = fromName,
                                color = AppColors.Blue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                GlassSurface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSearch)
                            .height(SEARCH_FIELD_HEIGHT)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlyphIcon(Glyph.Search, color = AppColors.Muted)
                        Text(
                            text = stringResource(R.string.where_to),
                            color = AppColors.Muted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val swapLabel = stringResource(R.string.swap_ends)
                GlassSurface(shape = RoundedCornerShape(12.dp, SEARCH_FIELD_CORNER, SEARCH_FIELD_CORNER, 12.dp)) {
                    Box(
                        modifier = Modifier
                            .semantics { contentDescription = swapLabel }
                            .clickable(onClick = onSwap)
                            .size(width = 52.dp, height = SEARCH_FIELD_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.Swap)
                    }
                }
            }
        }
        val cards: @Composable () -> Unit = {
            // The next departures. The card shows a few and scrolls inside for the rest;
            // a slice of the next row shows that there are more. It has a setting of its own.
            if (showDepartures) GlassSurface(modifier = cardIntro(0).fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
                Column {
                    val listScroll = rememberScrollState()
                    val peek = if (departures.size > departureRows) 26.dp else 0.dp
                    Column(
                        modifier = Modifier
                            // Placeholder rows giving way to the departures change the height softly.
                            .animateContentSize(Motion.settle())
                            .heightIn(max = DEPARTURE_ROW_HEIGHT * departureRows + peek + 14.dp)
                            .fadeEdges(listScroll, horizontal = false)
                            .verticalScroll(listScroll)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        // What the card shows: a note, the shimmering rows, none, or the departures.
                        // A change between them cross-fades; new departures of the same list do not.
                        val departuresShape = when {
                            error != null && departures.isEmpty() -> DEPARTURES_ERROR
                            !departuresLoaded -> DEPARTURES_LOADING
                            departures.isEmpty() -> DEPARTURES_NONE
                            else -> DEPARTURES_LIST
                        }
                        AnimatedContent(
                            targetState = departuresShape,
                            transitionSpec = {
                                fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(140)) using null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = "startDepartures",
                        ) { shape ->
                            when (shape) {
                                DEPARTURES_ERROR -> Text(
                                    text = problemText(error.orEmpty()),
                                    color = AppColors.Muted,
                                    modifier = Modifier.padding(vertical = 14.dp),
                                )

                                // While they load: rows that shimmer where the departures will be.
                                DEPARTURES_LOADING -> DepartureSkeleton(rows = departureRows)

                                DEPARTURES_NONE -> Text(
                                    text = stringResource(R.string.no_departures),
                                    color = AppColors.Muted,
                                    modifier = Modifier.padding(vertical = 14.dp),
                                )

                                else -> Column {
                                    departures.forEachIndexed { index, departure ->
                                        if (index > 0) RowDivider()
                                        DepartureItem(
                                            departure = departure,
                                            now = now,
                                            stopFavorite = stopFavorite,
                                            onAction = { action -> onDepartureAction(departure, action) },
                                            modifier = appearModifier(index).clip(RoundedCornerShape(14.dp)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (departuresLoaded && departures.isNotEmpty()) {
                        RowDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onAllDepartures)
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.departures_all),
                                color = AppColors.Blue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = " \u203A",
                                color = AppColors.Blue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }

            // Saved places and routes as tiles, each with the next journey there.
            PlaceTiles(tiles, modifier = cardIntro(1))

            // The next appointment with a place, and when to leave for it (unless removed).
            if (showAppointment) {
                AppointmentTile(
                    state = appointment,
                    now = now,
                    modifier = cardIntro(2),
                    onEnable = onEnableCalendar,
                    onOpen = onOpenAppointment,
                    onPick = onPickAppointment,
                    onRemove = onRemoveAppointment,
                    onLater = onLaterAppointment,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The gear has its fixed place at the edge; the chips scroll in the room next to it.
            Box(
                modifier = topIntro
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            ) {
                StopChips(
                    stops,
                    selectedStopId,
                    onSelectStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 56.dp),
                )
                RoundButton(
                    glyph = Glyph.Gear,
                    description = stringResource(R.string.settings),
                    onClick = onSettings,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            if (locationNote != null) {
                // Clear of the location button under the gear.
                LocationNote(text = locationNote, modifier = Modifier.padding(start = 12.dp, end = 68.dp))
            }

            if (classic) {
                // The page as it was before the sheet: the lower part stands on the bottom edge and
                // leaves the map free above it. It scrolls from its end, so the question where to go
                // is always the first thing in view.
                val pageScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = if (wide) Alignment.BottomStart else Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = sheetIntro
                            .then(if (wide) Modifier.widthIn(max = SIDE_PANEL_MAX) else Modifier)
                            .fillMaxWidth()
                            // Where the cards begin is where the map is covered.
                            .onGloballyPositioned { onCoverTop(it.boundsInRoot().top) }
                            .fadeEdges(
                                horizontal = false,
                                cutAtStart = { pageScroll.value < pageScroll.maxValue },
                                cutAtEnd = { pageScroll.value > 0 },
                            )
                            .verticalScroll(pageScroll, reverseScrolling = true)
                            // Room inside the scrolling area, so the shadows of the cards are not cut off.
                            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // A followed trip comes first: its next step, and a tap leads back to the companion.
                        if (trip != null) {
                            TripBanner(
                                step = tripStep(trip, now),
                                now = now,
                                caption = stringResource(R.string.trip_caption, trip.legs.last().to.plainStopName()),
                                onClick = onOpenTrip,
                                journey = trip,
                                actions = tripActions,
                            )
                        }
                        // Trips planned ahead (see PlannedTrips): compact, they come and go softly.
                        PlannedTripCards()
                        swapped {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (cardsShown) cards()
                                timeRow()
                                journeyRow()
                            }
                        }
                    }
                }
            } else {
                StageSheet(
                    // On a wide screen a panel of the full height; the stage the user chose is kept for later.
                    stage = if (wide) SheetStage.Full else stage,
                    onStage = { if (!wide) stage = it },
                    onRecentre = null,
                    onSheetTop = if (wide) null else onCoverTop,
                    modifier = sheetIntro
                        .weight(1f)
                        .then(if (wide) Modifier.widthIn(max = SIDE_PANEL_MAX) else Modifier)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    // A followed trip in one line at the top: a tap leads back to the companion.
                    header = if (trip == null) {
                        null
                    } else {
                        {
                            TripBand(
                                step = tripStep(trip, now),
                                now = now,
                                onClick = onOpenTrip,
                                actions = tripActions,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                            )
                        }
                    },
                    // While a place is shown it has the whole sheet; its own buttons lead on.
                    footer = if (placeShown) {
                        null
                    } else {
                        {
                            Column(
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                timeRow()
                                journeyRow()
                            }
                        }
                    },
                ) {
                    val pageScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fadeEdges(pageScroll, horizontal = false)
                            .verticalScroll(pageScroll)
                            // Room inside the scrolling area, so the shadows of the cards are not cut off.
                            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Trips planned ahead (see PlannedTrips) at the top of the sheet.
                        PlannedTripCards()
                        swapped {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (cardsShown) cards()
                            }
                        }
                    }
                }
            }
            // Clear of the tab bar. The classic page already ends in 10 dp of room for the shadows.
            Spacer(
                modifier = Modifier
                    .height(LocalNavInset.current - if (classic) 10.dp else 0.dp)
                    // Beside the panel of a wide screen only the tab bar covers the map.
                    .then(
                        if (wide && !classic) {
                            Modifier.onGloballyPositioned { onCoverTop(it.boundsInRoot().top) }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

// Sizes of the tab bar, as designed.
private val NAV_PAD = 8.dp
private val NAV_GAP = 4.dp
private val NAV_TAB_HEIGHT = 48.dp
private val NAV_TAB_REST = 56.dp
private val NAV_ICON = 22.dp
private val NAV_SIDE = 18.dp
private val NAV_ICON_GAP = 8.dp

/**
 * Where the parts of the tab bar lie for one position of the pill, in pixels: the left
 * edge and the width of every tab, how open each tab is (0 icon only, 1 with its name),
 * the pill and the width of the whole bar.
 */
private class NavGeometry(
    val lefts: FloatArray,
    val widths: FloatArray,
    val open: FloatArray,
    val pillLeft: Float,
    val pillWidth: Float,
    val total: Float,
)

/** The pixel sizes the geometry of the tab bar is worked out from. */
private class NavSizes(val pad: Float, val gap: Float, val rest: Float, val icon: Float, val side: Float, val lead: Float)

/**
 * The tab bar with the pill at [position]: 0 is the first tab, 1.5 half way from the
 * second to the third. A tab opens up for its name as the pill arrives and closes behind it.
 */
private fun navGeometry(position: Float, labelWidths: FloatArray, sizes: NavSizes): NavGeometry {
    val count = labelWidths.size
    val at = position.coerceIn(0f, (count - 1).toFloat())
    val lefts = FloatArray(count)
    val widths = FloatArray(count)
    val open = FloatArray(count)
    var x = sizes.pad
    for (index in 0 until count) {
        val share = (1f - abs(at - index)).coerceIn(0f, 1f)
        val full = maxOf(sizes.lead + labelWidths[index] + sizes.side, sizes.rest)
        open[index] = share
        widths[index] = sizes.rest + (full - sizes.rest) * share
        lefts[index] = x
        x += widths[index] + sizes.gap
    }
    val first = at.toInt().coerceAtMost(count - 1)
    val second = (first + 1).coerceAtMost(count - 1)
    val part = at - first
    return NavGeometry(
        lefts = lefts,
        widths = widths,
        open = open,
        pillLeft = lefts[first] + part * (widths[first] + sizes.gap),
        pillWidth = widths[first] + (widths[second] - widths[first]) * part,
        total = x - sizes.gap + sizes.pad,
    )
}

/** The position of the pill whose middle lies under the finger at [x]. */
private fun navPositionAt(x: Float, labelWidths: FloatArray, sizes: NavSizes): Float {
    var low = 0f
    var high = (labelWidths.size - 1).toFloat()
    // The middle of the pill only moves forward with its position: halving the range finds it.
    repeat(14) {
        val middle = (low + high) / 2f
        val geometry = navGeometry(middle, labelWidths, sizes)
        if (geometry.pillLeft + geometry.pillWidth / 2f < x) low = middle else high = middle
    }
    return (low + high) / 2f
}

/**
 * The floating tab bar. The open tab shows its icon and its name side by side on a wide
 * blue pill; the others show their icon only. A tap opens a tab, and the pill slides
 * there while it changes its width. Holding the bar, or dragging along it, takes the pill
 * under the finger; the tab it is let go on opens.
 */
@Composable
private fun FloatingNav(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = Tab.entries
    val labels = listOf(
        stringResource(R.string.tab_route),
        stringResource(R.string.tab_departures),
        stringResource(R.string.tab_map),
        stringResource(R.string.tab_favorites),
    )
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val sizes = with(LocalDensity.current) {
        NavSizes(
            pad = NAV_PAD.toPx(),
            gap = NAV_GAP.toPx(),
            rest = NAV_TAB_REST.toPx(),
            icon = NAV_ICON.toPx(),
            side = NAV_SIDE.toPx(),
            lead = (NAV_SIDE + NAV_ICON + NAV_ICON_GAP).toPx(),
        )
    }
    val tabHeight = with(LocalDensity.current) { NAV_TAB_HEIGHT.toPx() }
    val position = remember { Animatable(tabs.indexOf(current).toFloat()) }
    // The widths of the names in pixels, measured with the bar; the pill and the touches need them.
    val labelWidths = remember { FloatArray(tabs.size) }
    val currentTab by rememberUpdatedState(current)
    val select by rememberUpdatedState(onSelect)
    val settle = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

    // Another tab was opened: the pill travels there with a spring and changes its width on the way.
    LaunchedEffect(current) {
        position.animateTo(tabs.indexOf(current).toFloat(), settle)
    }

    GlassSurface(modifier = modifier, shape = RoundedCornerShape(32.dp)) {
        Layout(
            content = {
                tabs.forEachIndexed { index, item ->
                    val label = labels[index]
                    NavIcon(
                        tab = item,
                        // The ink turns white as the pill comes under the icon.
                        color = {
                            val share = (1f - abs(position.value.coerceIn(0f, tabs.lastIndex.toFloat()) - index))
                            lerp(AppColors.Ink, AppColors.OnBlue, share.coerceIn(0f, 1f))
                        },
                        // Touches are read by the bar as a whole; screen readers get each tab as a button of its own.
                        modifier = Modifier.semantics {
                            contentDescription = label
                            role = Role.Tab
                            selected = item == current
                            onClick {
                                select(item)
                                true
                            }
                        },
                    )
                }
                labels.forEach { label ->
                    Text(
                        text = label,
                        color = AppColors.OnBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        // The icon already says the name of its tab.
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            },
            modifier = Modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val slop = viewConfiguration.touchSlop
                        val holdMillis = viewConfiguration.longPressTimeoutMillis
                        var dragging = false
                        var lifted = false
                        var x = down.position.x
                        var uptime = down.uptimeMillis
                        var over = position.value.roundToInt()
                        // Takes the pill to the finger, with a short tick whenever it reaches another tab.
                        fun follow() {
                            val target = navPositionAt(x, labelWidths, sizes)
                            if (target.roundToInt() != over) {
                                over = target.roundToInt()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            scope.launch {
                                position.animateTo(target, spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh))
                            }
                        }
                        while (true) {
                            val event = if (dragging) {
                                awaitPointerEvent()
                            } else {
                                // Held still for long enough, the pill comes under the finger without a drag.
                                val left = (holdMillis - (uptime - down.uptimeMillis)).coerceAtLeast(1L)
                                withTimeoutOrNull(left) { awaitPointerEvent() }
                            }
                            if (event == null) {
                                dragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                follow()
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            x = change.position.x
                            uptime = change.uptimeMillis
                            if (!change.pressed) {
                                lifted = true
                                break
                            }
                            if (!dragging && abs(x - down.position.x) > slop) dragging = true
                            if (dragging) {
                                change.consume()
                                follow()
                            }
                        }
                        if (dragging && lifted) {
                            // Let go: the tab under the finger opens, and the pill settles on it.
                            val index = navPositionAt(x, labelWidths, sizes).roundToInt().coerceIn(0, tabs.lastIndex)
                            scope.launch { position.animateTo(index.toFloat(), settle) }
                            if (tabs[index] != currentTab) select(tabs[index])
                        } else if (dragging) {
                            // The touch was taken away: the pill goes back to the open tab.
                            scope.launch { position.animateTo(tabs.indexOf(currentTab).toFloat(), settle) }
                        } else if (lifted) {
                            // A plain tap opens the tab it hit.
                            val geometry = navGeometry(position.value, labelWidths, sizes)
                            val index = tabs.indices.firstOrNull { tab ->
                                x < geometry.lefts[tab] + geometry.widths[tab] + sizes.gap / 2f
                            } ?: tabs.lastIndex
                            // A light tick for another tab (silent with the setting off, see HapticSetting).
                            if (tabs[index] != currentTab) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            select(tabs[index])
                        }
                    }
                }
                .drawBehind {
                    val geometry = navGeometry(position.value, labelWidths, sizes)
                    val at = position.value.coerceIn(0f, tabs.lastIndex.toFloat())
                    // On its way the pill gets a little flatter, like something soft that is pulled along.
                    val pillHeight = tabHeight * (1f - 0.12f * sin((at - floor(at)) * PI).toFloat())
                    drawRoundRect(
                        color = AppColors.Blue,
                        topLeft = Offset(geometry.pillLeft, (size.height - pillHeight) / 2f),
                        size = Size(geometry.pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillHeight / 2f),
                    )
                },
        ) { measurables, constraints ->
            val count = tabs.size
            val icons = measurables.take(count).map { it.measure(Constraints()) }
            for (index in 0 until count) {
                labelWidths[index] = measurables[count + index].maxIntrinsicWidth(Constraints.Infinity).toFloat()
            }
            // Read here, so the bar is laid out again whenever the pill moves.
            val geometry = navGeometry(position.value, labelWidths, sizes)
            // A name gets the room its tab has opened so far; the rest of it is cut off.
            val names = (0 until count).map { index ->
                val room = (geometry.widths[index] - sizes.lead - sizes.side / 2f).roundToInt().coerceAtLeast(0)
                measurables[count + index].measure(Constraints(maxWidth = room))
            }
            val height = (tabHeight + sizes.pad * 2f).roundToInt()
            val width = geometry.total.roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
            layout(width, height) {
                for (index in 0 until count) {
                    val open = geometry.open[index]
                    // The icon sits in the middle of a closed tab and at the left of an open one.
                    val closedInset = (sizes.rest - sizes.icon) / 2f
                    val iconX = geometry.lefts[index] + closedInset + (sizes.side - closedInset) * open
                    icons[index].place(iconX.roundToInt(), (height - icons[index].height) / 2)
                    val name = names[index]
                    if (open > 0.3f && name.width > 0) {
                        val nameAlpha = ((open - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        name.placeWithLayer(
                            x = (geometry.lefts[index] + sizes.lead).roundToInt(),
                            y = (height - name.height) / 2,
                        ) { alpha = nameAlpha }
                    }
                }
            }
        }
    }
}

/** The icons of the tabs as designed, drawn here so no icon library is needed. [color] is read while drawing. */
@Composable
private fun NavIcon(tab: Tab, color: () -> Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(NAV_ICON)) {
        val ink = color()
        val w = size.width
        // The icons are drawn on a grid of 24 units.
        val u = w / 24f
        val line = w * 0.09f
        val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            // From one stop along a winding way to another.
            Tab.Route -> {
                drawCircle(ink, radius = u * 2.5f, center = Offset(u * 6f, u * 18f), style = stroke)
                drawCircle(ink, radius = u * 2.5f, center = Offset(u * 18f, u * 6f), style = stroke)
                val way = Path().apply {
                    moveTo(u * 15.5f, u * 6f)
                    lineTo(u * 10f, u * 6f)
                    arcTo(Rect(u * 7f, u * 6f, u * 13f, u * 12f), 270f, -180f, false)
                    lineTo(u * 14f, u * 12f)
                    arcTo(Rect(u * 11f, u * 12f, u * 17f, u * 18f), 270f, 180f, false)
                    lineTo(u * 8.5f, u * 18f)
                }
                drawPath(way, ink, style = stroke)
            }

            // A clock.
            Tab.Departures -> {
                drawCircle(ink, radius = u * 9f, center = center, style = stroke)
                val hands = Path().apply {
                    moveTo(u * 12f, u * 7f)
                    lineTo(u * 12f, u * 12f)
                    lineTo(u * 15f, u * 14f)
                }
                drawPath(hands, ink, style = stroke)
            }

            // A pin on the map.
            Tab.Map -> {
                val pin = Path().apply {
                    moveTo(u * 12f, u * 21f)
                    cubicTo(u * 12f, u * 21f, u * 19f, u * 14.8f, u * 19f, u * 10f)
                    arcTo(Rect(u * 5f, u * 3f, u * 19f, u * 17f), 0f, -180f, false)
                    cubicTo(u * 5f, u * 14.8f, u * 12f, u * 21f, u * 12f, u * 21f)
                    close()
                }
                drawPath(pin, ink, style = stroke)
                drawCircle(ink, radius = u * 2.5f, center = Offset(u * 12f, u * 10f), style = stroke)
            }

            // A five-pointed star.
            Tab.Favorites -> {
                val star = Path()
                for (point in 0 until 10) {
                    val radius = if (point % 2 == 0) w * 0.42f else w * 0.19f
                    val angle = -PI / 2 + point * PI / 5
                    val x = w * 0.5f + radius * cos(angle).toFloat()
                    val y = w * 0.52f + radius * sin(angle).toFloat()
                    if (point == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, ink, style = stroke)
            }
        }
    }
}

// How far above its top edge a page put away for a walk may draw (shadows, glass rims), moved out with it (#194).
private val PAGE_AWAY_SHADOW = 32.dp
