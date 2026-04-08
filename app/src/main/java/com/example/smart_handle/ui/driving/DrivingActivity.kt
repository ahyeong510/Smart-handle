package com.example.smart_handle.ui.driving

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.data.AppDatabase
import com.example.smart_handle.data.RideDao
import com.example.smart_handle.data.RideEntity
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.util.Log

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

    private val firestore = FirebaseFirestore.getInstance()

    private var startTime: Long = 0L
    private var totalDistanceMeters: Float = 0f
    private var lastLocation: Location? = null

    private var roomSaved = false
    private var firestoreSaved = false
    private var surveyShown = false
    private var rideFinished = false
    private var isClosing = false

    private var readyToWrite = false
    private var turnEvents: MutableList<TurnEvent> = mutableListOf()
    private var nextTurnIndex = 0
    private var isArrivalNotified = false

    private var repeatHandler: Handler? = null
    private var repeatRunnable: Runnable? = null
    private var isRepeating = false

    private val routePoints = mutableListOf<LatLng>()
    private var routePolyline: Polyline? = null

    private var routeType: String = "navigation"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        Log.d("VIBRATION", "🔥 DrivingActivity started")

        database = AppDatabase.getDatabase(this)
        rideDao = database.rideDao()

        startTime = System.currentTimeMillis()
        routeType = intent.getStringExtra("routeType") ?: "navigation"

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.drive_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        turnCard = findViewById(R.id.turnCard)
        turnIcon = findViewById(R.id.turnIcon)
        turnDistance = findViewById(R.id.turnDistance)
        turnTypeText = findViewById(R.id.turnTypeText)

        findViewById<Button>(R.id.btn_stop_route).setOnClickListener {
            handleRideFinishedByUser()
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

    private fun isFitnessRoute(): Boolean {
        return routeType == "fitness"
    }

    private fun getDurationSeconds(): Long {
        return (System.currentTimeMillis() - startTime) / 1000L
    }

    private fun getDistanceKm(): Double {
        return totalDistanceMeters / 1000.0
    }

    private fun saveRideToRoom(isCompleted: Boolean) {
        if (roomSaved) return
        roomSaved = true

        val durationSeconds = getDurationSeconds()
        val distanceKm = getDistanceKm()

        lifecycleScope.launch {
            try {
                val ride = RideEntity(
                    distance = distanceKm,
                    duration = durationSeconds,
                    elevationGain = 0.0,
                    completed = isCompleted,
                    date = System.currentTimeMillis()
                )

                rideDao.insertRide(ride)
                android.util.Log.d("DB_SAVE", "Room 저장 완료: $distanceKm km")
            } catch (e: Exception) {
                android.util.Log.e("DB_SAVE", "Room 저장 실패", e)
            }
        }
    }

    private fun saveFitnessSurveyToFirestore(satisfaction: String) {
        if (firestoreSaved) {
            safeFinish()
            return
        }
        firestoreSaved = true

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "로그인 정보가 없어 만족도 저장을 건너뜁니다.", Toast.LENGTH_SHORT).show()
            safeFinish()
            return
        }

        val data = hashMapOf(
            "routeType" to "fitness",
            "distanceKm" to getDistanceKm(),
            "durationSec" to getDurationSeconds(),
            "satisfaction" to satisfaction,
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("users")
            .document(user.uid)
            .collection("ride_history")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "운동 기록 저장 완료", Toast.LENGTH_SHORT).show()
                safeFinish()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FIRESTORE_SAVE", "저장 실패", e)
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                safeFinish()
            }
    }

    private fun showSatisfactionDialog() {
        if (surveyShown || isFinishing || isDestroyed) return
        surveyShown = true

        AlertDialog.Builder(this)
            .setTitle("운동 만족도")
            .setMessage("이번 운동 경로는 어떠셨나요?")
            .setPositiveButton("만족") { _, _ ->
                saveFitnessSurveyToFirestore("만족")
            }
            .setNeutralButton("보통") { _, _ ->
                saveFitnessSurveyToFirestore("보통")
            }
            .setNegativeButton("불만족") { _, _ ->
                saveFitnessSurveyToFirestore("불만족")
            }
            .setCancelable(false)
            .show()
    }

    private fun handleRideFinishedByUser() {
        if (rideFinished) return
        rideFinished = true

        stopRepeating()
        stopLocationTracking()
        saveRideToRoom(true)

        if (isFitnessRoute()) {
            showSatisfactionDialog()
        } else {
            safeFinish()
        }
    }

    private fun handleRideFinishedByArrival() {
        if (rideFinished) return
        rideFinished = true

        stopRepeating()
        stopLocationTracking()
        saveRideToRoom(true)

        isArrivalNotified = true

        turnCard.visibility = View.VISIBLE
        turnDistance.text = ""
        turnTypeText.text = "목적지에 도착했습니다"

        startArrivalVibration()

        if (isFitnessRoute()) {
            showSatisfactionDialog()
        }
    }

    private fun safeFinish() {
        if (isClosing) return
        isClosing = true
        try {
            finish()
        } catch (e: Exception) {
            android.util.Log.e("DRIVING_FINISH", "finish 오류", e)
        }
    }

    private fun stopLocationTracking() {
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            android.util.Log.e("LOCATION", "removeLocationUpdates 오류", e)
        }
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

        fused.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
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

            checkTurnEvent(here)
            checkArrival(here)

            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(here, 17f)
            )
        }
    }

    private fun checkTurnEvent(current: LatLng) {
        if (rideFinished) return

        if (nextTurnIndex >= turnEvents.size) {
            if (!isArrivalNotified) {
                turnCard.visibility = View.GONE
            }
            return
        }

        val target = turnEvents[nextTurnIndex]
        val dist = distance(current, target.location)
        val displayDist = dist.roundToInt()

        turnCard.visibility = View.VISIBLE
        turnDistance.text = "${displayDist}m 후"

        when (target.type) {
            TurnType.LEFT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_left)
                turnTypeText.text = "좌회전"
            }
            TurnType.RIGHT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_right)
                turnTypeText.text = "우회전"
            }
            TurnType.STRAIGHT -> {
                turnTypeText.text = "직진"
            }
        }

        if (!target.trigger50 && dist < 50 && dist >= 25) {
            sendVibration(target.type, target.isContinuous)
            target.trigger50 = true
        }

        if (!target.trigger25 && dist < 25 && dist >= 10) {
            sendVibration(target.type, target.isContinuous)
            target.trigger25 = true
        }

        if (dist < 10 && dist >= 3) {
            if (!isRepeating) {
                startRepeating(target.type, target.isContinuous)
                isRepeating = true
            }
        }

        if (dist < 3) {
            stopRepeating()
            nextTurnIndex++
        }
    }

    private fun checkArrival(current: LatLng) {
        if (isArrivalNotified || turnEvents.isEmpty()) return

        val destination = turnEvents.last().location
        val distToDest = distance(current, destination)

        if (distToDest <= 20f) {
            handleRideFinishedByArrival()
        }
    }

    private fun startArrivalVibration() {
        if (!readyToWrite) return

        val handler = Handler(Looper.getMainLooper())
        var count = 0

        val runnable = object : Runnable {
            override fun run() {
                if (count >= 3) return

                BluetoothManager.sendText("L")
                BluetoothManager.sendText("R")

                count++
                handler.postDelayed(this, 300)
            }
        }

        handler.post(runnable)
    }

    private fun sendVibration(type: TurnType, isContinuous: Boolean) {
        if (!readyToWrite) return

        when (type) {
            TurnType.LEFT -> {
                if (isContinuous) {
                    Log.d("VIBRATION", "LC")
                    BluetoothManager.sendText("LC")   // 🔥 연속 좌회전
                } else {
                    Log.d("VIBRATION", "L")
                    BluetoothManager.sendText("L")
                }
            }

            TurnType.RIGHT -> {
                if (isContinuous) {
                    Log.d("VIBRATION", "RC")
                    BluetoothManager.sendText("RC")   // 🔥 연속 우회전
                } else {
                    Log.d("VIBRATION", "R")
                    BluetoothManager.sendText("R")
                }
            }

            TurnType.STRAIGHT -> {}
        }
    }

    private fun startRepeating(type: TurnType, isContinuous: Boolean) {
        repeatHandler = Handler(Looper.getMainLooper())
        repeatRunnable = object : Runnable {
            override fun run() {
                sendVibration(type, isContinuous)
                repeatHandler?.postDelayed(this, 2000)
            }
        }
        repeatHandler?.post(repeatRunnable!!)
    }

    private fun stopRepeating() {
        repeatRunnable?.let { repeatHandler?.removeCallbacks(it) }
        repeatRunnable = null
        repeatHandler = null
        isRepeating = false
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

        stopRepeating()
        stopLocationTracking()

        if (!roomSaved) {
            saveRideToRoom(false)
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