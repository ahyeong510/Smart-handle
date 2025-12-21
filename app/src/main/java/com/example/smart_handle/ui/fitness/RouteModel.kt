package com.example.smart_handle.ui.fitness

import com.google.android.gms.maps.model.LatLng

data class RouteModel(
    val id: Int,
    val title: String,
    val distanceKm: Double,
    val timeMin: Int,
    val path: List<LatLng>,

    // ⭐ 운동 모드 → 주행 모드 연결에 필요한 도착 좌표
    val endLat: Double,
    val endLng: Double
)
