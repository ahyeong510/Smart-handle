package com.example.smart_handle.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 에뮬레이터에서 PC 로컬 서버 접속할 때:
    // 10.0.2.2 = 내 컴퓨터 localhost
    private const val BASE_URL = "http://192.168.219.105:8000/"

    val fitnessApi: FitnessApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FitnessApiService::class.java)
    }
}