package com.farhannz.kaitou.helpers

import android.content.Context
import com.farhannz.kaitou.data.room.JmdictDatabase


// Database Manager (Global Access)
object DatabaseManager {
    private lateinit var database: JmdictDatabase
    private var wordsCache: Set<String>? = null
    private var surfaceToUniDic: Map<String, String> = emptyMap()
    fun initialize(context: Context) {
        database = JmdictDatabase.getDatabase(context)
    }

    suspend fun initializeWordsCache() {
        if (wordsCache == null) {
            wordsCache = database.dictionaryDao().getAllDictionaryWords().toHashSet()
//            surfaceToUniDic =  database.dictionaryDao().buildSurfaceToUniDicMap()
        }
    }

    fun getCache(): Set<String>? {
        return wordsCache
    }

    fun getUnidicPos(): Map<String, String> {
        return surfaceToUniDic
    }

    fun getDatabase(): JmdictDatabase {
        if (!::database.isInitialized) {
            throw IllegalStateException("DatabaseManager must be initialized first")
        }
        return database
    }
}