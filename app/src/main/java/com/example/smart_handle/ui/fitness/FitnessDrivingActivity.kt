package com.example.smart_handle.ui.fitness

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.maps.model.LatLng

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

        // 👉 TODO: 지도에 polyline 표시 (이미 DrivingActivity에서 했던 방식 가져오면 됨)

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
                val selected = options[which]
                saveToFirestore(selected)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveToFirestore(satisfaction: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val data = hashMapOf(
            "satisfaction" to satisfaction,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(user.uid)
            .collection("ride_history")
            .add(data)
    }
}