package com.example.smart_handle.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var fused: FusedLocationProviderClient

    private var destLatLng: LatLng? = null
    private var currentLatLng: LatLng? = null
    private var routePolyline: Polyline? = null

    // 위치 권한 런처
    private val locationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            enableMyLocationAndProceed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // 1) 인텐트에서 목적지 좌표 받기
        val dLat = intent.getDoubleExtra("extra_dest_lat", Double.NaN)
        val dLng = intent.getDoubleExtra("extra_dest_lng", Double.NaN)
        if (!dLat.isNaN() && !dLng.isNaN()) {
            destLatLng = LatLng(dLat, dLng)
        }

        // 2) 지도 준비
        fused = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        enableMyLocationAndProceed()
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationAndProceed() {
        val m = googleMap ?: return

        // 권한 체크 & 요청
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // 내 위치 표시
        m.isMyLocationEnabled = true

        // 3) 현재 위치 가져오기
        fused.lastLocation.addOnSuccessListener { loc: Location? ->
            currentLatLng = loc?.let { LatLng(it.latitude, it.longitude) }
                ?: LatLng(37.5665, 126.9780) // 실패 시 서울 시청으로 fallback

            // 4) 출발/도착 마커
            currentLatLng?.let { origin ->
                m.addMarker(
                    MarkerOptions()
                        .position(origin)
                        .title("현재 위치")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            }
            destLatLng?.let { dest ->
                m.addMarker(
                    MarkerOptions()
                        .position(dest)
                        .title("목적지")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }

            // 5) 카메라 이동
            val focus = destLatLng ?: currentLatLng
            focus?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 14f)) }

            // 6) 경로 그리기
            val origin = currentLatLng
            val dest = destLatLng
            if (origin != null && dest != null) {
                fetchAndDrawRoute(origin, dest)
            } else {
                Toast.makeText(this, "출발/도착 좌표가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Directions API로 polyline 포인트를 받아서 지도에 그린다.
     * MapDirectionHelper.getRoutePoints(...)를 호출(코루틴)하여 결과를 반영.
     */
    private fun fetchAndDrawRoute(origin: LatLng, dest: LatLng) {
        Log.d("RouteDebug", "origin=${origin.latitude},${origin.longitude} dest=${dest.latitude},${dest.longitude}")
        lifecycleScope.launch {
            try {
                // ✅ Kakao 기준: Helper 내부에서 (경도,위도) 순서로 URL을 구성해야 함
                val points = MapDirectionHelper.getRoutePoints(
                    startLat = origin.latitude,
                    startLng = origin.longitude,
                    endLat   = dest.latitude,
                    endLng   = dest.longitude
                )
                Log.d("RouteDebug", "points-size=${points.size}")

                if (points.size >= 2) {
                    drawPolyline(points)
                } else {
                    Toast.makeText(
                        this@MapsActivity,
                        "경로 포인트 없음: API 키/요청/좌표를 확인하세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                    // 필요시, 아래 직선 대체를 잠깐 켜고 확인
                    // drawPolyline(listOf(origin, dest))
                }
            } catch (e: Exception) {
                Log.e("RouteDebug", "getRoutePoints error", e)
                Toast.makeText(this@MapsActivity, "경로 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                // 필요시 임시 직선
                // drawPolyline(listOf(origin, dest))
            }
        }
    }

    private fun drawPolyline(points: List<LatLng>) {
        val m = googleMap ?: return
        routePolyline?.remove()
        routePolyline = m.addPolyline(
            PolylineOptions()
                .addAll(points)
                .width(10f)
                .color(0xFF2196F3.toInt()) // 파란색 라인
        )
    }
}
