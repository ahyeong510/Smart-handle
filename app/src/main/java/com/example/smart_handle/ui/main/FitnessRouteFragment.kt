package com.example.smart_handle.ui.main

import android.content.Intent
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
import com.example.smart_handle.ui.driving.DrivingActivity
import com.example.smart_handle.ui.fitness.RouteAdapter
import com.example.smart_handle.ui.fitness.RouteModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FitnessRouteFragment : Fragment() {

    private lateinit var npDistance: NumberPicker
    private lateinit var btnGenerateRoute: Button
    private lateinit var btnStartDriving: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResult: TextView
    private lateinit var recyclerFitness: RecyclerView

    private val viewModel: FitnessRouteViewModel by viewModels()

    // 임시 현재 위치
    private val currentLocationLat = 37.5665
    private val currentLocationLng = 126.9780

    private var hasRequested = false
    private var selectedRoute: RouteModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_fitness_route, container, false)

        npDistance = view.findViewById(R.id.npDistance)
        btnGenerateRoute = view.findViewById(R.id.btnGenerateRoute)
        btnStartDriving = view.findViewById(R.id.btnStartDriving)
        progressBar = view.findViewById(R.id.progressBar)
        tvResult = view.findViewById(R.id.tvResult)
        recyclerFitness = view.findViewById(R.id.recyclerFitness)

        btnStartDriving.visibility = View.GONE

        npDistance.minValue = 1
        npDistance.maxValue = 50
        npDistance.value = 10

        recyclerFitness.layoutManager = LinearLayoutManager(requireContext())

        // 추천 경로 요청
        btnGenerateRoute.setOnClickListener {
            hasRequested = true
            selectedRoute = null
            btnStartDriving.visibility = View.GONE

            viewModel.loadRecommend(
                lat = currentLocationLat,
                lng = currentLocationLng,
                distance = npDistance.value.toDouble()
            )
        }

        // 🚀 주행 시작
        btnStartDriving.setOnClickListener {
            val route = selectedRoute ?: run {
                Toast.makeText(requireContext(), "경로를 선택하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(requireContext(), DrivingActivity::class.java).apply {
                // 길찾기 모드와 동일
                putParcelableArrayListExtra("path", ArrayList(route.path))

                // 🔥 핵심 수정: key 이름 통일
                putExtra("endLat", route.endLat)
                putExtra("endLng", route.endLng)
            }

            startActivity(intent)
        }

        observeViewModel()

        return view
    }

    private fun observeViewModel() {

        // 로딩 상태
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loading.collectLatest { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // 추천 결과
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recommendResult.collectLatest { response ->

                // 버튼 누르기 전 자동 emit 무시
                if (!hasRequested) return@collectLatest

                // 서버 응답 자체가 없음
                if (response == null) {
                    Toast.makeText(
                        requireContext(),
                        "서버 오류 발생",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@collectLatest
                }

                // routes 비어 있음
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

                // 🔥 핵심 1: polyline 없는 route 제거
                val validRoutes = response.routes.filter {
                    !it.polyline.isNullOrEmpty()
                }

                // 🔥 유효한 경로가 하나도 없으면 종료
                if (validRoutes.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "유효한 경로가 없습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                    recyclerFitness.adapter = null
                    tvResult.text = ""
                    return@collectLatest
                }

                // 정상 UI 표시
                tvResult.text = "추천된 거리: ${response.recommended_distance} km"

                // 🔥 핵심 2: convertToRouteModel 안전 호출
                val routeList = try {
                    validRoutes.map { convertToRouteModel(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(
                        requireContext(),
                        "경로 변환 중 오류 발생",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@collectLatest
                }

                recyclerFitness.adapter = RouteAdapter(
                    routeList,
                    onSelected = { route ->
                        selectedRoute = route
                        showRouteOnMap(route)
                        btnStartDriving.visibility = View.VISIBLE
                    }
                )
            }
        }
    }


    // 서버 → UI 변환
    private fun convertToRouteModel(src: RouteSummary): RouteModel {

        // 🔥 polyline null or empty 방어
        val rawPolyline = src.polyline
        if (rawPolyline.isNullOrEmpty()) {
            throw IllegalStateException("서버에서 polyline이 비어 있음")
        }

        val pts: List<LatLng> = rawPolyline.map { pair ->
            LatLng(pair[0], pair[1])
        }

        val titleText = when {
            src.difficulty_score < 30 -> "쉬운 코스"
            src.difficulty_score < 60 -> "보통 코스"
            else -> "어려운 코스"
        }

        val lastPoint = pts.last()

        return RouteModel(
            id = 0,
            title = titleText,
            distanceKm = src.distance_m / 1000.0,
            timeMin = ((src.distance_m / 1000.0) * 4).toInt(),
            path = pts,
            endLat = lastPoint.latitude,
            endLng = lastPoint.longitude
        )
    }

    // 지도 표시 (길찾기 모드 로직 재사용)
    private fun showRouteOnMap(route: RouteModel) {
        // drawPolyline(route.path)
    }
}
