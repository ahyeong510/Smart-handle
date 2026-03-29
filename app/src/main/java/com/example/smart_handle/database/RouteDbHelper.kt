package com.example.smart_handle.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RouteDbHelper(context: Context) :
    SQLiteOpenHelper(context, "route_db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE route_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                slope REAL,
                congestion REAL,
                turn_count INTEGER,
                duration REAL,
                selected INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}