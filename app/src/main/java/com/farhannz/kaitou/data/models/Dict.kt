package com.farhannz.kaitou.data.models
//
//import androidx.room.*
//import kotlinx.serialization.Serializable
data class Example(
    val japanese: String,
    val reading: String,
    val english: String
)

data class VocabularyEntry(
    val word: String,
    val reading: String,
    val romaji: String,
    val definition: String,
    val partOfSpeech: String,
    val jlptLevel: String,
    val examples: ArrayList<Example>
)
//
//@Serializable
//data class DictionaryData(
//    val version: String,
//    val languages: List<String>,
//    val commonOnly: Boolean,
//    val dictDate: String,
//    val dictRevisions: List<String>,
//    val tags: Map<String, String>,
//    val words: List<Word>
//)
//
//// Entity classes
//@Serializable
//@Entity(tableName = "words")
//data class Word(
//    @PrimaryKey val id: String,
//    val kanji_json: String,
//    val kana_json: String,
//    val sense_json: String
//)
//
//@Serializable
//@Entity(tableName = "kanji")
//data class Kanji(
//    @PrimaryKey(autoGenerate = true) val kanjiId: Long = 0,
//    val wordId: String,
//    val text: String,
//    val common: Boolean,
//    val tags: String // JSON string of tags array
//)
//
//@Serializable
//@Entity(tableName = "kana")
//data class Kana(
//    @PrimaryKey(autoGenerate = true) val kanaId: Long = 0,
//    val wordId: String,
//    val text: String,
//    val common: Boolean,
//    val tags: String, // JSON string of tags array
//    val appliesToKanji: String // JSON string of applies array
//)
//
//@Serializable
//@Entity(tableName = "sense")
//data class Sense(
//    @PrimaryKey(autoGenerate = true) val senseId: Long = 0,
//    val wordId: String,
//    val partOfSpeech: String, // JSON string of POS array
//    val appliesToKanji: String, // JSON string
//    val appliesToKana: String, // JSON string
//    val related: String, // JSON string
//    val antonym: String, // JSON string
//    val field: String, // JSON string
//    val dialect: String, // JSON string
//    val misc: String, // JSON string
//    val info: String, // JSON string
//    val languageSource: String // JSON string
//)
//
//@Serializable
//@Entity(tableName = "glosses")
//data class Gloss(
//    @PrimaryKey(autoGenerate = true) val glossId: Long = 0,
//    val senseId: Long,
//    val lang: String,
//    val gender: String?,
//    val type: String?,
//    val text: String
//)
//
//// Additional tables for better normalization
//@Entity(tableName = "tags")
//data class Tag(
//    @PrimaryKey val key: String,
//    val description: String
//)
//
//@Entity(tableName = "dictionary_info")
//data class DictionaryInfo(
//    @PrimaryKey val id: Int = 1,
//    val version: String,
//    val languages: List<String>,
//    val commonOnly: Boolean,
//    val dictDate: String,
//    val dictRevisions: List<String>
//)
//
////data class EntryWithRelations(
////    @Embedded val entry: Entry,
////    @Relation(parentColumn = "id", entityColumn = "entryId") val kanji: List<Kanji>,
////    @Relation(parentColumn = "id", entityColumn = "entryId") val kana: List<Kana>,
////    @Relation(parentColumn = "id", entityColumn = "entryId") val glosses: List<Gloss>
////)