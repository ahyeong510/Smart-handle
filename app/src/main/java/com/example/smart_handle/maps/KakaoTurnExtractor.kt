package com.example.smart_handle.maps

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

object KakaoTurnExtractor {

    private const val TAG = "KAKAO_TURN"

    // 기존 값이 너무 크면 짧은 골목/교차로가 guide 근처라는 이유로 전부 제거될 수 있음.
    // 그래서 캡스톤 테스트용으로 STRAIGHT 후보를 조금 더 보존하는 쪽으로 완화함.
    private const val DUPLICATE_DISTANCE_M = 8f
    private const val START_END_SKIP_DISTANCE_M = 12f
    private const val ROAD_BOUNDARY_MIN_DISTANCE_M = 10f

    private data class Candidate(
        val location: LatLng,
        val type: TurnType,
        val source: String,
        val order: Int,
        var progressMeters: Float = 0f
    )

    fun extractTurnEvents(jsonBody: String): List<TurnEvent> {
        val routePoints = ArrayList<LatLng>()
        val guideCandidates = ArrayList<Candidate>()
        val roadBoundaryCandidates = ArrayList<Candidate>()
        var order = 0

        try {
            val root = JSONObject(jsonBody)
            val routes = root.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()

            val firstRoute = routes.getJSONObject(0)
            val sections = firstRoute.optJSONArray("sections") ?: JSONArray()

            for (sectionIndex in 0 until sections.length()) {
                val section = sections.getJSONObject(sectionIndex)

                val roads = section.optJSONArray("roads") ?: JSONArray()
                for (roadIndex in 0 until roads.length()) {
                    val road = roads.getJSONObject(roadIndex)
                    val roadPoints = extractRoadPoints(road)
                    if (roadPoints.isEmpty()) continue

                    // roadStart/roadEnd는 "경로가 나뉜 지점" 후보로 사용한다.
                    // 실제 모든 골목을 100% 의미하지는 않지만, Kakao 응답만으로 가능한 가장 쉬운 후보임.
                    if (routePoints.isNotEmpty()) {
                        roadBoundaryCandidates.add(
                            Candidate(
                                location = roadPoints.first(),
                                type = TurnType.STRAIGHT,
                                source = "roadStart[$sectionIndex,$roadIndex]",
                                order = order++
                            )
                        )
                    }

                    for (point in roadPoints) {
                        addRoutePoint(routePoints, point)
                    }

                    roadBoundaryCandidates.add(
                        Candidate(
                            location = roadPoints.last(),
                            type = TurnType.STRAIGHT,
                            source = "roadEnd[$sectionIndex,$roadIndex]",
                            order = order++
                        )
                    )
                }

                val guides = section.optJSONArray("guides") ?: JSONArray()
                for (guideIndex in 0 until guides.length()) {
                    val guide = guides.getJSONObject(guideIndex)
                    val lat = guide.optDouble("y")
                    val lng = guide.optDouble("x")
                    val guidance = guide.optString("guidance", "")

                    if (lat.isNaN() || lng.isNaN()) continue
                    if (shouldSkipGuide(guidance)) continue

                    guideCandidates.add(
                        Candidate(
                            location = LatLng(lat, lng),
                            type = guidanceToTurnType(guidance),
                            source = "guide[$sectionIndex,$guideIndex]:$guidance",
                            order = order++
                        )
                    )
                }
            }

            val merged = mergeCandidates(
                routePoints = routePoints,
                guideCandidates = guideCandidates,
                roadBoundaryCandidates = roadBoundaryCandidates
            )

            val straightCount = merged.count { it.type == TurnType.STRAIGHT }
            val directionalCount = merged.size - straightCount

            Log.d(
                TAG,
                "routePoints=${routePoints.size}, guides=${guideCandidates.size}, " +
                        "roadBoundariesRaw=${roadBoundaryCandidates.size}, " +
                        "finalEvents=${merged.size}, straight=$straightCount, directional=$directionalCount"
            )

            merged.forEachIndexed { index, candidate ->
                Log.d(
                    TAG,
                    "event[$index] type=${candidate.type}, source=${candidate.source}, " +
                            "progress=${candidate.progressMeters.toInt()}m, " +
                            "lat=${candidate.location.latitude}, lng=${candidate.location.longitude}"
                )
            }

            return merged.map { candidate ->
                TurnEvent(
                    location = candidate.location,
                    type = candidate.type
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractTurnEvents error: ${e.message}", e)
            return emptyList()
        }
    }

    private fun mergeCandidates(
        routePoints: List<LatLng>,
        guideCandidates: List<Candidate>,
        roadBoundaryCandidates: List<Candidate>
    ): List<Candidate> {
        if (routePoints.size < 2) return guideCandidates

        val routeTotalM = calculateRouteTotalDistance(routePoints)

        val allRaw = ArrayList<Candidate>()
        allRaw.addAll(guideCandidates)
        allRaw.addAll(roadBoundaryCandidates)

        for (candidate in allRaw) {
            candidate.progressMeters = calculateRouteProgress(routePoints, candidate.location)
                ?: candidate.order.toFloat()
        }

        val filteredRoadBoundaries = roadBoundaryCandidates
            .filter { candidate ->
                val p = candidate.progressMeters
                // 출발/도착 바로 근처의 road boundary는 안내 교차로로 쓰면 출발하자마자 꼬일 수 있어서 제거
                p >= START_END_SKIP_DISTANCE_M && p <= routeTotalM - START_END_SKIP_DISTANCE_M
            }
            .distinctByDistanceAndProgress(ROAD_BOUNDARY_MIN_DISTANCE_M)

        Log.d(
            TAG,
            "merge: guides=${guideCandidates.size}, roadRaw=${roadBoundaryCandidates.size}, " +
                    "roadAfterStartEnd=${filteredRoadBoundaries.size}, routeTotal=${routeTotalM.toInt()}m"
        )

        val all = ArrayList<Candidate>()
        all.addAll(guideCandidates)
        all.addAll(filteredRoadBoundaries)

        if (all.isEmpty()) return emptyList()

        val sorted = all.sortedWith(
            compareBy<Candidate> { it.progressMeters }.thenBy { it.order }
        )

        val merged = ArrayList<Candidate>()

        for (candidate in sorted) {
            val last = merged.lastOrNull()

            if (last != null && isDuplicateEvent(last, candidate)) {
                val replaceLast = isDirectional(candidate.type) && !isDirectional(last.type)

                if (replaceLast) {
                    merged[merged.lastIndex] = candidate
                }
                // 둘 다 방향 안내거나, 둘 다 STRAIGHT면 기존 것을 유지한다.
            } else {
                merged.add(candidate)
            }
        }

        return merged
    }

    private fun isDuplicateEvent(a: Candidate, b: Candidate): Boolean {
        return distance(a.location, b.location) < DUPLICATE_DISTANCE_M ||
                abs(a.progressMeters - b.progressMeters) < DUPLICATE_DISTANCE_M
    }

    private fun List<Candidate>.distinctByDistanceAndProgress(minDistanceMeters: Float): List<Candidate> {
        val result = ArrayList<Candidate>()

        for (candidate in this.sortedWith(compareBy<Candidate> { it.progressMeters }.thenBy { it.order })) {
            val duplicated = result.any { existing ->
                distance(existing.location, candidate.location) < minDistanceMeters ||
                        abs(existing.progressMeters - candidate.progressMeters) < minDistanceMeters
            }

            if (!duplicated) {
                result.add(candidate)
            }
        }

        return result
    }

    private fun extractRoadPoints(road: JSONObject): List<LatLng> {
        val points = ArrayList<LatLng>()
        val vertexes = road.optJSONArray("vertexes") ?: JSONArray()

        var i = 0
        while (i + 1 < vertexes.length()) {
            val lng = vertexes.optDouble(i)
            val lat = vertexes.optDouble(i + 1)

            if (!lat.isNaN() && !lng.isNaN()) {
                points.add(LatLng(lat, lng))
            }

            i += 2
        }

        return points
    }

    private fun addRoutePoint(points: MutableList<LatLng>, point: LatLng) {
        val last = points.lastOrNull()

        if (last == null || distance(last, point) >= 1f) {
            points.add(point)
        }
    }

    private fun shouldSkipGuide(guidance: String): Boolean {
        val text = guidance.trim()
        if (text.isEmpty()) return true

        return text.contains("출발") ||
                text.contains("도착") ||
                text.contains("목적지") ||
                text.contains("경유지") ||
                text.contains("경유")
    }

    private fun guidanceToTurnType(guidance: String): TurnType {
        return when {
            guidance.contains("우") || guidance.contains("오른") -> TurnType.RIGHT
            guidance.contains("좌") || guidance.contains("왼") || guidance.contains("유턴") -> TurnType.LEFT
            else -> TurnType.STRAIGHT
        }
    }

    private fun isDirectional(type: TurnType): Boolean {
        return type == TurnType.LEFT || type == TurnType.RIGHT
    }

    private fun calculateRouteTotalDistance(routePoints: List<LatLng>): Float {
        var total = 0f

        for (i in 0 until routePoints.lastIndex) {
            total += distance(routePoints[i], routePoints[i + 1])
        }

        return total
    }

    private fun calculateRouteProgress(routePoints: List<LatLng>, point: LatLng): Float? {
        if (routePoints.size < 2) return null

        val cumulative = ArrayList<Float>()
        var total = 0f
        cumulative.add(total)

        for (i in 0 until routePoints.lastIndex) {
            total += distance(routePoints[i], routePoints[i + 1])
            cumulative.add(total)
        }

        var bestDistanceSq = Double.MAX_VALUE
        var bestProgressMeters = 0.0

        for (i in 0 until routePoints.lastIndex) {
            val a = routePoints[i]
            val b = routePoints[i + 1]

            val latRad = Math.toRadians((a.latitude + b.latitude + point.latitude) / 3.0)
            val metersPerDegreeLng = 111320.0 * cos(latRad)
            val metersPerDegreeLat = 110540.0

            val bx = (b.longitude - a.longitude) * metersPerDegreeLng
            val by = (b.latitude - a.latitude) * metersPerDegreeLat
            val px = (point.longitude - a.longitude) * metersPerDegreeLng
            val py = (point.latitude - a.latitude) * metersPerDegreeLat

            val segmentLengthSq = bx * bx + by * by
            val t =
                if (segmentLengthSq > 0.0) {
                    ((px * bx + py * by) / segmentLengthSq).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }

            val projectedX = bx * t
            val projectedY = by * t

            val dx = px - projectedX
            val dy = py - projectedY
            val distanceSq = dx * dx + dy * dy

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestProgressMeters = cumulative[i] + sqrt(segmentLengthSq) * t
            }
        }

        return bestProgressMeters.toFloat()
    }

    private fun distance(a: LatLng, b: LatLng): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            result
        )
        return result[0]
    }
}
