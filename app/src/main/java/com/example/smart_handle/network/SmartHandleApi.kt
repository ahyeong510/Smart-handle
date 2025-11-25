package com.example.smart_handle.network

import com.example.smart_handle.network.models.RecommendResponse
import com.example.smart_handle.network.models.RouteDetail
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SmartHandleApi {

    // ✅ 추천 경로 3개 + 추천 ID 받아오기
    @GET("/recommend")
    suspend fun getRecommend(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("distance") distance: Double
    ): RecommendResponse

    // ✅ 특정 경로의 좌표(path) + 턴 정보까지 상세 조회 (나중에 사용)
    @GET("/route/{id}")
    suspend fun getRouteDetail(
        @Path("id") id: Int
    ): RouteDetail
}
