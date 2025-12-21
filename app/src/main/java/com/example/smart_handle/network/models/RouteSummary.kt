package com.example.smart_handle.network.models

data class RouteSummary(
    val distance_m: Double,
    val total_ascent_m: Double,
    val max_grade_percent: Double,
    val difficulty_score: Double
)
