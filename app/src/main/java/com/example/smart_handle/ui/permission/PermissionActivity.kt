package com.example.smart_handle.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.smart_handle.R
import com.example.smart_handle.auth.LoginActivity   // ⭐ 추가

class PermissionActivity : AppCompatActivity() {

    private val REQUEST_LOCATION = 1001
    private val REQUEST_BLUETOOTH = 1002

    private lateinit var btnLocation: Button
    private lateinit var btnBluetooth: Button
    private lateinit var btnDone: Button

    private var locationGranted = false
    private var bluetoothGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        btnLocation = findViewById(R.id.btnAllowLocation)
        btnBluetooth = findViewById(R.id.btnAllowBluetooth)
        btnDone = findViewById(R.id.btnDone)

        btnDone.isEnabled = false
        btnDone.alpha = 0.5f

        btnLocation.setOnClickListener { requestLocationPermission() }
        btnBluetooth.setOnClickListener { requestBluetoothPermission() }

        btnDone.setOnClickListener {
            if (locationGranted && bluetoothGranted) {
                val intent = Intent(this, LoginActivity::class.java)  // ⭐ 변경
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "모든 권한을 허용해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestLocationPermission() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            locationGranted = true
            btnLocation.text = "✅ 위치 권한 허용됨"
            btnLocation.isEnabled = false
            updateDoneButtonState()
            return
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_LOCATION)
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            if (permissions.all {
                    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                }) {
                bluetoothGranted = true
                btnBluetooth.text = "✅ 블루투스 권한 허용됨"
                btnBluetooth.isEnabled = false
                updateDoneButtonState()
                return
            }

            ActivityCompat.requestPermissions(this, permissions, REQUEST_BLUETOOTH)
        } else {
            bluetoothGranted = true
            btnBluetooth.text = "✅ 블루투스 권한 허용됨"
            btnBluetooth.isEnabled = false
            updateDoneButtonState()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_LOCATION -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    locationGranted = true
                    btnLocation.text = "✅ 위치 권한 허용됨"
                    btnLocation.isEnabled = false
                } else {
                    Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }

            REQUEST_BLUETOOTH -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    bluetoothGranted = true
                    btnBluetooth.text = "✅ 블루투스 권한 허용됨"
                    btnBluetooth.isEnabled = false
                } else {
                    Toast.makeText(this, "블루투스 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        updateDoneButtonState()
    }

    private fun updateDoneButtonState() {
        if (locationGranted && bluetoothGranted) {
            btnDone.isEnabled = true
            btnDone.alpha = 1.0f
        }
    }
}