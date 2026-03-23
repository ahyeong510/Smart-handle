package com.example.smart_handle.exercise

import com.google.android.gms.maps.model.LatLng

object ExerciseRouteGenerator {

    fun generateDummyCandidates(targetDistance: Double): List<ExerciseRouteCandidate> {
        val baseLat = 37.5665
        val baseLng = 126.9780

        val balancedDistance = targetDistance
        val fastDistance = if (targetDistance > 1.0) targetDistance - 0.2 else targetDistance
        val stableDistance = targetDistance + 0.3

        return listOf(
            ExerciseRouteCandidate(
                title = "균형형",
                description = "거리, 시간, 회전 수가 전체적으로 균형 잡힌 경로",
                destLatLng = LatLng(baseLat + 0.010, baseLng + 0.008),
                distanceKm = balancedDistance,
                estimatedTimeMin = estimateTime(balancedDistance, 14.0),
                turnCount = 7,
                routePoints = emptyList(),
                turnEvents = emptyList()
            ),
            ExerciseRouteCandidate(
                title = "빠른형",
                description = "예상 시간이 짧아 효율적으로 달릴 수 있는 경로",
                destLatLng = LatLng(baseLat + 0.006, baseLng + 0.014),
                distanceKm = fastDistance,
                estimatedTimeMin = estimateTime(fastDistance, 16.5),
                turnCount = 10,
                routePoints = emptyList(),
                turnEvents = emptyList()
            ),
            ExerciseRouteCandidate(
                title = "안정형",
                description = "회전 수가 적어 비교적 단순하게 이동할 수 있는 경로",
                destLatLng = LatLng(baseLat + 0.014, baseLng + 0.003),
                distanceKm = stableDistance,
                estimatedTimeMin = estimateTime(stableDistance, 13.0),
                turnCount = 4,
                routePoints = emptyList(),
                turnEvents = emptyList()
            )
        )
    }

    private fun estimateTime(distanceKm: Double, speedKmPerHour: Double): Int {
        val hours = distanceKm / speedKmPerHour
        return (hours * 60).toInt().coerceAtLeast(1)
    }
}