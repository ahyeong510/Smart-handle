package com.example.smart_handle.ui.driving

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smart_handle.R
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import com.example.smart_handle.ui.ble.BluetoothManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

class DrivingActivity : AppCompatActivity(),
    BluetoothManager.Listener,
    OnMapReadyCallback {

    private lateinit var fused: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var currentLatLng: LatLng? = null

    private lateinit var turnCard: View
    private lateinit var turnIcon: ImageView
    private lateinit var turnDistance: TextView
    private lateinit var turnTypeText: TextView

    private var readyToWrite = false
    private var turnEvents: MutableList<TurnEvent> = mutableListOf()
    private var nextTurnIndex = 0

    private var repeatHandler: Handler? = null
    private var repeatRunnable: Runnable? = null
    private var isRepeating = false

    private val routePoints = mutableListOf<LatLng>()
    private var routePolyline: Polyline? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.drive_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        turnCard = findViewById(R.id.turnCard)
        turnIcon = findViewById(R.id.turnIcon)
        turnDistance = findViewById(R.id.turnDistance)
        turnTypeText = findViewById(R.id.turnTypeText)

        findViewById<Button>(R.id.btn_stop_route).setOnClickListener {
            finish()
        }

        BluetoothManager.attachListener(this)
        BluetoothManager.startScanAndConnect()

        fused = LocationServices.getFusedLocationProviderClient(this)

        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")?.let {
            turnEvents.addAll(it)
        }

        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let {
            routePoints.addAll(it)
        }

        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            startLocationTracking()
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startLocationTracking()
        }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        googleMap?.isMyLocationEnabled = true

        val req = LocationRequest.Builder(700)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return

            val here = LatLng(loc.latitude, loc.longitude)
            currentLatLng = here

            checkTurnEvent(here)

            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(here, 17f)
            )
        }
    }

    // ================================
    // ⭐ 최종: 거리 기반 진동 + 턴 필터링
    // ================================
    private fun checkTurnEvent(current: LatLng) {
        if (nextTurnIndex >= turnEvents.size) {
            turnCard.visibility = View.GONE
            return
        }

        val target = turnEvents[nextTurnIndex]

        // 거리 계산 + 반경 보정 (6m 허용)
        val rawDist = distance(current, target.location)
        val dist = (rawDist - 6f).coerceAtLeast(0f)

        turnCard.visibility = View.VISIBLE
        turnDistance.text = "${dist.toInt()}m 후"

        when (target.type) {
            TurnType.LEFT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_left)
                turnTypeText.text = "좌회전"
            }
            TurnType.RIGHT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_right)
                turnTypeText.text = "우회전"
            }
            else -> {}
        }

        // 🛑 시작위치 근처 가짜 턴 제거
        if (nextTurnIndex == 0 && dist < 20f) {
            nextTurnIndex++
            return
        }

        // 100m 이상 → 진동 없음
        if (dist > 100f) {
            stopRepeating()
            return
        }

        // 100~50m → 4초 간격 진동
        if (dist in 50f..100f) {
            startRepeatingInterval(target.type, 4000)
            return
        }

        // 50~10m → 1.2초 간격 진동
        if (dist in 10f..50f) {
            startRepeatingInterval(target.type, 1200)
            return
        }

        // 10m 이하 → 턴 도달 → 다음 턴
        if (dist <= 10f) {
            stopRepeating()
            nextTurnIndex++
            return
        }
    }

    private fun distance(a: LatLng, b: LatLng): Float {
        val arr = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, arr)
        return arr[0]
    }

    private fun sendVibration(type: TurnType) {
        if (!readyToWrite) return

        when (type) {
            TurnType.LEFT -> BluetoothManager.sendText("L")
            TurnType.RIGHT -> BluetoothManager.sendText("R")
            else -> {}
        }
    }

    private fun startRepeatingInterval(type: TurnType, intervalMs: Long) {
        if (isRepeating) return

        repeatHandler = Handler(Looper.getMainLooper())
        repeatRunnable = object : Runnable {
            override fun run() {
                sendVibration(type)
                repeatHandler?.postDelayed(this, intervalMs)
            }
        }

        isRepeating = true
        repeatHandler?.post(repeatRunnable!!)
    }

    private fun stopRepeating() {
        repeatRunnable?.let { repeatHandler?.removeCallbacks(it) }
        isRepeating = false
    }

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothManager.attachListener(null)
        stopRepeating()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
        }

        if (routePoints.isNotEmpty()) {
            val polylineOptions = PolylineOptions()
                .addAll(routePoints)
                .width(10f)
                .color(0xFF2196F3.toInt())

            routePolyline = googleMap?.addPolyline(polylineOptions)

            googleMap?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(routePoints.first(), 16f)
            )
        }
    }

    override fun onLog(msg: String) {}
}
