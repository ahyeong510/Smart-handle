package com.example.smart_handle.network

import retrofit2.http.GET
import retrofit2.http.Query

interface FastApiService {

    @GET("navigate")
    suspend fun getNavigation(
        @Query("from") from: String,
        @Query("to") to: String
    ): NavigationResponse
}
