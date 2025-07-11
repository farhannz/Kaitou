package com.farhannz.kaitou.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey


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