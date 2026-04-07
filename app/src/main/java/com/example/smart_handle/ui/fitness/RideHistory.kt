package com.example.smart_handle.data

import com.google.firebase.Timestamp

data class RideHistory(
    val routeType: String = "",
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0L,
    val satisfaction: String? = null,
    val createdAt: Timestamp? = null
)