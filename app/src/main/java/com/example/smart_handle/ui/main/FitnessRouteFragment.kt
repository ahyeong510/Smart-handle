package com.example.smart_handle.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.exercise.ExerciseRouteAdapter
import com.example.smart_handle.exercise.ExerciseRouteGenerator
import com.example.smart_handle.maps.MapsActivity

class FitnessRouteFragment : Fragment() {

    private lateinit var etDistance: EditText
    private lateinit var btnGenerateFitness: Button
    private lateinit var rvRoutes: RecyclerView

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

        // 지금은 임시 현재 위치
        val currentLocation = LatLng(37.5665, 126.9780)

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
                val intent = Intent(requireContext(), MapsActivity::class.java)
                intent.putExtra("extra_dest_lat", selectedRoute.destLatLng.latitude)
                intent.putExtra("extra_dest_lng", selectedRoute.destLatLng.longitude)
                startActivity(intent)
            }
        }
    }}