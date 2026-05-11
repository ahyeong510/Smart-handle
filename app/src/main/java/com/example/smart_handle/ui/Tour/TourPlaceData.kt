package com.example.smart_handle.ui.Tour

import java.io.Serializable

data class TourPlaceData(
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String = ""
) : Serializable