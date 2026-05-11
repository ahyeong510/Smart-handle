package com.example.smart_handle.ui.fitness

import com.example.smart_handle.ui.Tour.TourPlaceData
import java.io.Serializable

data class FitnessRouteOption(
    val routeId: String,
    val title: String,
    val distanceKm: Double,
    val durationMin: Int,
    val elevationGain: Int,
    val turnCount: Int,
    val score: Double,

    val routePoints: ArrayList<RoutePointData> = arrayListOf(),

    // ⭐ 관광모드용 관광지 목록
    val tourPlaces: ArrayList<TourPlaceData> = arrayListOf()
) : Serializable