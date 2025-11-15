package com.example.smart_handle.ui.driving

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart_handle.R
import com.example.smart_handle.maps.MapDirectionHelper
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import com.example.smart_handle.ui.ble.BluetoothManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

class DrivingActivity : AppCompatActivity(), BluetoothManager.Listener {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var directionImg: ImageView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView

    private var ble: BluetoothManager? = null
    private var readyToWrite = false

    /** 턴 이벤트 리스트 */
    private var turnEvents: MutableList<TurnEvent> = mutableListOf()
    private var nextTurnIndex = 0

    /** 반복 진동용 */
    private var repeatHandler: Handler? = null
    private var repeatRunnable: Runnable? = null
    private var isRepeating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        directionImg = findViewById(R.id.img_direction)
        distanceText = findViewById(R.id.text_distance)
        timeText = findViewById(R.id.text_time)

        ble = BluetoothManager(this).also { it.listener = this }
        fused = LocationServices.getFusedLocationProviderClient(this)

        // 1) turnEvents 받아오기
        val tempList = intent.getParcelableArrayListExtra<TurnEvent>("turn_events")
        if (tempList != null) {
            turnEvents.addAll(tempList)
        }

        // 2) 위치 권한 확인 → 추적 시작
        checkLocationPermission()
    }

    // --------------------------------------------
    // GPS 위치 권한
    // --------------------------------------------

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

    // --------------------------------------------
    // GPS 추적 시작
    // --------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {

        val req = LocationRequest.Builder(700)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val cur = LatLng(loc.latitude, loc.longitude)

            checkTurnEvent(cur)
        }
    }

    // --------------------------------------------
    // 턴 이벤트 처리
    // --------------------------------------------

    private fun checkTurnEvent(current: LatLng) {
        if (nextTurnIndex >= turnEvents.size) return

        val target = turnEvents[nextTurnIndex]
        val dist = distance(current, target.location)

        distanceText.text = "다음 턴까지: %.1f m".format(dist)

        // --------------------------
        // 🔵 50m 알림
        // --------------------------
        if (!target.trigger50 && dist < 50 && dist >= 25) {
            sendTurnVibration(target.type)
            target.trigger50 = true
        }

        // --------------------------
        // 🟡 25m 알림
        // --------------------------
        if (!target.trigger25 && dist < 25 && dist >= 10) {
            sendTurnVibration(target.type)
            target.trigger25 = true
        }

        // --------------------------
        // 🔴 10m 반복 진동
        // --------------------------
        if (dist < 10 && dist >= 3) {
            if (!isRepeating) {
                startRepeatingVibration(target.type)
                isRepeating = true
            }
        }

        // --------------------------
        // ✔ 턴 통과
        // --------------------------
        if (dist < 3) {
            stopRepeatingVibration()
            nextTurnIndex++
        }
    }

    private fun distance(a: LatLng, b: LatLng): Float {
        val res = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, res)
        return res[0]
    }

    // --------------------------------------------
    // BLE 진동 함수
    // --------------------------------------------

    private fun sendTurnVibration(type: TurnType) {
        when (type) {
            TurnType.LEFT -> ble?.sendText("L")   // 왼쪽 모터
            TurnType.RIGHT -> ble?.sendText("R")  // 오른쪽 모터
            TurnType.STRAIGHT -> return
        }
    }

    private fun startRepeatingVibration(type: TurnType) {
        repeatHandler = Handler(Looper.getMainLooper())
        repeatRunnable = object : Runnable {
            override fun run() {
                sendTurnVibration(type)
                repeatHandler?.postDelayed(this, 2000)
            }
        }
        repeatHandler?.post(repeatRunnable!!)
    }

    private fun stopRepeatingVibration() {
        repeatRunnable?.let { repeatHandler?.removeCallbacks(it) }
        isRepeating = false
    }

    // --------------------------------------------
    // BLE Listener
    // --------------------------------------------

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
    }

    override fun onStateChanged(connected: Boolean, deviceName: String?) {}
    override fun onLog(msg: String) {}
}
