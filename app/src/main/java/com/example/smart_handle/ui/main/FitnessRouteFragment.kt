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
import com.example.smart_handle.network.FitnessRecommendRequest
import com.example.smart_handle.network.FitnessRecommendResponse
import com.example.smart_handle.network.RetrofitClient
import com.example.smart_handle.network.RideHistoryItem
import com.example.smart_handle.ui.fitness.FitnessRecommendResultActivity
import com.example.smart_handle.ui.fitness.FitnessRouteOption
import com.example.smart_handle.ui.fitness.RoutePointData
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FitnessRouteFragment : Fragment() {

    private lateinit var etDistance: EditText
    private lateinit var btnGenerateFitness: Button

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fitness_route, container, false)

        etDistance = view.findViewById(R.id.etDistance)
        btnGenerateFitness = view.findViewById(R.id.btnGenerateFitness)

        btnGenerateFitness.setOnClickListener {
            requestFitnessRoute()
        }

        return view
    }

    private fun requestFitnessRoute() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val distanceText = etDistance.text.toString().trim()
        if (distanceText.isEmpty()) {
            Toast.makeText(requireContext(), "목표 거리를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetKm = distanceText.toDoubleOrNull()
        if (targetKm == null || targetKm <= 0.0) {
            Toast.makeText(requireContext(), "올바른 거리를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        getCurrentLocation { location ->
            if (location == null) {
                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@getCurrentLocation
            }

            loadRecentRideHistory(user.uid) { rideHistory ->
                val request = FitnessRecommendRequest(
                    user_id = user.uid,
                    start_lat = location.latitude,
                    start_lng = location.longitude,
                    target_km = targetKm,
                    ride_history = rideHistory
                )

                RetrofitClient.fitnessApi.recommendLoopRoute(request)
                    .enqueue(object : Callback<FitnessRecommendResponse> {
                        override fun onResponse(
                            call: Call<FitnessRecommendResponse>,
                            response: Response<FitnessRecommendResponse>
                        ) {
                            if (!isAdded) return

                            if (!response.isSuccessful) {
                                Toast.makeText(
                                    requireContext(),
                                    "추천 요청 실패",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return
                            }

                            val routeList = response.body()?.routes ?: emptyList()
                            if (routeList.isEmpty()) {
                                Toast.makeText(
                                    requireContext(),
                                    "추천 경로가 없습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return
                            }

                            val options = ArrayList<FitnessRouteOption>()

                            for (route in routeList) {
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
                                        elevationGain = route.elevationGain,
                                        turnCount = route.turnCount,
                                        score = route.score,
                                        routePoints = routePoints
                                    )
                                )
                            }

                            val intent = Intent(
                                requireContext(),
                                FitnessRecommendResultActivity::class.java
                            ).apply {
                                putExtra("fitness_routes", options)
                            }

                            startActivity(intent)
                        }

                        override fun onFailure(
                            call: Call<FitnessRecommendResponse>,
                            t: Throwable
                        ) {
                            if (!isAdded) return
                            Toast.makeText(
                                requireContext(),
                                "서버 연결 실패: ${t.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })
            }
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

    private fun loadRecentRideHistory(
        uid: String,
        onLoaded: (List<RideHistoryItem>) -> Unit
    ) {
        firestore.collection("users")
            .document(uid)
            .collection("ride_history")
            .get()
            .addOnSuccessListener { snapshot ->

                val history = snapshot.documents
                    .sortedByDescending { doc ->
                        when (val value = doc.get("createdAt")) {
                            is Long -> value
                            is Double -> value.toLong()
                            is com.google.firebase.Timestamp -> value.seconds * 1000
                            else -> 0L
                        }
                    }
                    .take(10)
                    .mapNotNull { doc ->
                        try {
                            val elevationGain = (doc.getLong("elevationGain") ?: 0L).toInt()
                            val turnCount = (doc.getLong("turnCount") ?: 0L).toInt()
                            val durationMin = (doc.getLong("durationMin") ?: 0L).toInt()
                            val completionPercent = (doc.getLong("completionPercent") ?: 0L).toInt()
                            val satisfaction = doc.getString("satisfaction") ?: "보통"

                            RideHistoryItem(
                                elevationGain,
                                turnCount,
                                durationMin,
                                completionPercent,
                                satisfaction
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                onLoaded(history)
            }
            .addOnFailureListener {
                onLoaded(emptyList())
            }
    }
}