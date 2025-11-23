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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.roundToInt



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

        // 🔥 DrivingActivity 가 BLE listener가 됨
        BluetoothManager.attachListener(this)


        fused = LocationServices.getFusedLocationProviderClient(this)

        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")?.let {
            turnEvents.addAll(it)
        }

        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let {
            routePoints.addAll(it)
        }


        checkLocationPermission()
    }

    // 경로 전체 좌표
    private val routePoints = mutableListOf<LatLng>()

    // 지도에 그려질 폴리라인 객체
    private var routePolyline: Polyline? = null


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

            // 1) 기존 턴 이벤트 체크
            checkTurnEvent(here)

            // 2)  네비처럼 카메라를 현재 위치로 이동
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(here, 17f)   // 17 정도면 네비 느낌
            )
        }
    }


    private fun checkTurnEvent(current: LatLng) {
        if (nextTurnIndex >= turnEvents.size) {
            turnCard.visibility = View.GONE
            return
        }

        val target = turnEvents[nextTurnIndex]
        val dist = distance(current, target.location)

        val displayDist = dist.roundToInt()
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

        if (!target.trigger50 && dist < 50 && dist >= 25) {
            sendVibration(target.type)
            target.trigger50 = true
        }

        if (!target.trigger25 && dist < 25 && dist >= 10) {
            sendVibration(target.type)
            target.trigger25 = true
        }

        if (dist < 10 && dist >= 3) {
            if (!isRepeating) {
                startRepeating(target.type)
                isRepeating = true
            }
        }

        if (dist < 3) {
            stopRepeating()
            nextTurnIndex++
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

    private fun startRepeating(type: TurnType) {
        repeatHandler = Handler(Looper.getMainLooper())
        repeatRunnable = object : Runnable {
            override fun run() {
                sendVibration(type)
                repeatHandler?.postDelayed(this, 2000)
            }
        }
        repeatHandler?.post(repeatRunnable!!)
    }

    private fun stopRepeating() {
        repeatRunnable?.let { repeatHandler?.removeCallbacks(it) }
        isRepeating = false
    }

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
        android.util.Log.d("DrivingActivity_BLE", "readyToWrite = $ready")
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothManager.attachListener(null)
        stopRepeating()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // 위치 권한이 이미 있다면 파란 점(내 위치) 켜기
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
        }

        // 🚗 주행 경로 폴리라인 그리기
        if (routePoints.isNotEmpty()) {
            val polylineOptions = PolylineOptions()
                .addAll(routePoints)
                .width(10f)
                .color(0xFF2196F3.toInt())   // MapsActivity와 동일 색상

            routePolyline = googleMap?.addPolyline(polylineOptions)

            // 처음 진입 시 카메라를 경로 시작 지점 근처로
            googleMap?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(routePoints.first(), 16f)
            )
        }
    }

    //로그캣 확인용 코드-ble 연결 확인
    override fun onLog(msg: String) {
        android.util.Log.d("DrivingActivity_BLE", msg)
    }


}
