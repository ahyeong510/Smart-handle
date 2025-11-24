package com.example.smart_handle.network.models

data class RouteDetail(
    val id: Int,
    val name: String,
    val distance: Double,
    val start: LatLngDto,
    val path: List<LatLngDto>,
    val turns: List<TurnInfo>,
    val source: String
)
