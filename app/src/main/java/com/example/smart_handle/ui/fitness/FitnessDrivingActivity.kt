package com.example.smart_handle.ui.fitness

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FitnessDrivingActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnFinish: Button

    private var isDriving = false
    private var startTime = 0L

    private val firestore = FirebaseFirestore.getInstance()

    // 추천된 경로 정보
    private var routeTitle: String = ""
    private var plannedDistanceKm: Double = 0.0
    private var plannedDurationMin: Int = 0
    private var elevationGain: Int = 0
    private var turnCount: Int = 0
    private var congestionText: String = ""
    private var congestionScore: Double = 0.0
    private var routeScore: Double = 0.0
    private var routeType: String = "fitness"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_fitness)

        btnStart = findViewById(R.id.btnStart)
        btnFinish = findViewById(R.id.btnFinish)

        val routePoints =
            intent.getParcelableArrayListExtra<LatLng>("route_points") ?: arrayListOf()

        routeType = intent.getStringExtra("routeType") ?: "fitness"
        routeTitle = intent.getStringExtra("routeTitle") ?: ""
        plannedDistanceKm = intent.getDoubleExtra("plannedDistanceKm", 0.0)
        plannedDurationMin = intent.getIntExtra("plannedDurationMin", 0)
        elevationGain = intent.getIntExtra("elevationGain", 0)
        turnCount = intent.getIntExtra("turnCount", 0)
        congestionText = intent.getStringExtra("congestionText") ?: ""
        congestionScore = intent.getDoubleExtra("congestionScore", 0.0)
        routeScore = intent.getDoubleExtra("routeScore", 0.0)

        //필요하면 지도에 polyline 표시 필요하면 여기에 추가

        btnStart.setOnClickListener {
            isDriving = true
            startTime = System.currentTimeMillis()
        }

        btnFinish.setOnClickListener {
            if (isDriving) {
                showSatisfactionDialog()
            }
        }
    }

    private fun showSatisfactionDialog() {
        val options = arrayOf("만족", "보통", "불만족")

        AlertDialog.Builder(this)
            .setTitle("운동 만족도")
            .setItems(options) { _, which ->
                val selectedSatisfaction = options[which]
                showRiddenPercentDialog(selectedSatisfaction)
            }
            .setCancelable(false)
            .show()
    }

    private fun showRiddenPercentDialog(satisfaction: String) {
        val options = arrayOf("25%", "50%", "75%", "100%")
        val percentValues = arrayOf(25, 50, 75, 100)

        AlertDialog.Builder(this)
            .setTitle("얼마나 탔나요?")
            .setItems(options) { _, which ->
                val riddenPercent = percentValues[which]
                saveToFirestore(satisfaction, riddenPercent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveToFirestore(satisfaction: String, riddenPercent: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val endTime = System.currentTimeMillis()
        val actualDurationSec =
            if (startTime > 0L) ((endTime - startTime) / 1000L) else 0L

        val effectiveDistanceKm = plannedDistanceKm * riddenPercent / 100.0
        val isCompleted = riddenPercent >= 100

        val data = hashMapOf(
            "routeType" to routeType,
            "routeTitle" to routeTitle,

            // 추천 경로 feature
            "plannedDistanceKm" to plannedDistanceKm,
            "plannedDurationMin" to plannedDurationMin,
            "elevationGain" to elevationGain,
            "turnCount" to turnCount,
            "congestionText" to congestionText,
            "congestionScore" to congestionScore,
            "routeScore" to routeScore,

            // 실제 주행 결과
            "riddenPercent" to riddenPercent,
            "effectiveDistanceKm" to effectiveDistanceKm,
            "actualDurationSec" to actualDurationSec,
            "isCompleted" to isCompleted,
            "satisfaction" to satisfaction,

            // 호환용 필드(기존 기록 화면용)
            "distanceKm" to effectiveDistanceKm,
            "durationSec" to actualDurationSec,

            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(user.uid)
            .collection("ride_history")
            .add(data)
    }
}
