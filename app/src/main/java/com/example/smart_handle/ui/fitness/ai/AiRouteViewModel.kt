package com.example.smart_handle.ui.fitness.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_handle.ui.fitness.model.AiRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class AiRouteViewModel : ViewModel() {

    private val _routes = MutableStateFlow<List<AiRoute>>(emptyList())
    val routes: StateFlow<List<AiRoute>> = _routes

    private val api: AiApi by lazy {
        Retrofit.Builder()
            // 🔥 실제 폰에서는 PC IP 사용해야 함
            .baseUrl("http://192.168.219.118:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApi::class.java)
    }

    fun fetchRoutes(lat: Double, lng: Double, distance: Double) {
        viewModelScope.launch {
            try {
                val res = api.recommend(lat, lng, distance)
                _routes.value = res.routes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

interface AiApi {
    @GET("recommend")
    suspend fun recommend(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("distance") distance: Double
    ): AiRouteResponse
}

data class AiRouteResponse(
    val routes: List<AiRoute>
)
