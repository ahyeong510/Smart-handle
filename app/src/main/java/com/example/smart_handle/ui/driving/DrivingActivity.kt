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

        // GPS 오차 때문에 "정확히 3m 안에 들어와야 다음 회전" 방식은 실제 길에서 자주 멈춤.
        // 그래서 경로 폴리라인 기준 15m 안이면 경로 위에 있다고 보고, 지나간 회전은 넘긴다.
        private const val ROUTE_MATCH_THRESHOLD_M = 15f
        private const val INITIAL_TURN_SKIP_DISTANCE_M = 15f
        private const val TURN_PASS_DISTANCE_M = 8f
        private const val TURN_PASS_PROGRESS_MARGIN_M = 5f

        // 가까운 회전이 연속으로 있을 때 LC/RC 명령을 쓰기 위한 기준값.
        private const val CONTINUOUS_TURN_GAP_M = 80f
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
            turnCard.visibility = View.GONE
            return
        }

        val routeProgress = calculateRouteProgress(current)

        // 시작하자마자 첫 회전점이 너무 가까우면 GPS 오차 때문에 첫 회전에 묶이는 문제가 생겨서 넘김.
        skipInitialTooCloseTurns(current, routeProgress)

        if (nextTurnIndex >= turnEvents.size) {
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
                turnTypeText.text =
                    if (continuous) "연속 좌회전" else "좌회전"
            }

            TurnType.RIGHT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_right)
                turnTypeText.text =
                    if (continuous) "연속 우회전" else "우회전"
            }

            TurnType.STRAIGHT -> {
                turnTypeText.text = "직진"
            }
        }

        Log.d(
            "NAV_TURN",
            "idx=$nextTurnIndex/${turnEvents.size}, dist=${displayDist}m, " +
                    "type=${target.type}, continuous=$continuous, ready=$readyToWrite"
        )

        // 연속 회전일 때만 50m에서 파랑 LED 명령.
        if (continuous && !target.trigger50 && dist <= 50f && dist > 25f) {
            sendNavigationCommand(target.type, true, 50)
            target.trigger50 = true
        }

        // 일반 회전은 25m에서 L25/R25, 연속 회전은 25m에서 LC25/RC25.
        if (!target.trigger25 && dist <= 25f) {
            sendNavigationCommand(target.type, continuous, 25)
            target.trigger25 = true
            target.trigger50 = true
        }

        // 아주 가까워지면 25m 명령을 반복해서 한 번 더 확실히 알려줌.
        if (dist <= 10f && dist > TURN_PASS_DISTANCE_M) {
            if (!isRepeating) {
                startRepeating(target.type, continuous)
                isRepeating = true
            }
        }

        // GPS가 정확히 3m 안에 들어오지 않아도, 경로 진행상 지나갔다고 판단되면 다음 회전으로 넘김.
        if (shouldMoveToNextTurn(routeProgress, nextTurnIndex, dist)) {
            stopRepeating()
            Log.d("NAV_TURN", "다음 회전으로 이동: idx=$nextTurnIndex -> ${nextTurnIndex + 1}")
            nextTurnIndex++
        }
    }

    private fun skipInitialTooCloseTurns(current: LatLng, routeProgress: RouteProgress?) {
        if (initialTurnSkipDone) return

        // 첫 GPS가 튀어서 경로 밖으로 잡히면 여기서 스킵 판정을 하지 않고,
        // 사용자가 폴리라인 15m 안으로 들어온 뒤 첫 회전 스킵을 한 번만 실행한다.
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
        if (directDistance <= TURN_PASS_DISTANCE_M) {
            return true
        }

        val progress = routeProgress ?: return false

        if (progress.distanceToRouteMeters > ROUTE_MATCH_THRESHOLD_M) {
            Log.d(
                "NAV_ROUTE",
                "경로에서 ${progress.distanceToRouteMeters.roundToInt()}m 떨어짐: 진행도 기반 스킵 보류"
            )
            return false
        }

        val turnProgress = getTurnProgress(index) ?: return false

        return progress.progressMeters > turnProgress + TURN_PASS_PROGRESS_MARGIN_M
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
                if (count >= 3) return

                BluetoothManager.sendText("L25")
                BluetoothManager.sendText("R25")

                count++
                handler.postDelayed(this, 300)
            }
        }

        handler.post(runnable)
    }

    private fun sendNavigationCommand(type: TurnType, isContinuous: Boolean, distanceMeter: Int) {
        val command = when (type) {
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
        } ?: return

        // readyToWrite가 false여도 바로 return 하지 않음.
        // 화면 전환 후 listener가 준비 상태를 다시 못 받으면 false로 남을 수 있어서,
        // BluetoothManager.sendText()의 실제 결과를 기준으로 판단한다.
        val ok = BluetoothManager.sendText(command)

        if (ok) {
            Log.d(
                "VIBRATION",
                "전송 성공: $command, ready=$readyToWrite, continuous=$isContinuous, distance=$distanceMeter"
            )
        } else {
            Log.w(
                "VIBRATION",
                "전송 실패: $command, ready=$readyToWrite, BLE 연결/특성 준비 상태 확인 필요"
            )
        }
    }

    private fun sendVibration(type: TurnType, isContinuous: Boolean) {
        sendNavigationCommand(type, isContinuous, 25)
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

        stopRepeating()
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