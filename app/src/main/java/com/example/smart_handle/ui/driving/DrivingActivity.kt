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
import kotlin.math.max
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

    private val routePoints = mutableListOf<LatLng>()
    private var routePolyline: Polyline? = null

    // 경로 진행률(progress, m) 기반으로 교차로 통과 여부를 판정하기 위한 캐시
    private val routeCumDistances = mutableListOf<Float>()
    private val turnProgressMeters = mutableListOf<Float>()
    private var routeDistanceCacheBuilt = false
    private var turnProgressCacheBuilt = false
    private var lastRouteProgressM = 0f
    private var hasRouteProgress = false

    // 출발 위치가 첫 교차로와 너무 가까운 경우,
    // 기존 앱처럼 15m 이내 첫 턴/교차로는 이미 지난 것으로 처리하기 위한 1회성 플래그
    private var initialNearTurnCheckDone = false

    private var routeType: String = "navigation"

    private val tourPlaces = arrayListOf<TourPlaceData>()
    private val spokenTourPlaceNames = mutableSetOf<String>()
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    companion object {
        private const val TOUR_PLACE_TRIGGER_DISTANCE_M = 70f

        // LED는 거리 기준이 아니라 남은 교차로 개수 기준으로 제어
        private const val BLUE_LED_REMAINING_INTERSECTION_COUNT = 2
        private const val RED_LED_REMAINING_INTERSECTION_COUNT = 1

        // route progress 기준으로 교차로를 이만큼 지난 뒤 통과 처리
        private const val TURN_PASS_AFTER_PROGRESS_M = 8f

        // route progress 계산이 불가능할 때만 쓰는 예비 거리 기준
        private const val TURN_PASS_DISTANCE_M = 15f

        // 출발지 바로 앞 15m 이내에 첫 교차로/턴이 있을 때 막히지 않도록
        // 경로 시작부의 가까운 이벤트는 앱 진입 직후 1회에 한해 통과 처리
        private const val START_NEAR_TURN_SKIP_DISTANCE_M = 15f
        private const val START_NEAR_TURN_SKIP_PROGRESS_M = 20f

        // GPS가 경로선에서 너무 멀면 해당 위치값은 교차로 통과 판정에 사용하지 않음
        private const val GPS_MAX_ROUTE_SNAP_DISTANCE_M = 35f
        private const val GPS_BACKWARD_TOLERANCE_M = 8f
        private const val EARTH_RADIUS_M = 6371000.0

        // 도착 20m 이내에서 A 전송
        private const val ARRIVAL_SIGNAL_DISTANCE_M = 20f

        // 진동은 실제 회전 50m 전부터 반복
        private const val VIBRATION_START_DISTANCE_M = 50f
        private const val VIBRATION_REPEAT_INTERVAL_MS = 700L
    }

    private data class RouteProjection(
        val progressM: Float,
        val distanceToRouteM: Float,
        val segmentIndex: Int
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

        intent.getParcelableArrayListExtra<LatLng>("route_points")?.let {
            routePoints.addAll(it)
        }

        Log.d("NAV_DISTANCE", "받은 회전 이벤트 수=${turnEvents.size}")
        Log.d("NAV_DISTANCE", "받은 폴리라인 포인트 수=${routePoints.size}")
        logReceivedNavigationData()

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

    private fun logReceivedNavigationData() {
        if (turnEvents.isEmpty()) {
            Log.w("NAV_COUNT", "Intent로 받은 turn_events가 비어 있음")
            return
        }

        turnEvents.forEachIndexed { index, event ->
            val progressText =
                projectPointToRoute(event.location)?.progressM?.roundToInt()?.let { "${it}m" }
                    ?: "unknown"

            Log.d(
                "NAV_COUNT",
                "intentEvent[$index] type=${event.type}, progress=$progressText, " +
                        "lat=${event.location.latitude}, lng=${event.location.longitude}"
            )
        }
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

        stopAllSignals("user stop", forceSendStop = true)
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

        stopAllSignals("arrival before A", forceSendStop = true)
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
        Log.d("LOCATION", "startLocationTracking 호출")

        googleMap?.isMyLocationEnabled = true

        locationRequest = LocationRequest.Builder(700)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fused.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // 실내/정지 상태에서는 위치 콜백이 바로 오지 않을 수 있어서,
        // 마지막 위치가 있으면 한 번 즉시 계산을 돌려 로그와 첫 안내가 뜨게 한다.
        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d(
                        "LOCATION",
                        "lastLocation 수신: lat=${loc.latitude}, lng=${loc.longitude}, " +
                                "accuracy=${loc.accuracy}m"
                    )
                    handleLocation(loc, "lastLocation")
                } else {
                    Log.w("LOCATION", "lastLocation 없음: 실제 GPS 콜백 대기")
                }
            }
            .addOnFailureListener { e ->
                Log.e("LOCATION", "lastLocation 조회 실패", e)
            }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            handleLocation(loc, "callback")
        }
    }

    private fun handleLocation(loc: Location, source: String) {
        Log.d(
            "LOCATION",
            "location[$source]: lat=${loc.latitude}, lng=${loc.longitude}, " +
                    "accuracy=${loc.accuracy}m"
        )

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

    private fun checkTourPlaceArrival(current: LatLng) {
        if (!isTtsReady || tourPlaces.isEmpty()) return

        for (place in tourPlaces) {
            val uniqueKey =
                if (place.contentId.isNotBlank()) {
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
        val displayName =
            when {
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

        advancePassedTurnEvents(current)

        val targetTurnIndex = findNextDirectionalTurnIndex(nextTurnIndex)

        if (targetTurnIndex == null) {
            setLedCommand(null, "no next turn")
            setVibrationCommand(null)

            if (!isArrivalNotified) {
                turnCard.visibility = View.GONE
            }

            return
        }

        val target = turnEvents[targetTurnIndex]
        val distToTarget = distance(current, target.location)
        val displayDist = distToTarget.roundToInt()
        val remainingIntersectionCount = countRemainingIntersectionsToTarget(targetTurnIndex)

        updateTurnCard(target, displayDist, remainingIntersectionCount)

        val ledCommand = getIntersectionBasedLedCommand(
            target.type,
            remainingIntersectionCount
        )
        setLedCommand(
            ledCommand,
            "intersections=${remainingIntersectionCount}, dist=${displayDist}m"
        )

        val vibrationCommand =
            if (distToTarget <= VIBRATION_START_DISTANCE_M) {
                getVibrationCommand(target.type)
            } else {
                null
            }

        setVibrationCommand(vibrationCommand)

        Log.d(
            "NAV_DISTANCE",
            "nextIndex=$nextTurnIndex, targetIndex=$targetTurnIndex, " +
                    "remainingIntersections=$remainingIntersectionCount, " +
                    "dist=${displayDist}m, type=${target.type}, " +
                    "led=$ledCommand, vibration=$vibrationCommand, ready=$readyToWrite"
        )
    }

    private fun advancePassedTurnEvents(current: LatLng) {
        ensureTurnProgressCache()
        handleInitialNearStartTurnIfNeeded(current)

        if (turnProgressCacheBuilt) {
            val currentProgress = getStableRouteProgress(current) ?: return

            while (nextTurnIndex < turnEvents.size) {
                val eventProgress = turnProgressMeters.getOrNull(nextTurnIndex) ?: break

                if (currentProgress < eventProgress + TURN_PASS_AFTER_PROGRESS_M) {
                    break
                }

                val event = turnEvents[nextTurnIndex]

                Log.d(
                    "NAV_DISTANCE",
                    "경로 진행률 기준 교차로 통과: idx=$nextTurnIndex, " +
                            "type=${event.type}, currentProgress=${currentProgress.roundToInt()}m, " +
                            "eventProgress=${eventProgress.roundToInt()}m"
                )

                stopAllSignals("turn/intersection passed by route progress", forceSendStop = true)
                nextTurnIndex++
            }

            return
        }

        // routePoints가 없어서 progress 계산이 불가능할 때만 쓰는 예비 처리
        while (nextTurnIndex < turnEvents.size) {
            val event = turnEvents[nextTurnIndex]
            val dist = distance(current, event.location)

            if (dist > TURN_PASS_DISTANCE_M) {
                break
            }

            Log.d(
                "NAV_DISTANCE",
                "거리 예비 기준 교차로 통과: idx=$nextTurnIndex, type=${event.type}, dist=${dist.roundToInt()}m"
            )

            stopAllSignals("turn/intersection passed by fallback distance", forceSendStop = true)
            nextTurnIndex++
        }
    }

    /**
     * 출발지에서 첫 교차로/턴이 5~15m 정도로 너무 가까우면,
     * 사용자가 정확한 guide 좌표를 지나지 않아도 다음 안내로 넘어가도록 하는 보정.
     *
     * 조건을 너무 넓게 잡으면 실제 첫 회전을 놓칠 수 있으므로:
     * 1) 앱 진입 후 첫 위치에서만 실행하고,
     * 2) 경로 시작부(progress 20m 이내)에 있는 이벤트만 대상으로 하며,
     * 3) 현재 위치와 이벤트 좌표가 15m 이내일 때만 통과 처리한다.
     */
    private fun handleInitialNearStartTurnIfNeeded(current: LatLng) {
        if (initialNearTurnCheckDone) return

        val projection =
            if (turnProgressCacheBuilt) {
                projectPointToRoute(current)
            } else {
                null
            }

        if (projection != null && projection.distanceToRouteM > GPS_MAX_ROUTE_SNAP_DISTANCE_M) {
            Log.w(
                "NAV_DISTANCE",
                "시작 근처 교차로 자동 통과 보류: GPS가 경로선에서 멂, " +
                        "snapDist=${projection.distanceToRouteM.roundToInt()}m"
            )
            return
        }

        initialNearTurnCheckDone = true

        var skippedCount = 0

        while (nextTurnIndex < turnEvents.size) {
            val event = turnEvents[nextTurnIndex]
            val distToEvent = distance(current, event.location)
            val eventProgress = turnProgressMeters.getOrNull(nextTurnIndex)

            val isNearCurrent = distToEvent <= START_NEAR_TURN_SKIP_DISTANCE_M
            val isNearRouteStart =
                eventProgress == null || eventProgress <= START_NEAR_TURN_SKIP_PROGRESS_M

            if (!isNearCurrent || !isNearRouteStart) {
                break
            }

            Log.d(
                "NAV_DISTANCE",
                "시작 위치 15m 이내 교차로 자동 통과: idx=$nextTurnIndex, " +
                        "type=${event.type}, dist=${distToEvent.roundToInt()}m, " +
                        "eventProgress=${eventProgress?.roundToInt()?.toString() ?: "unknown"}m"
            )

            nextTurnIndex++
            skippedCount++
        }

        if (skippedCount > 0) {
            stopAllSignals(
                "startup near turn skipped count=$skippedCount",
                forceSendStop = true
            )
        }
    }

    private fun updateTurnCard(
        target: TurnEvent,
        displayDistance: Int,
        remainingIntersectionCount: Int
    ) {
        turnCard.visibility = View.VISIBLE
        turnDistance.text = "${displayDistance}m 후"

        val directionText =
            when (target.type) {
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

        turnTypeText.text =
            when {
                target.type == TurnType.STRAIGHT -> directionText
                remainingIntersectionCount <= 1 -> "다음 교차로에서 $directionText"
                remainingIntersectionCount == 2 -> "두 번째 교차로에서 $directionText"
                else -> "${remainingIntersectionCount}번째 교차로에서 $directionText"
            }
    }

    private fun getIntersectionBasedLedCommand(
        type: TurnType,
        remainingIntersectionCount: Int
    ): String? {
        if (type == TurnType.STRAIGHT) return null

        return when (remainingIntersectionCount) {
            BLUE_LED_REMAINING_INTERSECTION_COUNT -> {
                when (type) {
                    TurnType.LEFT -> "LC2"
                    TurnType.RIGHT -> "RC2"
                    TurnType.STRAIGHT -> null
                }
            }

            RED_LED_REMAINING_INTERSECTION_COUNT -> {
                when (type) {
                    TurnType.LEFT -> "LC1"
                    TurnType.RIGHT -> "RC1"
                    TurnType.STRAIGHT -> null
                }
            }

            else -> {
                null
            }
        }
    }

    private fun getVibrationCommand(type: TurnType): String? {
        return when (type) {
            TurnType.LEFT -> "LV"
            TurnType.RIGHT -> "RV"
            TurnType.STRAIGHT -> null
        }
    }

    private fun countRemainingIntersectionsToTarget(targetTurnIndex: Int): Int {
        if (targetTurnIndex < nextTurnIndex) return 0

        var count = 0

        for (i in nextTurnIndex..targetTurnIndex) {
            if (isCountableIntersection(turnEvents[i])) {
                count++
            }
        }

        return count
    }

    private fun isCountableIntersection(event: TurnEvent): Boolean {
        return event.type == TurnType.LEFT ||
                event.type == TurnType.RIGHT ||
                event.type == TurnType.STRAIGHT
    }

    private fun ensureRouteDistanceCache() {
        if (routeDistanceCacheBuilt) return

        routeCumDistances.clear()

        if (routePoints.isEmpty()) {
            routeDistanceCacheBuilt = true
            return
        }

        routeCumDistances.add(0f)

        var cumulative = 0f
        for (i in 1 until routePoints.size) {
            cumulative += distance(routePoints[i - 1], routePoints[i])
            routeCumDistances.add(cumulative)
        }

        routeDistanceCacheBuilt = true
    }

    private fun ensureTurnProgressCache() {
        if (turnProgressCacheBuilt) return

        ensureRouteDistanceCache()

        turnProgressMeters.clear()

        if (routePoints.size < 2 || routeCumDistances.size != routePoints.size) {
            turnProgressCacheBuilt = false
            return
        }

        for (event in turnEvents) {
            val projection = projectPointToRoute(event.location)
            if (projection == null) {
                turnProgressCacheBuilt = false
                turnProgressMeters.clear()
                return
            }

            turnProgressMeters.add(projection.progressM)
        }

        turnProgressCacheBuilt = turnProgressMeters.size == turnEvents.size
    }

    private fun getStableRouteProgress(current: LatLng): Float? {
        val projection = projectPointToRoute(current) ?: return null

        if (projection.distanceToRouteM > GPS_MAX_ROUTE_SNAP_DISTANCE_M) {
            Log.w(
                "NAV_DISTANCE",
                "GPS가 경로선에서 멀어 교차로 통과 판정 보류: " +
                        "snapDist=${projection.distanceToRouteM.roundToInt()}m"
            )
            return null
        }

        val rawProgress = projection.progressM

        val stableProgress =
            if (!hasRouteProgress) {
                rawProgress
            } else if (rawProgress + GPS_BACKWARD_TOLERANCE_M < lastRouteProgressM) {
                // GPS 튐으로 진행률이 뒤로 밀리는 경우 무시
                lastRouteProgressM
            } else {
                max(lastRouteProgressM, rawProgress)
            }

        lastRouteProgressM = stableProgress
        hasRouteProgress = true

        return stableProgress
    }

    private fun projectPointToRoute(point: LatLng): RouteProjection? {
        ensureRouteDistanceCache()

        if (routePoints.size < 2 || routeCumDistances.size != routePoints.size) {
            return null
        }

        val origin = routePoints.first()
        val pointXY = toLocalXY(point, origin)

        var bestProgress = 0f
        var bestDistance = Float.MAX_VALUE
        var bestSegmentIndex = 0

        for (i in 0 until routePoints.size - 1) {
            val a = toLocalXY(routePoints[i], origin)
            val b = toLocalXY(routePoints[i + 1], origin)

            val abX = b.first - a.first
            val abY = b.second - a.second
            val apX = pointXY.first - a.first
            val apY = pointXY.second - a.second
            val abLenSq = abX * abX + abY * abY

            if (abLenSq <= 0.0001) continue

            val t = ((apX * abX + apY * abY) / abLenSq).coerceIn(0.0, 1.0)
            val nearestX = a.first + abX * t
            val nearestY = a.second + abY * t
            val dx = pointXY.first - nearestX
            val dy = pointXY.second - nearestY
            val snapDistance = sqrt(dx * dx + dy * dy).toFloat()

            if (snapDistance < bestDistance) {
                val segmentLength = distance(routePoints[i], routePoints[i + 1])
                bestProgress = routeCumDistances[i] + segmentLength * t.toFloat()
                bestDistance = snapDistance
                bestSegmentIndex = i
            }
        }

        return RouteProjection(
            progressM = bestProgress,
            distanceToRouteM = bestDistance,
            segmentIndex = bestSegmentIndex
        )
    }

    private fun toLocalXY(
        point: LatLng,
        origin: LatLng
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(origin.latitude)

        val x = Math.toRadians(point.longitude - origin.longitude) *
                EARTH_RADIUS_M *
                cos(latRad)

        val y = Math.toRadians(point.latitude - origin.latitude) *
                EARTH_RADIUS_M

        return Pair(x, y)
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

    private fun checkArrival(current: LatLng) {
        if (isArrivalNotified) return

        val destination =
            routePoints.lastOrNull()
                ?: turnEvents.lastOrNull()?.location
                ?: return

        val distToDest = distance(current, destination)

        Log.d("ARRIVAL", "목적지까지 ${distToDest.roundToInt()}m")

        if (distToDest <= ARRIVAL_SIGNAL_DISTANCE_M) {
            handleRideFinishedByArrival()
        }
    }

    private fun setLedCommand(command: String?, reason: String) {
        if (activeLedCommand == command) return

        if (command == null) {
            activeLedCommand = null

            // S는 모든 출력을 끄는 명령이므로 LED 변경 때마다 보내지 않음.
            // 진동이 없는 상태에서 LED만 꺼야 할 때만 S를 보낸다.
            if (activeVibrationCommand == null && repeatRunnable == null) {
                sendStopCommand("LED off: $reason")
            }

            return
        }

        val ok = sendCommandOnce(command, "LED set: $reason")

        if (ok) {
            activeLedCommand = command
        }
    }

    private fun setVibrationCommand(command: String?) {
        if (activeVibrationCommand == command && repeatRunnable != null) {
            return
        }

        stopVibrationLoop()

        if (command == null) return

        activeVibrationCommand = command

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
        repeatRunnable?.let {
            repeatHandler?.removeCallbacks(it)
        }

        repeatRunnable = null
        repeatHandler = null
        activeVibrationCommand = null
    }

    private fun stopAllSignals(
        reason: String,
        forceSendStop: Boolean
    ) {
        val hadSignal =
            activeLedCommand != null ||
                    activeVibrationCommand != null ||
                    repeatRunnable != null

        stopVibrationLoop()
        activeLedCommand = null

        if (forceSendStop || hadSignal) {
            sendStopCommand(reason)
        }
    }

    private fun sendCommandOnce(
        command: String,
        reason: String
    ): Boolean {
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

    private fun distance(
        a: LatLng,
        b: LatLng
    ): Float {
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
            stopAllSignals("destroy", forceSendStop = true)
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

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
        }

        if (routePoints.isNotEmpty()) {
            val polylineOptions =
                PolylineOptions()
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