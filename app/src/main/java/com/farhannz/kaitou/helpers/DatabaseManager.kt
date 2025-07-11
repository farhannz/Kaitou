package com.farhannz.kaitou.helpers

import android.content.Context
import com.farhannz.kaitou.data.room.JmdictDatabase


// Database Manager (Global Access)
object DatabaseManager {
    private lateinit var database: JmdictDatabase

    fun initialize(context: Context) {
        database = JmdictDatabase.getDatabase(context)
    }

    fun getDatabase(): JmdictDatabase {
        if (!::database.isInitialized) {
            throw IllegalStateException("DatabaseManager must be initialized first")
        }
        return database
    }
}