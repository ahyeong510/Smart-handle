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

        return results
    }
}