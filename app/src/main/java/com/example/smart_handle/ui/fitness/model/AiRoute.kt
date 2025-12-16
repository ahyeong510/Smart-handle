package com.example.smart_handle.ui.fitness.model

import com.google.gson.annotations.SerializedName

data class AiRoute(
    val id: Int,

    @SerializedName("distance_km")
    val distanceKm: Double,

    @SerializedName("duration_min")
    val durationMin: Int,

    // 🔽 길찾기/기존 코드 호환용 (AI 운동탭에서는 사용 안 함)
    val path: List<AiPathPoint> = emptyList(),
    val turns: List<AiTurn> = emptyList()
)
