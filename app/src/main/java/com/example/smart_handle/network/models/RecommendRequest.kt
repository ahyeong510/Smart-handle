package com.example.smart_handle.network.models

data class RecommendRequest(
    val lat: Double,
    val lng: Double,
    val distance: Double
)