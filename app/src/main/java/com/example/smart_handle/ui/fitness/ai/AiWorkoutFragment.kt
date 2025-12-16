package com.example.smart_handle.ui.fitness.ai

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
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.ui.driving.DrivingActivity
import com.example.smart_handle.ui.fitness.model.AiRoute
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AiWorkoutFragment : Fragment() {

    companion object {
        // 🔴 여기만 실제 PC IP로 수정
        private const val SERVER_IP = "192.168.219.118"   // 예: 192.168.0.12
        private const val SERVER_PORT = 8000
    }

    private lateinit var adapter: AiRouteAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_ai_workout, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        val distanceInput = view.findViewById<EditText>(R.id.etDistance)
        val generateBtn = view.findViewById<Button>(R.id.btnGenerateRoute)

        adapter = AiRouteAdapter { routeId ->
            startDriving(routeId)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        generateBtn.setOnClickListener {
            val km = distanceInput.text.toString().toDoubleOrNull()
            if (km == null || km <= 0) {
                Toast.makeText(requireContext(), "거리(km)를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestAiRoutes(km)
        }

        return view
    }

    // =========================
    // AI 경로 추천 요청
    // =========================
    private fun requestAiRoutes(distanceKm: Double) {
        Thread {
            try {
                // ✅ 안전한 URL 생성 (핵심)
                val httpUrl = HttpUrl.Builder()
                    .scheme("http")
                    .host(SERVER_IP)
                    .port(SERVER_PORT)
                    .addPathSegment("recommend")
                    .addQueryParameter("lat", "37.5665")
                    .addQueryParameter("lng", "126.9780")
                    .addQueryParameter("distance", distanceKm.toString())
                    .build()

                android.util.Log.e("AI_URL", httpUrl.toString())

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(httpUrl)
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code()}")
                }

                val body = response.body()?.string()
                    ?: throw RuntimeException("empty body")

                android.util.Log.e("AI_DEBUG", "response = $body")

                val json = JSONObject(body)
                val arr = json.getJSONArray("routes")

                val newRoutes = mutableListOf<AiRoute>()
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    newRoutes.add(
                        AiRoute(
                            id = r.getInt("id"),
                            distanceKm = r.getDouble("distance_km"),
                            durationMin = r.getInt("duration_min")
                        )
                    )
                }

                requireActivity().runOnUiThread {
                    adapter.submit(newRoutes)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "추천 경로 실패: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // =========================
    // 주행 시작
    // =========================
    private fun startDriving(routeId: Int) {
        val intent = Intent(requireContext(), DrivingActivity::class.java).apply {
            putExtra("ROUTE_MODE", "AI_WORKOUT")
            putExtra("ROUTE_ID", routeId)
        }
        startActivity(intent)
    }
}
