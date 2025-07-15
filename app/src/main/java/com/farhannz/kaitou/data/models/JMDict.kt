package com.farhannz.kaitou.data.models

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.posMapping


@Entity(tableName = "dictionary_info")
data class DictionaryInfo(
    @PrimaryKey val id: Int? = 1,
    val version: String?,
    val languages: String?,
    @ColumnInfo(name = "common_only") val commonOnly: Boolean?,
    @ColumnInfo(name = "dict_date") val dictDate: String?,
    @ColumnInfo(name = "dict_revisions") val dictRevisions: String?
)


// Tag.kt
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val key: String,
    val description: String?
)

// Word.kt
@Entity(tableName = "words",
    indices = [Index(value = ["id"], name = "idx_words_id")]
    )
data class Word(
    @PrimaryKey val id: String
)

// Kanji.kt
@Entity(
    tableName = "kanji",
    foreignKeys = [ForeignKey(entity = Word::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = CASCADE)],
    indices = [Index(value = ["word_id"], name = "idx_kanji_word_id")]
)
data class Kanji(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "kanji_id") val kanjiId: Int? = 0,
    @ColumnInfo(name = "word_id") val wordId: String,
    val common: Boolean?,
    val text: String,
    val tags: String
)

// Kana.kt
@Entity(
    tableName = "kana",
    foreignKeys = [ForeignKey(entity = Word::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = CASCADE)],
    indices = [Index(value = ["word_id"], name = "idx_kana_word_id")]
)
data class Kana(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "kana_id") val kanaId: Int? = 0,
    @ColumnInfo(name = "word_id") val wordId: String,
    val common: Boolean?,
    val text: String,
    val tags: String,
    @ColumnInfo(name = "applies_to_kanji") val appliesToKanji: String
)

// Sense.kt
@Entity(
    tableName = "sense",
    foreignKeys = [ForeignKey(entity = Word::class, parentColumns = ["id"], childColumns = ["word_id"], onDelete = CASCADE)],
    indices = [Index(value = ["word_id"], name = "idx_sense_word_id")]
)
data class Sense(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sense_id")val senseId: Int? = 0,
    @ColumnInfo(name = "word_id") val wordId: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    @ColumnInfo(name = "applies_to_kanji") val appliesToKanji: String,
    @ColumnInfo(name = "applies_to_kana") val appliesToKana: String,
    val related: String,
    val antonym: String,
    val field: String,
    val dialect: String,
    val misc: String,
    val info: String,
    @ColumnInfo(name = "language_source") val languageSource: String
)

// Gloss.kt
@Entity(
    tableName = "gloss",
    foreignKeys = [ForeignKey(
        entity = Sense::class,
        parentColumns = ["sense_id"],
        childColumns = ["sense_id"],
        onDelete = CASCADE
    )],
    indices = [Index(value = ["sense_id"], name = "idx_gloss_sense_id")]
)
data class Gloss(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "gloss_id") val glossId: Int? = 0,
    @ColumnInfo(name = "sense_id") val senseId: Int,
    val lang: String,
    val gender: String?,
    val type: String?,
    val text: String
)
// Relations

data class GlossWithLang(
    val lang: String,
    val text: String
)

data class SenseWithGlosses(
    @Embedded val sense: Sense,
    @Relation(
        parentColumn = "sense_id",
        entityColumn = "sense_id"
    )
    val glosses: List<Gloss>
)

data class WordFull(

    @Embedded val word: Word,

    @Relation(
        parentColumn = "id",
        entityColumn = "word_id"
    )
    val kanji: List<Kanji>,

    @Relation(
        parentColumn = "id",
        entityColumn = "word_id"
    )
    val kana: List<Kana>,

    @Relation(
        parentColumn = "id",
        entityColumn = "word_id",
        entity = Sense::class
    )
    val senses: List<SenseWithGlosses>
) {
    fun String.containsAny(posList: List<String>): Boolean {
        return posList.any { this.contains(it) }
    }
    fun getMostLikelyKana(token: TokenInfo): String? {
        return kana.firstOrNull { it.text == token.surface }?.text
            ?: kana.firstOrNull()?.text
    }
    fun getMostLikelyMeaning(token: TokenInfo): String? {

        val kuromojiPOS = token.partOfSpeech.substringBefore("-")
        val mappedJmdictPOS = posMapping[kuromojiPOS] ?: emptyList()

        val prioritized = senses.sortedBy { if ("uk" in it.sense.misc) 2 else 0 }

        val matched = prioritized.firstOrNull { senseWithGloss ->
            val rawPOS = senseWithGloss.sense.partOfSpeech
            val parsedPOS = rawPOS
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
            mappedJmdictPOS.any { it in parsedPOS }
        }

        val gloss = matched ?: prioritized.firstOrNull()

        return gloss?.glosses?.firstOrNull { it.lang == "eng" && it.text.isNotBlank() }?.text
    }

    @Deprecated("The usage of getting the most likely meaning and part of speech only from string is deprecated due to bad meaning and pos result")
    fun getMostLikelyKana(): String? {
        // 1. Try common kana with no appliesToKanji restriction
        kana.firstOrNull { it.common == true && it.appliesToKanji.isBlank() }?.let {
            return it.text
        }

        // 2. Try any kana with no restriction
        kana.firstOrNull { it.appliesToKanji.isBlank() }?.let {
            return it.text
        }

        // 3. Fallback: first available kana
        return kana.firstOrNull()?.text
    }

    @Deprecated("The usage of getting the most likely meaning and part of speech only from string is deprecated due to bad meaning and pos result")
    fun getMostLikelyMeaningsWithPOS(tokenPOS: String? = null, limit: Int = 3): List<Pair<String, String>> {
        val prioritized = senses.sortedBy { if ("uk" in it.sense.misc) 2 else 0 }

        return prioritized
            .asSequence()
            // Filter out senses without English gloss
            .filter { senseWithGloss ->
                senseWithGloss.glosses.any { g -> g.lang == "eng" && g.text.isNotBlank() }
            }
            // If tokenPOS is provided, try to match sense POS
            .filter { senseWithGloss ->
                if (tokenPOS == null) true
                else senseWithGloss.sense.partOfSpeech.any { tokenPOS.contains(it) }
            }
            .take(limit)
            .map { senseWithGloss ->
                val glosses = senseWithGloss.glosses
                    .filter { it.lang == "eng" && it.text.isNotBlank() }
                    .map { it.text }

                val posString = senseWithGloss.sense.partOfSpeech
                glosses.joinToString("; ") to posString
            }
            .toList()
    }
}