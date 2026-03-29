package com.example.smart_handle.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface FitnessApiService {

    @POST("fitness/recommend-loop")
    fun recommendLoopRoute(
        @Body request: FitnessRecommendRequest
    ): Call<FitnessRecommendResponse>
}