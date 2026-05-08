package com.example.smart_handle.ui.main

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smart_handle.R
import com.example.smart_handle.network.RetrofitClient
import com.example.smart_handle.network.TourRecommendRequest
import com.example.smart_handle.network.TourRecommendResponse
import com.example.smart_handle.ui.fitness.FitnessRecommendResultActivity
import com.example.smart_handle.ui.fitness.FitnessRouteOption
import com.example.smart_handle.ui.fitness.RoutePointData
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TourRouteFragment : Fragment() {

    private lateinit var etRadius: EditText
    private lateinit var btnGenerate: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_tour_route, container, false)

        etRadius = view.findViewById(R.id.etRadius)
        btnGenerate = view.findViewById(R.id.btnGenerateTour)

        btnGenerate.setOnClickListener {
            requestTourRoutes()
        }

        return view
    }

    private fun requestTourRoutes() {
        val radiusKm = etRadius.text.toString().trim().toDoubleOrNull() ?: 5.0

        if (radiusKm < 3.0 || radiusKm > 10.0) {
            Toast.makeText(requireContext(), "반경은 3~10km 사이로 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        getCurrentLocation { location ->
            if (location == null) {
                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@getCurrentLocation
            }

            val request = TourRecommendRequest(
                start_lat = location.latitude,
                start_lng = location.longitude,
                radius_m = (radiusKm * 1000).toInt()
            )

            RetrofitClient.fitnessApi.recommendTourRoutes(request)
                .enqueue(object : Callback<TourRecommendResponse> {
                    override fun onResponse(
                        call: Call<TourRecommendResponse>,
                        response: Response<TourRecommendResponse>
                    ) {
                        if (!isAdded) return

                        if (!response.isSuccessful) {
                            Toast.makeText(requireContext(), "관광지 추천 요청 실패", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val routes = response.body()?.routes ?: emptyList()

                        if (routes.isEmpty()) {
                            Toast.makeText(requireContext(), "추천 관광지 경로가 없습니다.", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val options = ArrayList<FitnessRouteOption>()

                        for (route in routes) {
                            val routePoints = ArrayList<RoutePointData>()

                            for (point in route.polyline) {
                                if (point.size >= 2) {
                                    routePoints.add(
                                        RoutePointData(
                                            lat = point[0],
                                            lng = point[1]
                                        )
                                    )
                                }
                            }

                            options.add(
                                FitnessRouteOption(
                                    routeId = route.routeId,
                                    title = route.title,
                                    distanceKm = route.distanceKm,
                                    durationMin = route.durationMin,
                                    elevationGain = 0,
                                    turnCount = route.turnCount,
                                    score = route.score,
                                    routePoints = routePoints
                                )
                            )
                        }

                        val intent = Intent(requireContext(), FitnessRecommendResultActivity::class.java).apply {
                            putExtra("route_mode", "tour")
                            putExtra("fitness_routes", options)
                        }

                        startActivity(intent)
                    }

                    override fun onFailure(call: Call<TourRecommendResponse>, t: Throwable) {
                        if (!isAdded) return
                        Toast.makeText(requireContext(), "서버 연결 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(onResult: (Location?) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                onResult(location)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
}