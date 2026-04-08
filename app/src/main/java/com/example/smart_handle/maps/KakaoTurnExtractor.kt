package com.example.smart_handle.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

object KakaoTurnExtractor {

    fun extractTurnEvents(jsonBody: String): List<TurnEvent> {
        val results = ArrayList<TurnEvent>()

        try {
            val root = JSONObject(jsonBody)
            val routes = root.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()

            val firstRoute = routes.getJSONObject(0)
            val sections = firstRoute.optJSONArray("sections") ?: JSONArray()

            for (i in 0 until sections.length()) {
                val sec = sections.getJSONObject(i)
                val guides = sec.optJSONArray("guides") ?: JSONArray()

                for (g in 0 until guides.length()) {
                    val gObj = guides.getJSONObject(g)

                    val lat = gObj.optDouble("y")
                    val lng = gObj.optDouble("x")
                    val maneuver = gObj.optString("guidance")

                    val turnType = when {
                        maneuver.contains("좌") -> TurnType.LEFT
                        maneuver.contains("우") -> TurnType.RIGHT
                        else -> TurnType.STRAIGHT
                    }

                    if (!lat.isNaN() && !lng.isNaN()) {
                        results.add(
                            TurnEvent(
                                location = LatLng(lat, lng),
                                type = turnType
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TurnExtractor", "extractTurnEvents error: ${e.message}", e)
        }

        // 🔥 여기 핵심 변경
        return markContinuousTurns(results)
    }

    // 🔥 거리 계산 함수
    private fun distanceMeters(a: LatLng, b: LatLng): Float {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            result
        )
        return result[0]
    }

    // 🔥 연속 좌/우회전 판별
    private fun markContinuousTurns(events: List<TurnEvent>): List<TurnEvent> {
        if (events.size < 2) return events

        for (i in 0 until events.size - 1) {
            val current = events[i]
            val next = events[i + 1]

            val sameDirection =
                (current.type == TurnType.LEFT && next.type == TurnType.LEFT) ||
                        (current.type == TurnType.RIGHT && next.type == TurnType.RIGHT)

            val closeDistance = distanceMeters(current.location, next.location) <= 35f

            if (sameDirection && closeDistance) {
                current.isContinuous = true
            }
        }
        return events
    }
}