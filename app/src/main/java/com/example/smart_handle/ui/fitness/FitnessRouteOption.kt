package com.example.smart_handle.ui.fitness

import java.io.Serializable

data class FitnessRouteOption(
    val routeId: String,
    val title: String,
    val distanceKm: Double,
    val durationMin: Int,
    val elevationGain: Int,
    val congestionText: String,
    val score: Double,
    val routePoints: ArrayList<RoutePointData> = arrayListOf()
) : Serializable