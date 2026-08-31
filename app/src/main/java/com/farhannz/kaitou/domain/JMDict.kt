package com.farhannz.kaitou.domain

import com.farhannz.kaitou.data.models.TokenInfo

interface DBDictionary {
    suspend fun lookup(
        tokenIdx: Int,
        sentenceTokens: List<TokenInfo>,
        selectedEmbedding: FloatArray
    ): LookupResult
}

sealed class LookupResult {
    data class Success(val morphemeData: MorphemeData) : LookupResult()
    data class Skipped(val message: String) : LookupResult()
    data class Error(val message: String, val exception: Throwable? = null) : LookupResult()
}

data class MorphemeData(
    val text: String,
    val reading: String,
    val meaning: String,
    val type: String,
    val confidence: Float = 1.0f,
    val alternatives: List<String> = emptyList()
)