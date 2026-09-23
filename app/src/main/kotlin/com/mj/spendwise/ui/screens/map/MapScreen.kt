package com.mj.spendwise.ui.screens.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.spendwise.CATEGORIES
import com.mj.spendwise.HQ_ADDRESS
import com.mj.spendwise.HQ_LAT
import com.mj.spendwise.HQ_LNG
import com.mj.spendwise.HQ_NAME
import com.mj.spendwise.location.Geo
import com.mj.spendwise.location.LocationProvider
import com.mj.spendwise.location.TravelMode
import com.mj.spendwise.ui.components.CategoryChip
import com.mj.spendwise.util.toLocalDateTime
import com.mj.spendwise.viewmodel.ExpenseViewModel
import com.mj.spendwise.viewmodel.MapFilter
import com.mj.spendwise.viewmodel.MapViewModel
import java.time.YearMonth

/** OpenStreetMap (osmdroid) map with HQ + expense markers, distance/ETA, route line and "Start navigation". */
@Composable
fun MapScreen(expenseVm: ExpenseViewModel, modifier: Modifier = Modifier, mapVm: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val expenses by expenseVm.expenses.collectAsStateWithLifecycle()
    val online by expenseVm.isOnline.collectAsStateWithLifecycle()
    val myLocation by mapVm.myLocation.collectAsStateWithLifecycle()
    val route by mapVm.route.collectAsStateWithLifecycle()
    val mode by mapVm.mode.collectAsStateWithLifecycle()
    val sampleStart by mapVm.usingSampleStart.collectAsStateWithLifecycle()
    val filter by mapVm.filter.collectAsStateWithLifecycle()
    val category by mapVm.category.collectAsStateWithLifecycle()

    // Ask for location once; if denied the map still works with a sample start point.
    var asked by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) mapVm.refreshLocation() else denied = true
    }
    LaunchedEffect(Unit) {
        if (LocationProvider(context).hasPermission()) mapVm.refreshLocation()
        else if (!asked) {
            asked = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else denied = true
    }

    val thisMonth = remember { YearMonth.now() }
    val visible = remember(expenses, filter, category) {
        expenses.orEmpty().filter { e ->
            e.latitude != null && when (filter) {
                MapFilter.ALL -> true
                MapFilter.THIS_MONTH -> YearMonth.from(e.timestamp.toLocalDateTime()) == thisMonth
                MapFilter.BY_CATEGORY -> category == null || e.category == category
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MapFilter.entries) { f ->
                FilterChip(selected = filter == f, onClick = { mapVm.setFilter(f) }, label = { Text(f.label) })
            }
        }
        if (filter == MapFilter.BY_CATEGORY) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CATEGORIES) { c ->
                    CategoryChip(c, selected = category == c, onClick = { mapVm.setCategory(if (category == c) null else c) })
                }
            }
        }

        val mapContent: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                OsmMapView(
                    expenses = visible,
                    myLocation = myLocation,
                    routePoints = route.points,
                    modifier = Modifier.fillMaxSize()
                )
                if (!online) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    ) {
                        Text("Map tiles unavailable offline", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        val card: @Composable (Modifier) -> Unit = { m ->
            DirectionsCard(
                mode = mode,
                onMode = mapVm::setMode,
                distanceMeters = route.distanceMeters,
                // The public OSRM demo server only knows car speeds (its "foot" answer is a driving time),
                // so real OSRM time is used for Drive and the fixed 5 km/h estimate for Walk.
                durationSeconds = if (route.source == "OSRM" && mode == TravelMode.DRIVE) route.durationSeconds
                else Geo.etaSeconds(route.distanceMeters, mode),
                routeSource = route.source,
                sampleStart = sampleStart,
                locationDenied = denied,
                onStart = { startNavigation(context) },
                modifier = m
            )
        }

        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape: map on the left, scrollable directions card on the right so "Start navigation" stays reachable.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                mapContent(Modifier.weight(1f).fillMaxHeight())
                card(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(8.dp))
            }
        } else {
            // Portrait: card below the map (not on top of it) so the whole route stays visible.
            mapContent(Modifier.weight(1f).fillMaxWidth())
            card(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun DirectionsCard(
    mode: TravelMode,
    onMode: (TravelMode) -> Unit,
    distanceMeters: Double,
    durationSeconds: Double,
    routeSource: String,
    sampleStart: Boolean,
    locationDenied: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(HQ_NAME, style = MaterialTheme.typography.titleSmall)
            Text(HQ_ADDRESS, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TravelMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { onMode(m) }, label = { Text(m.label) })
                }
                Text("${Geo.formatDistance(distanceMeters)} · ${Geo.formatDuration(durationSeconds)}", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                buildString {
                    append(if (routeSource == "OSRM") "Route: OpenStreetMap roads (OSRM)." else "Straight-line estimate at ${mode.kmh.toInt()} km/h.")
                    if (sampleStart) append(if (locationDenied) " Location denied: using a sample start point." else " Using a sample start point until GPS is available.")
                },
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onStart, Modifier.fillMaxWidth()) { Text("Start navigation") }
        }
    }
}

/**
 * Opens whichever maps app is installed (Google Maps included) with a standard geo: intent.
 * If nothing handles it, falls back to OpenStreetMap directions in the browser. Never crashes.
 */
private fun startNavigation(context: Context) {
    val geo = Uri.parse("geo:$HQ_LAT,$HQ_LNG?q=$HQ_LAT,$HQ_LNG(${Uri.encode("SpendWise HQ")})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        val web = Uri.parse("https://www.openstreetmap.org/directions?to=$HQ_LAT%2C$HQ_LNG")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) {
            // No browser either: nothing more to do.
        }
    }
}
