package com.example.smart_handle.network

import com.example.smart_handle.network.models.RecommendResponse
import com.example.smart_handle.network.models.RouteDetail
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SmartHandleApi {

    @GET("/recommend")
    suspend fun getRecommend(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("distance") distance: Double
    ): RecommendResponse

    @GET("/route/{id}")
    suspend fun getRouteDetail(
        @Path("id") id: Int
    ): RouteDetail
}
