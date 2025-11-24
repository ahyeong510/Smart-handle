package com.example.smart_handle.network.models

data class RecommendResponse(
    val start: LatLngDto,
    val target_distance: Double,
    val routes: List<RouteSummary>,
    val recommended_route_id: Int,
    val recommended_distance: Double
)
