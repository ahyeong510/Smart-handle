package com.example.smart_handle.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Kakao Mobility Directions API를 호출해
 * 도로를 따라가는 Polyline 포인트 목록을 반환한다.
 *
 * 사용법:
 *   val points = MapDirectionHelper.getRoutePoints(startLat, startLng, endLat, endLng)
 *   // points: 지도에 그대로 addPolyline 하면 됨
 */
object MapDirectionHelper {

    // ⚠️ 여기에 너의 Kakao REST API 키를 넣어라. "KakaoAK " 접두사 포함 주의!
    //   ex) const val KAKAO_API_KEY = "KakaoAK 1234567890abcdef..."
    private const val KAKAO_API_KEY = "KakaoAK b0d04327171c2efc6885e9d9a153ca55"

    // Kakao Directions endpoint
    private const val BASE_URL = "https://apis-navi.kakaomobility.com/v1/directions"

    /**
     * Kakao는 origin/destination이 "경도,위도(lon,lat)" 순서임에 주의!
     */
    suspend fun getRoutePoints(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): List<LatLng> = withContext(Dispatchers.IO) {
        val origin = "${startLng},${startLat}"
        val destination = "${endLng},${endLat}"

        // 필요 시 옵션(차량/회피옵션 등) 추가 가능
        val params = "origin=${urlEnc(origin)}&destination=${urlEnc(destination)}"

        val urlStr = "$BASE_URL?$params"
        Log.d("RouteDebug", "Kakao URL=$urlStr")

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeUnit.SECONDS.toMillis(10).toInt()
            readTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
            setRequestProperty("Authorization", KAKAO_API_KEY) // ← 반드시 필요
        }

        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }

            Log.d("RouteDebug", "responseCode=$code")
            Log.d("RouteDebug", "body=${body.take(500)}")

            if (code !in 200..299) {
                // 서버가 에러 반환
                return@withContext emptyList()
            }

            // ---- JSON 파싱 ----
            val json = JSONObject(body)
            val routes = json.optJSONArray("routes") ?: JSONArray()
            if (routes.length() == 0) return@withContext emptyList()

            val points = ArrayList<LatLng>()

            // routes[0].sections[].roads[].vertexes[] (경도/위도 1차원 배열)
            val firstRoute = routes.getJSONObject(0)
            val sections = firstRoute.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until sections.length()) {
                val sec = sections.getJSONObject(i)
                val roads = sec.optJSONArray("roads") ?: JSONArray()
                for (j in 0 until roads.length()) {
                    val road = roads.getJSONObject(j)
                    val vertexes = road.optJSONArray("vertexes") ?: JSONArray()
                    // vertexes = [lon1, lat1, lon2, lat2, ...]
                    var k = 0
                    while (k + 1 < vertexes.length()) {
                        val lon = vertexes.optDouble(k)       // 경도
                        val lat = vertexes.optDouble(k + 1)   // 위도
                        if (!lat.isNaN() && !lon.isNaN()) {
                            points.add(LatLng(lat, lon))
                        }
                        k += 2
                    }
                }
            }

            // 중복 포인트 제거(선택)
            if (points.size >= 2) {
                return@withContext points
            } else {
                // 포인트가 없거나 1개면 경로가 없는 것 -> 상위에서 처리
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e("RouteDebug", "Kakao request error: ${e.message}", e)
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private fun urlEnc(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}
