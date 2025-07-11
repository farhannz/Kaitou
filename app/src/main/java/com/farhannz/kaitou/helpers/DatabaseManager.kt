package com.farhannz.kaitou.helpers

import android.content.Context
import com.farhannz.kaitou.data.room.JmdictDatabase


// Database Manager (Global Access)
object DatabaseManager {
    private lateinit var database: JmdictDatabase
    private var wordsCache: Set<String>? = null
    fun initialize(context: Context) {
        database = JmdictDatabase.getDatabase(context)
    }

    suspend fun initializeWordsCache() {
        if (wordsCache == null) {
            wordsCache = database.dictionaryDao().getAllDictionaryWords().toHashSet()
        }
    }

    fun getCache(): Set<String>? {
        return wordsCache
    }

    fun getDatabase(): JmdictDatabase {
        if (!::database.isInitialized) {
            throw IllegalStateException("DatabaseManager must be initialized first")
        }
        return database
    }
}