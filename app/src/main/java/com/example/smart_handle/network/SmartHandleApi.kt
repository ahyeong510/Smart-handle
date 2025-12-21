package com.example.smart_handle.network

import com.example.smart_handle.network.models.RecommendResponse
import retrofit2.http.POST
import retrofit2.http.Query

interface SmartHandleApi {

    @POST("ai/recommend")
    suspend fun getRecommend(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("target_km") targetKm: Double
    ): RecommendResponse
}
