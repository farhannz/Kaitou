package com.farhannz.kaitou.presentation.ocr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.domain.LookupResult
import com.farhannz.kaitou.domain.MorphemeData
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.posMapping
import com.farhannz.kaitou.impl.JMDict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PopupViewModel(
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val LOG_TAG = "PopupViewModel"
        private val logger = Logger(LOG_TAG)
    }

    private val _morphemeStates = mutableStateMapOf<Int, MorphemeLookupState>()
    val morphemeStates: Map<Int, MorphemeLookupState> get() = _morphemeStates

    var isLoading by mutableStateOf(false)
        private set

    fun lookupMorpheme(
        tokenIdx: Int,
        sentenceTokens: List<TokenInfo>,
        selectedEmbedding: FloatArray
    ) {
        if (_morphemeStates.containsKey(tokenIdx)) return

        _morphemeStates[tokenIdx] = MorphemeLookupState.Loading
        isLoading = true

        coroutineScope.launch {
            val result = withContext(Dispatchers.Default) {
                JMDict.lookup(tokenIdx, sentenceTokens, selectedEmbedding)
            }

            _morphemeStates[tokenIdx] = when (result) {
                is LookupResult.Success -> MorphemeLookupState.Done(result.morphemeData)
                is LookupResult.Skipped -> {
                    logger.INFO(result.message)
                    MorphemeLookupState.Skipped
                }
                is LookupResult.Error -> MorphemeLookupState.NotFound
            }
            isLoading = false
        }
    }

    fun getMorphemeState(tokenIdx: Int): MorphemeLookupState {
        return _morphemeStates[tokenIdx] ?: MorphemeLookupState.Loading
    }

    fun getFallbackMorphemeData(token: TokenInfo): MorphemeData {
        val meaning = token.metadata["merged_meaning"] as? String ?: ""
        val reading = token.reading.ifEmpty { "" }
        return MorphemeData(
            text = token.baseForm ?: token.surface,
            reading = reading,
            meaning = meaning,
            type = posMapping[token.partOfSpeech]?.joinToString(",") ?: ""
        )
    }

    fun clearStates() {
        _morphemeStates.clear()
        isLoading = false
    }
}

sealed class MorphemeLookupState {
    object Loading : MorphemeLookupState()
    object Skipped : MorphemeLookupState()
    data class Done(val data: MorphemeData) : MorphemeLookupState()
    object NotFound : MorphemeLookupState()
}