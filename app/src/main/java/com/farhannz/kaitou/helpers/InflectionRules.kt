package com.farhannz.kaitou.helpers

import com.farhannz.kaitou.data.models.TokenInfo
import org.intellij.lang.annotations.Pattern

data class InflectionRule(
    val pattern: List<(TokenInfo) -> Boolean>,
    val mergedBaseForm: String,
    val mergedMeaning: String,
    val mergedPos: String
)


fun posContains(substring: String): (TokenInfo) -> Boolean = {
    it.partOfSpeech.contains(substring)
}

fun baseIs(base: String): (TokenInfo) -> Boolean = {
    it.baseForm == base
}

fun surfaceIs(surface: String): (TokenInfo) -> Boolean = {
    it.surface == surface
}

fun inflTypeIs(type: String): (TokenInfo) -> Boolean = {
    it.inflectionType == type
}

fun inflFormIs(form: String): (TokenInfo) -> Boolean = {
    it.inflectionForm == form
}

fun posIs(pos: String): (TokenInfo) -> Boolean = {
    it.partOfSpeech == pos
}

infix fun ((TokenInfo) -> Boolean).and(other: (TokenInfo) -> Boolean): (TokenInfo) -> Boolean = {
    this(it) && other(it)
}

infix fun ((TokenInfo) -> Boolean).or(other: (TokenInfo) -> Boolean): (TokenInfo) -> Boolean = {
    this(it) || other(it)
}


object InflectionRules {
    private val unsortedInflectionRules = listOf(
        InflectionRule(
            pattern = listOf(
                baseIs("いたす") and inflFormIs("連用形"),
                baseIs("ます") and inflFormIs("未然ウ接続"),
                baseIs("う") and posContains("助動詞")
            ),
            mergedBaseForm = "いたしますしょう",
            mergedMeaning = "let's humbly do",
            mergedPos = "複合表現"
        ),
        InflectionRule(
            pattern = listOf(
                baseIs("じゃ"),
                baseIs("ない") and inflFormIs("連用テ接続"),
                baseIs("なる") and inflFormIs("連用タ接続"),
                baseIs("た") and posContains("助動詞")
            ),
            mergedBaseForm = "じゃなくなった",
            mergedMeaning = "ceased to be",
            mergedPos = "複合表現"
        ),
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("しまう") and inflFormIs("連用タ接続"),
                baseIs("た")
            ),
            mergedBaseForm = "てしまった",
            mergedMeaning = "regrettably/completely did",
            mergedPos = "複合表現"
        ),
        InflectionRule(
            pattern = listOf(
                baseIs("ない") and inflFormIs("未然形"),
                baseIs("れる"),
                baseIs("ば"),
                baseIs("なる"),
                baseIs("ない")
            ),
            mergedBaseForm = "なければならない",
            mergedMeaning = "must / have to",
            mergedPos = "複合表現"
        ),
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("いく")
            ),
            mergedBaseForm = "ていく",
            mergedMeaning = "go and do / continue to do",
            mergedPos = "複合表現"
        ),
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("くる")
            ),
            mergedBaseForm = "てくる",
            mergedMeaning = "come and do / start to do",
            mergedPos = "複合表現"
        ),
        // ～てしまう (regret / perfective)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("しまう")
            ),
            mergedBaseForm = "てしまう",
            mergedMeaning = "regrettably do / finish doing",
            mergedPos = "複合表現"
        ),

// ～ておく (do in advance)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("おく")
            ),
            mergedBaseForm = "ておく",
            mergedMeaning = "do in advance / keep …-ed",
            mergedPos = "複合表現"
        ),

// ～てある (resultant state)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("ある")
            ),
            mergedBaseForm = "てある",
            mergedMeaning = "be in the state of having been done",
            mergedPos = "複合表現"
        ),

// ～てみる (try doing)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("みる")
            ),
            mergedBaseForm = "てみる",
            mergedMeaning = "try doing",
            mergedPos = "複合表現"
        ),

// ～てしまう (past) → てしまった
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("しまう") and inflFormIs("連用タ接続"),
                baseIs("た")
            ),
            mergedBaseForm = "てしまった",
            mergedMeaning = "regrettably/completely did",
            mergedPos = "複合表現"
        ),

