package com.mj.spendwise.ui.screens.map

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mj.spendwise.HQ_LAT
import com.mj.spendwise.HQ_LNG
import com.mj.spendwise.HQ_NAME
import com.mj.spendwise.backend.Expense
import com.mj.spendwise.categoryColor
import com.mj.spendwise.location.LatLng
import com.mj.spendwise.util.formatInr
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap (osmdroid) MapView embedded in Compose with AndroidView. Default MAPNIK tiles, no API key.
 * The MapView must be told about the Android lifecycle (onResume/onPause), done with an observer below.
 */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(HQ_LAT, HQ_LNG))
        }
    }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    return mapView
}

/** A round coloured dot marker; tapping it shows the osmdroid info window with [title] and [snippet]. */
private fun dotMarker(map: MapView, point: GeoPoint, title: String, snippet: String, color: Int, sizePx: Int) =
    Marker(map).apply {
        position = point
        this.title = title
        this.snippet = snippet
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(4, AndroidColor.WHITE)
            setSize(sizePx, sizePx)
        }
    }

/** Main map: HQ marker, expense markers coloured by category, my location, and the route polyline. */
@Composable
fun OsmMapView(
    expenses: List<Expense>,
    myLocation: LatLng?,
    routePoints: List<LatLng>,
    modifier: Modifier = Modifier
) {
    val mapView = rememberMapView()

    // Fit the whole route on screen whenever it changes (short delay so the view has been laid out).
    LaunchedEffect(routePoints) {
        if (routePoints.size >= 2) {
            delay(400)
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(routePoints.map { GeoPoint(it.lat, it.lng) }), true, 120)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()
            if (routePoints.size >= 2) {
                map.overlays.add(Polyline(map).apply {
                    setPoints(routePoints.map { GeoPoint(it.lat, it.lng) })
                    outlinePaint.color = AndroidColor.parseColor("#1E88E5")
                    outlinePaint.strokeWidth = 12f
                })
            }
            expenses.filter { it.latitude != null && it.longitude != null }.forEach { e ->
                map.overlays.add(
                    dotMarker(
                        map, GeoPoint(e.latitude!!, e.longitude!!), e.merchant,
                        "${formatInr(e.amount)} · ${e.category}", categoryColor(e.category).toArgb(), 36
                    )
                )
            }
            myLocation?.let {
                map.overlays.add(dotMarker(map, GeoPoint(it.lat, it.lng), "You are here", "", AndroidColor.parseColor("#1565C0"), 40))
            }
            // HQ last so it's drawn on top.
            map.overlays.add(dotMarker(map, GeoPoint(HQ_LAT, HQ_LNG), HQ_NAME, "Visit us / Get directions", AndroidColor.BLACK, 60))
            map.invalidate()
        }
    )
}

/** Small non-scrolling map for the expense detail screen. */
@Composable
fun MiniMap(lat: Double, lng: Double, title: String, modifier: Modifier = Modifier) {
    val mapView = rememberMapView()
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            val point = GeoPoint(lat, lng)
            map.overlays.clear()
            map.overlays.add(dotMarker(map, point, title, "", AndroidColor.parseColor("#E53935"), 44))
            map.controller.setZoom(16.0)
            map.controller.setCenter(point)
            map.invalidate()
        }
    )
}
