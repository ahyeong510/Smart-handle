package com.example.smart_handle.ui.Tour

import java.io.Serializable

data class TourPlaceData(
    val name: String = "이름 없음",
    val lat: Double = 0.0,
    val lng: Double = 0.0,

    // Kakao 주소
    val address: String = "주소 없음",

    // TourAPI 정보
    val contentId: String = "",
    val contentTypeId: String = "",
    val tourTitle: String = "",
    val addr1: String = "",

    // TourAPI overview
    val description: String? = "설명 없음"
) : Serializable