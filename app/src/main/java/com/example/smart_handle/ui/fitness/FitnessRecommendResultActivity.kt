package com.example.smart_handle.ui.fitness

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R
import com.example.smart_handle.database.RouteRepository
import com.example.smart_handle.maps.FitnessTurnExtractor
import com.example.smart_handle.ui.driving.DrivingActivity
import com.google.android.gms.maps.model.LatLng

class FitnessRecommendResultActivity : AppCompatActivity() {

    private lateinit var tvRoute1: TextView
    private lateinit var tvRoute2: TextView
    private lateinit var tvRoute3: TextView

    private lateinit var btnRoute1: Button
    private lateinit var btnRoute2: Button
    private lateinit var btnRoute3: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fitness_recommend_result)

        tvRoute1 = findViewById(R.id.tvRoute1)
        tvRoute2 = findViewById(R.id.tvRoute2)
        tvRoute3 = findViewById(R.id.tvRoute3)

        btnRoute1 = findViewById(R.id.btnRoute1)
        btnRoute2 = findViewById(R.id.btnRoute2)
        btnRoute3 = findViewById(R.id.btnRoute3)

        val routes = getRouteListFromIntent()

        if (routes.isEmpty()) {
            Toast.makeText(this, "추천 경로를 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRouteViews(routes)
    }

    private fun getRouteListFromIntent(): ArrayList<FitnessRouteOption> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            @Suppress("UNCHECKED_CAST")
            intent.getSerializableExtra("fitness_routes", ArrayList::class.java) as? ArrayList<FitnessRouteOption>
                ?: arrayListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("fitness_routes") as? ArrayList<FitnessRouteOption>
                ?: arrayListOf()
        }
    }

    private fun setupRouteViews(routes: ArrayList<FitnessRouteOption>) {
        if (routes.size >= 1) {
            bindRoute(tvRoute1, routes[0])
            btnRoute1.isEnabled = true
            btnRoute1.setOnClickListener { onRouteSelected(routes[0]) }
        } else {
            tvRoute1.text = "추천 경로 없음"
            btnRoute1.isEnabled = false
        }

        if (routes.size >= 2) {
            bindRoute(tvRoute2, routes[1])
            btnRoute2.isEnabled = true
            btnRoute2.setOnClickListener { onRouteSelected(routes[1]) }
        } else {
            tvRoute2.text = "추천 경로 없음"
            btnRoute2.isEnabled = false
        }

        if (routes.size >= 3) {
            bindRoute(tvRoute3, routes[2])
            btnRoute3.isEnabled = true
            btnRoute3.setOnClickListener { onRouteSelected(routes[2]) }
        } else {
            tvRoute3.text = "추천 경로 없음"
            btnRoute3.isEnabled = false
        }
    }

    private fun bindRoute(textView: TextView, route: FitnessRouteOption) {
        textView.text = """
            ${route.title}
            거리: ${route.distanceKm} km
            예상 시간: ${route.durationMin}분
            상승고도: ${route.elevationGain}m
            회전 수: ${route.turnCount}
            적합도 점수: ${route.score}
        """.trimIndent()
    }

    private fun onRouteSelected(route: FitnessRouteOption) {
        if (route.routePoints.isEmpty()) {
            Toast.makeText(this, "경로 좌표가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val latLngPoints = ArrayList<LatLng>()
        for (point in route.routePoints) {
            latLngPoints.add(LatLng(point.lat, point.lng))
        }

        if (latLngPoints.size < 2) {
            Toast.makeText(this, "주행에 필요한 경로 데이터가 부족합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val turnEvents = FitnessTurnExtractor.extractTurnEvents(latLngPoints)

        val routeMode = intent.getStringExtra("route_mode") ?: "fitness"

        val drivingIntent = Intent(this, DrivingActivity::class.java).apply {
            putExtra("routeType", routeMode)
            putExtra("routeId", route.routeId)
            putExtra("distanceKm", route.distanceKm)
            putExtra("durationMin", route.durationMin)
            putExtra("elevationGain", route.elevationGain)
            putExtra("turnCount", route.turnCount)
            putParcelableArrayListExtra("turn_events", turnEvents)
            putParcelableArrayListExtra("route_points", latLngPoints)
            putExtra("tour_places", route.tourPlaces)
        }

        startActivity(drivingIntent)
    }
}