package io.github.jnbp.nextstop.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.BuildConfig
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.BUNDLED_FONTS
import io.github.jnbp.nextstop.data.BvgRestSource
import io.github.jnbp.nextstop.data.PhotonSearch
import io.github.jnbp.nextstop.data.ProjectLinks
import io.github.jnbp.nextstop.data.PteSource
import io.github.jnbp.nextstop.data.THIRD_PARTY_LIBRARIES
import io.github.jnbp.nextstop.data.ThirdPartyNotice
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.appLanguage
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * "About Next Stop", the last category of the settings: the app's name, version and promise
 * (free, open source, no ads, no tracking), the links to its code, website, privacy policy and
 * issues, and three sheets: the license in plain words, the open-source libraries and fonts
 * (from data/ThirdPartyNotices.kt), and every data source with its attribution. The data
 * sources sheet is also what the small "i" on the map opens ([MapInfoButton]), so the
 * OpenStreetMap attribution is one tap away wherever the map is. Links open in the browser;
 * without one nothing happens and the text stays readable.
 */

/** Which sheet of "About" is open; kept across a rotation. */
internal enum class AboutSheet { None, License, Libraries, Sources }

/** The rows of "About Next Stop" and the sheets they open. */
@Composable
internal fun AboutSettings() {
    val context = LocalContext.current
    val language = remember(context) { appLanguage(context) }
    var sheet by rememberSaveable { mutableStateOf(AboutSheet.None) }
    val last = 7
    SettingsGroup(label = stringResource(R.string.about_title)) {
        AboutHeader(shape = groupShape(0, last))
        AboutRow(
            shape = groupShape(1, last),
            title = stringResource(R.string.about_source),
            text = shownAddress(ProjectLinks.SOURCE_CODE),
            external = true,
        ) { openInBrowser(context, ProjectLinks.SOURCE_CODE) }
        val website = ProjectLinks.website(language)
        AboutRow(
            shape = groupShape(2, last),
            title = stringResource(R.string.source_link_website),
            text = shownAddress(website),
            external = true,
        ) { openInBrowser(context, website) }
        val privacy = ProjectLinks.privacyPolicy(language)
        AboutRow(
            shape = groupShape(3, last),
            title = stringResource(R.string.about_privacy),
            text = shownAddress(privacy),
            external = true,
        ) { openInBrowser(context, privacy) }
        AboutRow(
            shape = groupShape(4, last),
            title = stringResource(R.string.about_report),
            text = shownAddress(ProjectLinks.ISSUES),
            external = true,
        ) { openInBrowser(context, ProjectLinks.ISSUES) }
        AboutRow(
            shape = groupShape(5, last),
            title = stringResource(R.string.about_license),
            text = stringResource(R.string.about_license_text, ProjectLinks.LICENSE_ID),
            external = false,
        ) { sheet = AboutSheet.License }
        AboutRow(
            shape = groupShape(6, last),
            title = stringResource(R.string.about_libraries),
            text = stringResource(R.string.about_libraries_text),
            external = false,
        ) { sheet = AboutSheet.Libraries }
        AboutRow(
            shape = groupShape(7, last),
            title = stringResource(R.string.about_sources),
            text = stringResource(R.string.about_sources_text),
            external = false,
        ) { sheet = AboutSheet.Sources }
    }
    val close = { sheet = AboutSheet.None }
    when (sheet) {
        AboutSheet.None -> Unit
        AboutSheet.License -> LicenseSheet(onDismiss = close)
        AboutSheet.Libraries -> LibrariesSheet(onDismiss = close)
        AboutSheet.Sources -> DataSourcesSheet(onDismiss = close)
    }
}

