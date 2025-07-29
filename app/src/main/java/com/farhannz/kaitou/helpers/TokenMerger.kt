package com.farhannz.kaitou.helpers

import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.data.models.WordFull

interface TokenMerger {
    suspend fun merge(tokens: List<TokenInfo>, dict: Set<String>?): List<TokenInfo>
}

fun katakanaToHiragana(katakanaString: String): String {
    val hiraganaBuilder = StringBuilder()
    for (char in katakanaString) {
        if (char.isKatakana()) {
            // Katakana Unicode range: U+30A0 - U+30FF
            // Hiragana Unicode range: U+3040 - U+309F
            // The offset is 0x60 (96 in decimal)
            hiraganaBuilder.append((char.code - 0x60).toChar())
        } else {
            hiraganaBuilder.append(char) // Append non-Katakana characters as-is
        }
    }
    return hiraganaBuilder.toString()
}

// Extension function to check if a character is Katakana
fun Char.isKatakana(): Boolean {
    return this.code in 0x30A0..0x30FF
}

fun mapPosToJmdict(pos: String, inflectionType: String? = null): List<String> {
    val posList = pos.split("-")
    val allPossibleMappings = mutableSetOf<String>()

    // Try exact matches for all possible lengths
    for (length in minOf(3, posList.size) downTo 1) {
        val key = posList.take(length).joinToString("-")
        posMapping[key]?.let { allPossibleMappings.addAll(it) }
    }

//    val normalized = mutableSetOf<String>()
//    allPossibleMappings.forEach {
//        normalized.addAll(it.split("-"))
//    }
//    println(posList.joinToString(","))
//    println(normalized.joinToString(","))
//    if (pos.startsWith("動詞") && inflectionType != null) {
//        return when {
//            inflectionType.startsWith("一段") -> listOf("v1")
//            inflectionType.startsWith("五段") -> listOf("v5")
//            inflectionType.contains("サ変") -> listOf("vs", "vs-i", "vs-s")
//            inflectionType.contains("カ変") -> listOf("vk")
//            else -> listOf("unc")
//        }
//    }

    return if (allPossibleMappings.isNotEmpty()) {
        allPossibleMappings.toList()
    } else {
        listOf("unc")
    }
}

val posMapping: Map<String, List<String>> = mapOf(
    // Nouns - Common
    "名詞-普通" to listOf("n"),
    "名詞-一般" to listOf("n"),
    "名詞-サ変可能" to listOf("n", "vs"),
    "名詞-形状詞可能" to listOf("n", "adj-na"),
    "名詞-サ変接続" to listOf("n", "vs", "vt"),
    // Proper nouns
    "名詞-固有名詞-人名" to listOf("n", "n-pr", "person", "given", "surname"),
    "名詞-固有名詞-地名" to listOf("n", "n-pr", "place"),
    "名詞-固有名詞-組織" to listOf("n", "n-pr", "organization"),

    "名詞-代名詞-一般" to listOf("pn"),

    // Numerals and counters
    "名詞-数詞" to listOf("num"),
    "名詞-助数詞" to listOf("ctr"),

    // Suffix nouns
    "名詞-接尾-一般" to listOf("n-suf", "n", "suf"),
    "名詞-接尾-人名" to listOf("n-suf", "n", "suf", "n-pr", "given", "surname"),
    "名詞-接尾-地名" to listOf("n-suf", "n", "suf", "n-pr", "place"),
    "名詞-接尾-副詞可能" to listOf("n-suf", "n", "suf", "adv"),
    "名詞-接尾-助数詞" to listOf("n-suf", "n", "suf", "ctr"),
    "名詞-接尾-サ変可能" to listOf("n-suf", "n", "suf", "vs"),
    "名詞-接尾-助動詞語幹" to listOf("adj-na", "aux"),

    // Verbs
    "動詞-自立" to listOf("v5", "v1", "vk", "vs", "vi", "vt"),
    "動詞-非自立可能" to listOf("aux-v"),
    "動詞-非自立" to listOf("exp"),

    // Adjectives
    "形容詞-自立" to listOf("adj-i", "adj-ix"),
    "形容詞-非自立可能" to listOf("aux-adj"),

    // Na-adjectives
    "形状詞-一般" to listOf("adj-na"),
    "形状詞-タリ" to listOf("adj-na"),
    "形容動詞" to listOf("adj-na"),

    // Adverbs
    "副詞" to listOf("adv", "adv-to"),
    "副詞-一般" to listOf("adv"),
    "副詞-助詞類接続" to listOf("adv", "adv-to"),

    // Adnominal words
    "連体詞" to listOf("adj-no", "adj-pn"),
    // Conjunctions
    "接続詞" to listOf("conj"),

    // Interjections
    "感動詞" to listOf("int"),

    // Particles
    "助詞-係助詞" to listOf("prt"),
    "助詞-格助詞" to listOf("prt"),
    "助詞-終助詞" to listOf("prt"),
    "助詞-副助詞" to listOf("prt"),
    "助詞-接続助詞" to listOf("prt", "conj"),
    "助詞-連体化" to listOf("prt"),

    // Auxiliary verbs/adjectives
    "助動詞" to listOf("aux-v", "aux-adj"),

    // Affixes
    "接尾辞" to listOf("suf"),
    "接頭辞" to listOf("pref"),
    "接頭詞" to listOf("pref"),

    // Symbols
    "記号-一般" to listOf("sym"),
    "記号-補助記号" to listOf("sym"),
    "記号-括弧開" to listOf("sym"),
    "記号-括弧閉" to listOf("sym"),

    // Other
    "フィラー" to listOf("unc"),
    "間投詞" to listOf("int"),

    // Fallback general categories
    "名詞" to listOf("n"),
    "動詞" to listOf("v5", "v1", "vk", "vs"),
    "形容詞" to listOf("adj-i")
)


