package com.farhannz.kaitou.impl

import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.domain.DBDictionary
import com.farhannz.kaitou.domain.LookupResult
import com.farhannz.kaitou.domain.MorphemeData
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.posMapping
import kotlin.collections.joinToString

object JMDict : DBDictionary {
    override suspend fun lookup(token: TokenInfo): LookupResult {
        val result = DatabaseManager.getDatabase().dictionaryDao().lookupWord(token)
        if (result.isEmpty()) {
            return LookupResult.Error("No results")
        }
        val entry = result.first()

        // Use the matched kanji/kana from lookupWord result
        val surfaceForm = entry.kanji.find { it.text == token.surface }?.text
            ?: entry.kana.find { it.text == token.surface }?.text
            ?: entry.kanji.find { it.text == token.baseForm }?.text
            ?: entry.kana.find { it.text == token.baseForm }?.text
            ?: entry.kanji.find { it.common == true }?.text
            ?: entry.kana.find { it.common == true }?.text
            ?: token.surface

        val reading = entry.kana.find { it.text == token.surface }?.text
            ?: entry.kana.find { it.text == token.baseForm }?.text
            ?: entry.kana.find { it.common == true }?.text
            ?: entry.kana.firstOrNull()?.text
            ?: ""

        val meaning = entry.senses.firstOrNull()?.glosses
            ?.firstOrNull { it.lang == "eng" }?.text ?: ""

        return LookupResult.Success(
            MorphemeData(
                surfaceForm,
                reading,
                meaning,
                posMapping[token.partOfSpeech]?.joinToString(",") ?: ""
            )
        )
    }
}