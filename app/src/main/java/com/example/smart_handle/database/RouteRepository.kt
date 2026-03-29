package com.example.smart_handle.database

import android.content.ContentValues
import android.content.Context
import com.example.smart_handle.database.RouteDbHelper

class RouteRepository(context: Context) {

    private val dbHelper = RouteDbHelper(context)

    fun insertRoute(
        slope: Double,
        congestion: Double,
        turnCount: Int,
        duration: Double,
        selected: Int
    ) {
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("slope", slope)
            put("congestion", congestion)
            put("turn_count", turnCount)
            put("duration", duration)
            put("selected", selected)
        }

        db.insert("route_history", null, values)
        db.close()
    }
}