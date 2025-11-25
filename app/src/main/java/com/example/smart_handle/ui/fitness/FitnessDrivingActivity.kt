package com.example.smart_handle.ui.fitness

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.databinding.ActivityDrivingFitnessBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions

class FitnessDrivingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrivingFitnessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrivingFitnessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 인텐트에서 경로 좌표 리스트 받기 (나중에 /route/{id} 결과를 여기로 넘길 예정)
        val coords = intent.getParcelableArrayListExtra<LatLng>("polyline") ?: return

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { googleMap ->

            val polyline = PolylineOptions()
                .addAll(coords)
                .color(Color.parseColor("#4A63FF"))
                .width(12f)

            googleMap.addPolyline(polyline)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coords[0], 15f))

            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isRotateGesturesEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}
