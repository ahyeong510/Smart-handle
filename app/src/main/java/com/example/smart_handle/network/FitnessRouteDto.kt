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
    val polyline: List<List<Double>> = emptyList(),

    // ⭐ 관광모드용 관광지 목록
    @SerializedName("tour_places")
    val tourPlaces: List<TourPlaceDto> = emptyList()
)

data class TourPlaceDto(

    @SerializedName("name")
    val name: String,

    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lng")
    val lng: Double,

    @SerializedName("address")
    val address: String = "",

    @SerializedName("description")
    val description: String? = ""
)