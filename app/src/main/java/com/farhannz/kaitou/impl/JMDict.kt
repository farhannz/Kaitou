package com.farhannz.kaitou.impl

import android.util.Log
import com.farhannz.kaitou.data.models.SenseWithGlosses
import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.data.models.WordFull
import com.farhannz.kaitou.domain.DBDictionary
import com.farhannz.kaitou.domain.LookupResult
import com.farhannz.kaitou.domain.MorphemeData
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.helpers.TokenHelper
import com.farhannz.kaitou.helpers.TokenHelper.coordinatingParticles
import com.farhannz.kaitou.helpers.TokenHelper.extractLocalContextPhrase
import com.farhannz.kaitou.helpers.TokenHelper.sentenceFinalParticles
import com.farhannz.kaitou.helpers.TransformerManager
import com.farhannz.kaitou.helpers.mapPosToJmdict
import com.farhannz.kaitou.helpers.posMapping
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object TagCategories {
    val json = Json { ignoreUnknownKeys = true } // Configure as needed

    // Common/Standard POS (add more as needed based on frequency)
    val COMMON_POS_TAGS = setOf(
        "n",
        "adj-i",
        "v1",
        "v5u",
        "v5k",
        "v5s",
        "v5t",
        "v5r",
        "v5g",
        "v5b",
        "v5m",
        "v5n",
        "v5aru",
        "v5uru",
        "vs",
        "vk",
        "vz", // Common verb types
        "adv",
        "adv-to",
        "prt",
        "aux",
        "aux-v",
        "cop",
        "conj",
        "int",
        "pref",
        "suf",
        "exp"
    )

    // Tags that generally make an entry less preferred for standard lookup
    val NEGATIVE_TAGS = setOf(
        "rare", "arch", "obs", // Rarity/Time
        "sl", "vulg", "X", "joc", "col", "m-sl", "net-sl", // Informality/Vulgarity
        "ktb", "hob", "kyb", "osb", "thb", "kyu", "tsb", "ksb", "rkb", "tsug", // Dialects
        "derog", "euph", // Specific connotations
        "iK", "ik", "io", "oK", "ok", "sK", "sk", "rk" // Irregular/Outdated forms
    )
}

/**
 * Calculates a score for a Sense based on its tags and the token context.
 * Higher score is better.
 */
fun scoreSenseForLookup(sense: SenseWithGlosses, token: TokenInfo): Int {
    var score = 0
    val json = TagCategories.json
    // --- Parse Tags using kotlinx.serialization ---
    val posTags: Set<String> = try {
        json.decodeFromString<List<String>>(sense.sense.partOfSpeech).toSet()
    } catch (e: SerializationException) {
        emptySet()
    } catch (e: IllegalArgumentException) {
        emptySet()
    }

    val miscTags: Set<String> = try {
        json.decodeFromString<List<String>>(sense.sense.misc).toSet()
    } catch (e: SerializationException) {
        emptySet()
    } catch (e: IllegalArgumentException) {
        emptySet()
    }

    val dialectTags: Set<String> = try {
        json.decodeFromString<List<String>>(sense.sense.dialect).toSet()
    } catch (e: SerializationException) {
        emptySet()
    } catch (e: IllegalArgumentException) {
        emptySet()
    }

    val fieldTags: Set<String> = try {
        json.decodeFromString<List<String>>(sense.sense.field).toSet()
    } catch (e: SerializationException) {
        emptySet()
    } catch (e: IllegalArgumentException) {
        emptySet()
    }

    // Combine for easy checking against negative categories
    val allMiscTags = miscTags + dialectTags


    // --- Scoring Logic ---

    // 1. Strongly prefer senses with common POS tags
    if (posTags.any { it in TagCategories.COMMON_POS_TAGS }) {
        score += 20
    } else if (posTags.isNotEmpty()) {
        // Slightly prefer any specific POS over none (which might indicate a headword)
        score += 5
    }

    // 2. Penalize for negative tags (rare, slang, dialect, irregular)
    val negativeTagCount = allMiscTags.count { it in TagCategories.NEGATIVE_TAGS }
    score -= negativeTagCount * 10 // Significant penalty

    // 3. Minor penalty for field-specific terms (unless contextually relevant)
    // This is tricky without domain context, so a small penalty might suffice
    // or you could boost if you know the context (e.g., medical app)
    if (fieldTags.isNotEmpty()) {
        score -= 2
    }

    // 5. Prefer senses that have glosses in the target language
    val hasEnglishGloss = sense.glosses.any { it.lang == "eng" }
    if (hasEnglishGloss) {
        score += 5
    } else {
        score -= 10 // Strongly penalize if no English gloss at all
    }

    // You could add more nuanced scoring based on gloss content, etc.

    return score
}


