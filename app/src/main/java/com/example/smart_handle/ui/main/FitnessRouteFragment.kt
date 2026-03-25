package com.example.smart_handle.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.data.AppDatabase
import com.example.smart_handle.data.ExerciseRouteLogEntity
import com.example.smart_handle.exercise.ExerciseRouteAdapter
import com.example.smart_handle.exercise.ExerciseRouteGenerator
import com.example.smart_handle.maps.MapsActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

class FitnessRouteFragment : Fragment() {

    private lateinit var etDistance: EditText
    private lateinit var btnGenerateFitness: Button
    private lateinit var rvRoutes: RecyclerView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fitness_route, container, false)

        etDistance = view.findViewById(R.id.etDistance)
        btnGenerateFitness = view.findViewById(R.id.btnGenerateFitness)
        rvRoutes = view.findViewById(R.id.rvRoutes)

        rvRoutes.layoutManager = LinearLayoutManager(requireContext())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        db = AppDatabase.getDatabase(requireContext())

        btnGenerateFitness.setOnClickListener {
            generateRouteCandidates()
        }

        return view
    }

    private fun generateRouteCandidates() {
        val input = etDistance.text.toString().trim()

        if (input.isEmpty()) {
            Toast.makeText(requireContext(), "목표 거리를 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetDistance = input.toDoubleOrNull()
        if (targetDistance == null || targetDistance <= 0.0) {
            Toast.makeText(requireContext(), "올바른 거리 값을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        getCurrentLocation { currentLocation ->

            if (currentLocation == null) {
                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@getCurrentLocation
            }

            viewLifecycleOwner.lifecycleScope.launch {

                val candidates = ExerciseRouteGenerator.generateRoutes(
                    startLatLng = currentLocation,
                    targetDistanceKm = targetDistance
                )

                if (candidates.isEmpty()) {
                    Toast.makeText(requireContext(), "경로를 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                rvRoutes.adapter = ExerciseRouteAdapter(candidates) { selectedRoute ->

                    viewLifecycleOwner.lifecycleScope.launch {

                        val logId = db.exerciseRouteLogDao().insertLog(
                            ExerciseRouteLogEntity(
                                targetDistance = targetDistance,
                                selectedType = selectedRoute.title,
                                distanceKm = selectedRoute.distanceKm,
                                estimatedTimeMin = selectedRoute.estimatedTimeMin,
                                turnCount = selectedRoute.turnCount,
                                timestamp = System.currentTimeMillis()
                            )
                        )

                        val intent = Intent(requireContext(), MapsActivity::class.java)
                        intent.putExtra("extra_dest_lat", selectedRoute.destLatLng.latitude)
                        intent.putExtra("extra_dest_lng", selectedRoute.destLatLng.longitude)
                        intent.putExtra("exercise_log_id", logId.toInt())
                        intent.putExtra("expected_distance_km", selectedRoute.distanceKm)

                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun getCurrentLocation(onResult: (LatLng?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onResult(null)
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onResult(LatLng(location.latitude, location.longitude))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
}