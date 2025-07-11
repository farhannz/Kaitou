package com.farhannz.kaitou.helpers

import com.farhannz.kaitou.data.models.TokenInfo
import org.apache.lucene.analysis.ja.Token

class TokenManager {

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
                token.surface == "ない" && i > 0 && tokens[i-1].surface == "は" ->
                    token.copy(partOfSpeech = "助動詞")

                // Rule 2: Correct ない after verbs
                token.surface == "ない" && i > 0 && tokens[i-1].partOfSpeech.startsWith("動詞") ->
                    token.copy(partOfSpeech = "助動詞")

                // Rule 3: Handle common negative forms
                token.surface in setOf("ん", "ず") ->
                    token.copy(partOfSpeech = "助動詞")

                else -> token
            }
        }
    }
    // Helper for composite POS
    private fun determineCompositePos(span: List<TokenInfo>): String {
        // Custom logic based on span tokens
        return if (span.any { it.partOfSpeech.startsWith("助詞") }) "compound"
        else span.last().partOfSpeech
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