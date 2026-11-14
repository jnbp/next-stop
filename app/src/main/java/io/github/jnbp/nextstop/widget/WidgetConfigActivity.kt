package io.github.jnbp.nextstop.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.ui.ChoiceChip
import io.github.jnbp.nextstop.ui.LineBadge
import io.github.jnbp.nextstop.ui.PrimaryButton
import io.github.jnbp.nextstop.ui.SegmentedToggle
import io.github.jnbp.nextstop.ui.placeIconLabel
import io.github.jnbp.nextstop.ui.productLabel
import io.github.jnbp.nextstop.ui.savedPlaceName
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.ui.theme.Appearance
import io.github.jnbp.nextstop.ui.theme.NextStopTheme

// A search waits this long after the last key.
private const val SEARCH_DELAY_MILLIS = 350L

/**
 * Sets up one widget: the stop, the line or the products for the departures and the mini
 * widget, the favourite place for the favourite and trip widgets, and the colours of each.
 * The widgets work without it too ("configuration_optional"): the nearest stop and the first
 * favourite place. A long press on a placed widget opens it again.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Leaving without "Done" keeps a new widget off the home screen, as Android expects.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val kind = if (invalidWidgetId(appWidgetId)) null else WidgetUpdates.kindOf(this, appWidgetId)
        val glanceId = if (invalidWidgetId(appWidgetId)) null else glanceIdOf(this, appWidgetId)
        if (kind == null || glanceId == null) {
            finish()
            return
        }
        Appearance.load(this)
        enableEdgeToEdge()
        setContent {
            NextStopTheme {
                val scope = rememberCoroutineScope()
                var config by remember { mutableStateOf<WidgetConfig?>(null) }
                LaunchedEffect(Unit) {
                    val state: Preferences = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                    config = readConfig(state)
                }
                val loaded = config
                if (loaded != null) {
                    WidgetSettings(
                        kind = kind,
                        config = loaded,
                        onChange = { config = it },
                        onDone = {
                            scope.launch {
                                updateAppWidgetState(this@WidgetConfigActivity, glanceId) { writeConfig(it, loaded) }
                                WidgetUpdates.refreshWidget(applicationContext, kind, glanceId)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetSettings(kind: WidgetKind, config: WidgetConfig, onChange: (WidgetConfig) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val source = remember {
        RoutingSource(choice = { prefs.sourceChoice }, userAgent = TransitousSource.userAgent(context), bvgUrl = { ownServerUrl(prefs.ownServer) })
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.widget_config_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
        Text(stringResource(widgetName(kind)), fontSize = 14.sp, color = AppColors.Muted)
        when (kind) {
            WidgetKind.Departures, WidgetKind.Mini -> {
                StopSection(config, source, prefs, onChange)
                LineSection(kind, config, source, onChange)
                if (kind == WidgetKind.Departures) ProductSection(config, onChange)
            }
            WidgetKind.Favorite, WidgetKind.Trip -> FavoriteSection(config, prefs, onChange)
            WidgetKind.Appointment, WidgetKind.Auto -> Section(stringResource(R.string.widget_config_content)) {
                Text(stringResource(R.string.widget_config_automatic), fontSize = 13.sp, color = AppColors.Muted)
            }
        }
        ColorSection(config, prefs, onChange)
        PrimaryButton(text = stringResource(R.string.widget_config_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

internal fun widgetName(kind: WidgetKind): Int = when (kind) {
    WidgetKind.Departures -> R.string.widget_departures_name
    WidgetKind.Favorite -> R.string.widget_favorite_name
    WidgetKind.Trip -> R.string.widget_trip_name
    WidgetKind.Mini -> R.string.widget_mini_name
    WidgetKind.Appointment -> R.string.widget_appointment_name
    WidgetKind.Auto -> R.string.widget_auto_name
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.Ink)
        content()
    }
}

/** A choice in a list: a row with a dot that is filled when picked. */
@Composable
private fun PickRow(text: String, picked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (picked) AppColors.BlueContainer else AppColors.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (picked) AppColors.OnBlueContainer else AppColors.Ink,
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/** The stop: the nearest one, a saved stop, or one found by its name. */
@Composable
private fun StopSection(config: WidgetConfig, source: RoutingSource, prefs: AppPrefs, onChange: (WidgetConfig) -> Unit) {
    val saved = remember {
        (
            prefs.loadPlaces(AppPrefs.KEY_FAVORITES) + prefs.loadFavoritePlaces().mapNotNull { it.place } +
                prefs.loadSavedPlaces().map { it.place }
            )
            .filter { it.kind == PlaceKind.Stop }
            .distinctBy { it.id }
    }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Stop>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            found = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DELAY_MILLIS)
        found = try {
            source.searchStops(query.trim(), 6).filter { it.kind == PlaceKind.Stop }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    Section(stringResource(R.string.widget_config_stop)) {
        PickRow(stringResource(R.string.widget_nearest_stop), config.stop == null) {
            onChange(config.copy(stop = null, line = null))
        }
        val chosen = config.stop
        if (chosen != null && saved.none { it.id == chosen.id }) {
            PickRow(chosen.name.plainStopName(), true) {}
        }
        saved.forEach { stop ->
            PickRow(stop.name.plainStopName(), config.stop?.id == stop.id) {
                onChange(config.copy(stop = stop, line = null))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.widget_config_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        found.forEach { stop ->
            PickRow(stop.name.plainStopName(), config.stop?.id == stop.id) {
                onChange(config.copy(stop = stop.copy(distanceMeters = null), line = null))
                query = ""
            }
        }
    }
}

/**
 * The line: the lines that leave the chosen stop soon, as badges. The mini widget shows one
 * line; the departures widget all of them unless one is picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineSection(kind: WidgetKind, config: WidgetConfig, source: RoutingSource, onChange: (WidgetConfig) -> Unit) {
    val stop = config.stop
    var lines by remember(stop?.id) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(stop?.id) {
        if (stop == null) return@LaunchedEffect
        lines = try {
            source.departures(stop.id, results = 40)
                .map { it.line to it.product }
                .distinctBy { it.first }
                .sortedWith(compareBy({ PRODUCT_KEYS.indexOf(it.second) }, { it.first }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    Section(stringResource(R.string.widget_config_line)) {
        if (stop == null) {
            Text(stringResource(R.string.widget_config_line_nearest), fontSize = 13.sp, color = AppColors.Muted)
            return@Section
        }
        PickRow(stringResource(R.string.widget_config_all_lines), config.line == null) { onChange(config.copy(line = null)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { (line, product) ->
                val picked = config.line == line
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (picked) AppColors.BlueContainer else AppColors.Chip)
                        .clickable { onChange(config.copy(line = if (picked) null else line)) }
                        .padding(6.dp),
                ) {
                    LineBadge(line, product)
                }
            }
        }
        if (kind == WidgetKind.Mini && config.line == null) {
            Text(stringResource(R.string.widget_config_mini_hint), fontSize = 12.sp, color = AppColors.Muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductSection(config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    Section(stringResource(R.string.widget_config_products)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PRODUCT_KEYS.forEach { product ->
                val on = product in config.products
                ChoiceChip(
                    text = stringResource(productLabel(product)),
                    active = on,
                    onClick = {
                        val changed = if (on) config.products - product else config.products + product
                        // At least one product stays: none would show nothing at all.
                        if (changed.isNotEmpty()) onChange(config.copy(products = changed))
                    },
                    minHeight = 40.dp,
                )
            }
        }
    }
}

@Composable
private fun FavoriteSection(config: WidgetConfig, prefs: AppPrefs, onChange: (WidgetConfig) -> Unit) {
    val places = remember { prefs.loadFavoritePlaces() }
    val savedPlaces = remember { prefs.loadSavedPlaces() }
    val patterns = remember { prefs.loadPatterns() }
    val firstWithPlace = places.indexOfFirst { it.place != null }
    // A remembered connection that was forgotten in the meantime counts as not picked; one that is picked comes first.
    val patternPick = config.pattern?.takeIf { id -> patterns.any { it.id == id } }
    // A saved place that was removed in the meantime counts as not picked.
    val savedPick = config.saved?.takeIf { id -> patternPick == null && savedPlaces.any { it.id == id } }
    Section(stringResource(R.string.widget_config_favorite)) {
        if (firstWithPlace < 0 && savedPlaces.isEmpty() && patterns.isEmpty()) {
            Text(stringResource(R.string.widget_no_favorite), fontSize = 13.sp, color = AppColors.Muted)
            return@Section
        }
        places.forEachIndexed { index, favorite ->
            if (favorite.place == null) return@forEachIndexed
            val picked = patternPick == null && savedPick == null && (config.favorite ?: firstWithPlace) == index
            val name = favorite.name.ifBlank { placeIconLabel(favorite.icon) }
            PickRow(name, picked) { onChange(config.copy(favorite = index, saved = null, pattern = null)) }
        }
        if (savedPlaces.isNotEmpty()) {
            Text(
                stringResource(R.string.widget_config_saved),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = AppColors.Ink,
                modifier = Modifier.padding(top = 4.dp),
            )
            savedPlaces.forEach { saved ->
                val picked = savedPick == saved.id ||
                    (patternPick == null && savedPick == null && firstWithPlace < 0 && saved == savedPlaces.first())
                PickRow(savedPlaceName(saved), picked) { onChange(config.copy(saved = saved.id, favorite = null, pattern = null)) }
            }
        }
        if (patterns.isNotEmpty()) {
            Text(
                stringResource(R.string.saved_patterns),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = AppColors.Ink,
                modifier = Modifier.padding(top = 4.dp),
            )
            patterns.forEach { pattern ->
                PickRow(pattern.label, patternPick == pattern.id) {
                    onChange(config.copy(pattern = pattern.id, saved = null, favorite = null))
                }
            }
        }
    }
}

/** The colours of this widget, and the common colours of all widgets that follow them. */
@Composable
private fun ColorSection(config: WidgetConfig, prefs: AppPrefs, onChange: (WidgetConfig) -> Unit) {
    val context = LocalContext.current
    var global by remember { mutableStateOf(prefs.widgetColors) }
    Section(stringResource(R.string.widget_config_colors)) {
        SegmentedToggle(
            labels = listOf(
                stringResource(R.string.widget_colors_global),
                stringResource(R.string.widget_colors_app),
                stringResource(R.string.widget_colors_material),
            ),
            selected = WidgetScheme.entries.indexOf(config.scheme),
            onSelect = { onChange(config.copy(scheme = WidgetScheme.entries[it])) },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
        SegmentedToggle(
            labels = listOf(
                stringResource(R.string.dark_system),
                stringResource(R.string.dark_light),
                stringResource(R.string.dark_dark),
            ),
            selected = WidgetShade.entries.indexOf(config.shade),
            onSelect = { onChange(config.copy(shade = WidgetShade.entries[it])) },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
        Text(stringResource(R.string.widget_colors_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.Ink)
        Text(stringResource(R.string.widget_colors_text), fontSize = 12.sp, color = AppColors.Muted)
        SegmentedToggle(
            labels = listOf(stringResource(R.string.widget_colors_app), stringResource(R.string.widget_colors_material)),
            selected = WidgetColorChoice.entries.indexOf(global),
            onSelect = { index ->
                global = WidgetColorChoice.entries[index]
                prefs.widgetColors = global
                WidgetUpdates.redrawAll(context)
            },
            minHeight = 40.dp,
            ground = AppColors.Chip,
        )
    }
}
