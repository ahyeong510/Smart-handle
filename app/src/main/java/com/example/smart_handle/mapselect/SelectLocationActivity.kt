package com.example.smart_handle.mapselect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class SelectLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_location)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // 초기 위치: 서울 시청 근처
        val seoul = LatLng(37.5665, 126.9780)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(seoul, 14f))

        // 클릭 시 목적지 마커 추가하고 좌표 반환
        googleMap?.setOnMapClickListener { latLng ->
            googleMap?.clear()
            googleMap?.addMarker(MarkerOptions().position(latLng).title("선택한 목적지"))

            val data = Intent().apply {
                putExtra("extra_dest_lat", latLng.latitude)
                putExtra("extra_dest_lng", latLng.longitude)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }
}
