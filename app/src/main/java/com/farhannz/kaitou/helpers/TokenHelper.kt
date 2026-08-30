package com.farhannz.kaitou.helpers

import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.data.models.WordFull
import kotlinx.serialization.json.Json
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.*
import org.apache.lucene.analysis.tokenattributes.*
import java.io.StringReader
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

object TokenHelper {
    private val LOG_TAG = TokenHelper::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private val json = Json { ignoreUnknownKeys = true }

    val coordinatingParticles = mapOf(
        "と" to "and / with (coordinating particle)",
        "や" to "and / such as (listing)",
        "とか" to "and / or (informal listing)",
        "も" to "also / too",
        "し" to "and / moreover"
    )

    val sentenceFinalParticles = mapOf(
        "か" to "question marker",
        "ね" to "seeking agreement / softener",
        "よ" to "assertion / emphasis",
        "な" to "prohibition / exclamation (masc.)",
        "ぞ" to "strong assertion (masc.)",
        "さ" to "casual emphasis",
        "わ" to "soft feminine tone",
        "の" to "soft assertion / explanation",
        "ぜ" to "masculine emphasis / encouragement",
        "かな" to "self-questioning / uncertainty",
        "かしら" to "I wonder… (feminine)"
    )

    fun tokenizeWithPOS(text: String): List<TokenInfo> {
        val tokenizer = JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.NORMAL)
        tokenizer.setReader(StringReader(text))
        tokenizer.reset()
        val readingAttr = tokenizer.getAttribute(ReadingAttribute::class.java)
        val inflectionAttr = tokenizer.getAttribute(InflectionAttribute::class.java)
        val surfaceAttr = tokenizer.getAttribute(CharTermAttribute::class.java)
        val baseAttr = tokenizer.getAttribute(BaseFormAttribute::class.java)
        val posAttr = tokenizer.getAttribute(PartOfSpeechAttribute::class.java)

        val result = mutableListOf<TokenInfo>()

        while (tokenizer.incrementToken()) {
            result.add(
                TokenInfo(
                    surface = surfaceAttr.toString(),
                    baseForm = baseAttr?.baseForm ?: surfaceAttr.toString(),
                    partOfSpeech = posAttr.partOfSpeech ?: "未知",
                    reading = readingAttr?.reading ?: "",
                    inflectionType = inflectionAttr?.inflectionType ?: "",
                    inflectionForm = inflectionAttr?.inflectionForm ?: ""
                )
            )
        }

        tokenizer.end()
        tokenizer.close()

