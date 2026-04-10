package com.example.smart_handle.maps

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.atan2

object FitnessTurnExtractor {

    fun extractTurnEvents(points: List<LatLng>): ArrayList<TurnEvent> {
        val result = arrayListOf<TurnEvent>()

        if (points.size < 3) {
            if (points.isNotEmpty()) {
                result.add(
                    TurnEvent(
                        location = points.last(),
                        type = TurnType.STRAIGHT
                    )
                )
            }
            return result
        }

        for (i in 1 until points.size - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val angle = calculateTurnAngle(prev, curr, next)

            val turnType = when {
                angle > 25 -> TurnType.RIGHT
                angle < -25 -> TurnType.LEFT
                else -> null
            }

            if (turnType != null) {
                result.add(
                    TurnEvent(
                        location = curr,
                        type = turnType
                    )
                )
            }
        }

        // 마지막은 도착 지점 판정용으로 꼭 넣어줌
        result.add(
            TurnEvent(
                location = points.last(),
                type = TurnType.STRAIGHT
            )
        )

        markContinuousTurns(result)
        return result
    }

    private fun calculateTurnAngle(a: LatLng, b: LatLng, c: LatLng): Double {
        val v1x = b.longitude - a.longitude
        val v1y = b.latitude - a.latitude
        val v2x = c.longitude - b.longitude
        val v2y = c.latitude - b.latitude

        val angle1 = atan2(v1y, v1x)
        val angle2 = atan2(v2y, v2x)

        var diff = Math.toDegrees(angle2 - angle1)

        while (diff > 180) diff -= 360.0
        while (diff < -180) diff += 360.0

        return diff
    }

    private fun markContinuousTurns(events: ArrayList<TurnEvent>) {
        if (events.size < 2) return

        for (i in 0 until events.size - 1) {
            val current = events[i]
            val next = events[i + 1]

            val sameDirection =
                (current.type == TurnType.LEFT && next.type == TurnType.LEFT) ||
                        (current.type == TurnType.RIGHT && next.type == TurnType.RIGHT)

            val closeDistance = distanceMeters(current.location, next.location) <= 35f

            if (sameDirection && closeDistance) {
                current.isContinuous = true
            }
        }
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Float {
        val result = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            result
        )
        return result[0]
    }
}