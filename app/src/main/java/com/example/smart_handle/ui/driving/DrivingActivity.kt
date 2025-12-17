package com.example.smart_handle.ui.driving

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
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
import kotlin.math.pow

class DrivingActivity : AppCompatActivity(),
    BluetoothManager.Listener,
    OnMapReadyCallback {

    companion object {
        private const val SERVER_IP = "172.30.1.50"
        private const val SERVER_PORT = 8000
    }

    /* ================= UI ================= */
    private lateinit var turnDistanceText: TextView
    private lateinit var turnTypeText: TextView
    private lateinit var turnIcon: ImageView
    private lateinit var stopButton: Button

    /* ================= MAP ================= */
    private var mMap: GoogleMap? = null
    private var mapReady = false
    private var routeReady = false

    /* ================= LOCATION ================= */
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    /* ================= ROUTE ================= */
    private val turnEvents = mutableListOf<TurnEvent>()
    private var currentIndex = 0
    private var routePoints: List<LatLng> = emptyList()
    private var destLatLng: LatLng? = null

    private var routeMode = "NAV"
    private var routeId = -1

    /* ================= BLE ================= */
    private var readyToWrite = false
    private var hasRightTurn = false

    /* ================= VIBRATION ================= */
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var arrivalVibrationActive = false
    private var enteredStraightMode = false
    private var arrivalNotified = false

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

        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")?.let {
            turnEvents.addAll(it)
        }
        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let {
            routePoints = it
        }

        destLatLng = routePoints.lastOrNull()
        hasRightTurn = turnEvents.any { it.type == TurnType.RIGHT }

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.drive_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupBluetooth()
        setupBackPress()

        if (routeMode == "AI_WORKOUT") {
            loadAiRouteFromServer()
        } else {
            routeReady = routePoints.isNotEmpty()
            tryInitMap()
            startLocationUpdates()
        }
    }

    /* ================= MAP ================= */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap?.isMyLocationEnabled = true
        mapReady = true
        tryInitMap()
    }

    private fun tryInitMap() {
        if (!mapReady || !routeReady || routePoints.isEmpty()) return

        mMap?.clear()
        mMap?.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .color(Color.BLUE)
                .width(12f)
        )

        mMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(routePoints.first(), 15f)
        )
    }

    /* ================= AI ROUTE ================= */
    private fun loadAiRouteFromServer() {
        lifecycleScope.launch {
            try {
                routePoints = fetchPolyline(routeId)
                if (routePoints.isEmpty()) throw Exception("empty route")

                destLatLng = routePoints.last()
                routeReady = true
                tryInitMap()
                startLocationUpdates()

            } catch (e: Exception) {
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

            val arr = JSONObject(response.body()?.string() ?: return@withContext emptyList())
                .getJSONArray("polyline")

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
                val last = result.lastLocation ?: return
                val my = LatLng(last.latitude, last.longitude)

                handleTurn(my)
            }
        }

        fusedLocation.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    /* ================= TURN + VIBRATION (원본 유지) ================= */
    private fun handleTurn(my: LatLng) {

        val dest = destLatLng ?: return
        val snapDist = getPolylineSnapDistance(my)

        /* ===== 마지막 턴 이후 ===== */
        if (currentIndex >= turnEvents.size) {

            val distDest = distance(my, dest).toInt()
            turnDistanceText.text = "${distDest}m 후"
            turnTypeText.text = "직진"

            if (!enteredStraightMode) {
                BluetoothManager.sendText("B")
                enteredStraightMode = true
            }

            if (distDest in 20..70) {
                if (!arrivalVibrationActive) {
                    startArrivalVibrationRepeated()
                    arrivalVibrationActive = true
                }
            } else {
                if (arrivalVibrationActive) {
                    stopVibration()
                    arrivalVibrationActive = false
                }
            }

            if ((distDest < 30 || snapDist < 12) && !arrivalNotified) {
                arrivalNotified = true
                stopVibration()
                Toast.makeText(this, "목적지에 도착했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        /* ===== 일반 턴 ===== */
        val target = turnEvents[currentIndex]
        val dist = distance(my, target.location).roundToInt()

        turnDistanceText.text = "${dist}m 후"
        turnTypeText.text = when (target.type) {
            TurnType.LEFT -> "좌회전"
            TurnType.RIGHT -> "우회전"
            TurnType.STRAIGHT -> "직진"
        }

        if (!target.trigger50 && dist in 50..120) {
            target.trigger50 = true
            startRepeating(target.type, 4000)
        }

        if (!target.trigger25 && dist in 20..50) {
            target.trigger25 = true
            startRepeating(target.type, 1200)
        }

        if (dist < 20) {
            stopVibration()
            currentIndex++
        }
    }

    /* ================= VIBRATION ================= */
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

    private fun startArrivalVibrationRepeated() {
        stopVibration()
        repeatRunnable = object : Runnable {
            override fun run() {
                BluetoothManager.sendText("B")
                handler.postDelayed(this, 5000)
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
        BluetoothManager.sendText("STOP")
    }

    /* ================= UTILS ================= */
    private fun getPolylineSnapDistance(pos: LatLng): Int {
        var min = Double.MAX_VALUE
        for (p in routePoints) {
            val d = distance(pos, p)
            if (d < min) min = d
        }
        return min.toInt()
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

    /* ================= BLE ================= */
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
        fusedLocation.removeLocationUpdates(locationCallback)
    }
}
