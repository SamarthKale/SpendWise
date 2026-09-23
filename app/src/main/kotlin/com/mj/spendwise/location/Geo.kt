package com.mj.spendwise.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Walk / Drive toggle. Speeds are the fixed averages from CLAUDE.md (ETA is an estimate, not live traffic). */
enum class TravelMode(val label: String, val osrmProfile: String, val kmh: Double) {
    WALK("Walk", "foot", 5.0),
    DRIVE("Drive", "driving", 25.0)
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in metres (haversine formula). */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** ETA in seconds at the mode's average speed. */
    fun etaSeconds(distanceMeters: Double, mode: TravelMode): Double =
        distanceMeters / 1000.0 / mode.kmh * 3600.0

    fun formatDistance(meters: Double): String =
        if (meters < 1000) "%.0f m".format(meters) else "%.1f km".format(meters / 1000)

    fun formatDuration(seconds: Double): String {
        val minutes = Math.round(seconds / 60.0).toInt().coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }
}
