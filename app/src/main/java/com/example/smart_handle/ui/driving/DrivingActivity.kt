package com.example.smart_handle.ui.driving

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.graphics.Color
import android.location.Location
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
        private const val SERVER_IP = "172.30.1.50"
        private const val SERVER_PORT = 8000
    }

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

    /* =====================================================
       AI WORKOUT : 서버에서 polyline만 로드
       ===================================================== */
    private fun loadAiRouteFromServer() {
        if (routeId <= 0) {
            Toast.makeText(this, "경로 정보 오류", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                routePoints = fetchPolyline(routeId)
                if (routePoints.isEmpty()) throw Exception("empty route")

                redrawMap()
                startLocationUpdates()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DrivingActivity, "경로 로딩 실패", Toast.LENGTH_SHORT).show()
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

            val bodyStr = response.body()?.string() ?: return@withContext emptyList()
            val arr = JSONObject(bodyStr).getJSONArray("polyline")

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

    /* =====================================================
       MAP
       ===================================================== */
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
            mMap?.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
        } catch (e: Exception) {
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f))
        }
    }

    /* =====================================================
       LOCATION + TURN LOGIC (NAV 전용)
       ===================================================== */
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

                if (routeMode != "AI_WORKOUT") {
                    handleTurn(my)
                }
            }
        }

        fusedLocation.requestLocationUpdates(
            req,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /* =====================================================
       ⭐ 원래 잘 되던 길찾기 턴 처리 로직
       ===================================================== */
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

        // UI 표시
        turnDistanceText.text = "${dist}m 후"
        turnTypeText.text = when (target.type) {
            TurnType.LEFT -> "좌회전"
            TurnType.RIGHT -> "우회전"
            TurnType.STRAIGHT -> "직진"
        }

        // 아이콘
        val iconRes = when (target.type) {
            TurnType.LEFT -> R.drawable.ic_turn_left
            TurnType.RIGHT -> R.drawable.ic_turn_right
            TurnType.STRAIGHT -> R.drawable.ic_turn_left

        }
        turnIcon.setImageResource(iconRes)

        // 진동 (50m / 25m / 도착)
        if (dist <= 50 && !target.trigger50) {
            BluetoothManager.sendText("TURN_50")
            target.trigger50 = true
        }

        if (dist <= 25 && !target.trigger25) {
            BluetoothManager.sendText("TURN_25")
            target.trigger25 = true
        }

        if (dist <= 10) {
            BluetoothManager.sendText("TURN_NOW")
            currentIndex++
        }
    }

    /* =====================================================
       BLE / LIFECYCLE
       ===================================================== */
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
        BluetoothManager.sendText("STOP")
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
}
