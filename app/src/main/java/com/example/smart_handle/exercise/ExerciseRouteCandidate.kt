package com.example.smart_handle.exercise

import com.example.smart_handle.maps.TurnEvent
import com.google.android.gms.maps.model.LatLng

data class ExerciseRouteCandidate(
    val title: String,
    val description: String,
    val destLatLng: LatLng,
    val distanceKm: Double,
    val estimatedTimeMin: Int,
    val turnCount: Int,
    val routePoints: List<LatLng>,
    val turnEvents: List<TurnEvent>
)