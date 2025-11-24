package com.example.smart_handle.ui.permission

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.MainActivity
import com.example.smart_handle.R

class PermissionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        val btnLocation = findViewById<Button>(R.id.btnAllowLocation)
        val btnBluetooth = findViewById<Button>(R.id.btnAllowBluetooth)
        val btnDone = findViewById<Button>(R.id.btnDone)

        // 🔥 개발 편의를 위한 임시 코드: 권한 자동 허용 처리
        btnLocation.text = "✅ 위치 권한 허용됨"
        btnBluetooth.text = "✅ 블루투스 권한 허용됨"

        // 버튼 비활성화
        btnLocation.isEnabled = false
        btnBluetooth.isEnabled = false

        // 🔥 완료 버튼 바로 활성화
        btnDone.isEnabled = true
        btnDone.alpha = 1.0f

        // 🔥 완료 버튼 누르면 바로 메인 화면으로 이동
        btnDone.setOnClickListener {
            val intent = Intent(this@PermissionActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