// ～ておく (past) → ておいた
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("おく") and inflFormIs("連用タ接続"),
                baseIs("た")
            ),
            mergedBaseForm = "ておいた",
            mergedMeaning = "did in advance",
            mergedPos = "複合表現"
        ),

// ～ている (progressive / resultant state)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("いる")
            ),
            mergedBaseForm = "ている",
            mergedMeaning = "be doing / be in the state of",
            mergedPos = "複合表現"
        ),

// ～ていた (progressive past)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("いる") and inflFormIs("連用タ接続"),
                baseIs("た")
            ),
            mergedBaseForm = "ていた",
            mergedMeaning = "was doing / had been in the state of",
            mergedPos = "複合表現"
        ),

// ～てはいけない (must not)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("は"),
                baseIs("いけない")
            ),
            mergedBaseForm = "てはいけない",
            mergedMeaning = "must not do",
            mergedPos = "複合表現"
        ),

// ～なくてもいい (don’t have to)
        InflectionRule(
            pattern = listOf(
                baseIs("ない") and inflFormIs("連用テ接続"),
                baseIs("て"),
                baseIs("も"),
                baseIs("いい")
            ),
            mergedBaseForm = "なくてもいい",
            mergedMeaning = "don’t have to do",
            mergedPos = "複合表現"
        ),

// ～なければいけない (have to)
        InflectionRule(
            pattern = listOf(
                baseIs("ない") and inflFormIs("未然形"),
                baseIs("れる"),
                baseIs("ば"),
                baseIs("いけない")
            ),
            mergedBaseForm = "なければいけない",
            mergedMeaning = "must / have to",
            mergedPos = "複合表現"
        ),

// ～てから (after doing)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("から")
            ),
            mergedBaseForm = "てから",
            mergedMeaning = "after doing",
            mergedPos = "複合表現"
        ),

// ～てばかり (only/always doing)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("ばかり")
            ),
            mergedBaseForm = "てばかり",
            mergedMeaning = "only / always doing",
            mergedPos = "複合表現"
        ),

// ～てすぐ (immediately after)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("すぐ")
            ),
            mergedBaseForm = "てすぐ",
            mergedMeaning = "immediately after doing",
            mergedPos = "複合表現"
        ),

// ～ていて (て-form + いて)
        InflectionRule(
            pattern = listOf(
                baseIs("て"),
                baseIs("いる") and inflFormIs("連用テ接続"),
                baseIs("て")
            ),
            mergedBaseForm = "ていて",
            mergedMeaning = "doing … and",
            mergedPos = "複合表現"
        ),
        //  いられる (potential form of いる)
        InflectionRule(
            pattern = listOf(
                baseIs("いる") and inflFormIs("未然形"),
                baseIs("られる")
            ),
            mergedBaseForm = "いられる",
            mergedMeaning = "to be able to stay/be",
            mergedPos = "複合表現"
        ),
    )

    private val inflectionRules = unsortedInflectionRules.sortedByDescending { it.pattern.size }
    fun matchInflection(tokens: List<TokenInfo>): List<TokenInfo> {
        val result = mutableListOf<TokenInfo>()
        var i = 0

        while (i < tokens.size) {
            var matched = false

            for (rule in inflectionRules) {
                val size = rule.pattern.size
                if (i + size <= tokens.size) {
                    val window = tokens.subList(i, i + size)
                    if (rule.pattern.zip(window).all { (pred, token) -> pred(token) }) {
                        val mergedSurface = window.joinToString("") { it.surface }
                        val mergedReading = window.joinToString("") { it.reading }

                        result += TokenInfo(
                            surface = mergedSurface,
                            baseForm = rule.mergedBaseForm,
                            partOfSpeech = rule.mergedPos,
                            reading = mergedReading,
                            inflectionType = "",
                            inflectionForm = "",
                            metadata = mapOf(
                                "merged_meaning" to rule.mergedMeaning
                            )
                        )
                        i += size
                        matched = true
                        break
                    }
                }
            }

            if (!matched) {
                result += tokens[i]
                i++
            }
        }

        return result
    }
}
