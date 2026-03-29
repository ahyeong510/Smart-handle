package com.example.smart_handle.ml

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.atan2

object FeatureExtractor {

    fun calculateTurnCount(points: List<LatLng>): Int {
        var count = 0

        for (i in 1 until points.size - 1) {
            val p1 = points[i - 1]
            val p2 = points[i]
            val p3 = points[i + 1]

            val angle = angleBetween(p1, p2, p3)

            if (abs(angle) > 30) {
                count++
            }
        }
        return count
    }

    private fun angleBetween(a: LatLng, b: LatLng, c: LatLng): Double {
        val ab = atan2(b.latitude - a.latitude, b.longitude - a.longitude)
        val bc = atan2(c.latitude - b.latitude, c.longitude - b.longitude)
        return Math.toDegrees(bc - ab)
    }
}