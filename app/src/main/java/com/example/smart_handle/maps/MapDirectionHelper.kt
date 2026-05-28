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
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object MapDirectionHelper {

    private const val KAKAO_API_KEY = "KakaoAK b0d04327171c2efc6885e9d9a153ca55"
    private const val BASE_URL = "https://apis-navi.kakaomobility.com/v1/directions"

    // 전체 결과 리턴
    data class RouteResult(
        val points: List<LatLng>,
        val turnEvents: List<TurnEvent>,
        val body: String
    )

    /**
     * 전체 경로 + 턴 이벤트 리턴
     */
    suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): RouteResult = withContext(Dispatchers.IO) {

        val origin = "${startLng},${startLat}"
        val destination = "${endLng},${endLat}"
        val params = "origin=${urlEnc(origin)}&destination=${urlEnc(destination)}"
        val urlStr = "$BASE_URL?$params"

        Log.d("RouteDebug", "Kakao URL=$urlStr")

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Authorization", KAKAO_API_KEY)
        }

        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }

            if (code !in 200..299) {
                return@withContext RouteResult(emptyList(), emptyList(), body)
            }

            val polyline = extractPolyline(body)
            val kakaoTurns = KakaoTurnExtractor.extractTurnEvents(body)
            val turns = IntersectionAugmenter.augment(
                routePoints = polyline,
                kakaoTurns = kakaoTurns
            )

            return@withContext RouteResult(
                points = polyline,
                turnEvents = turns,
                body = body
            )

        } catch (e: Exception) {
            Log.e("RouteDebug", "Error ${e.message}", e)
            return@withContext RouteResult(emptyList(), emptyList(), "")
        } finally {
            conn.disconnect()
        }
    }

    // ---- Polyline 추출 코드 (기존과 동일) ----
    private fun extractPolyline(body: String): List<LatLng> {
        val points = ArrayList<LatLng>()
        val json = JSONObject(body)

        val routes = json.optJSONArray("routes") ?: JSONArray()
        if (routes.length() == 0) return emptyList()

        val firstRoute = routes.getJSONObject(0)
        val sections = firstRoute.optJSONArray("sections") ?: JSONArray()

        for (i in 0 until sections.length()) {
            val sec = sections.getJSONObject(i)
            val roads = sec.optJSONArray("roads") ?: JSONArray()

            for (j in 0 until roads.length()) {
                val road = roads.getJSONObject(j)
                val vertexes = road.optJSONArray("vertexes") ?: JSONArray()

                var k = 0
                while (k + 1 < vertexes.length()) {
                    val lon = vertexes.optDouble(k)
                    val lat = vertexes.optDouble(k + 1)
                    if (!lat.isNaN() && !lon.isNaN()) {
                        points.add(LatLng(lat, lon))
                    }
                    k += 2
                }
            }
        }
        return points
    }

    private fun urlEnc(v: String): String =
        URLEncoder.encode(v, StandardCharsets.UTF_8.name())
}
