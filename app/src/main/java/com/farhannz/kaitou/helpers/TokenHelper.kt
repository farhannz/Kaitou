package com.farhannz.kaitou.helpers

//import ai.djl.nn.core.Embedding
import com.farhannz.kaitou.data.models.Kanji
import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.`data`.models.SenseWithGlosses
import com.farhannz.kaitou.data.models.WordFull
import com.farhannz.kaitou.domain.ModelInput
import kotlinx.serialization.json.Json

object TokenHelper {
    private val LOG_TAG = TokenHelper::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private val json = Json { ignoreUnknownKeys = true }


    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        require(a.size == b.size) { "Vectors must have the same length" }

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).toFloat()
    }

    data class RankedSense(
        val wordId: String,
        val senseId: Int,
        val score: Float,
        val kanjiTexts: List<String>,
        val kanaTexts: List<String>,
        val glossTexts: List<String>,
        val partOfSpeech: List<String>,
    )

    fun rankDictionaryEntries(
        entries: List<WordFull>,
        sentenceEmbedding: FloatArray,
        mappedPOS: List<String>
    ): List<Pair<TokenInfo, String>> {
        if (entries.isEmpty()) {
            println("No dictionary entries found")
            return emptyList()
        }

        println("Found ${entries.size} dictionary entries:")
        // 1. Filter entries by POS match
        val posMatched = entries.filter { wordFull ->
            wordFull.senses.any { (sense, glosses) ->
                val pos = json.decodeFromString<List<String>>(sense.partOfSpeech)
                pos.any { it in mappedPOS }
            }
        }

        // 2. Deduplicate by wordId
        val unique = posMatched.distinctBy { it.word.id }
        val results = mutableListOf<RankedSense>()
        for (word in unique) {
            val kanjiMap = word.kanji.associateBy { it.kanjiId }
            val kanaMap = word.kana.associateBy { it.kanaId }
            val relevantKanji = mutableListOf<String>()
            val relevantKana = mutableListOf<String>()
            for (senseWithGloss in word.senses) {
                val sense = senseWithGloss.sense
                val glosses = senseWithGloss.glosses.map { it.text }
                try {
                    // Determine which kanji/kana apply to this sense
                    val kanji = if (sense.appliesToKanji.contains("*")) {
                        word.kanji.map { it.text }
                    } else {
                        sense.appliesToKanji.mapNotNull { id ->
                            kanjiMap.getValue(id.code).text
                        }
                    }
                    relevantKanji.addAll(kanji)

                    val kana = if (sense.appliesToKana.contains("*")) {
                        word.kana.map { it.text }
                    } else {
                        sense.appliesToKana.mapNotNull { id ->
                            kanaMap.getValue(id.code).text
                        }
                    }
                    relevantKana.addAll(kana)
                } catch (e: Throwable) {
                    logger.ERROR(e.toString())
                }

                // Collect all texts to embed
                val textsToEmbed = (relevantKanji + relevantKana + glosses).distinct()

                // Get max similarity from any of those
                var maxScore = -1.0f
                for (text in textsToEmbed) {
                    val vec = TransformerManager.getEmbeddings(text)
                    val score = cosineSimilarity(sentenceEmbedding.toList(), vec.toList())
                    if (score > maxScore) maxScore = score
                }

                results.add(
                    RankedSense(
                        wordId = word.word.id,
                        senseId = sense.senseId!!,
                        score = maxScore,
                        kanjiTexts = relevantKanji,
                        kanaTexts = relevantKana,
                        glossTexts = glosses,
                        partOfSpeech = json.decodeFromString<List<String>>(sense.partOfSpeech)
                    )
                )
            }
        }

        logger.DEBUG(results.joinToString(","))
// Sort by score descending and take top 3 (or more if needed)
//        val top = scored.sortedByDescending { it.third }.take(3)
//        logger.DEBUG(top.toString())
//        val bestSenses = entries.map { entry ->
//            entry.senses.map { (sense, glosses )->
//                val score = glosses.map {
//
//                }
//            }
//        }
//        val posMatched = entries
//            .filter { entry ->
//                val posList = entry.senses.map {
//                    json.decodeFromString<List<String>>(it.sense.partOfSpeech)
//                }.distinctBy { it }
//                posList.any { it.any { pos -> pos in mappedPOS } }
//            }
//
//        posMatched.forEach {
//            logger.DEBUG(it.toString())
//        }

//        val unique = posMatched.distinctBy { it["word_id"] }


//        val textToVec = mutableMapOf<String, Embedding>()
//        unique.mapNotNull { entry ->
//            entry["kanji_text"] as? String          // 1st choice
//                ?: entry["kana_text"] as? String    // 2nd choice
//                ?: entry["gloss_text"] as? String   // last resort
//        }.distinct().forEach { text ->
//            textToVec[text] = if (useServer) {
//                sendEmbedRequest(text)
//            } else {
//                val ids = tokenizer.encode(text).ids
//                val result = model.run(ModelInput(ids))
//                Embedding(result.output.toList())
//            }
//        }
//
////        // 3. Produce the scored list
//        val scored = posMatched.map { entry ->
//            val vec =
//            val score = cosineSimilarity(sentenceEmbedding.value, vec.value)
//            entry to score
//        }
////
//        return scored.take(3).map {
//            val surface = (it.first["kanji_text"] as String?) ?: it.first["kana_text"] as String
//            val reading = (it.first["kana_text"] as String)
//            val meaning = (it.first["gloss_text"] as String)
//            val pos = (it.first["part_of_speech"] as String)
//
//            Pair(
//                TokenInfo("", surface, pos, reading, "", ""),
//                "$meaning (cosine similarity: ${"%.2f".format(it.second)})"
//            )
//        }
        return emptyList()
    }

    fun mergeWithDictionary(tokens: List<TokenInfo>, dict: Set<String>?, maxGram: Int = 6): List<TokenInfo> {
        // Add before merging
        val filteredTokens = tokens.map {
            if (it.surface in setOf("!", "?", "！", "？", "。", "、")) {
                it.copy(partOfSpeech = "記号") // Mark as symbol
            } else it
        }
        val result = mutableListOf<TokenInfo>()
        var i = 0
        while (i < filteredTokens.size) {
            var merged: TokenInfo? = null
            outer@ for (n in maxGram downTo 2) {
                if (i + n > filteredTokens.size) continue
                val span = filteredTokens.subList(i, i + n)
                val text = span.joinToString("") { it.surface } // Always use surface form

// Special case: Allow particle + auxiliary verb merging
                val allowMerge = span.all { token ->
                    when {
                        token.partOfSpeech.startsWith("助詞") -> true // Allow particles
                        token.partOfSpeech.startsWith("助動詞") -> true
                        token.partOfSpeech.startsWith("名詞") -> true
                        token.partOfSpeech.startsWith("動詞") -> true
                        else -> false
                    }
                }

                if (allowMerge && dict?.contains(text) ?: false) {
                    // Preserve original surface but use first POS
                    merged = TokenInfo(text, text, span.first().partOfSpeech)
                    i += n
                    break@outer
                }
            }
            result.add(merged ?: filteredTokens[i])
            i += if (merged != null) 0 else 1
        }
        return result
    }

    fun correctAuxiliaryNegative(tokens: List<TokenInfo>): List<TokenInfo> {
        return tokens.mapIndexed { i, token ->
            when {
                // Rule 1: Correct ない after particles
                token.surface == "ない" && i > 0 && tokens[i - 1].surface == "は" ->
                    token.copy(partOfSpeech = "助動詞")

                // Rule 2: Correct ない after verbs
                token.surface == "ない" && i > 0 && tokens[i - 1].partOfSpeech.startsWith("動詞") ->
                    token.copy(partOfSpeech = "助動詞")

                // Rule 3: Handle common negative forms
                token.surface in setOf("ん", "ず") ->
                    token.copy(partOfSpeech = "助動詞")

                else -> token
            }
        }
    }

