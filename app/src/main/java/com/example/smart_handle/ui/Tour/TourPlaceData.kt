package com.example.smart_handle.ui.Tour

import java.io.Serializable

data class TourPlaceData(
    val name: String = "이름 없음",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "주소 없음",

    // ⭐ 관광 설명
    val description: String? = "설명 없음"
) : Serializable