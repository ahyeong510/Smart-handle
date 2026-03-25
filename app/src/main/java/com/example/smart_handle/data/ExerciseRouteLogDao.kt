package com.example.smart_handle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExerciseRouteLogDao {

    @Insert
    suspend fun insertLog(log: ExerciseRouteLogEntity): Long   // 🔥 중요

    @Query("SELECT * FROM exercise_route_log")
    suspend fun getAllLogs(): List<ExerciseRouteLogEntity>
}