package com.farhannz.kaitou.impl

import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.domain.DBDictionary
import com.farhannz.kaitou.domain.LookupResult
import com.farhannz.kaitou.domain.MorphemeData
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.posMapping
import kotlin.collections.joinToString

object JMDict : DBDictionary {
    const val isRework = true
    override suspend fun lookup(token: TokenInfo): LookupResult {
        val dao = DatabaseManager.getDatabase().dictionaryDao()
        val result = if (isRework) dao.lookupWordRework(token) else dao.lookupWord(token)
        if (result.isEmpty()) {
            return LookupResult.Error("No results")
        }
        val bestWord = result.first()

        val dictForm = token.baseForm?.takeIf { it.isNotEmpty() } ?: token.surface

        val kanjiLine = bestWord.kanji.find { it.text == dictForm }
        val kanaLine = bestWord.kana.find { it.text == dictForm } ?: bestWord.kana.first()

        val surfaceForm = kanjiLine?.text ?: dictForm
        val reading = kanaLine.text

// DAO already filtered senses; pick the first one that has the right POS
        val bestSense = bestWord.senses.firstOrNull { s ->
            when {
                token.inflectionType == "サ変・スル" -> "vs" in s.sense.partOfSpeech
                else -> true
            }
        } ?: bestWord.senses.first()

        val meaning = if (token.metadata.containsKey("jmdict_tags")) {
            token.metadata["merged_meaning"] as String
        } else {
            bestSense.glosses.firstOrNull { it.lang == "eng" }?.text ?: ""
        }
        val displayPos = buildString {
            append(
                posMapping[token.partOfSpeech]?.first()
                    ?: posMapping[token.partOfSpeech.substringBefore("-")]?.first()
                    ?: ""
            )
            if (token.inflectionType.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("${token.inflectionType} ${token.inflectionForm}")
            }
        }



        return LookupResult.Success(
            MorphemeData(
                surfaceForm,
                reading,
                meaning,
                displayPos
            )
        )
    }
}