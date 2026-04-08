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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_fitness)

        btnStart = findViewById(R.id.btnStart)
        btnFinish = findViewById(R.id.btnFinish)

        val routePoints =
            intent.getParcelableArrayListExtra<LatLng>("route_points") ?: arrayListOf()

        // 필요하면 나중에 지도 polyline 표시 추가
        // 현재는 route_points만 받아두는 상태

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
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveToFirestore(satisfaction: String, completionPercent: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val routeType = intent.getStringExtra("routeType") ?: "fitness"
        val routeId = intent.getStringExtra("routeId") ?: ""
        val distanceKm = intent.getDoubleExtra("distanceKm", 0.0)
        val durationMin = intent.getIntExtra("durationMin", 0)
        val elevationGain = intent.getIntExtra("elevationGain", 0)
        val congestionText = intent.getStringExtra("congestionText") ?: "중간"
        val turnCount = intent.getIntExtra("turnCount", 0)

        val actualDurationSec = if (startTime > 0L) {
            (System.currentTimeMillis() - startTime) / 1000
        } else {
            0L
        }

        val data = hashMapOf(
            "routeType" to routeType,
            "routeId" to routeId,
            "distanceKm" to distanceKm,
            "durationMin" to durationMin,
            "elevationGain" to elevationGain,
            "congestionText" to congestionText,
            "turnCount" to turnCount,
            "completionPercent" to completionPercent,
            "satisfaction" to satisfaction,
            "actualDurationSec" to actualDurationSec,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(user.uid)
            .collection("ride_history")
            .add(data)
    }
}