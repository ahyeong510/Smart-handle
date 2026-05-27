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
    private var arrivalSignalSent = false

    private var repeatHandler: Handler? = null
    private var repeatRunnable: Runnable? = null
    private var activeLedCommand: String? = null
    private var activeVibrationCommand: String? = null
    private var isRepeating = false

    private val routePoints = mutableListOf<LatLng>()
    private var routePolyline: Polyline? = null
    private val routeCumulativeMeters = mutableListOf<Float>()
    private val turnProgressCache = mutableMapOf<Int, Float>()

    private var routeType: String = "navigation"

    private val tourPlaces = arrayListOf<TourPlaceData>()
    private val spokenTourPlaceNames = mutableSetOf<String>()
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    companion object {
        private const val TOUR_PLACE_TRIGGER_DISTANCE_M = 70f

        // 직진 교차로든 실제 회전 지점이든 15m 안에 들어오면 다음 안내로 넘김
        private const val EVENT_PASS_DISTANCE_M = 15f
        private const val EVENT_PASS_PROGRESS_MARGIN_M = 3f

        // 목적지 20m 안에 들어오면 A 전송
        private const val ARRIVAL_SIGNAL_DISTANCE_M = 20f

        // 진동은 실제 회전 지점 기준 50m 안에서만 반복
        private const val VIBRATION_START_DISTANCE_M = 50f
        private const val VIBRATION_REPEAT_INTERVAL_MS = 700L

        // GPS가 경로에서 이 정도 이상 벗어나면 진행도 기반 통과 판정은 보류
        private const val ROUTE_MATCH_THRESHOLD_M = 25f
    }

    private data class RouteProgress(
        val progressMeters: Float,
        val distanceToRouteMeters: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        Log.d("DRIVING", "DrivingActivity started")

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
                    val message = buildTourPlaceMessage(place)
                    speakTourMessage(message)

                    Toast.makeText(
                        this,
                        "${place.tourTitle.ifBlank { place.name }} TTS 테스트",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, "관광지 정보 없음", Toast.LENGTH_SHORT).show()
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

        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let {
            routePoints.addAll(it)
            buildRouteCumulativeMeters()
        }

        Log.d("NAV_INTERSECTION", "받은 이벤트 수=${turnEvents.size}, 폴리라인 수=${routePoints.size}")
        turnEvents.forEachIndexed { index, event ->
            Log.d(
                "NAV_INTERSECTION",
                "event[$index] type=${event.type}, lat=${event.location.latitude}, lng=${event.location.longitude}"
            )
        }

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

        stopAllSignals(forceSendStop = true, reason = "user stop")
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
        isArrivalNotified = true

        // 기존 빨강/파랑/진동을 먼저 끄고, 바로 A를 보내야 초록 LED가 살아남음
        stopAllSignals(forceSendStop = true, reason = "arrival before A")
        sendArrivalCommand()

        stopLocationTracking()
        saveRideToRoom(true)

        turnCard.visibility = View.VISIBLE
        turnDistance.text = ""
        turnTypeText.text = "목적지에 도착했습니다"

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
            setLedCommand(null, "no turn events")
            setVibrationCommand(null)
            turnCard.visibility = View.GONE
            return
        }

        val routeProgress = calculateRouteProgress(current)
        advancePassedEvents(current, routeProgress)

        val targetTurnIndex = findNextDirectionalTurnIndex(nextTurnIndex)

        if (targetTurnIndex == null) {
            setLedCommand(null, "no next directional turn")
            setVibrationCommand(null)
            if (!isArrivalNotified) {
                turnCard.visibility = View.GONE
            }
            return
        }

        val target = turnEvents[targetTurnIndex]
        val distToTarget = distance(current, target.location)
        val displayDist = distToTarget.roundToInt()

        // 핵심: STRAIGHT 이벤트도 교차로 1개로 계산함.
        // 예: STRAIGHT, STRAIGHT, RIGHT이면 count=3 -> LED 꺼짐
        // 첫 STRAIGHT 지나면 count=2 -> 파랑
        // 두 번째 STRAIGHT 지나면 count=1 -> 빨강
        val intersectionCount = targetTurnIndex - nextTurnIndex + 1

        turnCard.visibility = View.VISIBLE
        turnDistance.text = "${displayDist}m 후"

        val directionText = when (target.type) {
            TurnType.LEFT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_left)
                "좌회전"
            }

            TurnType.RIGHT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_right)
                "우회전"
            }

            TurnType.STRAIGHT -> {
                "직진"
            }
        }

        turnTypeText.text = when {
            intersectionCount <= 1 -> "다음 교차로 $directionText"
            intersectionCount == 2 -> "두 번째 교차로 $directionText"
            else -> "${intersectionCount}번째 교차로 $directionText"
        }

        val ledCommand = getLedCommand(target.type, intersectionCount)
        setLedCommand(ledCommand, "intersectionCount=$intersectionCount")

        val vibrationCommand =
            if (distToTarget <= VIBRATION_START_DISTANCE_M) {
                getVibrationCommand(target.type)
            } else {
                null
            }

        setVibrationCommand(vibrationCommand)

        Log.d(
            "NAV_INTERSECTION",
            "nextIndex=$nextTurnIndex, targetIndex=$targetTurnIndex, " +
                    "count=$intersectionCount, dist=${displayDist}m, type=${target.type}, " +
                    "led=$ledCommand, vibration=$vibrationCommand, ready=$readyToWrite"
        )
    }

    private fun advancePassedEvents(current: LatLng, routeProgress: RouteProgress?) {
        while (nextTurnIndex < turnEvents.size) {
            val event = turnEvents[nextTurnIndex]
            val dist = distance(current, event.location)

            val shouldAdvance =
                dist <= EVENT_PASS_DISTANCE_M || hasPassedEventByRouteProgress(routeProgress, nextTurnIndex)

            if (!shouldAdvance) break

            Log.d(
                "NAV_INTERSECTION",
                "이벤트 통과 처리: idx=$nextTurnIndex, type=${event.type}, dist=${dist.roundToInt()}m"
            )

            stopAllSignals(forceSendStop = true, reason = "event passed")
            nextTurnIndex++
        }
    }

    private fun hasPassedEventByRouteProgress(
        routeProgress: RouteProgress?,
        eventIndex: Int
    ): Boolean {
        val progress = routeProgress ?: return false

        if (progress.distanceToRouteMeters > ROUTE_MATCH_THRESHOLD_M) {
            return false
        }

        val eventProgress = getTurnProgress(eventIndex) ?: return false
        return progress.progressMeters > eventProgress + EVENT_PASS_PROGRESS_MARGIN_M
    }

    private fun findNextDirectionalTurnIndex(startIndex: Int): Int? {
        for (i in startIndex until turnEvents.size) {
            val type = turnEvents[i].type

            if (type == TurnType.LEFT || type == TurnType.RIGHT) {
                return i
            }
        }

        return null
    }

    private fun getLedCommand(type: TurnType, intersectionCount: Int): String? {
        return when {
            intersectionCount <= 1 && type == TurnType.LEFT -> "L25"
            intersectionCount <= 1 && type == TurnType.RIGHT -> "R25"

            intersectionCount == 2 && type == TurnType.LEFT -> "LC50"
            intersectionCount == 2 && type == TurnType.RIGHT -> "RC50"

            else -> null
        }
    }

    private fun getVibrationCommand(type: TurnType): String? {
        return when (type) {
            TurnType.LEFT -> "L"
            TurnType.RIGHT -> "R"
            TurnType.STRAIGHT -> null
        }
    }

    private fun checkArrival(current: LatLng) {
        if (isArrivalNotified) return

        val destination = routePoints.lastOrNull()
            ?: turnEvents.lastOrNull()?.location
            ?: return

        val distToDest = distance(current, destination)

        Log.d("ARRIVAL", "목적지까지 ${distToDest.roundToInt()}m")

        if (distToDest <= ARRIVAL_SIGNAL_DISTANCE_M) {
            handleRideFinishedByArrival()
        }
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

        Log.d("NAV_ROUTE", "폴리라인 포인트=${routePoints.size}, 총 길이=${total.roundToInt()}m")
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

    private fun setLedCommand(command: String?, reason: String) {
        if (activeLedCommand == command) return

        if (activeLedCommand != null) {
            sendStopCommand("LED change: $activeLedCommand -> $command, $reason")
            activeLedCommand = null
        }

        if (command != null) {
            val ok = sendCommandOnce(command, "LED set: $reason")

            if (ok) {
                activeLedCommand = command
            }
        }
    }

    private fun setVibrationCommand(command: String?) {
        if (activeVibrationCommand == command && repeatRunnable != null) {
            return
        }

        stopVibrationLoop()

        if (command == null) return

        activeVibrationCommand = command
        isRepeating = true

        repeatHandler = Handler(Looper.getMainLooper())
        repeatRunnable = object : Runnable {
            override fun run() {
                val currentCommand = activeVibrationCommand ?: return

                sendCommandOnce(currentCommand, "vibration loop")
                repeatHandler?.postDelayed(this, VIBRATION_REPEAT_INTERVAL_MS)
            }
        }

        repeatHandler?.post(repeatRunnable!!)
    }

    private fun stopVibrationLoop() {
        repeatRunnable?.let { repeatHandler?.removeCallbacks(it) }
        repeatRunnable = null
        repeatHandler = null
        activeVibrationCommand = null
        isRepeating = false
    }

    private fun stopAllSignals(forceSendStop: Boolean, reason: String) {
        val hadSignal = activeLedCommand != null || activeVibrationCommand != null || repeatRunnable != null

        stopVibrationLoop()
        activeLedCommand = null

        if (forceSendStop || hadSignal) {
            sendStopCommand(reason)
        }
    }

    private fun sendCommandOnce(command: String, reason: String): Boolean {
        val ok = BluetoothManager.sendText(command)

        if (ok) {
            Log.d("BLE_COMMAND", "전송 성공: $command, reason=$reason, ready=$readyToWrite")
        } else {
            Log.w("BLE_COMMAND", "전송 실패: $command, reason=$reason, ready=$readyToWrite")
        }

        return ok
    }

    private fun sendStopCommand(reason: String) {
        val ok = BluetoothManager.sendText("S")

        if (ok) {
            Log.d("BLE_COMMAND", "정지 전송 성공: S, reason=$reason, ready=$readyToWrite")
        } else {
            Log.w("BLE_COMMAND", "정지 전송 실패: S, reason=$reason, ready=$readyToWrite")
        }
    }

    private fun sendArrivalCommand() {
        if (arrivalSignalSent) return
        arrivalSignalSent = true

        val ok = BluetoothManager.sendText("A")

        if (ok) {
            Log.d("BLE_COMMAND", "도착 신호 전송 성공: A, ready=$readyToWrite")
        } else {
            Log.w("BLE_COMMAND", "도착 신호 전송 실패: A, ready=$readyToWrite")
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

        if (arrivalSignalSent) {
            stopVibrationLoop()
        } else {
            stopAllSignals(forceSendStop = true, reason = "destroy")
        }

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