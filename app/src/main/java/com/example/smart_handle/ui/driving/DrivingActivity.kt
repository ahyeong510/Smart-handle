package com.example.smart_handle.ui.driving

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R

class DrivingActivity : AppCompatActivity() {

    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private lateinit var directionImg: ImageView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving_navigation)

        // ✅ View 연결
        distanceText = findViewById(R.id.text_distance)
        timeText = findViewById(R.id.text_time)
        directionImg = findViewById(R.id.img_direction)
        startBtn = findViewById(R.id.btn_start_route)
        stopBtn = findViewById(R.id.btn_stop_route)

        // ✅ 버튼 클릭 테스트용
        startBtn.setOnClickListener {
            Toast.makeText(this, "🚴 주행을 시작합니다.", Toast.LENGTH_SHORT).show()
        }

        stopBtn.setOnClickListener {
            Toast.makeText(this, "🛑 주행을 종료합니다.", Toast.LENGTH_SHORT).show()
            finish() // 메인탭(MainActivity)로 돌아감
        }

        // ✅ 지도 대신 임시 안내 문구 표시
        distanceText.text = "지도 기능 비활성화됨 (테스트용)"
        timeText.text = "현재 지도 SDK 오류 제거 중"
    }
}
