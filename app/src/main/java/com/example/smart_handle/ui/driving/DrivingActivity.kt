package com.example.smart_handle.ui.driving

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.graphics.Color
import android.location.Location
import android.os.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import com.example.smart_handle.ui.ble.BluetoothManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToInt

class DrivingActivity : AppCompatActivity(),
    BluetoothManager.Listener,
    OnMapReadyCallback {

    companion object {
        private const val SERVER_IP = "172.16.169.48"
        private const val SERVER_PORT = 8000

        // 경로 로딩 전(특히 AI_WORKOUT) "세계지도(0,0)" 방지용 기본 카메라 위치
        private val DEFAULT_CENTER = LatLng(37.5665, 126.9780) // 서울 시청 근처
        private const val DEFAULT_ZOOM = 16f
    }

    /* ================= UI ================= */
    private lateinit var turnDistanceText: TextView
    private lateinit var turnTypeText: TextView
    private lateinit var turnIcon: ImageView
    private lateinit var stopButton: Button

    /* ================= MAP ================= */
    private var mMap: GoogleMap? = null
    private var routePoints: List<LatLng> = emptyList()
    private var destLatLng: LatLng? = null
    private var aiRouteLoaded = false

    /* ================= MODE ================= */
    private var routeMode: String = "NAV"
    private var routeId: Int = -1

    /* ================= LOCATION ================= */
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    /* ================= TURN / VIBRATION ================= */
    private val turnEvents = mutableListOf<TurnEvent>()
    private var currentIndex = 0
    private var readyToWrite = false
    private var hasRightTurn = false

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    /* ================= LIFECYCLE ================= */
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

        routeMode = intent.getStringExtra("ROUTE_MODE") ?: "NAV"
        routeId = intent.getIntExtra("ROUTE_ID", -1)

        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")
            ?.let { turnEvents.addAll(it) }

        intent.getParcelableArrayListExtra<LatLng>("route_points")
            ?.let { routePoints = it }

        destLatLng = routePoints.lastOrNull()
        hasRightTurn = turnEvents.any { it.type == TurnType.RIGHT }

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.drive_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupBackPress()
        setupBluetooth()

        if (routeMode == "AI_WORKOUT") {
            loadAiRouteFromServer()
        } else {
            startLocationUpdates()
        }
    }

    /* ================= MAP READY ================= */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // 권한이 이미 있다고 가정 (기존 코드 유지)
        try {
            mMap?.isMyLocationEnabled = true
        } catch (_: SecurityException) {
            // 권한 없으면 위치 레이어 못 켜는 건 어쩔 수 없음
        }

        // ✅ (핵심) 경로가 아직 없으면 "세계지도" 대신 내 위치/기본 위치로 카메라 먼저 이동
        moveCameraToInitialPosition()

        // ⭐ AI_WORKOUT인데 아직 polyline 없으면 redraw 하지 않음 (세계지도 방지)
        if (routeMode == "AI_WORKOUT" && !aiRouteLoaded) return

        redrawMap()
    }

    @SuppressLint("MissingPermission")
    private fun moveCameraToInitialPosition() {
        val map = mMap ?: return

        // 이미 routePoints 있으면 굳이 초기 이동 필요 없음
        if (routePoints.isNotEmpty()) return

        // lastLocation으로 먼저 카메라 이동 시도
        try {
            fusedLocation.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude),
                                DEFAULT_ZOOM
                            )
                        )
                    } else {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM))
                    }
                }
                .addOnFailureListener {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM))
                }
        } catch (_: SecurityException) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM))
        } catch (_: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM))
        }
    }

    private fun redrawMap() {
        val map = mMap ?: return
        if (routePoints.isEmpty()) return
        if (routeMode == "AI_WORKOUT" && !aiRouteLoaded) return

        map.clear()

        map.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .color(Color.BLUE)
                .width(12f)
        )

        try {
            val builder = LatLngBounds.Builder()
            routePoints.forEach { builder.include(it) }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
        } catch (_: Exception) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f))
        }
    }

    /* ================= AI ROUTE ================= */
    private fun loadAiRouteFromServer() {
        if (routeId <= 0) {
            Toast.makeText(this, "AI 경로 오류", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                routePoints = fetchPolyline(routeId)
                if (routePoints.isEmpty()) throw Exception("empty route")

                aiRouteLoaded = true
                destLatLng = routePoints.last()

                // ✅ map 준비 전이면 onMapReady에서 redrawMap이 처리함
                redrawMap()
                startLocationUpdates()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DrivingActivity, "AI 경로 로딩 실패", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun fetchPolyline(routeId: Int): List<LatLng> =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("http://$SERVER_IP:$SERVER_PORT/route/$routeId")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val arr = JSONObject(response.body()!!.string()).getJSONArray("polyline")
            val list = mutableListOf<LatLng>()

            var i = 0
            while (i < arr.length()) {
                val lng = arr.getDouble(i)
                val lat = arr.getDouble(i + 1)
                list.add(LatLng(lat, lng))
                i += 2
            }
            list
        }

    /* ================= LOCATION ================= */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val my = LatLng(loc.latitude, loc.longitude)

                if (routeMode == "NAV") {
                    handleTurn(my)
                }
            }
        }

        fusedLocation.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    /* ================= TURN + VIBRATION (기존 로직 100% 유지) ================= */
    private fun handleTurn(my: LatLng) {
        if (currentIndex >= turnEvents.size) return

        val target = turnEvents[currentIndex]
        val result = FloatArray(1)

        Location.distanceBetween(
            my.latitude, my.longitude,
            target.location.latitude, target.location.longitude,
            result
        )

        val dist = result[0].roundToInt()

        turnDistanceText.text = "${dist}m 후"
        turnTypeText.text = when (target.type) {
            TurnType.LEFT -> "좌회전"
            TurnType.RIGHT -> "우회전"
            TurnType.STRAIGHT -> "직진"
        }

        // 100~50m
        if (!target.trigger50 && dist in 50..120) {
            target.trigger50 = true
            startRepeating(target.type, 4000)
        }

        // 50~20m
        if (!target.trigger25 && dist in 20..50) {
            target.trigger25 = true
            startRepeating(target.type, 1200)
        }

        // 턴 완료
        if (dist < 20) {
            stopVibration()
            currentIndex++
        }
    }

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

    private fun sendVibration(type: TurnType) {
        if (!readyToWrite) return

        val safe = if (!hasRightTurn && type == TurnType.RIGHT) TurnType.LEFT else type

        when (safe) {
            TurnType.LEFT -> BluetoothManager.sendText("L")
            TurnType.RIGHT -> BluetoothManager.sendText("R")
            else -> {}
        }
    }

    private fun stopVibration() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    /* ================= BLE / ETC ================= */
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

    private fun setupBluetooth() {
        readyToWrite = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
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

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()

        // ✅ (핵심) startLocationUpdates를 안 탄 경우에도 안전하게 종료
        if (::locationCallback.isInitialized) {
            fusedLocation.removeLocationUpdates(locationCallback)
        }
    }
}
