package com.mj.spendwise.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mj.spendwise.HQ_LAT
import com.mj.spendwise.HQ_LNG
import com.mj.spendwise.location.Geo
import com.mj.spendwise.location.LatLng
import com.mj.spendwise.location.LocationProvider
import com.mj.spendwise.location.RouteResult
import com.mj.spendwise.location.RouteService
import com.mj.spendwise.location.TravelMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MapFilter(val label: String) { ALL("All"), THIS_MONTH("This month"), BY_CATEGORY("By category") }

/**
 * State for the Map screen: my location, Walk/Drive mode, the route to HQ and the marker filter.
 * Distance/ETA are always available locally (haversine); OSRM only upgrades them when reachable.
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {
    val hq = LatLng(HQ_LAT, HQ_LNG)
    private val locationProvider = LocationProvider(app)

    private val _myLocation = MutableStateFlow<LatLng?>(null)
    val myLocation: StateFlow<LatLng?> = _myLocation.asStateFlow()

    private val _mode = MutableStateFlow(TravelMode.DRIVE)
    val mode: StateFlow<TravelMode> = _mode.asStateFlow()

    private val _route = MutableStateFlow(estimate(sampleStart(), TravelMode.DRIVE))
    val route: StateFlow<RouteResult> = _route.asStateFlow()

    private val _usingSampleStart = MutableStateFlow(true)
    /** True when there is no GPS fix/permission, so the route starts ~2 km away so the demo still shows one. */
    val usingSampleStart: StateFlow<Boolean> = _usingSampleStart.asStateFlow()

    private val _filter = MutableStateFlow(MapFilter.ALL)
    val filter: StateFlow<MapFilter> = _filter.asStateFlow()

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    private var routeJob: Job? = null

    fun setFilter(f: MapFilter) { _filter.value = f }
    fun setCategory(c: String?) { _category.value = c }

    fun setMode(mode: TravelMode) {
        _mode.value = mode
        updateRoute()
    }

    /** Call when the screen opens or location permission is granted. */
    fun refreshLocation() {
        viewModelScope.launch {
            val fix = locationProvider.getCurrentLocation()
            _myLocation.value = fix
            _usingSampleStart.value = fix == null
            updateRoute()
        }
    }

    private fun sampleStart() = LatLng(HQ_LAT - 0.018, HQ_LNG - 0.006) // roughly 2 km south-west of HQ

    private fun estimate(start: LatLng, mode: TravelMode): RouteResult {
        val d = Geo.haversineMeters(start, hq)
        return RouteResult(listOf(start, hq), d, Geo.etaSeconds(d, mode), "Estimate")
    }

    private fun updateRoute() {
        val start = _myLocation.value ?: sampleStart()
        val mode = _mode.value
        _route.value = estimate(start, mode) // instant local answer, never blank
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            RouteService.fetch(start, hq, mode)?.let { _route.value = it } // upgrade to a real road route if reachable
        }
    }

    init {
        updateRoute()
    }
}
