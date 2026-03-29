package com.example.smart_handle.network

data class FitnessRouteDto(
    val route_id: String,
    val title: String,
    val distance_km: Double,
    val duration_min: Int,
    val elevation_gain: Int,
    val congestion_text: String,
    val score: Double,
    val polyline: List<List<Double>> = emptyList()
)