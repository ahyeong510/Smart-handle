package com.example.smart_handle.mapselect

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.smart_handle.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class SelectLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var fused: FusedLocationProviderClient
    private var currentLatLng: LatLng? = null   // 🔹 현위치 저장용

    // 🔥 위치 권한 요청 런처
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            enableMyLocation()
        } else {
            Toast.makeText(this, "현재 위치를 사용하려면 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_location)

        fused = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 👇 현위치 버튼: 눌렀을 때 내 위치로 카메라 이동
        findViewById<Button>(R.id.btn_my_location).setOnClickListener {
            moveCameraToCurrentLocation()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // 기본 제공 “내 위치” 버튼은 쓰지 않고(원하면 true 로 바꿔도 됨)
        googleMap?.uiSettings?.isMyLocationButtonEnabled = false

        // 권한 확인 후 현재 위치 활성화
        enableMyLocation()

        // 👇 지도 클릭 시 목적지 선택(기존 로직 유지)
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

    // 🔹 위치 권한 확인 + 파란 점(현위치) 켜고, 카메라도 현위치로 이동
    private fun enableMyLocation() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION

        val map = googleMap ?: return

        if (ActivityCompat.checkSelfPermission(this, fine) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, coarse) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(fine, coarse))
            return
        }

        // 파란 점(내 위치) 표시
        map.isMyLocationEnabled = true

        // 마지막 위치 한 번 가져와서 카메라 이동
        fused.lastLocation.addOnSuccessListener { loc: Location? ->
            currentLatLng = if (loc != null) {
                LatLng(loc.latitude, loc.longitude)
            } else {
                // 실패하면 기본값: 서울 시청
                LatLng(37.5665, 126.9780)
            }

            currentLatLng?.let {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
        }
    }

    // 🔹 “현위치” 버튼 눌렀을 때 카메라 이동
    private fun moveCameraToCurrentLocation() {
        val map = googleMap ?: return

        // 이미 받아 온 위치가 있으면 그쪽으로
        currentLatLng?.let {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
            return
        }

        // 없으면 한 번 더 가져오기
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION

        if (ActivityCompat.checkSelfPermission(this, fine) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, coarse) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(fine, coarse))
            return
        }

        fused.lastLocation.addOnSuccessListener { loc: Location? ->
            currentLatLng = if (loc != null) {
                LatLng(loc.latitude, loc.longitude)
            } else {
                LatLng(37.5665, 126.9780)
            }

            currentLatLng?.let {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f))
            }
        }
    }
}
