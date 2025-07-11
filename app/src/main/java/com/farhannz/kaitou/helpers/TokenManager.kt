package com.farhannz.kaitou.helpers

import com.farhannz.kaitou.data.models.TokenInfo
import org.apache.lucene.analysis.ja.Token

class TokenManager {

    fun mergeWithDictionary(tokens: List<TokenInfo>, dict: Set<String>?, maxGram: Int = 6): List<TokenInfo> {
        val result = mutableListOf<TokenInfo>()
        var i = 0
        while (i < tokens.size) {
            var merged: TokenInfo? = null
            outer@ for (n in maxGram downTo 2) {
                if (i + n > tokens.size) continue
                val span = tokens.subList(i, i + n)
                if (!span.all { it.partOfSpeech.startsWith("名詞") || it.partOfSpeech.startsWith("動詞") || it.partOfSpeech.startsWith("助動詞") }) continue
//                val text = span.dropLast(1).joinToString("") { it.surfaceFormString } + span.last().baseForm
                // Handle cases where baseForm might be null/empty
                val text = span.dropLast(1).joinToString("") { it.surface } +
                        (span.last().baseForm ?: span.last().surface)
                if (dict?.contains(text) ?: false) {
                    merged = TokenInfo(text,text,span.last().partOfSpeech)
                    i += n
                    break@outer
                }
            }
            if (merged != null) {
                result += merged
            } else {
                result += tokens[i]
                i++
            }
        }
        return result
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