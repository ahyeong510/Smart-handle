package com.example.smart_handle.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kakao Directions API의 JSON을 분석해
 * 회전(turn) 지점을 TurnEvent 리스트로 추출하는 클래스.
 *
 * -> MapDirectionHelper에서 받은 body(JSON 문자열)를 넘겨서 처리한다.
 */
object KakaoTurnExtractor {

    /**
     * @param jsonBody Kakao Directions API 전체 JSON 문자열
     * @return List<TurnEvent>  (좌회전/우회전/직진 등 포함)
     */
    fun extractTurnEvents(jsonBody: String): List<TurnEvent> {
        val results = ArrayList<TurnEvent>()

        try {
            val root = JSONObject(jsonBody)
            val routes = root.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()

            val firstRoute = routes.getJSONObject(0)
            val sections = firstRoute.optJSONArray("sections") ?: JSONArray()

            // sections[].guides[] 안에 회전 안내가 있음
            for (i in 0 until sections.length()) {
                val sec = sections.getJSONObject(i)
                val guides = sec.optJSONArray("guides") ?: JSONArray()

                for (g in 0 until guides.length()) {
                    val gObj = guides.getJSONObject(g)

                    val lat = gObj.optDouble("y")   // 위도
                    val lng = gObj.optDouble("x")   // 경도
                    val maneuver = gObj.optString("guidance") // ex) "좌회전", "우회전", "직진"

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
