package com.example.smart_handle.ai

data class RouteFeatures(
    val turnCountTotal: Int = 0,
    val turnCountLR: Int = 0,
    val tightTurnPairs: Int = 0
)