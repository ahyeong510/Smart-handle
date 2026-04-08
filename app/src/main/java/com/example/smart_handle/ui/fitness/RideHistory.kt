package com.example.smart_handle.ui.fitness

data class RideHistory(
    val routeType: String = "",
    val routeId: String = "",
    val distanceKm: Double = 0.0,
    val durationMin: Int = 0,
    val elevationGain: Int = 0,
    val congestionText: String = "",
    val turnCount: Int = 0,
    val completionPercent: Int = 0,
    val satisfaction: String? = null,
    val actualDurationSec: Long = 0L,
    val createdAt: Long? = null
)