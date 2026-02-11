package com.example.smart_handle.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val distance: Double,      // km
    val duration: Long,        // seconds
    val elevationGain: Double, // 고도 (지금은 0.0)
    val completed: Boolean,    // 완주 여부
    val date: Long             // 날짜
)
