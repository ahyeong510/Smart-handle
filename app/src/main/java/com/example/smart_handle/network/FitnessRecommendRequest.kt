package com.example.smart_handle.network

data class FitnessRecommendRequest(
    val user_id: String,
    val start_lat: Double,
    val start_lng: Double,
    val target_km: Double
)