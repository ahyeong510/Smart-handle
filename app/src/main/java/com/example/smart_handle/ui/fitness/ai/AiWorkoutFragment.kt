package com.example.smart_handle.ui.fitness.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.ui.driving.DrivingActivity
import com.example.smart_handle.ui.fitness.model.AiRoute
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToInt

class AiWorkoutFragment : Fragment(R.layout.fragment_ai_workout) {

    companion object {
        // ⭐ 서버 IP – 기존 그대로 유지
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

            requestAiRoutes(distanceKm, loc.latitude, loc.longitude)
        }
    }

    private fun requestAiRoutes(distanceKm: Double, lat: Double, lng: Double) {
        Thread {
            try {
                val url =
                    "http://$SERVER_IP:$SERVER_PORT/recommend?distance=$distanceKm&lat=$lat&lng=$lng"

                val request = Request.Builder().url(url).build()
                val response = OkHttpClient().newCall(request).execute()

                val body = response.body()?.string()
                    ?: throw Exception("Empty response")

                val json = JSONObject(body)
                val arr = json.getJSONArray("routes")

                val routes = mutableListOf<AiRoute>()

                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)

                    val dist = o.getDouble("distance_km")
                    val duration = (dist / 15.0 * 60).roundToInt()

                    // ⭐ polyline 파싱 (이중 배열 [lng, lat])
                    val polyArr = o.getJSONArray("polyline")
                    val points = mutableListOf<LatLng>()

                    for (j in 0 until polyArr.length()) {
                        val p = polyArr.getJSONArray(j)
                        val lngP = p.getDouble(0)
                        val latP = p.getDouble(1)
                        points.add(LatLng(latP, lngP))
                    }

                    routes.add(
                        AiRoute(
                            id = o.getInt("id"),
                            distanceKm = dist,
                            durationMin = duration,
                            polyline = points
                        )
                    )
                }

                requireActivity().runOnUiThread {
                    recyclerView.adapter = AiRouteAdapter(routes) { route ->
                        val intent =
                            Intent(requireContext(), DrivingActivity::class.java)

                        intent.putExtra("ROUTE_MODE", "AI_WORKOUT")
                        intent.putExtra("ROUTE_ID", route.id)

                        // ⭐⭐ 핵심: 경로를 주행 화면으로 직접 전달
                        intent.putParcelableArrayListExtra(
                            "route_points",
                            ArrayList(route.polyline)
                        )

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
