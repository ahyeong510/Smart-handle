package com.example.smart_handle.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.network.ApiClient
import com.example.smart_handle.network.models.RouteSummary
import com.example.smart_handle.ui.fitness.RouteAdapter
import com.example.smart_handle.ui.fitness.RouteModel
import kotlinx.coroutines.*
import com.google.android.gms.maps.model.LatLng

class FitnessRouteFragment : Fragment() {

    private lateinit var npDistance: NumberPicker
    private lateinit var btnGenerateRoute: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResult: TextView
    private lateinit var recyclerFitness: RecyclerView

    private val api = ApiClient.service

    private var currentLocationLat = 37.5665
    private var currentLocationLng = 126.9780

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_fitness_route, container, false)

        npDistance = view.findViewById(R.id.npDistance)
        btnGenerateRoute = view.findViewById(R.id.btnGenerateRoute)
        progressBar = view.findViewById(R.id.progressBar)
        tvResult = view.findViewById(R.id.tvResult)
        recyclerFitness = view.findViewById(R.id.recyclerFitness)

        // 거리 입력 NumberPicker
        npDistance.minValue = 1
        npDistance.maxValue = 50
        npDistance.value = 10

        recyclerFitness.layoutManager = LinearLayoutManager(requireContext())

        btnGenerateRoute.setOnClickListener {
            requestRecommendedRoutes(npDistance.value.toDouble())
        }

        return view
    }

    private fun requestRecommendedRoutes(distanceKm: Double) {
        progressBar.visibility = View.VISIBLE
        tvResult.text = ""

        CoroutineScope(Dispatchers.IO).launch {
            val response = try {
                api.getRecommend(
                    lat = currentLocationLat,
                    lng = currentLocationLng,
                    distance = distanceKm
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE

                if (response == null) {
                    Toast.makeText(requireContext(), "서버 오류 발생", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                // 추천된 거리 표시
                tvResult.text = "추천된 거리: ${response.recommended_distance} km"

                // 리스트 변환
                val routeList = response.routes.map { convertToRouteModel(it) }

                recyclerFitness.adapter = RouteAdapter(
                    routeList,
                    onSelected = { route ->
                        Toast.makeText(requireContext(), "${route.title} 선택됨!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun convertToRouteModel(src: RouteSummary): RouteModel {

        val pts = emptyList<LatLng>()  // polyline은 추후 상세 API에서 로딩

        return RouteModel(
            id = src.id,
            title = src.name,
            distanceKm = src.distance,
            timeMin = src.turn_count * 3,  // 임시 계산
            path = pts
        )
    }
}
