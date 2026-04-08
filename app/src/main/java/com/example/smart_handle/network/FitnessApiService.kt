package com.example.smart_handle.network

import retrofit2.Call
import retrofit2.http.POST
import retrofit2.http.Query

interface FitnessApiService {

    @POST("ai/recommend")
    fun recommendLoopRoute(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("target_km") targetKm: Double
    ): Call<FitnessRecommendResponse>
}