// Taken from https://is.muni.cz/th/opw9s/Thesis___Analyzer_of_Japanese_Texts_for_Language_Learning_Purposes.pdf
    /*
        Algorithm 1 Concatenation of N-grams
        procedure concatenateNgrams(𝑡𝑜𝑘𝑒𝑛𝑠)
            𝑖 ← 0
            while 𝑖 < 𝑡𝑜𝑘𝑒𝑛𝑠.𝑙𝑒𝑛𝑔𝑡ℎ do
                𝑚𝑒𝑟𝑔𝑒𝑑𝑊𝑜𝑟𝑑 ← 𝑚𝑎𝑡𝑐ℎ𝑊𝑜𝑟𝑑(𝑡𝑜𝑘𝑒𝑛𝑠, 𝑖)
                𝑖 ← 𝑖 + 𝑚𝑒𝑟𝑔𝑒𝑑𝑊𝑜𝑟𝑑.𝑛𝑔𝑟𝑎𝑚.𝑙𝑒𝑛𝑔𝑡ℎ
                end while
            end procedure

        function matchWord(𝑡𝑜𝑘𝑒𝑛𝑠, 𝑜𝑓 𝑓 𝑠𝑒𝑡)
            𝑛𝑔𝑟𝑎𝑚𝑆𝑖𝑧𝑒 ← 4
            while 𝑛𝑔𝑟𝑎𝑚𝑆𝑖𝑧𝑒 > 0 do
                𝑛𝑔𝑟𝑎𝑚 ← 𝑡𝑜𝑘𝑒𝑛𝑠[𝑜𝑓 𝑓 𝑠𝑒𝑡 ∶ 𝑜𝑓 𝑓 𝑠𝑒𝑡 + 𝑛𝑔𝑟𝑎𝑚𝑆𝑖𝑧𝑒]
                ▷ Get the surface form from all the n-gram tokens but the last and the dictionary form from the last token:
                𝑝𝑟𝑒𝑓 𝑖𝑥𝑆𝑢𝑟𝑓 𝑎𝑐𝑒𝑠 ← 𝑛𝑔𝑟𝑎𝑚[0 ∶ 𝑛𝑔𝑟𝑎𝑚𝑆𝑖𝑧𝑒 − 1].𝑚𝑎𝑝(𝑡𝑜𝑘𝑒𝑛 → 𝑡𝑜𝑘𝑒𝑛.𝑠𝑢𝑟𝑓 𝑎𝑐𝑒).𝑗𝑜𝑖𝑛()
                𝑤𝑜𝑟𝑑 ← 𝑝𝑟𝑒𝑓 𝑖𝑥𝑆𝑢𝑟𝑓 𝑎𝑐𝑒𝑠 + 𝑛𝑔𝑟𝑎𝑚[𝑛𝑔𝑟𝑎𝑚𝑆𝑖𝑧𝑒 − 1].𝑑𝑖𝑐𝑡
                if 𝑤𝑜𝑟𝑑 in 𝑑𝑖𝑐𝑡𝑖𝑜𝑛𝑎𝑟𝑦 then
                    return {𝑛𝑔𝑟𝑎𝑚 ∶ 𝑛𝑔𝑟𝑎𝑚, 𝑒𝑛𝑡𝑟𝑖𝑒𝑠 ∶ 𝑑𝑖𝑐𝑡𝑖𝑜𝑛𝑎𝑟𝑦[𝑤𝑜𝑟𝑑]}
                end if
                𝑛𝑔𝑟𝑎𝑚𝑆𝑖𝑧𝑒 ← 𝑛𝑔𝑟𝑎𝑚𝑆𝑖𝑧𝑒 − 1
            end while
            return {𝑛𝑔𝑟𝑎𝑚 ∶ [𝑡𝑜𝑘𝑒𝑛𝑠[𝑜𝑓 𝑓 𝑠𝑒𝑡]]}
        end function
    */
}