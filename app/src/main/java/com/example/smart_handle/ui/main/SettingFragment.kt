package com.example.smart_handle.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smart_handle.R
import com.example.smart_handle.auth.LoginActivity
import com.example.smart_handle.ui.fitness.RideHistory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class SettingFragment : Fragment() {

    private lateinit var distanceSeek: SeekBar
    private lateinit var intensitySeek: SeekBar
    private lateinit var distanceText: TextView
    private lateinit var intensityText: TextView
    private lateinit var btnHistory: Button
    private lateinit var btnLogout: Button
    private lateinit var tvUserEmail: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        distanceSeek = view.findViewById(R.id.distanceSeek)
        intensitySeek = view.findViewById(R.id.intensitySeek)
        distanceText = view.findViewById(R.id.distanceText)
        intensityText = view.findViewById(R.id.intensityText)
        btnHistory = view.findViewById(R.id.btnHistory)
        btnLogout = view.findViewById(R.id.btnLogout)
        tvUserEmail = view.findViewById(R.id.tvUserEmail)

        val user = FirebaseAuth.getInstance().currentUser
        tvUserEmail.text = "로그인: ${user?.email ?: "없음"}"

        distanceText.text = "진동 거리 임계값: ${distanceSeek.progress * 10}m"
        intensityText.text = "진동 강도: ${intensitySeek.progress}%"

        distanceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                distanceText.text = "진동 거리 임계값: ${progress * 10}m"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        intensitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intensityText.text = "진동 강도: ${progress}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnHistory.setOnClickListener {
            loadRideHistory()
        }

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()

            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        return view
    }

    private fun loadRideHistory() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "로그인 후 이용해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        btnHistory.isEnabled = false

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("ride_history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { result ->
                btnHistory.isEnabled = true

                if (result.isEmpty) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("지난 기록")
                        .setMessage("저장된 운동 기록이 없어요.")
                        .setPositiveButton("확인", null)
                        .show()
                    return@addOnSuccessListener
                }

                val items = result.documents.mapIndexed { index, doc ->

                    val routeType = doc.getString("routeType") ?: "-"
                    val routeTypeText = when (routeType) {
                        "fitness" -> "운동"
                        "tour" -> "관광지 코스"
                        "navigation" -> "길찾기"
                        else -> routeType
                    }

                    val distanceKm = doc.getDouble("distanceKm") ?: 0.0
                    val durationMin = doc.getLong("durationMin") ?: 0L
                    val actualDurationSec = doc.getLong("actualDurationSec") ?: 0L
                    val satisfaction = doc.getString("satisfaction") ?: "-"
                    val completionPercent = doc.getLong("completionPercent") ?: 0L

                    val durationText = if (actualDurationSec > 0) {
                        "${actualDurationSec / 60}분"
                    } else {
                        "${durationMin}분"
                    }

                    val completionText = if (completionPercent > 0) {
                        " / 주행률: ${completionPercent}%"
                    } else {
                        ""
                    }

                    "${index + 1}. [$routeTypeText] " +
                            "${String.format("%.2f", distanceKm)} km / " +
                            "$durationText / " +
                            "만족도: $satisfaction" +
                            completionText
                }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("지난 기록")
                    .setItems(items, null)
                    .setPositiveButton("닫기", null)
                    .show()
            }
            .addOnFailureListener { e ->
                btnHistory.isEnabled = true
                android.util.Log.e("HISTORY_LOAD", "기록 불러오기 실패", e)

                AlertDialog.Builder(requireContext())
                    .setTitle("오류")
                    .setMessage("기록을 불러오지 못했어요.\n${e.message}")
                    .setPositiveButton("확인", null)
                    .show()
            }
    }
}