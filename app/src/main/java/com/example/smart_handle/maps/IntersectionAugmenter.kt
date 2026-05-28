package com.example.smart_handle.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

object IntersectionAugmenter {

    private const val TAG = "INTERSECTION_AUG"

    private val DEMO_ANCHORS = listOf(
        LatLng(37.3010974, 127.0373927),
        LatLng(37.3016181308997, 127.03779440476745)
    )

    private const val DEMO_ROUTE_RADIUS_M = 180f

    // X 구간 이후 처음 지나치는 교차로 지점
    // 이 지점을 지나기 전까지 RC2 파란 LED 유지
    private val DEMO_STRAIGHT_POINT =
        LatLng(37.30124854082333, 127.03777655386224)

    // 실제 우회전해야 하는 다음 골목
    // DEMO_STRAIGHT_POINT 통과 후 RC1 빨간 LED
    private val DEMO_RIGHT_TURN_POINT =
        LatLng(37.3016181308997, 127.03779440476745)

    fun augment(
        routePoints: List<LatLng>,
        kakaoTurns: List<TurnEvent>
    ): List<TurnEvent> {
        if (routePoints.size < 2) return kakaoTurns

        if (!isNearDemoRoute(routePoints)) {
            Log.d(TAG, "not demo route: keep kakaoTurns=${kakaoTurns.size}")
            return kakaoTurns
        }

        val straightProgress = calculateRouteProgress(routePoints, DEMO_STRAIGHT_POINT)
        val rightProgress = calculateRouteProgress(routePoints, DEMO_RIGHT_TURN_POINT)

        val result = mutableListOf<TurnEvent>()

        result += TurnEvent(DEMO_STRAIGHT_POINT, TurnType.STRAIGHT)
        result += TurnEvent(DEMO_RIGHT_TURN_POINT, TurnType.RIGHT)

        kakaoTurns.forEach { event ->
            val p = calculateRouteProgress(routePoints, event.location)

            // 시연 구간 초반 LEFT/RIGHT는 수동 이벤트로 대체
            if (p <= rightProgress + 30f) {
                Log.d(TAG, "demo skip kakao turn: type=${event.type}, progress=${p.toInt()}m")
                return@forEach
            }

            result += event
        }

        val sorted = result
            .distinctBy { "${it.type}_${it.location.latitude}_${it.location.longitude}" }
            .sortedBy { calculateRouteProgress(routePoints, it.location) }

        Log.d(
            TAG,
            "demo augment applied: straight=${straightProgress.toInt()}m, " +
                    "right=${rightProgress.toInt()}m, result=${sorted.size}"
        )

        sorted.forEachIndexed { index, event ->
            val p = calculateRouteProgress(routePoints, event.location)
            Log.d(
                TAG,
                "event[$index] type=${event.type}, progress=${p.toInt()}m, " +
                        "lat=${event.location.latitude}, lng=${event.location.longitude}"
            )
        }

        return sorted
    }

    private fun isNearDemoRoute(routePoints: List<LatLng>): Boolean {
        return routePoints.any { point ->
            DEMO_ANCHORS.any { anchor ->
                distance(point, anchor) <= DEMO_ROUTE_RADIUS_M
            }
        }
    }

    private fun calculateRouteProgress(
        routePoints: List<LatLng>,
        point: LatLng
    ): Float {
        if (routePoints.size < 2) return 0f

        var totalBeforeSegment = 0f
        var bestProgress = 0f
        var bestDistance = Float.MAX_VALUE

        for (i in 0 until routePoints.lastIndex) {
            val a = routePoints[i]
            val b = routePoints[i + 1]

            val segmentLength = distance(a, b)
            if (segmentLength <= 0f) continue

            val projection = projectPointToSegment(point, a, b)
            val distToSegment = distance(point, projection.point)

            if (distToSegment < bestDistance) {
                bestDistance = distToSegment
                bestProgress = totalBeforeSegment + segmentLength * projection.t
            }

            totalBeforeSegment += segmentLength
        }

        return bestProgress
    }

    private data class ProjectionResult(
        val point: LatLng,
        val t: Float
    )

    private fun projectPointToSegment(
        p: LatLng,
        a: LatLng,
        b: LatLng
    ): ProjectionResult {
        val ax = a.longitude
        val ay = a.latitude
        val bx = b.longitude
        val by = b.latitude
        val px = p.longitude
        val py = p.latitude

        val dx = bx - ax
        val dy = by - ay

        val lenSq = dx * dx + dy * dy

        if (lenSq == 0.0) {
            return ProjectionResult(a, 0f)
        }

        var t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        t = t.coerceIn(0.0, 1.0)

        val projected = LatLng(
            ay + dy * t,
            ax + dx * t
        )

        return ProjectionResult(projected, t.toFloat())
    }

    private fun distance(a: LatLng, b: LatLng): Float {
        val earthRadius = 6371000.0

        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)

        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val h = sin(dLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2.0)

        return (2 * earthRadius * asin(sqrt(h))).toFloat()
    }
}