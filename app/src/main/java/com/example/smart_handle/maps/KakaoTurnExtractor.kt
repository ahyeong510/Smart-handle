package com.example.smart_handle.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

object KakaoTurnExtractor {

    fun extractTurnEvents(jsonBody: String): List<TurnEvent> {
        val rawEvents = ArrayList<TurnEvent>()
        val filteredEvents = ArrayList<TurnEvent>()

        try {
            val root = JSONObject(jsonBody)
            val routes = root.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()

            val firstRoute = routes.getJSONObject(0)
            val sections = firstRoute.optJSONArray("sections") ?: JSONArray()

            // guides 배열에서 회전 안내 수집
            for (i in 0 until sections.length()) {
                val sec = sections.getJSONObject(i)
                val guides = sec.optJSONArray("guides") ?: JSONArray()

                for (g in 0 until guides.length()) {
                    val gObj = guides.getJSONObject(g)

                    val lat = gObj.optDouble("y")
                    val lng = gObj.optDouble("x")
                    val maneuver = gObj.optString("guidance")

                    // guidance 문자열에 “좌/우” 포함 여부로 단순 판정
                    val turnType = when {
                        maneuver.contains("좌") -> TurnType.LEFT
                        maneuver.contains("우") -> TurnType.RIGHT
                        else -> TurnType.STRAIGHT
                    }

                    if (!lat.isNaN() && !lng.isNaN()) {
                        rawEvents.add(
                            TurnEvent(
                                location = LatLng(lat, lng),
                                type = turnType
                            )
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("TurnExtractor", "extractTurnEvents error: ${e.message}")
            return emptyList()
        }

        if (rawEvents.size < 2) return rawEvents

        // ---------- 🔥 1차 필터링: “좌→바로 우” 이런 가짜 우회전 제거 ----------
        for (i in rawEvents.indices) {
            if (i > 0) {
                val prev = rawEvents[i - 1]
                val cur = rawEvents[i]

                val dist = getDistance(prev.location, cur.location)

                // 좌회전 직후 40m 이내의 RIGHT는 거의 100% API 오류
                if (prev.type == TurnType.LEFT &&
                    cur.type == TurnType.RIGHT &&
                    dist < 40
                ) {
                    Log.w("TurnFix", "Filtered fake RIGHT after LEFT ($dist m)")
                    continue
                }
            }

            filteredEvents.add(rawEvents[i])
        }

        return filteredEvents
    }

    // 거리 계산
    private fun getDistance(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)

        return 2 * R * kotlin.math.atan2(
            kotlin.math.sqrt(h),
            kotlin.math.sqrt(1 - h)
        )
    }
}
