package com.example.smart_handle.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_route_log")
data class ExerciseRouteLogEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val targetDistance: Double,
    val selectedType: String,
    val distanceKm: Double,
    val estimatedTimeMin: Int,
    val turnCount: Int,
    val timestamp: Long
)