data class Candidate(
    val word: WordFull,
    val isCommon: Boolean   // taken from Kanji.common or Kana.common
)


object BoundaryViterbi {

    private const val MAX_SPAN = 6
    private const val IN_DICT_BONUS = -1.0
    private const val OOV_COST = 1.0

    fun preProcessPassive(tokens: List<TokenInfo>): List<TokenInfo> {
        val out = mutableListOf<TokenInfo>()
        var i = 0

        while (i < tokens.size) {
            // 1. passive / potential  れる / られる
            val passive1 = run {
                val suf = tokens.getOrNull(i + 1) ?: return@run null
                val aux = tokens.getOrNull(i + 2) ?: return@run null
                val verb = tokens[i]

                if (verb.partOfSpeech.startsWith("動詞") &&
                    suf.surface == "れ" && aux.surface in setOf("る", "た", "て", "ない", "ます")
                ) {
                    val base = verb.baseForm ?: verb.surface
                    val newBase = base
                    Triple(
                        verb.surface + suf.surface + aux.surface,
                        newBase,
                        "動詞-非自立可能"
                    )
                } else null
            }

            // 2. causative-passive  せられる / させられる
            val passive2 = run {
                val suf1 = tokens.getOrNull(i + 1) ?: return@run null
                val suf2 = tokens.getOrNull(i + 2) ?: return@run null
                val aux = tokens.getOrNull(i + 3) ?: return@run null
                val verb = tokens[i]

                if (verb.partOfSpeech.startsWith("動詞") &&
                    suf1.surface == "せ" && suf2.surface == "られ" &&
                    aux.surface in setOf("る", "た", "て", "ない", "ます")
                ) {

                    val base = verb.baseForm ?: verb.surface
                    val newBase = base
                    Triple(
                        verb.surface + suf1.surface + suf2.surface + aux.surface,
                        newBase,
                        "動詞-非自立可能"
                    )
                } else null
            }

            when {
                passive2 != null -> {
                    val (surf, base, pos) = passive2
                    out += TokenInfo(surf, base, pos)
                    i += 4
                }

                passive1 != null -> {
                    val (surf, base, pos) = passive1
                    out += TokenInfo(surf, base, pos)
                    i += 3
                }

                else -> {
                    out += tokens[i]
                    i++
                }
            }
        }
        return out
    }

    fun getBestPosTag(tokens: List<TokenInfo>, dict: Set<String>): String {
        if (tokens.size == 1) return tokens[0].partOfSpeech

        val surface = tokens.joinToString("") { it.surface }
        if (surface in dict) {
            // For dictionary words, infer POS from constituent tokens
            val firstPos = tokens[0].partOfSpeech
            return when {
                firstPos.startsWith("名詞") -> "名詞-普通名詞"
                firstPos.startsWith("動詞") -> "動詞-自立"
                else -> firstPos
            }
        }
        return tokens[0].partOfSpeech // Fallback to first token's POS
    }

    fun segment(tokens: List<TokenInfo>, dict: Set<String>): List<TokenInfo> {
        val n = tokens.size
        if (n == 0) return emptyList()

        data class Edge(val start: Int, val end: Int, val cost: Double)

        val dag = Array(n + 1) { mutableListOf<Edge>() }

        // 1. build lattice between Kuromoji boundaries
        for (i in 0 until n) {
            for (l in 1..minOf(MAX_SPAN, n - i)) {
                val surface = tokens.subList(i, i + l)
                    .joinToString("") { it.surface }

                // Need better cost calculation
                val cost = if (surface in dict) {
                    IN_DICT_BONUS * l
                } else {
                    val baseform = tokens.subList(i, i + l).joinToString("") { it.baseForm.toString() }
                    if (baseform in dict) IN_DICT_BONUS * l * 0.9 else OOV_COST * l
                }
                dag[i + l] += Edge(i, i + l, cost)
            }
        }

        // 2. Viterbi
        val best = DoubleArray(n + 1) { Double.MAX_VALUE }
        val prev = arrayOfNulls<Edge>(n + 1)
        best[0] = 0.0
        for (end in 1..n) {
            dag[end].forEach { e ->
                val c = best[e.start] + e.cost
                if (c < best[end]) {
                    best[end] = c
                    prev[end] = e
                }
            }
        }

        // 3. back-track → build new TokenInfo list
        val result = mutableListOf<TokenInfo>()
        var pos = n
        while (pos > 0) {
            val e = prev[pos] ?: break
            val mergedSurface = tokens.subList(e.start, e.end)
                .joinToString("") { it.surface }
            val mergedBase = tokens.subList(e.start, e.end)
                .joinToString("") { it.baseForm!! }
//            val posTag = tokens[e.start].partOfSpeech   // or smarter mapping
            val posTag = getBestPosTag(tokens.subList(e.start, e.end), dict)
            result.add(0, TokenInfo(mergedSurface, mergedBase, posTag))
            pos = e.start
        }
        return result
    }
}