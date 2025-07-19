package com.farhannz.kaitou.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.posMapping

@Dao
interface DictionaryDao {
    // Dictionary info and tags
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDictionaryInfo(info: DictionaryInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<Tag>)

    // Words and components
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKanjis(kanji: List<Kanji>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKanas(kana: List<Kana>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSenses(senses: List<Sense>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlosses(glosses: List<Gloss>)
    data class SurfacePos(val surface: String, val jmdictPos: String)

    @Query(
        """
    SELECT DISTINCT k.text AS surface, s.part_of_speech AS jmdictPos
    FROM kanji k
    JOIN sense s ON k.word_id = s.word_id
"""
    )
    suspend fun getKanjiSurfacePos(): List<SurfacePos>

    @Query(
        """
    SELECT DISTINCT k.text AS surface, s.part_of_speech AS jmdictPos
    FROM kana k
    JOIN sense s ON k.word_id = s.word_id
"""
    )
    suspend fun getKanaSurfacePos(): List<SurfacePos>

    @Query(
        """
    SELECT DISTINCT text AS surface, part_of_speech AS jmdictPos
    FROM (
        SELECT k.text, s.part_of_speech
        FROM kanji k
        JOIN sense s ON k.word_id = s.word_id
        
        UNION
        
        SELECT k.text, s.part_of_speech
        FROM kana k
        JOIN sense s ON k.word_id = s.word_id
    )
"""
    )
    suspend fun getAllSurfacePos(): List<SurfacePos>

    @Transaction
    @Query(
        """
        SELECT * FROM words 
        WHERE id IN (
            SELECT word_id FROM kanji WHERE text = :text
            UNION
            SELECT word_id FROM kana WHERE text = :text
        )
    """
    )
    suspend fun lookupWordsByText(text: String): List<WordFull>

    @Transaction
    @Query("SELECT text FROM kanji UNION SELECT text FROM kana")
    suspend fun getAllDictionaryWords(): List<String>


    @Transaction
    @Query(
        """
    SELECT * FROM words 
    WHERE id IN (
        SELECT word_id FROM kanji WHERE text IN (:terms)
        UNION
        SELECT word_id FROM kana WHERE text IN (:terms)
    )
"""
    )
    suspend fun lookupWordsByTerms(terms: List<String>): List<WordFull>

    data class WordWithSurface(
        @Embedded val word: WordFull,
        @ColumnInfo(name = "surface") val surface: String
    )

    @Transaction
    @Query(
        """
    SELECT w.*,
           k.text AS surface          -- or k.text if kanji wins ties
    FROM words w
    JOIN kanji k ON w.id = k.word_id
    WHERE k.text IN (:surfaces)
    UNION
    SELECT w.*,
           ka.text AS surface
    FROM words w
    JOIN kana ka ON w.id = ka.word_id
    WHERE ka.text IN (:surfaces)
"""
    )
    suspend fun lookupWordsWithSurface(surfaces: List<String>): List<WordWithSurface>

    /**
     * Looks up a word using its token information. For ambiguous parts of speech like
     * particles, it strictly filters the results to match the token's role.
     *
     * @param token The token information from the analyzer.
     * @return A list of matching `WordFull` objects, correctly filtered.
     */
    suspend fun lookupWord(token: TokenInfo): List<WordFull> {
        val terms = listOfNotNull(token.baseForm, token.surface).distinct()
        if (terms.isEmpty()) return emptyList()

        val potentialWords = lookupWordsByTerms(terms)
        if (potentialWords.isEmpty()) return emptyList()

        val tokenPosCategory = token.partOfSpeech.substringBefore("-")
        val posMapping = getPosMapping()
        val mappedJmdictPOS = posMapping[token.partOfSpeech] ?: posMapping[tokenPosCategory] ?: emptyList()

        data class ScoredWord(val word: WordFull, val score: Double)

        val scored = potentialWords.map { word ->
            var score = 0.0

            // Exact match bonus
            val surfaceMatch = word.kanji.any { it.text == token.surface } ||
                    word.kana.any { it.text == token.surface }
            val baseformMatch = word.kanji.any { it.text == token.baseForm } ||
                    word.kana.any { it.text == token.baseForm }

            when {
                surfaceMatch -> score += 20.0
                baseformMatch -> score += 10.0
            }

            // POS match scoring
            if (mappedJmdictPOS.isNotEmpty()) {
                val posMatches = word.senses.count { sense ->
                    val sensePosTags = sense.sense.partOfSpeech
                        .removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }
                    sensePosTags.any { it in mappedJmdictPOS }
                }

                // Strong POS filtering for particles/auxiliary verbs
                val ambiguousPosCategories = setOf("助詞", "助動詞")
                if (tokenPosCategory in ambiguousPosCategories) {
                    if (posMatches == 0) score = -100.0 // Exclude completely
                    else score += posMatches * 10.0
                } else {
                    score += posMatches * 5.0 // Bonus for POS match
                }
            }

            // Prefer words with fewer senses (more specific)
            score += (10.0 / (word.senses.size + 1))

            ScoredWord(word, score)
        }

        return scored
            .filter { it.score > 0 } // Remove excluded words
            .sortedByDescending { it.score }
            .map { it.word }
    }

    /**
     * Helper function to provide the mapping from Kuromoji POS to JMdict POS tags.
     */
    private fun getPosMapping(): Map<String, List<String>> {
        return posMapping
    }
}