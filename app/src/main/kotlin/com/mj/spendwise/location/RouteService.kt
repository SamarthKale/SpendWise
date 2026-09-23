package com.mj.spendwise.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** [source] is "OSRM" for a real road route or "Estimate" for the straight-line fallback. */
data class RouteResult(
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val source: String
)

/**
 * Fetches a real route from the public OSRM demo server (OpenStreetMap data, no API key).
 * Runs off the main thread with short timeouts and returns null on ANY failure (offline, server busy,
 * no route), so callers can fall back to a straight line. It must never throw.
 */
object RouteService {
    suspend fun fetch(from: LatLng, to: LatLng, mode: TravelMode): RouteResult? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = "https://router.project-osrm.org/route/v1/${mode.osrmProfile}/" +
                "${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson"
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 5_000
                setRequestProperty("User-Agent", "SpendWise-LabApp/1.0")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Split out so it can be unit-tested with a canned response. */
    internal fun parse(json: String): RouteResult? {
        val route = JSONObject(json).optJSONArray("routes")?.optJSONObject(0) ?: return null
        val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        // GeoJSON order is [longitude, latitude]
        val points = List(coords.length()) { i -> coords.getJSONArray(i).let { LatLng(it.getDouble(1), it.getDouble(0)) } }
        if (points.size < 2) return null
        return RouteResult(points, route.getDouble("distance"), route.getDouble("duration"), "OSRM")
    }
}
