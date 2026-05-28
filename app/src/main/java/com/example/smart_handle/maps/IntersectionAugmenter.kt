package com.example.smart_handle.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

object IntersectionAugmenter {

    private const val TAG = "INTERSECTION_AUG"

    // 실제 길 테스트 기준:
    // 1) DEMO_BLUE_START_POINT 전 X 구간 = LED OFF
    // 2) DEMO_BLUE_START_POINT 통과 후 = RC2/LC2 파란 LED
    // 3) DEMO_RED_START_POINT 통과 후 = RC1/LC1 빨간 LED
    // 4) DEMO_RIGHT_TURN_POINT = 실제 우회전 지점
    private val DEMO_BLUE_START_POINT =
        LatLng(37.3010974, 127.0373927)

    private val DEMO_RED_START_POINT =
        LatLng(37.30124854082333, 127.03777655386224)

    private val DEMO_RIGHT_TURN_POINT =
        LatLng(37.3016181308997, 127.03779440476745)

    private val DEMO_ANCHORS = listOf(
        DEMO_BLUE_START_POINT,
        DEMO_RED_START_POINT,
        DEMO_RIGHT_TURN_POINT
    )

    private const val DEMO_ROUTE_RADIUS_M = 180f

    fun augment(
        routePoints: List<LatLng>,
        kakaoTurns: List<TurnEvent>
    ): List<TurnEvent> {
        if (routePoints.size < 2) return kakaoTurns

        if (!isNearDemoRoute(routePoints)) {
            Log.d(TAG, "not demo route: keep kakaoTurns=${kakaoTurns.size}")
            return kakaoTurns
        }

        val blueStartProgress = calculateRouteProgress(routePoints, DEMO_BLUE_START_POINT)
        val redStartProgress = calculateRouteProgress(routePoints, DEMO_RED_START_POINT)
        val rightProgress = calculateRouteProgress(routePoints, DEMO_RIGHT_TURN_POINT)

        val result = mutableListOf<TurnEvent>()

        // 파란 LED 시작 기준점.
        // 이 지점 전에는 남은 교차로 수가 3개라서 DrivingActivity에서 LED가 꺼진다.
        result += TurnEvent(DEMO_BLUE_START_POINT, TurnType.STRAIGHT)

        // 빨간 LED 시작 기준점.
        // 이 지점을 지나면 실제 우회전까지 남은 교차로 수가 1개가 되어 빨간 LED가 켜진다.
        result += TurnEvent(DEMO_RED_START_POINT, TurnType.STRAIGHT)

        // 실제 우회전 지점.
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
            "demo augment applied: blueStart=${blueStartProgress.toInt()}m, " +
                    "redStart=${redStartProgress.toInt()}m, " +
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