        return result
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) Float.NEGATIVE_INFINITY else (dot / denom).toFloat()
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

    data class RankedContextBag(
        val ranked: RankedSense,
        val context: String,
        val gloss: String
    )

    // Utility to L2‐normalize your float arrays
    fun normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.map { it * it }.sum())
        return if (norm > 0f) vec.map { it / norm }.toFloatArray() else vec
    }

    fun flat2DArray(input: Array<LongArray>): LongArray {
        val batchSize = input.size
        val seqLen = input[0].size
        val flattened = LongArray(batchSize * seqLen)
        for (i in input.indices) {
            System.arraycopy(input[i], 0, flattened, i * seqLen, seqLen)
        }

        return flattened
    }

    fun padSequence(input: LongArray, maxLen: Int): LongArray {
        val padded = input + LongArray(maxLen - input.size) { 0L }
        return padded
    }

    fun sigmoid(logits: Float): Float {
        return 1.0f / (1.0f + exp(-logits))
    }

    fun softmax(logits: List<Float>): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expLogits = FloatArray(logits.size) { exp(logits[it] - maxLogit) }
        val sumExp = expLogits.sum()
        return FloatArray(logits.size) { expLogits[it] / sumExp }
    }

    // This function can be a top-level function or a member of TokenHelper, etc.
    fun extractLocalContextPhrase(
        currentTokenIndex: Int,
        allTokens: List<TokenInfo>,
        k: Int = 3
    ): String? {
        if (allTokens.isEmpty()) return null
        val startIndex = maxOf(0, currentTokenIndex - k)
        val endIndex = minOf(allTokens.size - 1, currentTokenIndex + k)

        val clauseStart = findClauseStart(currentTokenIndex, allTokens, startIndex)
        val clauseEnd = findClauseEnd(currentTokenIndex, allTokens, endIndex)

        // Ensure valid boundaries
        if (clauseStart > clauseEnd) {
            // Fallback to original window if clause detection fails
            val localTokens = allTokens.subList(startIndex, endIndex + 1)
            return localTokens.joinToString("") { it.surface }
        }

        val localTokens = allTokens.subList(clauseStart, clauseEnd + 1)
//        logger.DEBUG(localTokens.toString())
        return localTokens.joinToString("") { it.surface }
    }


    fun patchPotentialGodan(baseForm: String, inflectionType: String): String {
        val godanPotentialSuffixes =
            listOf("ける", "げる", "てる", "でる", "べる", "める", "れる", "せる")

        val isPotential = inflectionType == "一段" &&
                godanPotentialSuffixes.any { baseForm.endsWith(it) }
        // Kuromoji marks potential forms of godan -su verbs as 一段 verbs ending in "せる"
        val patchedBase = if (isPotential) {
            // Replace potential ending with godan dictionary ending
            when {
                baseForm.endsWith("せる") -> baseForm.dropLast(2) + "す"
                baseForm.endsWith("ける") -> baseForm.dropLast(2) + "く"
                baseForm.endsWith("げる") -> baseForm.dropLast(2) + "ぐ"
                baseForm.endsWith("てる") -> baseForm.dropLast(2) + "つ"
                baseForm.endsWith("でる") -> baseForm.dropLast(2) + "づ"
                baseForm.endsWith("べる") -> baseForm.dropLast(2) + "ぶ"
                baseForm.endsWith("める") -> baseForm.dropLast(2) + "む"
                baseForm.endsWith("れる") -> baseForm.dropLast(2) + "る"
                else -> baseForm
            }
        } else {
            baseForm
        }
        return patchedBase
    }

    fun getBaseReadingFromInflected(token: TokenInfo): String {
        val inflected = katakanaToHiragana(token.reading)

        return when (token.inflectionType) {
            "一段" -> inflected + "る"
            "五段・ラ行" -> when (token.inflectionForm) {
                "未然形", "連用形" -> inflected.dropLast(1) + "る"
                else -> inflected
            }

            "五段・カ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "く"
                "連用形" -> inflected.dropLast(1) + "く"
                else -> inflected
            }

            "五段・ガ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "ぐ"
                "連用形" -> inflected.dropLast(1) + "ぐ"
                else -> inflected
            }

            "五段・カ行イ音便" -> when (token.inflectionForm) {
                "連用形" -> inflected.dropLast(1) + "く"  // きい → きく
                "未然形" -> inflected.dropLast(1) + "く"  // きか → きく
                else -> inflected
            }

            "五段・サ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "す"
                "連用形" -> inflected.dropLast(1) + "す"
                else -> inflected
            }

            "五段・タ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "つ"
                "連用形" -> inflected.dropLast(1) + "つ"
                else -> inflected
            }

            "五段・ナ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "ぬ"
                "連用形" -> inflected.dropLast(1) + "ぬ"
                else -> inflected
            }

            "五段・バ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "ぶ"
                "連用形" -> inflected.dropLast(1) + "ぶ"
                else -> inflected
            }

            "五段・マ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "む"
                "連用形" -> inflected.dropLast(1) + "む"
                else -> inflected
            }

            "五段・ワ行" -> when (token.inflectionForm) {
                "未然形" -> inflected.dropLast(1) + "う"
                "連用形" -> inflected.dropLast(1) + "う"
                else -> inflected
            }

            else -> inflected
        }
    }

    private fun findClauseStart(currentIndex: Int, tokens: List<TokenInfo>, minIndex: Int): Int {
        for (i in currentIndex downTo minIndex) {
            val token = tokens[i]
            if (token.partOfSpeech.startsWith("助詞") || token.surface in listOf(
                    "、",
                    "。",
                    "が",
                    "は"
                )
            ) {
                return maxOf(minIndex, minOf(i + 1, currentIndex)) // Ensure start ≤ currentIndex
            }
        }
        return minIndex
    }

    private fun findClauseEnd(currentIndex: Int, tokens: List<TokenInfo>, maxIndex: Int): Int {
        for (i in currentIndex..maxIndex) {
            val token = tokens[i]
            if (token.partOfSpeech.startsWith("助詞") || token.surface in listOf(
                    "、",
                    "。",
                    "が",
                    "は",
                    "を",
                    "に"
                )
            ) {
                return minOf(maxIndex, maxOf(i, currentIndex)) // Ensure end ≥ currentIndex
            }
        }
        return maxIndex
    }

    fun rerankGlosses(
        entries: List<WordFull>,
        localContext: String,
        topN: Int = 3
    ): List<RankedSense> {
        val rankedContextBags = mutableListOf<RankedContextBag>()
        entries.forEach { word ->
            val kanjiMap = word.kanji.associateBy { it.kanjiId }
            val kanaMap = word.kana.associateBy { it.kanaId }
            val relevantKanji = mutableListOf<String>()
            val relevantKana = mutableListOf<String>()
            val senses = word.senses
            for (senseWithGloss in senses) {
                val sense = senseWithGloss.sense
                val glosses = senseWithGloss.glosses.map { it.text }
                val examples = senseWithGloss.examples
                val exampleTextBySenseId: Map<Int, String> = examples.associateBy(
                    { it.example.senseId },
                    {
                        it.sentences.firstOrNull { sentence -> sentence.language == "jpn" }?.text
                            ?: ""
                    }
                )
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
                val glossText = glosses.take(3).joinToString(", ")
                val example = exampleTextBySenseId[sense.senseId]?.let {
                    "e.g., $it"
                } ?: ""
                val combinedText = "$glossText $example"
                logger.DEBUG(combinedText)
                val placeholderScore = 1337f
                val ranked = RankedSense(
                    wordId = word.word.id,
                    senseId = sense.senseId!!,
                    score = placeholderScore,
                    kanjiTexts = relevantKanji,
                    kanaTexts = relevantKana,
                    glossTexts = glosses,
                    partOfSpeech = json.decodeFromString<List<String>>(sense.partOfSpeech)
                )
                rankedContextBags.add(RankedContextBag(ranked, localContext, glossText))
            }
        }
        var maxLen = Int.MIN_VALUE
        val inputIdsList = mutableListOf<LongArray>()
        val attentionMaskList = mutableListOf<LongArray>()
        rankedContextBags.forEach { (ranked, context, gloss) ->
            logger.DEBUG(gloss)
            val encoded = TransformerManager.tokenizePair(context, gloss)
            maxLen = max(encoded.ids.size, maxLen)
            inputIdsList.add(encoded.ids)
            attentionMaskList.add(encoded.attentionMask)
        }

        inputIdsList.indices.forEach { idx ->
            inputIdsList[idx] = padSequence(inputIdsList[idx], maxLen)
        }
        attentionMaskList.indices.forEach { idx ->
            attentionMaskList[idx] = padSequence(attentionMaskList[idx], maxLen)
        }
        println("${inputIdsList.size}, ${inputIdsList[0].size}")
        val flattenedIds = flat2DArray(inputIdsList.toTypedArray())
        val flattenedMasks = flat2DArray(attentionMaskList.toTypedArray())
        val result =
            TransformerManager.rankPairs(flattenedIds, flattenedMasks, inputIdsList.size.toLong())

        val contextLogitPair =
            rankedContextBags.zip(result.toList()).sortedByDescending { it.second }

        val topNScores = contextLogitPair.map { it.second }
        val activated = softmax(topNScores)
        val ranked = mutableListOf<RankedSense>()
        logger.DEBUG("Activated : ${activated.size} - rankedContextBag : ${rankedContextBags.size}")
        rankedContextBags.forEach { it ->
            logger.DEBUG("${it.context} - ${it.gloss}")
        }
        activated.forEachIndexed { index, score ->
            val temp = contextLogitPair[index].first.ranked
            ranked.add(
                RankedSense(
                    wordId = temp.wordId,
                    senseId = temp.senseId,
                    score = score,
                    kanjiTexts = temp.kanjiTexts,
                    kanaTexts = temp.kanaTexts,
                    glossTexts = temp.glossTexts,
                    partOfSpeech = temp.partOfSpeech
                )
            )
        }
        val output = ranked.sortedByDescending { it.score }
        output.forEach { logger.DEBUG("${it.glossTexts} ${it.kanjiTexts} ${it.kanaTexts} ${it.score} ${it.partOfSpeech}") }
        return output
    }

    fun rankDictionaryEntries(
        entries: List<WordFull>,
        localContextEmbedding: FloatArray?,
        sentenceEmbedding: FloatArray,
        mappedPOS: List<String>
    ): List<RankedSense> {
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

        println("ALL ENTRIES DICT LOOKUP RESULT")
        entries.forEach {
            println(it)
        }
        // 2. Deduplicate by wordId
//        val unique = posMatched.distinctBy { it.word.id }
        val results = mutableListOf<RankedSense>()
        for (word in posMatched) {
            val kanjiMap = word.kanji.associateBy { it.kanjiId }
            val kanaMap = word.kana.associateBy { it.kanaId }
            val relevantKanji = mutableListOf<String>()
            val relevantKana = mutableListOf<String>()
            for (senseWithGloss in word.senses) {
                val sense = senseWithGloss.sense
                val glosses = senseWithGloss.glosses.map { it.text }
                val examples = senseWithGloss.examples
                logger.DEBUG(examples.joinToString(","))
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

                val glossText = glosses.joinToString(", ")

// Check if info is available and not empty
                val infoTexts = json.decodeFromString<List<String>>(sense.info)
                val infoText = if (infoTexts.isNotEmpty()) {
                    " (${infoTexts.joinToString(",")})"
                } else {
                    ""
                }

                val contextualGloss = glossText + infoText
                val canonicalGloss = glossText.substringBefore("(").trim()
//                logger.DEBUG("Contextual gloss : $contextualGloss")
                val contextualEmbedding = TransformerManager.getEmbeddings(canonicalGloss)
                logger.DEBUG("Contextual embedding size ${contextualEmbedding.size}")
                val score = cosineSimilarity(sentenceEmbedding, contextualEmbedding)
                var finalScore = score
//                -0.37447652
//                -0.048565257
//                0.7874593
//                0.11824332
                if (localContextEmbedding != null) {
                    val localScore = cosineSimilarity(localContextEmbedding, contextualEmbedding)
                    finalScore = 0.3f * score + 0.7f * localScore
                }
                logger.DEBUG("contextualGloss $contextualGloss")
//                logger.DEBUG("contextualGloss embedding: ${contextualEmbedding.joinToString(",")}")
                logger.DEBUG("PER SENSE SCORE $finalScore")
                results.add(
                    RankedSense(
                        wordId = word.word.id,
                        senseId = sense.senseId!!,
                        score = finalScore,
                        kanjiTexts = relevantKanji,
                        kanaTexts = relevantKana,
                        glossTexts = glosses,
                        partOfSpeech = json.decodeFromString<List<String>>(sense.partOfSpeech)
                    )
                )
            }
        }
        val sorted = results.sortedByDescending { it.score }
        logger.DEBUG(sorted.joinToString(","))
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
        return sorted
    }

    fun mergeWithDictionary(
        tokens: List<TokenInfo>,
        dict: Set<String>?,
        maxGram: Int = 6
    ): List<TokenInfo> {
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