package com.example.smart_handle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RideDao {

    @Insert
    suspend fun insertRide(ride: RideEntity)

    @Query("SELECT * FROM rides ORDER BY date DESC")
    suspend fun getAllRides(): List<RideEntity>
}
