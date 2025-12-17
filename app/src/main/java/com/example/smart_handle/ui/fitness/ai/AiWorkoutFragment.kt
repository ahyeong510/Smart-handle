package com.example.smart_handle.ui.fitness.ai

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.ui.fitness.model.AiRoute
import com.google.android.gms.location.LocationServices
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToInt

class AiWorkoutFragment : Fragment(R.layout.fragment_ai_workout) {

    companion object {
        private const val SERVER_IP = "172.16.169.48"
        private const val SERVER_PORT = 8000
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputDistance: EditText
    private lateinit var btnGenerate: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.aiRouteRecycler)
        inputDistance = view.findViewById(R.id.edit_distance)
        btnGenerate = view.findViewById(R.id.btn_generate_ai_route)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        btnGenerate.setOnClickListener {
            val km = inputDistance.text.toString().toDoubleOrNull()
            if (km == null || km <= 0) {
                Toast.makeText(requireContext(), "거리 입력 오류", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            requestAiRoutesWithCurrentLocation(km)
        }
    }

    private fun requestAiRoutesWithCurrentLocation(distanceKm: Double) {
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())

        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc == null) {
                Toast.makeText(requireContext(), "현재 위치를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val lat = loc.latitude
            val lng = loc.longitude

            requestAiRoutes(distanceKm, lat, lng)
        }
    }

    private fun requestAiRoutes(distanceKm: Double, lat: Double, lng: Double) {
        Thread {
            try {
                val url =
                    "http://$SERVER_IP:$SERVER_PORT/recommend?distance=$distanceKm&lat=$lat&lng=$lng"

                val request = Request.Builder().url(url).build()
                val response = OkHttpClient().newCall(request).execute()

                val bodyString = response.body()?.string()
                    ?: throw Exception("Empty response")

                val json = JSONObject(bodyString)
                val arr = json.getJSONArray("routes")

                val routes = mutableListOf<AiRoute>()

                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val dist = o.getDouble("distance_km")
                    val duration = (dist / 15.0 * 60).roundToInt()

                    routes.add(
                        AiRoute(
                            id = o.getInt("id"),
                            distanceKm = dist,
                            durationMin = duration
                        )
                    )
                }

                requireActivity().runOnUiThread {
                    recyclerView.adapter = AiRouteAdapter(routes) { route ->
                        val intent =
                            android.content.Intent(requireContext(), com.example.smart_handle.ui.driving.DrivingActivity::class.java)
                        intent.putExtra("ROUTE_MODE", "AI_WORKOUT")
                        intent.putExtra("ROUTE_ID", route.id)
                        startActivity(intent)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "추천 경로 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
