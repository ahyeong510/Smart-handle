package com.example.smart_handle.maps

import android.content.Intent
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.ui.driving.DrivingActivity
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

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            enableMyLocationAndProceed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // 목적지 좌표 받기
        val dLat = intent.getDoubleExtra("extra_dest_lat", Double.NaN)
        val dLng = intent.getDoubleExtra("extra_dest_lng", Double.NaN)
        if (!dLat.isNaN() && !dLng.isNaN()) {
            destLatLng = LatLng(dLat, dLng)
        }

        fused = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        enableMyLocationAndProceed()
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationAndProceed() {
        val m = googleMap ?: return

        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // 내 위치 표시
        m.isMyLocationEnabled = true

        // 현재 위치 가져오기
        fused.lastLocation.addOnSuccessListener { loc ->
            currentLatLng = loc?.let { LatLng(it.latitude, it.longitude) }
                ?: LatLng(37.5665, 126.9780)

            currentLatLng?.let {
                m.addMarker(
                    MarkerOptions()
                        .position(it)
                        .title("현재 위치")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            }

            destLatLng?.let {
                m.addMarker(
                    MarkerOptions()
                        .position(it)
                        .title("목적지")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
            }

            val focus = destLatLng ?: currentLatLng
            focus?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 14f)) }

            val origin = currentLatLng
            val dest = destLatLng

            if (origin != null && dest != null) {
                fetchAndDrawRoute(origin, dest)
            } else {
                Toast.makeText(this, "출발/도착 좌표가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 🔥 경로 + 턴 이벤트 모두 요청 */
    private fun fetchAndDrawRoute(origin: LatLng, dest: LatLng) {
        lifecycleScope.launch {
            try {
                // Kakao Directions 요청
                val route = MapDirectionHelper.getRoute(
                    startLat = origin.latitude,
                    startLng = origin.longitude,
                    endLat = dest.latitude,
                    endLng = dest.longitude
                )

                // Polyline 표시
                if (route.points.size >= 2) {
                    drawPolyline(route.points)
                }

                // 턴 이벤트 있으면 DrivingActivity로 넘기기
                if (route.turnEvents.isNotEmpty()) {
                    val intent =
                        Intent(this@MapsActivity, DrivingActivity::class.java).apply {
                            putParcelableArrayListExtra(
                                "turn_events",
                                ArrayList(route.turnEvents)
                            )
                        }
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this@MapsActivity,
                        "⚠ 턴 이벤트가 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MapsActivity,
                    "경로 요청 실패: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
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
                .color(0xFF2196F3.toInt())
        )
    }
}
