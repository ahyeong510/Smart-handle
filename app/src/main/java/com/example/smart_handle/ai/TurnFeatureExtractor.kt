package com.example.smart_handle.ai

import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import kotlin.math.*

object TurnFeatureExtractor {

    /**
     * TurnEvent 리스트(좌표 + 좌/우/직진)를 기반으로
     * - 회전 이벤트 개수
     * - 짧은 구간 연속 회전(tight pairs)
     * 를 feature로 뽑아준다.
     *
     * @param turnEvents KakaoTurnExtractor 결과
     * @param tightThresholdM "짧은 구간" 기준 (미터). 기본 30m
     */
    fun fromTurnEvents(
        turnEvents: List<TurnEvent>,
        tightThresholdM: Int = 30
    ): RouteFeatures {

        // 직진 제외하고 회전만 필터
        val turnsOnly = turnEvents.filter { it.type == TurnType.LEFT || it.type == TurnType.RIGHT }

        val turnCountLR = turnsOnly.size
        val turnCountTotal = turnCountLR // 현재는 좌/우만 있으니 동일

        // 연속 회전 간 거리(직선거리)로 tight pair 계산
        var tightPairs = 0
        for (i in 0 until turnsOnly.size - 1) {
            val a = turnsOnly[i].location
            val b = turnsOnly[i + 1].location
            val d = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            if (d <= tightThresholdM) tightPairs += 1
        }

        return RouteFeatures(
            turnCountTotal = turnCountTotal,
            turnCountLR = turnCountLR,
            tightTurnPairs = tightPairs
        )
    }

    // 위경도 두 점 사이 거리(m) 계산 (haversine)
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}