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
    private var isArrivalNotified = false   // 목적지 도착 진동 이미 울렸는지 여부


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

            // 2) 목적지 도착(20m 이내) 체크
            checkArrival(here)

            // 3)  네비처럼 카메라를 현재 위치로 이동
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

    /** 목적지(마지막 TurnEvent) 20m 이내 진입 시 도착 진동 패턴 실행 */
    private fun checkArrival(current: LatLng) {
        // 이미 도착 진동을 울렸거나, 턴 이벤트가 없다면 아무것도 안 함
        if (isArrivalNotified || turnEvents.isEmpty()) return

        // 목적지를 turnEvents의 마지막 포인트로 간주
        val destination = turnEvents.last().location
        val distToDest = distance(current, destination)

        if (distToDest <= 20f) {
            // 도착 진동은 한 번만
            isArrivalNotified = true

            // 혹시 남아 있는 반복 턴 진동이 있다면 끄기
            stopRepeating()

            // 양쪽 핸들 도착 패턴 시작
            startArrivalVibration()
        }
    }

    /** 목적지 도착 시: 양쪽 핸들을 짧은 간격으로 3번 울리는 패턴 */
    private fun startArrivalVibration() {
        if (!readyToWrite) return

        val handler = Handler(Looper.getMainLooper())
        var count = 0

        val runnable = object : Runnable {
            override fun run() {
                if (count >= 3) {
                    // 3번 끝
                    return
                }

                // 양쪽 핸들을 짧게 한 번씩 진동
                BluetoothManager.sendText("L")
                BluetoothManager.sendText("R")

                count++
                // 다음 사이클까지 간격(0.3초 정도, 원하면 조절)
                handler.postDelayed(this, 300)
            }
        }

        // 바로 첫 사이클 시작
        handler.post(runnable)
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
