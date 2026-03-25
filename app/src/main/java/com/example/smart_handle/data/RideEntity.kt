package com.example.smart_handle.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ride_table")
data class RideEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val distanceKm: Double,
    val durationSec: Int,
    val avgSpeed: Double,
    val completed: Boolean,

    val timestamp: Long,

    // 🔥 추가 (핵심)
    val exerciseLogId: Int?,      // 어떤 추천에서 온 건지
    val completionRatio: Double  // 얼마나 탔는지 (0~1)
)