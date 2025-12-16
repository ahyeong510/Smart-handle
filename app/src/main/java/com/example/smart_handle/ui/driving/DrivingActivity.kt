package com.example.smart_handle.ui.driving

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.ui.ble.BluetoothManager
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DrivingActivity : AppCompatActivity(),
    BluetoothManager.Listener,
    OnMapReadyCallback {

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

    private var routeMode: String = "NAV"
    private var routeId: Int = -1

    private var destLatLng: LatLng? = null

    // =========================
    // Activity lifecycle
    // =========================
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

        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")?.let {
            turnEvents.addAll(it)
        }
        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let {
            routePoints = it
        }

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.drive_map)
                    as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupBackPress()
        setupBluetooth()

        if (routeMode == "AI_WORKOUT") {
            loadAiRouteFromServer()
        } else {
            destLatLng = routePoints.lastOrNull()
            startLocationUpdates()
        }
    }

    // =========================
    // AI 운동 경로 로딩
    // =========================
    private fun loadAiRouteFromServer() {
        if (routeId <= 0) {
            Toast.makeText(this, "경로 정보 오류", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                routePoints = fetchPolyline(routeId)
                destLatLng = routePoints.lastOrNull()
                redrawMap()
                startLocationUpdates()
            } catch (e: Exception) {
                Toast.makeText(this@DrivingActivity, "경로 로딩 실패", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun fetchPolyline(routeId: Int): List<LatLng> =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("http://10.0.2.2:8000/route/$routeId")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body()?.string()
                ?: error("empty response")

            val arr = JSONObject(body).getJSONArray("polyline")
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

    // =========================
    // Map
    // =========================
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap?.isMyLocationEnabled = true
        redrawMap()
    }

    private fun redrawMap() {
        if (mMap == null || routePoints.isEmpty()) return

        mMap?.clear()

        mMap?.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .color(Color.BLUE)
                .width(12f)
        )

        try {
            val builder = LatLngBounds.Builder()
            routePoints.forEach { builder.include(it) }
            mMap?.moveCamera(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 140)
            )
        } catch (e: Exception) {
            mMap?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(routePoints.first(), 14f)
            )
        }
    }

    // =========================
    // Location
    // =========================
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val last = result.lastLocation ?: return
                val my = LatLng(last.latitude, last.longitude)

                if (routeMode != "AI_WORKOUT") {
                    processTurn(my)
                }
            }
        }

        fusedLocation.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    // =========================
    // 기존 턴 처리 (이름만 맞춤)
    // =========================
    private fun processTurn(pos: LatLng) {
        // 👉 아영 기존 handleTurn 로직 그대로 여기 두면 됨
    }

    // =========================
    // Bluetooth / Vibration
    // =========================
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

    private fun stopVibration() {
        // 🔑 실제 프로젝트 구조에 맞는 안전한 종료 신호
        BluetoothManager.sendText("STOP")
    }

    // =========================
    // Back
    // =========================
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    stopVibration()
                    finish()
                }
            })
    }
}
