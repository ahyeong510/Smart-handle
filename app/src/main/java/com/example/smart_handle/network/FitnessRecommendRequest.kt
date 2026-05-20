package com.example.smart_handle.network

import com.google.gson.annotations.SerializedName

data class RideHistoryItem(
    @SerializedName("distanceKm")
    val distanceKm: Double,

    @SerializedName("elevationGain")
    val elevationGain: Int,

    @SerializedName("turnCount")
    val turnCount: Int,

    @SerializedName("durationMin")
    val durationMin: Int,

    @SerializedName("completionPercent")
    val completionPercent: Int,

    @SerializedName("satisfaction")
    val satisfaction: String
)

data class FitnessRecommendRequest(
    @SerializedName("user_id")
    val user_id: String,

    @SerializedName("start_lat")
    val start_lat: Double,

    @SerializedName("start_lng")
    val start_lng: Double,

    @SerializedName("target_km")
    val target_km: Double,

    @SerializedName("ride_history")
    val ride_history: List<RideHistoryItem>
)