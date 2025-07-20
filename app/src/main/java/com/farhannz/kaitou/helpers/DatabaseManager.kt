package com.farhannz.kaitou.helpers

import android.content.Context
import com.farhannz.kaitou.data.room.JmdictDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


// Database Manager (Global Access)
object DatabaseManager {
    private lateinit var database: JmdictDatabase
    private var wordsCache: Set<String>? = null
    private var surfaceToUniDic: Map<String, String> = emptyMap()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun initialize(context: Context) {
        database = JmdictDatabase.getDatabase(context)
        scope.launch {
            initializeWordsCache()
        }
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