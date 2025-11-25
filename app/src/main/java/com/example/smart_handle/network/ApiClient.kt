package com.example.smart_handle.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:8000/"   // 에뮬레이터용 FastAPI 서버 주소

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ⭐️ 이게 바로 service!
    val service: SmartHandleApi by lazy {
        retrofit.create(SmartHandleApi::class.java)
    }
}
