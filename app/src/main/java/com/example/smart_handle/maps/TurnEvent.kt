package com.example.smart_handle.maps

import com.google.android.gms.maps.model.LatLng

/**
 * Kakao Directions에서 추출한 회전 이벤트 정보
 */
data class TurnEvent(
    val location: LatLng,      // 회전 지점 좌표
    val type: TurnType         // LEFT, RIGHT, STRAIGHT
)

enum class TurnType {
    LEFT,      // 좌회전
    RIGHT,     // 우회전
    STRAIGHT   // 직진
}
