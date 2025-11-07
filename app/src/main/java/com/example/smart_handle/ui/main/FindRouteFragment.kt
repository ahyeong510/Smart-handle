package com.example.smart_handle.ui.main

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.mapselect.SelectLocationActivity
import com.example.smart_handle.maps.MapsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FindRouteFragment : Fragment() {

    // 지도 화면에서 선택한 목적지 좌표를 받아오는 런처
    private val selectDestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val data = res.data
            val lat = data?.getDoubleExtra("extra_dest_lat", Double.NaN) ?: Double.NaN
            val lng = data?.getDoubleExtra("extra_dest_lng", Double.NaN) ?: Double.NaN

            if (!lat.isNaN() && !lng.isNaN()) {
                val intent = Intent(requireContext(), MapsActivity::class.java).apply {
                    putExtra("extra_dest_lat", lat)
                    putExtra("extra_dest_lng", lng)
                }
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "목적지 좌표를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_find_route, container, false)

        val etEnd = view.findViewById<EditText>(R.id.etEnd)
        val btnStart = view.findViewById<Button>(R.id.btn_start_navigation)
        val btnSelect = view.findViewById<Button>(R.id.btn_select_destination)
        val btnTest = view.findViewById<Button>(R.id.btn_test_navigation)

        // 1️⃣ 목적지 검색 후 지도 이동
        btnStart.setOnClickListener {
            val query = etEnd.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(requireContext(), "목적지를 입력하거나 지도에서 선택하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val dest = withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        Geocoder(requireContext(), Locale.KOREA)
                            .getFromLocationName(query, 1)
                            ?.firstOrNull()
                    } catch (_: Exception) {
                        null
                    }
                }

                if (dest == null) {
                    Toast.makeText(
                        requireContext(),
                        "주소를 찾을 수 없습니다. 지도에서 직접 선택해 보세요.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val intent = Intent(requireContext(), MapsActivity::class.java).apply {
                    putExtra("extra_dest_lat", dest.latitude)
                    putExtra("extra_dest_lng", dest.longitude)
                }
                startActivity(intent)
            }
        }

        // 2️⃣ 지도에서 목적지 직접 선택
        btnSelect.setOnClickListener {
            val intent = Intent(requireContext(), SelectLocationActivity::class.java)
            selectDestLauncher.launch(intent)
        }

        // 3️⃣ 테스트 버튼은 숨김 처리
        btnTest?.visibility = View.GONE

        return view
    }
}
