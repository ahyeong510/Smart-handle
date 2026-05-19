package com.example.smart_handle.ui.fitness

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class HistoryActivity : AppCompatActivity() {

    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        textView = findViewById(R.id.text_history)

        loadHistory()
    }

    private fun loadHistory() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            textView.text = "로그인이 필요합니다."
            return
        }

        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("users")
            .document(user.uid)
            .collection("ride_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { result ->

                android.util.Log.d("RIDE_LOAD", "불러온 기록 개수: ${result.size()}")

                if (result.isEmpty) {
                    textView.text = "지난 기록 없음"
                    return@addOnSuccessListener
                }

                val builder = StringBuilder()

                for (doc in result.documents) {
                    val distance = doc.getDouble("distanceKm") ?: 0.0
                    val duration = doc.getLong("durationMin") ?: 0
                    val satisfaction = doc.getString("satisfaction") ?: "-"
                    val percent = doc.getLong("completionPercent") ?: 0

                    builder.append(
                        "거리: ${distance}km\n" +
                                "시간: ${duration}분\n" +
                                "주행률: ${percent}%\n" +
                                "만족도: ${satisfaction}\n\n"
                    )
                }

                textView.text = builder.toString()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RIDE_LOAD", "기록 불러오기 실패", e)
                textView.text = "기록 불러오기 실패: ${e.message}"
            }
    }
}