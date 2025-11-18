package com.example.smart_handle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_handle.network.NavigationResponse
import com.example.smart_handle.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NavigationViewModel : ViewModel() {

    private val _navigation = MutableStateFlow<NavigationResponse?>(null)
    val navigation = _navigation.asStateFlow()

    fun getRoute(from: String, to: String) {
        viewModelScope.launch {
            try {
                val result = RetrofitClient.api.getNavigation(from, to)
                _navigation.value = result
            } catch (e: Exception) {
                e.printStackTrace()
                _navigation.value = null
            }
        }
    }
}
