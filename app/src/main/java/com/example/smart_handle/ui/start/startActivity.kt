package com.example.smart_handle.ui.start

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_handle.R
import com.example.smart_handle.ui.permission.PermissionActivity

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val btnStart = findViewById<Button>(R.id.btnStart) // ⚠️ XML의 버튼 id 확인 필요

        btnStart.setOnClickListener {
            val intent = Intent(this, PermissionActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
