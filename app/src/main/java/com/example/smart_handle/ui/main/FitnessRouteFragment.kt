package com.example.smart_handle.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.network.models.RouteSummary
import com.example.smart_handle.ui.fitness.RouteAdapter
import com.example.smart_handle.ui.fitness.RouteModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FitnessRouteFragment : Fragment() {

    private lateinit var npDistance: NumberPicker
    private lateinit var btnGenerateRoute: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResult: TextView
    private lateinit var recyclerFitness: RecyclerView

    // ViewModel
    private val viewModel: FitnessRouteViewModel by viewModels()

    // 임시 현재 위치
    private val currentLocationLat = 37.5665
    private val currentLocationLng = 126.9780

    // 사용자가 버튼을 눌렀는지 여부
    private var hasRequested = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_fitness_route, container, false)

        npDistance = view.findViewById(R.id.npDistance)
        btnGenerateRoute = view.findViewById(R.id.btnGenerateRoute)
        progressBar = view.findViewById(R.id.progressBar)
        tvResult = view.findViewById(R.id.tvResult)
        recyclerFitness = view.findViewById(R.id.recyclerFitness)

        // NumberPicker 설정
        npDistance.minValue = 1
        npDistance.maxValue = 50
        npDistance.value = 10

        recyclerFitness.layoutManager = LinearLayoutManager(requireContext())

        btnGenerateRoute.setOnClickListener {
            hasRequested = true
            viewModel.loadRecommend(
                lat = currentLocationLat,
                lng = currentLocationLng,
                distance = npDistance.value.toDouble()
            )
        }

        observeViewModel()

        return view
    }

    private fun observeViewModel() {

        // 로딩 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loading.collectLatest { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // 추천 결과 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recommendResult.collectLatest { response ->

                // 앱 시작 직후 자동 emit 무시
                if (!hasRequested) return@collectLatest

                // 서버 에러
                if (response == null) {
                    Toast.makeText(
                        requireContext(),
                        "서버 오류 발생",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@collectLatest
                }

                // 추천 결과 없음
                if (response.routes.isNullOrEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "추천 가능한 경로가 없습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                    recyclerFitness.adapter = null
                    tvResult.text = ""
                    return@collectLatest
                }

                // 정상 응답
                tvResult.text = "추천된 거리: ${response.recommended_distance} km"

                val routeList = response.routes.map { route ->
                    convertToRouteModel(route)
                }

                recyclerFitness.adapter = RouteAdapter(
                    routeList,
                    onSelected = { route ->
                        Toast.makeText(
                            requireContext(),
                            "${route.title} 선택됨!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun convertToRouteModel(src: RouteSummary): RouteModel {

        val pts = emptyList<LatLng>()

        val titleText = when {
            src.difficulty_score < 30 -> "쉬운 코스"
            src.difficulty_score < 60 -> "보통 코스"
            else -> "어려운 코스"
        }

        return RouteModel(
            id = 0,
            title = titleText,
            distanceKm = src.distance_m / 1000.0,
            timeMin = ((src.distance_m / 1000.0) * 4).toInt(),
            path = pts
        )
    }
}

