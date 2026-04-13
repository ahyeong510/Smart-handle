package com.example.smart_handle.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.smart_handle.R
import com.example.smart_handle.network.FitnessRecommendRequest
import com.example.smart_handle.network.FitnessRecommendResponse
import com.example.smart_handle.network.RetrofitClient
import com.example.smart_handle.ui.fitness.FitnessRecommendResultActivity
import com.example.smart_handle.ui.fitness.FitnessRouteOption
import com.example.smart_handle.ui.fitness.RoutePointData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FitnessRouteFragment : Fragment() {

    private lateinit var etDistance: EditText
    private lateinit var btnGenerateFitness: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            handleGenerateClick()
        } else {
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fitness_route, container, false)

        etDistance = view.findViewById(R.id.etDistance)
        btnGenerateFitness = view.findViewById(R.id.btnGenerateFitness)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        btnGenerateFitness.setOnClickListener {
            checkPermissionAndStart()
        }

        return view
    }

    private fun checkPermissionAndStart() {
        val fineGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            handleGenerateClick()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun handleGenerateClick() {
        val distanceText = etDistance.text.toString().trim()

        if (distanceText.isEmpty()) {
            Toast.makeText(requireContext(), "목표 거리를 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetKm = distanceText.toDoubleOrNull()
        if (targetKm == null) {
            Toast.makeText(requireContext(), "숫자로 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetKm < 1 || targetKm > 50) {
            Toast.makeText(requireContext(), "목표 거리는 1~50km 사이로 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "로그인 정보가 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val fineGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Toast.makeText(requireContext(), "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location == null) {
                    Toast.makeText(requireContext(), "현재 위치를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                requestFitnessRoutes(
                    userId = user.uid,
                    location = location,
                    targetKm = targetKm
                )
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "위치 조회 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun requestFitnessRoutes(userId: String, location: Location, targetKm: Double) {
        val request = FitnessRecommendRequest(
            user_id = userId,
            start_lat = location.latitude,
            start_lng = location.longitude,
            target_km = targetKm
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
                            "서버 응답 오류: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    val routeList = response.body()?.routes ?: emptyList()
                    if (routeList.isEmpty()) {
                        Toast.makeText(requireContext(), "추천 경로가 없습니다.", Toast.LENGTH_SHORT).show()
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

                    val intent = Intent(requireContext(), FitnessRecommendResultActivity::class.java)
                    intent.putExtra("fitness_routes", options)
                    startActivity(intent)
                }

                override fun onFailure(call: Call<FitnessRecommendResponse>, t: Throwable) {
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