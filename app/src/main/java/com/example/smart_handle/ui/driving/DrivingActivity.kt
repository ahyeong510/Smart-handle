package com.example.smart_handle.ui.driving

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smart_handle.R
import com.example.smart_handle.maps.TurnEvent
import com.example.smart_handle.maps.TurnType
import com.example.smart_handle.ui.ble.BluetoothManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng

class DrivingActivity : AppCompatActivity(), BluetoothManager.Listener {

    private lateinit var fused: FusedLocationProviderClient

    private lateinit var turnCard: View
    private lateinit var turnIcon: ImageView
    private lateinit var turnDistance: TextView
    private lateinit var turnTypeText: TextView

    private var ble: BluetoothManager? = null
    private var readyToWrite = false

    private var turnEvents: MutableList<TurnEvent> = mutableListOf()
    private var nextTurnIndex = 0

    private var repeatHandler: Handler? = null
    private var repeatRunnable: Runnable? = null
    private var isRepeating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        // 🚗 UI 연결
        turnCard = findViewById(R.id.turnCard)
        turnIcon = findViewById(R.id.turnIcon)
        turnDistance = findViewById(R.id.turnDistance)
        turnTypeText = findViewById(R.id.turnTypeText)

        // 🔥 종료 버튼 → 이전 화면으로 돌아가기 (가장 안정적)
        findViewById<Button>(R.id.btn_stop_route).setOnClickListener {
            finish()
        }

        ble = BluetoothManager(this).also { it.listener = this }
        fused = LocationServices.getFusedLocationProviderClient(this)

        // turnEvents 받아오기
        intent.getParcelableArrayListExtra<TurnEvent>("turn_events")?.let {
            turnEvents.addAll(it)
        }

        checkLocationPermission()
    }

    // --------------------------------------------
    // 위치 권한 처리
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
    // GPS 위치 추적
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
    // 턴 안내 처리
    // --------------------------------------------
    private fun checkTurnEvent(current: LatLng) {

        // 모든 턴 끝 → 카드 숨김
        if (nextTurnIndex >= turnEvents.size) {
            turnCard.visibility = View.GONE
            return
        }

        val target = turnEvents[nextTurnIndex]
        val dist = distance(current, target.location)

        // UI 업데이트
        turnCard.visibility = View.VISIBLE
        turnDistance.text = "${dist.toInt()}m 후"

        when (target.type) {
            TurnType.LEFT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_left)
                turnTypeText.text = "좌회전"
            }
            TurnType.RIGHT -> {
                turnIcon.setImageResource(R.drawable.ic_turn_right)
                turnTypeText.text = "우회전"
            }
            else -> { /* STRAIGHT 안 씀 */ }
        }

        // 50m 진동
        if (!target.trigger50 && dist < 50 && dist >= 25) {
            sendTurnVibration(target.type)
            target.trigger50 = true
        }

        // 25m 진동
        if (!target.trigger25 && dist < 25 && dist >= 10) {
            sendTurnVibration(target.type)
            target.trigger25 = true
        }

        // 10m 반복 진동
        if (dist < 10 && dist >= 3) {
            if (!isRepeating) {
                startRepeatingVibration(target.type)
                isRepeating = true
            }
        }

        // 턴 완료
        if (dist < 3) {
            stopRepeatingVibration()
            nextTurnIndex++
        }
    }

    private fun distance(a: LatLng, b: LatLng): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0]
    }

    // --------------------------------------------
    // 진동 처리
    // --------------------------------------------
    private fun sendTurnVibration(type: TurnType) {
        when (type) {
            TurnType.LEFT -> ble?.sendText("L")
            TurnType.RIGHT -> ble?.sendText("R")
            else -> {}
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
