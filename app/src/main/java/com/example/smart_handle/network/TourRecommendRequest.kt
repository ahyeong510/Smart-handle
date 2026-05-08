package com.example.smart_handle.network

import com.google.gson.annotations.SerializedName

data class TourRecommendRequest(
    @SerializedName("start_lat")
    val start_lat: Double,

    @SerializedName("start_lng")
    val start_lng: Double,

    @SerializedName("radius_m")
    val radius_m: Int
)