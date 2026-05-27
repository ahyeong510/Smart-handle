package com.example.smart_handle.maps

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sqrt

object KakaoTurnExtractor {

    private const val TAG = "KAKAO_TURN"

    private const val DUPLICATE_DISTANCE_M = 14f
    private const val GUIDE_NEAR_DISTANCE_M = 18f
    private const val START_END_SKIP_DISTANCE_M = 18f
    private const val ROAD_BOUNDARY_MIN_DISTANCE_M = 18f

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

                    if (routePoints.isNotEmpty()) {
                        val boundary = roadPoints.first()
                        roadBoundaryCandidates.add(
                            Candidate(
                                location = boundary,
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

            Log.d(
                TAG,
                "routePoints=${routePoints.size}, guides=${guideCandidates.size}, " +
                        "roadBoundaries=${roadBoundaryCandidates.size}, finalEvents=${merged.size}"
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
        val routeStart = routePoints.firstOrNull()
        val routeEnd = routePoints.lastOrNull()

        val filteredRoadBoundaries = roadBoundaryCandidates
            .filter { candidate ->
                if (routeStart != null && distance(candidate.location, routeStart) < START_END_SKIP_DISTANCE_M) {
                    return@filter false
                }

                if (routeEnd != null && distance(candidate.location, routeEnd) < START_END_SKIP_DISTANCE_M) {
                    return@filter false
                }

                if (guideCandidates.any { guide -> distance(candidate.location, guide.location) < GUIDE_NEAR_DISTANCE_M }) {
                    return@filter false
                }

                true
            }
            .distinctByDistance(ROAD_BOUNDARY_MIN_DISTANCE_M)

        val all = ArrayList<Candidate>()
        all.addAll(guideCandidates)
        all.addAll(filteredRoadBoundaries)

        if (all.isEmpty()) return emptyList()

        for (candidate in all) {
            candidate.progressMeters = calculateRouteProgress(routePoints, candidate.location)
                ?: candidate.order.toFloat()
        }

        val sorted = all.sortedWith(
            compareBy<Candidate> { it.progressMeters }.thenBy { it.order }
        )

        val merged = ArrayList<Candidate>()

        for (candidate in sorted) {
            val last = merged.lastOrNull()

            if (last != null && distance(last.location, candidate.location) < DUPLICATE_DISTANCE_M) {
                val replaceLast = isDirectional(candidate.type) && !isDirectional(last.type)

                if (replaceLast) {
                    merged[merged.lastIndex] = candidate
                }
            } else {
                merged.add(candidate)
            }
        }

        return merged
    }

    private fun List<Candidate>.distinctByDistance(minDistanceMeters: Float): List<Candidate> {
        val result = ArrayList<Candidate>()

        for (candidate in this.sortedBy { it.order }) {
            if (result.none { existing -> distance(existing.location, candidate.location) < minDistanceMeters }) {
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