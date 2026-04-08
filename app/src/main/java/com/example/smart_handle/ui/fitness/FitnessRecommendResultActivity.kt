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
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import com.example.smart_handle.ml.FeatureExtractor
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

        if (routes.size < 3) {
            Toast.makeText(this, "추천 경로 데이터가 부족합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindRoute(tvRoute1, routes[0])
        bindRoute(tvRoute2, routes[1])
        bindRoute(tvRoute3, routes[2])

        btnRoute1.setOnClickListener { onRouteSelected(routes[0]) }
        btnRoute2.setOnClickListener { onRouteSelected(routes[1]) }
        btnRoute3.setOnClickListener { onRouteSelected(routes[2]) }
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

    private fun bindRoute(textView: TextView, route: FitnessRouteOption) {
        textView.text = """
            ${route.title}
            거리: ${route.distanceKm} km
            예상 시간: ${route.durationMin}분
            상승고도: ${route.elevationGain}m
            혼잡도: ${route.congestionText}
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

        val turnEvents = ArrayList<TurnEvent>()
        for (i in 1 until latLngPoints.size - 1) {
            turnEvents.add(
                TurnEvent(
                    location = latLngPoints[i],
                    type = TurnType.STRAIGHT
                )
            )
        }

        val slope = route.elevationGain.toDouble()

        val congestion = when (route.congestionText) {
            "낮음" -> 0.2
            "중간" -> 0.5
            else -> 0.8
        }

        val duration = route.durationMin.toDouble()
        val turnCount = FeatureExtractor.calculateTurnCount(latLngPoints)

        val repo = RouteRepository(this)
        repo.insertRoute(slope, congestion, turnCount, duration, 1)

        val intent = Intent(this, FitnessDrivingActivity::class.java)

        intent.putExtra("routeType", "fitness")
        intent.putExtra("routeId", route.routeId)
        intent.putExtra("distanceKm", route.distanceKm)
        intent.putExtra("durationMin", route.durationMin)
        intent.putExtra("elevationGain", route.elevationGain)
        intent.putExtra("congestionText", route.congestionText)
        intent.putExtra("turnCount", turnCount)

        intent.putParcelableArrayListExtra("turn_events", turnEvents)
        intent.putParcelableArrayListExtra("route_points", latLngPoints)

        startActivity(intent)
    }
}