package com.example.smart_handle.ui.driving

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.data.AppDatabase
import com.example.smart_handle.data.RideDao
import com.example.smart_handle.data.RideEntity
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.ui.ble.BluetoothManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.launch

class DrivingActivity : AppCompatActivity(),
    BluetoothManager.Listener,
    OnMapReadyCallback {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    private var googleMap: GoogleMap? = null
    private var currentLatLng: LatLng? = null

    private lateinit var turnCard: View
    private lateinit var turnIcon: ImageView
    private lateinit var turnDistance: TextView
    private lateinit var turnTypeText: TextView

    private lateinit var database: AppDatabase
    private lateinit var rideDao: RideDao

    private var startTime: Long = 0L
    private var totalDistanceMeters: Float = 0f
    private var lastLocation: Location? = null
    private var rideSaved = false

    private var readyToWrite = false
    private var turnEvents: MutableList<TurnEvent> = mutableListOf()
    private var isArrivalNotified = false

    private val routePoints = mutableListOf<LatLng>()
    private var routePolyline: Polyline? = null

    // 운동탭에서 넘겨받는 값
    private var exerciseLogId: Int = -1
    private var expectedDistanceKm: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        database = AppDatabase.getDatabase(this)
        rideDao = database.rideDao()

        startTime = System.currentTimeMillis()

        // 운동탭 선택 로그 연결용
        exerciseLogId = intent.getIntExtra("exercise_log_id", -1)
        expectedDistanceKm = intent.getDoubleExtra("expected_distance_km", 0.0)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.drive_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        turnCard = findViewById(R.id.turnCard)
        turnIcon = findViewById(R.id.turnIcon)
        turnDistance = findViewById(R.id.turnDistance)
        turnTypeText = findViewById(R.id.turnTypeText)

        findViewById<Button>(R.id.btn_stop_route).setOnClickListener {
            stopLocationTracking()
            saveRideData(false)
            finish()
        }

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

    private fun saveRideData(isCompleted: Boolean) {
        if (rideSaved) return
        rideSaved = true

        val endTime = System.currentTimeMillis()
        val durationSec = ((endTime - startTime) / 1000).toInt()
        val distanceKm = totalDistanceMeters / 1000.0

        val avgSpeed = if (durationSec > 0) {
            distanceKm / (durationSec / 3600.0)
        } else {
            0.0
        }

        val completionRatio = if (expectedDistanceKm > 0.0) {
            (distanceKm / expectedDistanceKm).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        lifecycleScope.launch {
            val ride = RideEntity(
                distanceKm = distanceKm,
                durationSec = durationSec,
                avgSpeed = avgSpeed,
                completed = isCompleted,
                timestamp = System.currentTimeMillis(),
                exerciseLogId = if (exerciseLogId != -1) exerciseLogId else null,
                completionRatio = completionRatio
            )

            rideDao.insertRide(ride)
            android.util.Log.d(
                "DB_SAVE",
                "저장 완료: ${distanceKm}km, duration=${durationSec}s, completion=$completionRatio"
            )
        }
    }

    private fun stopLocationTracking() {
        fused.removeLocationUpdates(locationCallback)
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

        locationRequest = LocationRequest.Builder(700)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fused.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return

            if (lastLocation != null) {
                totalDistanceMeters += lastLocation!!.distanceTo(loc)
            }
            lastLocation = loc

            val here = LatLng(loc.latitude, loc.longitude)
            currentLatLng = here

            checkArrival(here)

            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(here, 17f)
            )
        }
    }

    private fun checkArrival(current: LatLng) {
        if (isArrivalNotified || turnEvents.isEmpty()) return

        val destination = turnEvents.last().location
        val distToDest = distance(current, destination)

        if (distToDest <= 20f) {
            stopLocationTracking()
            saveRideData(true)

            isArrivalNotified = true

            turnCard.visibility = View.VISIBLE
            turnDistance.text = ""
            turnTypeText.text = "목적지에 도착했습니다"
        }
    }

    private fun distance(a: LatLng, b: LatLng): Float {
        val arr = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, arr)
        return arr[0]
    }

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationTracking()

        if (!rideSaved) {
            saveRideData(false)
        }

        BluetoothManager.attachListener(null)
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

    override fun onLog(msg: String) {
        android.util.Log.d("DrivingActivity_BLE", msg)
    }
}