/** The app icon, its name and version (name and code, as in the store), and its promise. */
@Composable
private fun AboutHeader(shape: Shape) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ABOUT_ICON_SIZE)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                color = AppColors.Muted,
                fontSize = 13.sp,
            )
            Text(text = stringResource(R.string.about_tagline), fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

// The size of the app icon in the header of "About".
private val ABOUT_ICON_SIZE = 52.dp

/**
 * A row of "About" and its sheets: a [title], one line below it, and at its end "↗" for a link
 * that leaves the app ([external]) or "›" for a sheet.
 */
@Composable
private fun AboutRow(shape: Shape, title: String, text: String, external: Boolean, onClick: () -> Unit) {
    val leaves = stringResource(R.string.about_open_link)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(onClickLabel = if (external) leaves else null, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingText(title = title, text = text, modifier = Modifier.weight(1f))
        Text(
            text = if (external) "↗" else "›",
            color = AppColors.Muted,
            fontSize = if (external) 18.sp else 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** An address as a row shows it: without "https://" and the closing slash. */
internal fun shownAddress(url: String): String = url.removePrefix("https://").removePrefix("http://").trimEnd('/')

/** Opens [url] in the browser; without a browser nothing happens. */
internal fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser: the address stays readable in the row.
    }
}

/**
 * A sheet of "About" with a [title] and its [content] scrolling below it, as the other sheets
 * of the app (see HiddenNoticesSheet): it slides up and is pulled down or tapped away to close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheetFrame(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                content()
            }
        }
    }
}

/** A line of explaining text in a sheet. */
@Composable
private fun SheetText(text: String) {
    Text(
        text = text,
        color = AppColors.Muted,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
    )
}

/** The license in plain words, and the links to its full text and to the app store permission. */
@Composable
private fun LicenseSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AboutSheetFrame(title = stringResource(R.string.license_sheet_title), onDismiss = onDismiss) {
        SheetText(stringResource(R.string.license_summary_use))
        SheetText(stringResource(R.string.license_summary_share))
        SheetText(stringResource(R.string.license_summary_stores))
        SheetText(stringResource(R.string.license_summary_warranty))
        AboutRow(
            shape = groupShape(0, 1),
            title = stringResource(R.string.license_full_text),
            text = ProjectLinks.LICENSE_ID,
            external = true,
        ) { openInBrowser(context, ProjectLinks.LICENSE_TEXT) }
        AboutRow(
            shape = groupShape(1, 1),
            title = stringResource(R.string.license_store_permission),
            text = shownAddress(ProjectLinks.APP_STORE_PERMISSION).substringAfterLast('/'),
            external = true,
        ) { openInBrowser(context, ProjectLinks.APP_STORE_PERMISSION) }
    }
}

/** The libraries and fonts of the apps, each with its maker and license; a tap opens its page. */
@Composable
private fun LibrariesSheet(onDismiss: () -> Unit) {
    AboutSheetFrame(title = stringResource(R.string.about_libraries), onDismiss = onDismiss) {
        SheetText(stringResource(R.string.libraries_intro))
        SectionLabel(stringResource(R.string.libraries_group), modifier = Modifier.padding(bottom = 4.dp))
        NoticeRows(THIRD_PARTY_LIBRARIES)
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel(stringResource(R.string.libraries_fonts), modifier = Modifier.padding(bottom = 4.dp))
        NoticeRows(BUNDLED_FONTS)
    }
}

/** [notices] as one group of rows: name, then maker and license. */
@Composable
private fun NoticeRows(notices: List<ThirdPartyNotice>) {
    val context = LocalContext.current
    notices.forEachIndexed { index, notice ->
        AboutRow(
            shape = groupShape(index, notices.lastIndex),
            title = notice.name,
            text = notice.maintainer + " · " + notice.license,
            external = true,
        ) { openInBrowser(context, notice.url) }
    }
}

/** A data source as the sheet lists it: its [name], what it is [used] for, its [credit] and its page. */
private class SourceCredit(val name: String, val used: String, val credit: String, val url: String)

// Pages of data sources that no other part of the app names.
private const val OSM_COPYRIGHT_PAGE = "https://www.openstreetmap.org/copyright"
private const val OPENFREEMAP_PAGE = "https://openfreemap.org"
private const val VBB_API_PAGE = "https://unternehmen.vbb.de/en/digital-services/"
private const val PTE_PAGE = "https://github.com/schildbach/public-transport-enabler"
private const val OSRM_PAGE = "https://routing.openstreetmap.de"
private const val CHANGE_POSITIONS_PAGE = "https://github.com/derhuerst/vbb-change-positions"
private const val ACCESSIBILITY_CLOUD_PAGE = "https://accessibility.cloud"
private const val FASTA_PAGE = "https://developers.deutschebahn.com/db-api-marketplace/apis/product/fasta"

// The Open Database License of OpenStreetMap and of the change positions.
private const val ODBL = "ODbL 1.0"

/**
 * Every source of data the app uses, with the attribution it asks for: OpenStreetMap (map,
 * stations, entrances, change positions; ODbL), the map tiles of OpenFreeMap (OpenMapTiles
 * schema), the timetable sources of the settings, Photon and the OSRM foot and bike server
 * (both on OpenStreetMap), the change positions of vbb-change-positions and the lift data.
 * public-transport-enabler only where the build includes it.
 */
@Composable
private fun DataSourcesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val osm = stringResource(R.string.osm_attribution)
    val terms = stringResource(R.string.sources_provider_terms)
    val sources = buildList<SourceCredit> {
        add(SourceCredit("OpenStreetMap", stringResource(R.string.sources_osm_use), "$osm · $ODBL", OSM_COPYRIGHT_PAGE))
        add(
            SourceCredit(
                "OpenFreeMap",
                stringResource(R.string.sources_tiles_use),
                "OpenFreeMap · © OpenMapTiles · $osm",
                OPENFREEMAP_PAGE,
            ),
        )
        add(
            SourceCredit(
                "Transitous",
                stringResource(R.string.sources_transitous_use),
                stringResource(R.string.sources_transitous_terms),
                TransitousSource.SOURCES_URL,
            ),
        )
        add(SourceCredit("VBB", stringResource(R.string.sources_vbb_use), "VBB Verkehrsverbund Berlin-Brandenburg · $terms", VBB_API_PAGE))
        add(
            SourceCredit(
                "BVG (transport.rest)",
                stringResource(R.string.sources_bvg_use),
                "BVG · " + shownAddress(BvgRestSource.DEFAULT_URL) + " · $terms",
                BvgRestSource.DEFAULT_URL,
            ),
        )
        if (PteSource.INCLUDED) {
            add(SourceCredit("public-transport-enabler", stringResource(R.string.sources_pte_use), "BVG · $terms", PTE_PAGE))
        }
        add(SourceCredit("Photon", stringResource(R.string.sources_photon_use), "komoot · $osm", PhotonSearch.DEFAULT_URL))
        add(SourceCredit("OSRM", stringResource(R.string.sources_routing_use), "FOSSGIS e.V. · $osm", OSRM_PAGE))
        add(
            SourceCredit(
                "vbb-change-positions",
                stringResource(R.string.sources_change_use),
                "derhuerst · $ODBL",
                CHANGE_POSITIONS_PAGE,
            ),
        )
        add(
            SourceCredit(
                "accessibility.cloud",
                stringResource(R.string.sources_lifts_use),
                "Sozialhelden e.V. · brokenlifts.org · $terms",
                ACCESSIBILITY_CLOUD_PAGE,
            ),
        )
        add(SourceCredit("DB FaSta", stringResource(R.string.sources_fasta_use), "Deutsche Bahn · CC BY 4.0", FASTA_PAGE))
    }
    AboutSheetFrame(title = stringResource(R.string.about_sources), onDismiss = onDismiss) {
        SheetText(stringResource(R.string.sources_intro))
        sources.forEachIndexed { index, source ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(groupShape(index, sources.lastIndex))
                    .background(AppColors.Card)
                    .clickable(onClickLabel = stringResource(R.string.about_open_link), role = Role.Button) {
                        openInBrowser(context, source.url)
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = source.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(text = source.used, fontSize = 13.sp, lineHeight = 17.sp)
                Text(text = source.credit, color = AppColors.Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/**
 * The small "i" at the edge of the map: the map's own attribution of MapLibre sits in a corner
 * the sheets cover, so this one stands under the "my location" button and opens the sheet of
 * the data sources, with OpenStreetMap and the map tiles first.
 */
@Composable
internal fun MapInfoButton(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.map_info_button)
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .semantics { contentDescription = label }
            .springPress(interactions)
            .clip(CircleShape)
            .then(glassGround())
            .border(1.dp, glassRim(), CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = { open = true },
            )
            .size(MAP_INFO_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "i", color = AppColors.Muted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
    if (open) DataSourcesSheet(onDismiss = { open = false })
}

// The "i" is smaller than the round buttons of the map (48 dp): it is there, but quiet.
private val MAP_INFO_SIZE = 32.dp
