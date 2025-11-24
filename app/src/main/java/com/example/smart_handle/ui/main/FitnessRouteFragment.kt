package com.example.smart_handle.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_handle.R
import com.example.smart_handle.network.models.LatLngDto
import com.example.smart_handle.network.models.RecommendResponse
import com.example.smart_handle.network.models.RouteSummary
import com.example.smart_handle.ui.fitness.RouteAdapter
import com.example.smart_handle.ui.fitness.RouteModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

class FitnessRouteFragment : Fragment() {

    private val viewModel: FitnessRouteViewModel by viewModels()

    private lateinit var etDistance: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResult: TextView
    private lateinit var recyclerFitness: RecyclerView

    private var currentRoutes: List<RouteModel> = emptyList()
    private var selectedRoute: RouteModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_fitness_route, container, false)

        etDistance = view.findViewById(R.id.etDistance)
        btnGenerate = view.findViewById(R.id.btnGenerateRoute)
        progressBar = view.findViewById(R.id.progressBar)
        tvResult = view.findViewById(R.id.tvResult)
        recyclerFitness = view.findViewById(R.id.recyclerFitness)

        recyclerFitness.layoutManager = LinearLayoutManager(requireContext())

        setupDistanceFormatter()
        setupButton()

        return view
    }

    private fun setupDistanceFormatter() {
        etDistance.addTextChangedListener(object : TextWatcher {
            private var editing = false

            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (editing) return
                editing = true

                var raw = s.toString().replace("km", "").trim()
                raw = raw.filter { it.isDigit() }

                if (raw.isNotEmpty()) {
                    etDistance.setText("$raw km")
                    etDistance.setSelection(etDistance.text.length - 3)
                }

                editing = false
            }
        })
    }

    private fun setupButton() {
        btnGenerate.setOnClickListener {
            val raw = etDistance.text.toString().replace("km", "").trim()
            val distanceKm = raw.toDoubleOrNull() ?: return@setOnClickListener

            requestRecommendRoutes(distanceKm)
        }
    }

    private fun requestRecommendRoutes(distanceKm: Double) {
        // 현재 위치 값을 넣는다면 여기서 GPS 값 불러오면 됨
        val currentLat = 37.5665       // 테스트용, 나중에 실제 GPS로 변경
        val currentLng = 126.9780

        progressBar.visibility = View.VISIBLE
        tvResult.text = ""
        recyclerFitness.adapter = null
        selectedRoute = null

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadRecommend(currentLat, currentLng, distanceKm)

            val res: RecommendResponse? = viewModel.recommendResult.value
            progressBar.visibility = View.GONE

            if (res == null) {
                tvResult.text = "🚫 추천 경로를 불러올 수 없습니다."
                return@launch
            }

            // AI 추천 ID + 목록 출력
            val sb = StringBuilder().apply {
                append("AI 추천 경로 3개\n\n")
                res.routes.forEach { r ->
                    append("• ${r.name} (${r.distance} km)\n")
                }
                append("\nAI 추천 ID: ${res.recommended_route_id}")
            }
            tvResult.text = sb.toString()

            // 리스트 변환
            currentRoutes = res.routes.map { convertToRouteModel(it) }

            recyclerFitness.adapter = RouteAdapter(currentRoutes) { selected ->
                selectedRoute = selected
                // TODO: 주행 화면 연결
            }
        }
    }

    private fun convertToRouteModel(src: RouteSummary): RouteModel {

        // ⭐ 서버가 path 리스트를 안 주기 때문에 빈 리스트로 생성
        val pts = emptyList<LatLng>()

        return RouteModel(
            id = src.id,
            title = src.name,
            distanceKm = src.distance,
            timeMin = src.turn_count,       // ⭐ 서버에 duration 정보 없음 → 일단 turn_count 사용
            level = src.path_point_count.toInt(),   // ⭐ difficulty 정보 없음 → 임시로 path_point_count 사용
            path = pts                      // ⭐ 좌표 데이터 없음 → 빈 리스트
        )
    }

}
