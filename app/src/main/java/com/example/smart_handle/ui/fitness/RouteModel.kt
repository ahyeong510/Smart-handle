package com.example.smart_handle.ui.fitness

import com.google.android.gms.maps.model.LatLng

/**
 * 운동 코스 1개를 나타내는 모델
 */
data class RouteModel(
    val id: Int,
    val title: String,
    val distanceKm: Double,
    val timeMin: Int,
    val level: Int,
    val path: List<LatLng>
)
