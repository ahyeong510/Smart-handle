package com.example.smart_handle.ui.fitness

data class RideHistory(
    val routeType: String = "",
    val routeId: String = "",
    val distanceKm: Double = 0.0,
    val durationMin: Long = 0L,
    val elevationGain: Long = 0L,
    val congestionText: String = "",
    val turnCount: Long = 0L,
    val completionPercent: Long = 0L,
    val satisfaction: String? = null,
    val actualDurationSec: Long = 0L,
    val createdAt: Long? = null
)