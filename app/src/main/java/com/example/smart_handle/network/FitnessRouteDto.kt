package com.example.smart_handle.network

import com.google.gson.annotations.SerializedName

data class FitnessRouteDto(
    @SerializedName("route_id")
    val routeId: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("distance_km")
    val distanceKm: Double,

    @SerializedName("duration_min")
    val durationMin: Int,

    @SerializedName("elevation_gain")
    val elevationGain: Int,

    @SerializedName("turn_count")
    val turnCount: Int,

    @SerializedName("score")
    val score: Double,

    @SerializedName("polyline")
    val polyline: List<List<Double>> = emptyList()
)