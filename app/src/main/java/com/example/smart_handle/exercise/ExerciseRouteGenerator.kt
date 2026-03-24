package com.example.smart_handle.exercise

import android.location.Location
import com.example.smart_handle.maps.MapDirectionHelper
import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.sin

object ExerciseRouteGenerator {

    suspend fun generateRoutes(
        startLatLng: LatLng,
        targetDistanceKm: Double
    ): List<ExerciseRouteCandidate> {

        val directions = listOf(
            0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0
        )

        val candidates = mutableListOf<ExerciseRouteCandidate>()

        for (angle in directions) {
            val dest = createDestination(
                start = startLatLng,
                distanceKm = targetDistanceKm * 0.75, // 직선거리 보정
                angle = angle
            )

            val route = MapDirectionHelper.getRoute(
                startLat = startLatLng.latitude,
                startLng = startLatLng.longitude,
                endLat = dest.latitude,
                endLng = dest.longitude
            )

            if (route.points.isNotEmpty()) {
                val distanceKm = calculateRouteDistanceKm(route.points)
                val estimatedTimeMin = estimateTime(distanceKm)
                val turnCount = route.turnEvents.size

                candidates.add(
                    ExerciseRouteCandidate(
                        title = "",
                        description = "",
                        destLatLng = dest,
                        distanceKm = distanceKm,
                        estimatedTimeMin = estimatedTimeMin,
                        turnCount = turnCount,
                        routePoints = route.points,
                        turnEvents = route.turnEvents
                    )
                )
            }
        }

        return selectTop4(candidates, targetDistanceKm)
    }

    private fun createDestination(
        start: LatLng,
        distanceKm: Double,
        angle: Double
    ): LatLng {
        val earthRadius = 6371.0
        val d = distanceKm / earthRadius

        val lat1 = Math.toRadians(start.latitude)
        val lng1 = Math.toRadians(start.longitude)
        val bearing = Math.toRadians(angle)

        val lat2 = Math.asin(
            sin(lat1) * cos(d) +
                    cos(lat1) * sin(d) * cos(bearing)
        )

        val lng2 = lng1 + Math.atan2(
            sin(bearing) * sin(d) * cos(lat1),
            cos(d) - sin(lat1) * sin(lat2)
        )

        return LatLng(
            Math.toDegrees(lat2),
            Math.toDegrees(lng2)
        )
    }

    private fun calculateRouteDistanceKm(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0

        var totalMeters = 0f
        val result = FloatArray(1)

        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]

            Location.distanceBetween(
                a.latitude, a.longitude,
                b.latitude, b.longitude,
                result
            )

            totalMeters += result[0]
        }

        return totalMeters / 1000.0
    }

    private fun estimateTime(distanceKm: Double): Int {
        val speedKmPerHour = 15.0
        return ((distanceKm / speedKmPerHour) * 60).toInt().coerceAtLeast(1)
    }

    private fun selectTop4(
        list: List<ExerciseRouteCandidate>,
        target: Double
    ): List<ExerciseRouteCandidate> {

        if (list.isEmpty()) return emptyList()

        val result = mutableListOf<ExerciseRouteCandidate>()
        val used = mutableSetOf<Int>()

        fun addIfNotUsed(candidate: ExerciseRouteCandidate?, title: String, description: String) {
            if (candidate == null) return
            val index = list.indexOf(candidate)
            if (index in used) return

            used.add(index)
            result.add(
                candidate.copy(
                    title = title,
                    description = description
                )
            )
        }

        val balanced = list.minByOrNull { kotlin.math.abs(it.distanceKm - target) }
        val fastest = list
            .filter { it != balanced }
            .minByOrNull { it.estimatedTimeMin }
        val simplest = list
            .filter { it != balanced && it != fastest }
            .minByOrNull { it.turnCount }
        val mostTurns = list
            .filter { it != balanced && it != fastest && it != simplest }
            .maxByOrNull { it.turnCount }

        addIfNotUsed(balanced, "균형형", "목표 거리와 가장 유사한 경로")
        addIfNotUsed(fastest, "빠른형", "가장 빠르게 도착 가능한 경로")
        addIfNotUsed(simplest, "안정형", "회전이 적어 단순한 경로")
        addIfNotUsed(mostTurns, "탐험형", "회전이 많아 다양한 길을 경험할 수 있는 경로")

        return result
    }
}