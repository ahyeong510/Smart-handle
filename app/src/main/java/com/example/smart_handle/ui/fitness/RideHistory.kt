package com.example.smart_handle.ui.fitness

import com.google.firebase.Timestamp

data class RideHistory(
    val routeType: String = "",
    val routeTitle: String = "",

    // 기존 호환 필드
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0L,

    // 추천 경로 feature
    val plannedDistanceKm: Double = 0.0,
    val plannedDurationMin: Int = 0,
    val elevationGain: Int = 0,
    val turnCount: Int = 0,
    val congestionText: String = "",
    val congestionScore: Double = 0.0,
    val routeScore: Double = 0.0,

    // 실제 주행 결과
    val riddenPercent: Int = 0,
    val effectiveDistanceKm: Double = 0.0,
    val actualDurationSec: Long = 0L,
    val isCompleted: Boolean = false,
    val satisfaction: String? = null,

    val createdAt: Timestamp? = null
)