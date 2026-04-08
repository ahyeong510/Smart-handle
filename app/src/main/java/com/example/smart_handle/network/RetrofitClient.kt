package com.example.smart_handle.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // ⭐ 현재 PC IP로 변경
    private const val BASE_URL = "http://172.30.1.3:8000/"

    val fitnessApi: FitnessApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FitnessApiService::class.java)
    }
}