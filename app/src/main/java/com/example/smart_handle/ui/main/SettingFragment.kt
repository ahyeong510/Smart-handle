package com.example.smart_handle.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.smart_handle.R

class SettingFragment : Fragment() {

    private lateinit var distanceSeek: SeekBar
    private lateinit var intensitySeek: SeekBar
    private lateinit var distanceText: TextView
    private lateinit var intensityText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        distanceSeek = view.findViewById(R.id.distanceSeek)
        intensitySeek = view.findViewById(R.id.intensitySeek)
        distanceText = view.findViewById(R.id.distanceText)
        intensityText = view.findViewById(R.id.intensityText)

        // 거리 임계값 조절
        distanceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                distanceText.text = "진동 거리 임계값: ${progress * 10}m"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 진동 강도 조절
        intensitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intensityText.text = "진동 강도: ${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        return view
    }
}

