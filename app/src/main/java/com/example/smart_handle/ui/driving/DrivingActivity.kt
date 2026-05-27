package com.example.smart_handle.ui.driving

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
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
import com.example.smart_handle.ui.Tour.TourPlaceData
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
    private var activeCommand: String? = null
    private var activeSignalTurnIndex = -1

    private val routePoints = mutableListOf<LatLng>()
    private var routePolyline: Polyline? = null
    private val routeCumulativeMeters = mutableListOf<Float>()
    private val turnProgressCache = mutableMapOf<Int, Float>()
    private var initialTurnSkipDone = false

    private var routeType: String = "navigation"

    // 관광모드 TTS 관련
    private val tourPlaces = arrayListOf<TourPlaceData>()
    private val spokenTourPlaceNames = mutableSetOf<String>()
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    companion object {
        private const val TOUR_PLACE_TRIGGER_DISTANCE_M = 70f

        // 경로 이탈/매칭 기준. 실제 GPS 오차 때문에 3m는 너무 빡세서 15m 기준으로 잡음.
        private const val ROUTE_MATCH_THRESHOLD_M = 15f
        private const val INITIAL_TURN_SKIP_DISTANCE_M = 15f

        // 턴을 지나갔다고 보는 기준.
        // 폴리라인 진행도가 있으면 진행도 기준을 우선 사용하고, 없으면 직접 거리 5m를 보조로 사용함.
        private const val TURN_PASS_DISTANCE_M = 5f
        private const val TURN_PASS_PROGRESS_MARGIN_M = 5f

        // 가까운 회전들이 연속으로 있을 때 LC/RC로 처리할 거리 기준.
        private const val CONTINUOUS_TURN_GAP_M = 80f

        // 앱에서 같은 명령을 반복 전송하는 간격.
        // 아두이노 loop()가 S 전까지 현재 동작을 유지하더라도, BLE 누락 방지용으로 계속 반복 전송함.
        private const val COMMAND_REPEAT_INTERVAL_MS = 700L

        // GPS 튐 때문에 25m/50m 경계에서 LED가 깜빡이는 것을 막는 여유 거리.
        private const val SIGNAL_EXIT_BUFFER_M = 5f
    }

    private data class RouteProgress(
        val progressMeters: Float,
        val distanceToRouteMeters: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        Log.d("VIBRATION", "DrivingActivity started")

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

        val btnTestTts = findViewById<Button>(R.id.btn_test_tts)

        if (isTourRoute()) {
            btnTestTts.visibility = View.VISIBLE

            btnTestTts.setOnClickListener {
                if (tourPlaces.isNotEmpty()) {
                    val place = tourPlaces[0]

                    Log.d("TOUR_DESC", "name=${place.name}")
                    Log.d("TOUR_DESC", "tourTitle=${place.tourTitle}")
                    Log.d("TOUR_DESC", "description=${place.description}")

                    val message = buildTourPlaceMessage(place)

                    Log.d("TOUR_TTS_MESSAGE", message)

                    speakTourMessage(message)

                    Toast.makeText(
                        this,
                        "${place.tourTitle.ifBlank { place.name }} TTS 테스트",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "관광지 정보 없음",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            btnTestTts.visibility = View.GONE
        }

        BluetoothManager.attachListener(this)
        fused = LocationServices.getFusedLocationProviderClient(this)

        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")?.let {
            turnEvents.addAll(it)
        }

        Log.d("NAV_TURN", "받은 회전 이벤트 수=${turnEvents.size}")

        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let {
            routePoints.addAll(it)
            buildRouteCumulativeMeters()
        }

        Log.d("NAV_ROUTE", "받은 폴리라인 포인트 수=${routePoints.size}")

        loadTourPlacesFromIntent()

        if (isTourRoute()) {
            initTextToSpeech()
        }

        checkLocationPermission()
    }

    private fun isFitnessRoute(): Boolean {
        return routeType == "fitness"
    }

    private fun isTourRoute(): Boolean {
        return routeType == "tour"
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun loadTourPlacesFromIntent() {
        if (!isTourRoute()) return

        val receivedPlaces: ArrayList<TourPlaceData>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(
                    "tour_places",
                    ArrayList::class.java
                ) as? ArrayList<TourPlaceData>
            } else {
                intent.getSerializableExtra("tour_places") as? ArrayList<TourPlaceData>
            }

        receivedPlaces?.let {
            tourPlaces.addAll(it)
        }

        Log.d("TOUR_TTS", "받은 관광지 수: ${tourPlaces.size}")
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN)

                isTtsReady =
                    result != TextToSpeech.LANG_MISSING_DATA &&
                            result != TextToSpeech.LANG_NOT_SUPPORTED

                if (isTtsReady) {
                    Log.d("TOUR_TTS", "TTS 초기화 성공")
                } else {
                    Log.e("TOUR_TTS", "한국어 TTS를 지원하지 않음")
                }
            } else {
                Log.e("TOUR_TTS", "TTS 초기화 실패")
            }
        }
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
                Log.d("DB_SAVE", "Room 저장 완료: $distanceKm km")
            } catch (e: Exception) {
                Log.e("DB_SAVE", "Room 저장 실패", e)
            }
        }
    }

    private fun saveToFirestore(satisfaction: String, completionPercent: Int) {
        if (firestoreSaved) {
            safeFinish()
            return
        }
        firestoreSaved = true

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(
                this,
                "로그인 정보가 없어 운동 기록 저장을 건너뜁니다.",
                Toast.LENGTH_SHORT
            ).show()
            safeFinish()
            return
        }

        val routeId = intent.getStringExtra("routeId") ?: ""
        val distanceKmFromIntent = intent.getDoubleExtra("distanceKm", 0.0)
        val durationMinFromIntent = intent.getIntExtra("durationMin", 0)
        val elevationGain = intent.getIntExtra("elevationGain", 0)
        val congestionText = intent.getStringExtra("congestionText") ?: "중간"
        val turnCount = intent.getIntExtra("turnCount", 0)

        val actualDurationSec = getDurationSeconds()
        val actualDistanceKm = getDistanceKm()

        val data = hashMapOf(
            "routeType" to "fitness",
            "routeId" to routeId,
            "distanceKm" to distanceKmFromIntent,
            "durationMin" to durationMinFromIntent,
            "elevationGain" to elevationGain,
            "congestionText" to congestionText,
            "turnCount" to turnCount,
            "completionPercent" to completionPercent,
            "satisfaction" to satisfaction,
            "actualDurationSec" to actualDurationSec,
            "actualDistanceKm" to actualDistanceKm,
            "createdAt" to FieldValue.serverTimestamp()
        )

        Log.d("RIDE_SAVE", "저장 시작")
        Log.d("RIDE_SAVE", data.toString())

        firestore.collection("users")
            .document(user.uid)
            .collection("ride_history")
            .add(data)
            .addOnSuccessListener {
                Log.d("RIDE_SAVE", "Firestore 저장 성공")
                Toast.makeText(this, "운동 기록 저장 완료", Toast.LENGTH_SHORT).show()
                safeFinish()
            }
            .addOnFailureListener { e ->
                Log.e("FIRESTORE_SAVE", "저장 실패", e)
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                safeFinish()
            }
    }

    private fun showSatisfactionDialog() {
        if (surveyShown || isFinishing || isDestroyed) return
        surveyShown = true

        val options = arrayOf("만족", "보통", "불만족")

        AlertDialog.Builder(this)
            .setTitle("운동 만족도")
            .setItems(options) { _, which ->
                val selectedSatisfaction = options[which]
                showCompletionPercentDialog(selectedSatisfaction)
            }
            .setCancelable(false)
            .show()
    }

    private fun showCompletionPercentDialog(satisfaction: String) {
        val percentOptions = arrayOf("25%", "50%", "75%", "100%")
        val percentValues = arrayOf(25, 50, 75, 100)

        AlertDialog.Builder(this)
            .setTitle("얼마나 탔나요?")
            .setItems(percentOptions) { _, which ->
                val completionPercent = percentValues[which]
                saveToFirestore(satisfaction, completionPercent)
            }
            .setCancelable(false)
            .show()
    }

    private fun handleRideFinishedByUser() {
        if (rideFinished) return
        rideFinished = true

        stopRepeating(sendStop = true)
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

        stopRepeating(sendStop = true)
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
            Log.e("DRIVING_FINISH", "finish 오류", e)
        }
    }

    private fun stopLocationTracking() {
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e("LOCATION", "removeLocationUpdates 오류", e)
        }
    }

    private fun checkLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (fine != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            startLocationTracking()
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (fineGranted) {
                startLocationTracking()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
                Log.w("LOCATION", "ACCESS_FINE_LOCATION 권한 거부됨")
            }
        }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        googleMap?.isMyLocationEnabled = true

        locationRequest = LocationRequest.Builder(700)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fused.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
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

            if (isTourRoute()) {
                checkTourPlaceArrival(here)
            }

            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(here, 17f)
            )
        }
    }

    private fun checkTourPlaceArrival(current: LatLng) {
        if (!isTtsReady || tourPlaces.isEmpty()) return

        for (place in tourPlaces) {
            val uniqueKey = if (place.contentId.isNotBlank()) {
                place.contentId
            } else {
                place.name
            }

            if (spokenTourPlaceNames.contains(uniqueKey)) continue

            val placeLatLng = LatLng(place.lat, place.lng)
            val dist = distance(current, placeLatLng)

            if (dist <= TOUR_PLACE_TRIGGER_DISTANCE_M) {
                spokenTourPlaceNames.add(uniqueKey)

                val message = buildTourPlaceMessage(place)
                speakTourMessage(message)

                turnCard.visibility = View.VISIBLE
                turnDistance.text = ""
                turnTypeText.text =
                    "${place.tourTitle.ifBlank { place.name }} 근처에 도착했습니다"

                Log.d(
                    "TOUR_TTS",
                    "관광지 도착 안내: ${place.tourTitle.ifBlank { place.name }}, 거리=${dist.roundToInt()}m"
                )
                break
            }
        }
    }

    private fun buildTourPlaceMessage(place: TourPlaceData): String {
        val displayName = when {
            place.name.isNotBlank() -> place.name
            place.tourTitle.isNotBlank() -> place.tourTitle
            else -> "관광지"
        }

        val tourTitle = place.tourTitle.trim()
        val description = place.description?.trim().orEmpty()

        return if (description.isNotBlank() && description != "설명 없음") {
            if (tourTitle.isNotBlank() && tourTitle != displayName) {
                "$displayName 근처입니다. 관련 관광 정보로 $tourTitle 설명을 안내합니다. $description"
            } else {
                "$displayName 근처에 도착했습니다. $description"
            }
        } else if (place.addr1.isNotBlank()) {
            "$displayName 근처에 도착했습니다. 주소는 ${place.addr1} 입니다."
        } else if (place.address.isNotBlank() && place.address != "주소 없음") {
            "$displayName 근처에 도착했습니다. 주소는 ${place.address} 입니다."
        } else {
            "$displayName 근처에 도착했습니다."
        }
    }

    private fun speakTourMessage(message: String) {
        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "tour_place_guide"
        )
    }

    private fun checkTurnEvent(current: LatLng) {
        if (rideFinished) return

        if (turnEvents.isEmpty()) {
            stopRepeating(sendStop = true)
            turnCard.visibility = View.GONE
            return
        }

        val routeProgress = calculateRouteProgress(current)
        skipInitialTooCloseTurns(current, routeProgress)

        if (nextTurnIndex >= turnEvents.size) {
            stopRepeating(sendStop = true)
            if (!isArrivalNotified) {
                turnCard.visibility = View.GONE
            }
            return
        }

        val target = turnEvents[nextTurnIndex]
        val dist = distance(current, target.location)
        val displayDist = dist.roundToInt()
        val continuous = isContinuousTurn(nextTurnIndex)

        turnCard.visibility = View.VISIBLE
        turnDistance.text = "${displayDist}m 후"

        when (target.type) {
            TurnType.LEFT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_left)
                turnTypeText.text = if (continuous) "연속 좌회전" else "좌회전"
            }

            TurnType.RIGHT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_right)
                turnTypeText.text = if (continuous) "연속 우회전" else "우회전"
            }

            TurnType.STRAIGHT -> {
                turnTypeText.text = "직진"
            }
        }

        Log.d(
            "NAV_TURN",
            "idx=$nextTurnIndex/${turnEvents.size}, dist=${displayDist}m, " +
                    "type=${target.type}, continuous=$continuous, ready=$readyToWrite, active=$activeCommand"
        )

        // 턴을 지난 것으로 판단되면 반복 전송을 멈추고 S를 보낸 뒤 다음 턴으로 넘어감.
        // 여기서 return하므로 다음 신호는 다음 GPS 업데이트 때 다시 판단되어, S 이후에 새 명령이 나감.
        if (shouldMoveToNextTurn(routeProgress, nextTurnIndex, dist)) {
            stopRepeating(sendStop = true)
            Log.d("NAV_TURN", "턴 종료: S 전송 후 다음 턴으로 이동 idx=$nextTurnIndex -> ${nextTurnIndex + 1}")
            nextTurnIndex++
            return
        }

        val command = getCommandForTurn(target.type, continuous, dist)

        if (command != null) {
            if (command.endsWith("50")) {
                target.trigger50 = true
            }
            if (command.endsWith("25")) {
                target.trigger25 = true
                target.trigger50 = true
            }

            startRepeating(command, nextTurnIndex)
        } else {
            // 아직 50m/25m 안내 구간 밖이면 이전 신호가 남아있지 않게 S를 보냄.
            if (activeSignalTurnIndex == nextTurnIndex && activeCommand != null) {
                stopRepeating(sendStop = true)
            }
        }
    }

    private fun getCommandForTurn(
        type: TurnType,
        isContinuous: Boolean,
        distanceToTurn: Float
    ): String? {
        if (type == TurnType.STRAIGHT) return null

        val currentActive = activeCommand
        val sameTurnActive = activeSignalTurnIndex == nextTurnIndex && currentActive != null

        return if (isContinuous) {
            when {
                distanceToTurn <= 25f -> {
                    buildNavigationCommand(type, isContinuous = true, distanceMeter = 25)
                }

                // 이미 25m 빨간 신호로 들어간 뒤 GPS가 25m 밖으로 살짝 튀어도 빨간 신호 유지.
                sameTurnActive && currentActive?.endsWith("25") == true &&
                        distanceToTurn <= 25f + SIGNAL_EXIT_BUFFER_M -> {
                    currentActive
                }

                distanceToTurn <= 50f -> {
                    buildNavigationCommand(type, isContinuous = true, distanceMeter = 50)
                }

                // 이미 50m 파란 신호가 켜진 뒤 GPS가 50m 밖으로 살짝 튀어도 파란 신호 유지.
                sameTurnActive && currentActive?.endsWith("50") == true &&
                        distanceToTurn <= 50f + SIGNAL_EXIT_BUFFER_M -> {
                    currentActive
                }

                else -> null
            }
        } else {
            when {
                distanceToTurn <= 25f -> {
                    buildNavigationCommand(type, isContinuous = false, distanceMeter = 25)
                }

                sameTurnActive && currentActive?.endsWith("25") == true &&
                        distanceToTurn <= 25f + SIGNAL_EXIT_BUFFER_M -> {
                    currentActive
                }

                else -> null
            }
        }
    }

    private fun buildNavigationCommand(
        type: TurnType,
        isContinuous: Boolean,
        distanceMeter: Int
    ): String? {
        return when (type) {
            TurnType.LEFT -> {
                if (isContinuous) {
                    if (distanceMeter >= 50) "LC50" else "LC25"
                } else {
                    "L25"
                }
            }

            TurnType.RIGHT -> {
                if (isContinuous) {
                    if (distanceMeter >= 50) "RC50" else "RC25"
                } else {
                    "R25"
                }
            }

            TurnType.STRAIGHT -> null
        }
    }

    private fun skipInitialTooCloseTurns(current: LatLng, routeProgress: RouteProgress?) {
        if (initialTurnSkipDone) return

        // 현재 위치가 폴리라인에서 15m 이상 떨어져 있으면 GPS가 아직 정확히 안 잡힌 것으로 보고,
        // 첫 턴 스킵 판정을 보류함.
        if (routeProgress != null && routeProgress.distanceToRouteMeters > ROUTE_MATCH_THRESHOLD_M) {
            Log.d(
                "NAV_SKIP",
                "초기 위치가 경로에서 ${routeProgress.distanceToRouteMeters.roundToInt()}m 떨어져 있어 첫 회전 스킵 대기"
            )
            return
        }

        initialTurnSkipDone = true

        while (nextTurnIndex < turnEvents.size) {
            val target = turnEvents[nextTurnIndex]
            val dist = distance(current, target.location)
            val passedByRouteProgress = shouldMoveToNextTurn(
                routeProgress = routeProgress,
                index = nextTurnIndex,
                directDistance = dist
            )

            if (dist < INITIAL_TURN_SKIP_DISTANCE_M || passedByRouteProgress) {
                Log.d(
                    "NAV_SKIP",
                    "시작 지점에서 너무 가까운 회전 스킵: idx=$nextTurnIndex, dist=${dist.roundToInt()}m"
                )
                nextTurnIndex++
            } else {
                break
            }
        }
    }

    private fun shouldMoveToNextTurn(
        routeProgress: RouteProgress?,
        index: Int,
        directDistance: Float
    ): Boolean {
        val progress = routeProgress

        if (progress != null) {
            if (progress.distanceToRouteMeters > ROUTE_MATCH_THRESHOLD_M) {
                Log.d(
                    "NAV_ROUTE",
                    "경로에서 ${progress.distanceToRouteMeters.roundToInt()}m 떨어짐: 진행도 기반 턴 종료 보류"
                )
                return false
            }

            val turnProgress = getTurnProgress(index)
            if (turnProgress != null) {
                return progress.progressMeters > turnProgress + TURN_PASS_PROGRESS_MARGIN_M
            }
        }

        return directDistance <= TURN_PASS_DISTANCE_M
    }

    private fun isContinuousTurn(index: Int): Boolean {
        val event = turnEvents.getOrNull(index) ?: return false

        if (event.type == TurnType.STRAIGHT) return false
        if (event.isContinuous) return true

        return isCloseTurnPair(index, index - 1) || isCloseTurnPair(index, index + 1)
    }

    private fun isCloseTurnPair(index: Int, otherIndex: Int): Boolean {
        val event = turnEvents.getOrNull(index) ?: return false
        val other = turnEvents.getOrNull(otherIndex) ?: return false

        if (event.type == TurnType.STRAIGHT || other.type == TurnType.STRAIGHT) {
            return false
        }

        val eventProgress = getTurnProgress(index)
        val otherProgress = getTurnProgress(otherIndex)

        val gapMeters =
            if (eventProgress != null && otherProgress != null) {
                abs(otherProgress - eventProgress)
            } else {
                distance(event.location, other.location)
            }

        return gapMeters <= CONTINUOUS_TURN_GAP_M
    }

    private fun buildRouteCumulativeMeters() {
        routeCumulativeMeters.clear()
        turnProgressCache.clear()

        if (routePoints.isEmpty()) return

        var total = 0f
        routeCumulativeMeters.add(total)

        for (i in 0 until routePoints.lastIndex) {
            total += distance(routePoints[i], routePoints[i + 1])
            routeCumulativeMeters.add(total)
        }

        Log.d(
            "NAV_ROUTE",
            "폴리라인 포인트=${routePoints.size}, 총 길이=${total.roundToInt()}m"
        )
    }

    private fun getTurnProgress(index: Int): Float? {
        if (index !in turnEvents.indices) return null
        if (routePoints.size < 2 || routeCumulativeMeters.size != routePoints.size) return null

        val cached = turnProgressCache[index]
        if (cached != null) {
            return if (cached >= 0f) cached else null
        }

        val progress = calculateRouteProgress(turnEvents[index].location)
        val value = progress?.progressMeters ?: -1f
        turnProgressCache[index] = value

        return progress?.progressMeters
    }

    private fun calculateRouteProgress(point: LatLng): RouteProgress? {
        if (routePoints.size < 2 || routeCumulativeMeters.size != routePoints.size) {
            return null
        }

        var bestDistanceSq = Double.MAX_VALUE
        var bestProgressMeters = 0.0

        for (i in 0 until routePoints.lastIndex) {
            val a = routePoints[i]
            val b = routePoints[i + 1]

            val latRad = Math.toRadians((a.latitude + b.latitude + point.latitude) / 3.0)
            val metersPerDegreeLng = 111320.0 * cos(latRad)
            val metersPerDegreeLat = 110540.0

            val bx = (b.longitude - a.longitude) * metersPerDegreeLng
            val by = (b.latitude - a.latitude) * metersPerDegreeLat
            val px = (point.longitude - a.longitude) * metersPerDegreeLng
            val py = (point.latitude - a.latitude) * metersPerDegreeLat

            val segmentLengthSq = bx * bx + by * by
            val t =
                if (segmentLengthSq > 0.0) {
                    ((px * bx + py * by) / segmentLengthSq).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }

            val projectedX = bx * t
            val projectedY = by * t

            val dx = px - projectedX
            val dy = py - projectedY
            val distanceSq = dx * dx + dy * dy

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestProgressMeters = routeCumulativeMeters[i] + sqrt(segmentLengthSq) * t
            }
        }

        return RouteProgress(
            progressMeters = bestProgressMeters.toFloat(),
            distanceToRouteMeters = sqrt(bestDistanceSq).toFloat()
        )
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
        val handler = Handler(Looper.getMainLooper())
        var count = 0

        val runnable = object : Runnable {
            override fun run() {
                if (count >= 3) {
                    sendStopCommand("arrival finished")
                    return
                }

                sendCommandOnce("L25", "arrival")
                sendCommandOnce("R25", "arrival")

                count++
                handler.postDelayed(this, 300)
            }
        }

        handler.post(runnable)
    }

    private fun startRepeating(command: String, turnIndex: Int) {
        if (activeCommand == command && activeSignalTurnIndex == turnIndex && repeatRunnable != null) {
            return
        }

        // 같은 턴에서 LC50 -> LC25처럼 색이 바뀌거나, 다른 턴으로 넘어갈 때는 먼저 S로 끄고 새 명령 시작.
        if (activeCommand != null) {
            stopRepeating(sendStop = true)
        }

        activeCommand = command
        activeSignalTurnIndex = turnIndex
        isRepeating = true

        repeatHandler = Handler(Looper.getMainLooper())
        repeatRunnable = object : Runnable {
            override fun run() {
                val currentCommand = activeCommand ?: return
                sendCommandOnce(currentCommand, "loop")
                repeatHandler?.postDelayed(this, COMMAND_REPEAT_INTERVAL_MS)
            }
        }

        repeatHandler?.post(repeatRunnable!!)
    }

    private fun stopRepeating(sendStop: Boolean = true) {
        val hadActiveSignal = activeCommand != null || repeatRunnable != null

        repeatRunnable?.let { repeatHandler?.removeCallbacks(it) }
        repeatRunnable = null
        repeatHandler = null
        isRepeating = false
        activeCommand = null
        activeSignalTurnIndex = -1

        if (sendStop && hadActiveSignal) {
            sendStopCommand("turn finished")
        }
    }

    private fun sendCommandOnce(command: String, reason: String): Boolean {
        val ok = BluetoothManager.sendText(command)

        if (ok) {
            Log.d("VIBRATION", "전송 성공: $command, reason=$reason, ready=$readyToWrite")
        } else {
            Log.w("VIBRATION", "전송 실패: $command, reason=$reason, ready=$readyToWrite")
        }

        return ok
    }

    private fun sendStopCommand(reason: String) {
        val ok = BluetoothManager.sendText("S")

        if (ok) {
            Log.d("VIBRATION", "정지 전송 성공: S, reason=$reason, ready=$readyToWrite")
        } else {
            Log.w("VIBRATION", "정지 전송 실패: S, reason=$reason, ready=$readyToWrite")
        }
    }

    private fun distance(a: LatLng, b: LatLng): Float {
        val arr = FloatArray(1)
        Location.distanceBetween(
            a.latitude,
            a.longitude,
            b.latitude,
            b.longitude,
            arr
        )
        return arr[0]
    }

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
        Log.d("DrivingActivity_BLE", "readyToWrite=$ready")
    }

    override fun onDestroy() {
        super.onDestroy()

        stopRepeating(sendStop = true)
        stopLocationTracking()

        tts?.stop()
        tts?.shutdown()
        tts = null

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
        Log.d("DrivingActivity_BLE", msg)
    }
}