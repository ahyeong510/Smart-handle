//package com.example.smart_handle.ui.main
//
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.smart_handle.network.ApiClient
//import com.example.smart_handle.network.models.RecommendResponse
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.launch
//
//class FitnessRouteViewModel : ViewModel() {
//
//    private val _recommendResult = MutableStateFlow<RecommendResponse?>(null)
//    val recommendResult: StateFlow<RecommendResponse?> = _recommendResult
//
//    private val _loading = MutableStateFlow(false)
//    val loading: StateFlow<Boolean> = _loading
//
//    /**
//     * 추천 경로 요청 함수
//     * Fragment에서는 viewModel.loadRecommend() 으로 호출하면 됨
//     */
//    fun loadRecommend(lat: Double, lng: Double, distance: Double) {
//        viewModelScope.launch {
//            _loading.value = true
//            try {
//                val result = ApiClient.api.getRecommend(lat, lng, distance)
//                _recommendResult.value = result
//            } catch (e: Exception) {
//                e.printStackTrace()
//                _recommendResult.value = null
//                println("🔥 API 호출 실패: ${e.message}")
//            } finally {
//                _loading.value = false
//            }
//        }
//    }
//}
