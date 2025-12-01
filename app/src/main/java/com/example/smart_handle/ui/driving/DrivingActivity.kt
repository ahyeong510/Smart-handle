package com.example.smart_handle.ui.driving

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R
import com.example.smart_handle.ui.ble.BluetoothManager
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

class DrivingActivity : AppCompatActivity(), BluetoothManager.Listener, OnMapReadyCallback {

    private lateinit var turnDistanceText: TextView
    private lateinit var turnTypeText: TextView
    private lateinit var turnIcon: ImageView
    private lateinit var stopButton: Button

    private var mMap: GoogleMap? = null
    private var readyToWrite = false

    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val turnEvents = mutableListOf<TurnEvent>()
    private var currentIndex = 0

    private var routePoints: List<LatLng> = emptyList()
    private var destLatLng: LatLng? = null

    private var hasRightTurn = false

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private var arrivalNotified = false
    private var enteredStraightMode = false
    private var arrivalVibrationActive = false   // ⭐ 도착 구간 진동 ON/OFF 상태

    private var lastCameraUpdate = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        turnDistanceText = findViewById(R.id.turnDistance)
        turnTypeText = findViewById(R.id.turnTypeText)
        turnIcon = findViewById(R.id.turnIcon)
        stopButton = findViewById(R.id.btn_stop_route)

        stopButton.setOnClickListener {
            stopVibration()
            finish()
        }

        fusedLocation = LocationServices.getFusedLocationProviderClient(this)

        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")?.let { turnEvents.addAll(it) }
        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let { routePoints = it }

        destLatLng = routePoints.lastOrNull()
        hasRightTurn = turnEvents.any { it.type == TurnType.RIGHT }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.drive_map)
                as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupBackPress()
        setupBluetooth()
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // 내 위치 아이콘 표시
        mMap?.isMyLocationEnabled = true

        if (routePoints.isNotEmpty()) {
            val poly = PolylineOptions()
                .addAll(routePoints)
                .color(Color.BLUE)
                .width(12f)

            mMap?.addPolyline(poly)
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints[0], 16f))
        }
    }

    override fun onResume() {
        super.onResume()
        BluetoothManager.attachListener(this)
    }

    override fun onPause() {
        super.onPause()
        BluetoothManager.attachListener(null)
    }

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
    }

    override fun onStateChanged(connected: Boolean, deviceName: String?) {
        if (!connected) stopVibration()
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    stopVibration()
                    finish()
                }
            })
    }

    private fun setupBluetooth() {
        readyToWrite = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val last = result.lastLocation ?: return
                val my = LatLng(last.latitude, last.longitude)
                handleTurn(my)
                updateCamera(my)
            }
        }

        fusedLocation.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun updateCamera(pos: LatLng) {
        val now = System.currentTimeMillis()
        if (now - lastCameraUpdate < 300) return
        lastCameraUpdate = now

        mMap?.moveCamera(CameraUpdateFactory.newLatLng(pos))
    }

    private fun handleTurn(my: LatLng) {

        val dest = destLatLng ?: return
        val snapDist = getPolylineSnapDistance(my)

        // -------------------------------------------------------
        // 📌 마지막 턴 이후 → 직진/도착 안내 구간
        // -------------------------------------------------------
        if (currentIndex >= turnEvents.size) {

            val distDest = distance(my, dest).toInt()
            turnDistanceText.text = "${distDest}m 후"
            turnTypeText.text = "직진"

            // 직진 구간 처음 진입 시 → B 1회 전송
            if (!enteredStraightMode) {
                BluetoothManager.sendText("B")
                enteredStraightMode = true
            }

            // ⭐ 도착 70~20m: 5초 주기 B 반복 진동 (구간 진입 시 한 번만 시작)
            if (distDest in 20..70) {
                if (!arrivalVibrationActive) {
                    startArrivalVibrationRepeated()
                    arrivalVibrationActive = true
                }
            } else {
                // ⭐ 70m 밖으로 나가거나 20m 아래로 내려가면 → 반복진동 종료
                if (arrivalVibrationActive) {
                    stopVibration()
                    arrivalVibrationActive = false
                }
            }

            // ⭐ 도착 안내 (기존 조건 유지)
            if ((distDest < 30 || snapDist < 12) && !arrivalNotified) {
                arrivalNotified = true
                stopVibration()
                Toast.makeText(this, "목적지에 도착했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }

            return
        }

        // -------------------------------------------------------
        // 📌 일반 턴 안내
        // -------------------------------------------------------
        val target = turnEvents[currentIndex]
        val dist = distance(my, target.location).toInt()

        turnDistanceText.text = "${dist}m 후"
        turnTypeText.text = when (target.type) {
            TurnType.LEFT -> "좌회전"
            TurnType.RIGHT -> "우회전"
            TurnType.STRAIGHT -> "직진"
        }

        // 100~50m: 4초 간격
        if (!target.trigger50 && dist in 50..120) {
            target.trigger50 = true
            startRepeating(type = target.type, interval = 4000)
            return
        }

        // 50~20m: 1.2초 간격
        if (!target.trigger25 && dist in 20..50) {
            target.trigger25 = true
            startRepeating(type = target.type, interval = 1200)
            return
        }

        // 턴 완료 (20m 이하)
        if (dist < 20) {
            stopVibration()
            currentIndex++
        }
    }

    private fun getPolylineSnapDistance(pos: LatLng): Int {
        if (routePoints.isEmpty()) return 9999

        var minDist = Double.MAX_VALUE
        for (p in routePoints) {
            val d = distance(pos, p)
            if (d < minDist) minDist = d
        }
        return minDist.toInt()
    }

    // L/R 신호 전송
    private fun sendVibration(type: TurnType) {
        if (!readyToWrite) return

        val safe = if (!hasRightTurn && type == TurnType.RIGHT) TurnType.LEFT else type

        when (safe) {
            TurnType.LEFT -> BluetoothManager.sendText("L")
            TurnType.RIGHT -> BluetoothManager.sendText("R")
            else -> {}
        }
    }

    // 턴 안내 반복 진동
    private fun startRepeating(type: TurnType, interval: Long) {
        stopVibration()
        repeatRunnable = object : Runnable {
            override fun run() {
                sendVibration(type)
                handler.postDelayed(this, interval)
            }
        }
        handler.post(repeatRunnable!!)
    }

    // ⭐ 도착 70~20m 구간 반복 진동 (5초 간격)
    private fun startArrivalVibrationRepeated() {
        // 마지막 턴 진동(좌/우) 등 정리
        stopVibration()

        repeatRunnable = object : Runnable {
            override fun run() {
                BluetoothManager.sendText("B")   // 양쪽 진동
                handler.postDelayed(this, 5000) // ★ 5초 간격
            }
        }
        handler.post(repeatRunnable!!)
    }

    private fun distance(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val h = Math.sin(dLat / 2).pow(2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2).pow(2)

        return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }

    private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()
        fusedLocation.removeLocationUpdates(locationCallback)
    }

    private fun stopVibration() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
        // arrivalVibrationActive 플래그는 handleTurn 쪽에서 관리
    }
}
