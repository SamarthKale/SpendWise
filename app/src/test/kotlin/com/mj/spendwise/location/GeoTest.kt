package com.mj.spendwise.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeoTest {
    @Test
    fun haversine_samePoint_isZero() {
        val p = LatLng(19.0745, 72.9985)
        assertEquals(0.0, Geo.haversineMeters(p, p), 0.001)
    }

    @Test
    fun haversine_knownDistance() {
        // Mumbai (19.0760, 72.8777) to Pune (18.5204, 73.8567) is about 120 km in a straight line.
        val km = Geo.haversineMeters(LatLng(19.0760, 72.8777), LatLng(18.5204, 73.8567)) / 1000
        assertEquals(120.0, km, 5.0)
    }

    @Test
    fun eta_usesFixedSpeeds() {
        assertEquals(3600.0, Geo.etaSeconds(25_000.0, TravelMode.DRIVE), 0.01) // 25 km at 25 km/h = 1 h
        assertEquals(3600.0, Geo.etaSeconds(5_000.0, TravelMode.WALK), 0.01)   // 5 km at 5 km/h = 1 h
    }

    @Test
    fun formatting() {
        assertEquals("850 m", Geo.formatDistance(850.0))
        assertEquals("2.5 km", Geo.formatDistance(2500.0))
        assertEquals("12 min", Geo.formatDuration(720.0))
        assertEquals("1 h 5 min", Geo.formatDuration(3900.0))
    }

    @Test
    fun osrmResponse_isParsed_lngLatOrderFlipped() {
        val json = """{"code":"Ok","routes":[{"distance":1234.5,"duration":321.0,
            "geometry":{"type":"LineString","coordinates":[[72.99,19.07],[73.00,19.08]]}}]}"""
        val r = RouteService.parse(json)
        assertNotNull(r)
        assertEquals(19.07, r!!.points[0].lat, 1e-9) // GeoJSON is [lng, lat]
        assertEquals(72.99, r.points[0].lng, 1e-9)
        assertEquals(1234.5, r.distanceMeters, 0.001)
        assertEquals("OSRM", r.source)
    }

    @Test
    fun osrmResponse_withNoRoute_isNull() {
        assertNull(RouteService.parse("""{"code":"NoRoute","routes":[]}"""))
    }
}
