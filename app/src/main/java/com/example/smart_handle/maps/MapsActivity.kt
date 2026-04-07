package com.example.smart_handle.maps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.ui.driving.DrivingActivity
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
    private lateinit var fused: com.google.android.gms.location.FusedLocationProviderClient

    private var destLatLng: LatLng? = null
    private var currentLatLng: LatLng? = null
    private var routePolyline: Polyline? = null
    private var cachedRoutePoints: List<LatLng> = emptyList()

    // 경로의 턴 정보 저장
    private var cachedTurnEvents: List<TurnEvent> = emptyList()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) enableMyLocationAndProceed()
        else Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fused = LocationServices.getFusedLocationProviderClient(this)

        val lat = intent.getDoubleExtra("extra_dest_lat", Double.NaN)
        val lng = intent.getDoubleExtra("extra_dest_lng", Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) {
            destLatLng = LatLng(lat, lng)
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment)
                as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<Button>(R.id.btnStartDrive).setOnClickListener {
            if (cachedTurnEvents.isEmpty()) {
                Toast.makeText(this, "경로가 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, DrivingActivity::class.java)
            intent.putExtra("routeType", "navigation")
            intent.putParcelableArrayListExtra(
                "turn_events",
                ArrayList(cachedTurnEvents)
            )
            intent.putParcelableArrayListExtra(
                "route_points",
                ArrayList(cachedRoutePoints)
            )

            startActivity(intent)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        enableMyLocationAndProceed()
    }

    private fun enableMyLocationAndProceed() {
        val map = googleMap ?: return

        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION

        if (ActivityCompat.checkSelfPermission(this, fine) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, coarse) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(fine, coarse))
            return
        }

        map.isMyLocationEnabled = true

        fused.lastLocation.addOnSuccessListener { loc: Location? ->
            if (loc == null) {
                currentLatLng = LatLng(37.5665, 126.9780)
            } else {
                currentLatLng = LatLng(loc.latitude, loc.longitude)
            }

            currentLatLng?.let { drawCurrentLocation(it) }
            destLatLng?.let { drawDestination(it) }

            val origin = currentLatLng
            val dest = destLatLng
            if (origin != null && dest != null) {
                fetchAndDrawRoute(origin, dest)
            }
        }
    }

    private fun drawCurrentLocation(pos: LatLng) {
        googleMap?.addMarker(
            MarkerOptions()
                .position(pos)
                .title("현재 위치")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14f))
    }

    private fun drawDestination(dest: LatLng) {
        googleMap?.addMarker(
            MarkerOptions()
                .position(dest)
                .title("목적지")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(dest, 14f))
    }

    private fun fetchAndDrawRoute(origin: LatLng, dest: LatLng) {
        lifecycleScope.launch {
            val route = MapDirectionHelper.getRoute(
                startLat = origin.latitude,
                startLng = origin.longitude,
                endLat = dest.latitude,
                endLng = dest.longitude
            )

            drawPolyline(route.points)

            cachedRoutePoints = route.points
            cachedTurnEvents = route.turnEvents

            if (cachedTurnEvents.isEmpty()) {
                Toast.makeText(this@MapsActivity, "턴 이벤트가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawPolyline(points: List<LatLng>) {
        val map = googleMap ?: return
        routePolyline?.remove()

        if (points.isEmpty()) return

        val options = PolylineOptions()
            .addAll(points)
            .width(10f)
            .color(0xFF2196F3.toInt())

        routePolyline = map.addPolyline(options)
    }
}