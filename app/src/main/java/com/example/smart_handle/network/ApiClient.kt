package com.example.smart_handle.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:8000/"
    // 반드시 뒤에 / 붙여야 retrofit이 엔드포인트 해석함

    val api: SmartHandleApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmartHandleApi::class.java)
    }
}