object JMDict : DBDictionary {
    const val isRework = true
    val LOG_TAG = this::class.simpleName
    val logger = Logger(LOG_TAG!!)
    private var localContextWindow = 3

    fun setLocalContextWindow(k: Int) {
        localContextWindow = k
    }

    private val lookupCache = mutableMapOf<TokenInfo, List<WordFull>>()
    fun clearCache() {
        lookupCache.clear()
    }

    fun isCoordinatingParticle(token: TokenInfo): Boolean {
        return token.partOfSpeech.startsWith("助詞-並立助詞") &&
                coordinatingParticles.containsKey(token.surface)
    }

    fun isSentenceFinalParticle(token: TokenInfo): Boolean {
        return token.partOfSpeech.startsWith("助詞-終助詞") &&
                sentenceFinalParticles.containsKey(token.surface)
    }

    override suspend fun lookup(
        tokenIdx: Int,
        sentenceTokens: List<TokenInfo>,
        selectedEmbedding: FloatArray
    ): LookupResult {
        val token = sentenceTokens[tokenIdx]
        logger.DEBUG(token.toString())

        val skipPOS = listOf("助詞-接続助詞", "助動詞", "記号", "名詞-非自立-一般")
        if (skipPOS.any { token.partOfSpeech.startsWith(it) }) {
            return LookupResult.Skipped("${token.surface} Skipped due to pos - ${token.partOfSpeech} ")
        }
        if (isCoordinatingParticle(token)) {
            return LookupResult.Success(
                MorphemeData(
                    token.baseForm!!,
                    token.reading,
                    coordinatingParticles[token.surface]!!,
                    "coordinating particle"
                )
            )
        } else if (isSentenceFinalParticle(token)) {
            return LookupResult.Success(
                MorphemeData(
                    token.baseForm!!,
                    token.reading,
                    sentenceFinalParticles[token.surface]!!,
                    "sentence final particle"
                )
            )
        }
        val dao = DatabaseManager.getDatabase().dictionaryDao()
        val result = if (lookupCache.containsKey(token)) {
            lookupCache[token]
        } else {
            val currentLookup =
                if (isRework) {
                    dao.lookupWordRework(tokenIdx, sentenceTokens)
                } else {
                    dao.lookupWord(
                        token
                    )
                }
            lookupCache[token] = currentLookup
            lookupCache[token]
        }
        if (result == null || result.isEmpty()) {
            return LookupResult.Error("No results")
        }

        val targetPos = mapPosToJmdict(token.partOfSpeech, token.inflectionType)
        logger.DEBUG(token.toString())
        val localContextPhrase =
            extractLocalContextPhrase(tokenIdx, sentenceTokens, localContextWindow)
        val ranked =
            TokenHelper.rerankGlosses(result, sentenceTokens.joinToString("") { it.surface })
        /*
        //        THESE WERE USED USING SENTENCE EMBEDDING
        //        val localContextEmbedding: FloatArray? = localContextPhrase?.let {
        //            if (it.isNotBlank()) TransformerManager.getEmbeddings(it) else null
        //        }
         */

        val bestWord = result.first()

        val dictForm = token.baseForm?.takeIf { it.isNotEmpty() } ?: token.surface

        val kanjiLine = bestWord.kanji.find { it.text == dictForm }
        val kanaLine = bestWord.kana.find { it.text == dictForm } ?: bestWord.kana.first()

//        val surfaceForm = kanjiLine?.text ?: dictForm
        val reading = kanaLine.text
        val bestSense = ranked.first()

        val meaning = if (token.metadata.containsKey("merged_meaning")) {
            token.metadata["merged_meaning"] as String
        } else {
            bestSense.glossTexts.take(3).joinToString(",")
        }
        val displayPos = bestSense.partOfSpeech.joinToString(",")
        return LookupResult.Success(
            MorphemeData(
                dictForm,
                reading,
                meaning,
                displayPos
            )
        )
    }
}