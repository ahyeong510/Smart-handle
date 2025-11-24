package com.example.smart_handle.network.models

data class TurnInfo(
    val seq: Int,
    val type: String,
    val at: LatLngDto
)
