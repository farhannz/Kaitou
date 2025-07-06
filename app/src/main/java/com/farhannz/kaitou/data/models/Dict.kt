package com.farhannz.kaitou.data.models

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