package com.example.smart_handle.network.models

data class RouteSummary(
    val id: Int,
    val name: String,
    val distance: Double,
    val source: String,
    val path_point_count: Int,
    val turn_count: Int
)
