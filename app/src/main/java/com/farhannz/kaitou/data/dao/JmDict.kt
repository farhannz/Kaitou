package com.farhannz.kaitou.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.katakanaToHiragana
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
        val base = token.baseForm.orEmpty()
        val reading = token.reading
        val surface = token.surface

        if (base.isEmpty() && surface.isEmpty()) return emptyList()

        val uniqueTerms = buildSet {
            add(surface)
            if (base != surface) add(base)
        }

        if (uniqueTerms.isEmpty()) return emptyList()

        val potentialWords = lookupWordsByTerms(uniqueTerms.toList())

        if (potentialWords.isEmpty()) return emptyList()

        val tokenPosCategory = token.partOfSpeech.substringBefore("-")
        val posMapping = getPosMapping()
        val mappedPOS = posMapping[token.partOfSpeech]
            ?: posMapping[tokenPosCategory]
            ?: emptyList()

        val ambiguousCategories = setOf("助詞", "助動詞")
        val filteredWords = potentialWords.filter { word ->
            // Strict POS filtering for ambiguous parts of speech
            if (tokenPosCategory in ambiguousCategories) {
                word.senses.any { sense ->
                    val posTags = sense.sense.partOfSpeech
                        .removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }

                    posTags.any { it in mappedPOS }
                }
            } else true // Keep broader matches for other categories
        }

        // Optionally: retain only exact/normalized form matches
        val filteredByForm = filteredWords.filter { word ->
            val allForms = word.kanji.map { it.text } + word.kana.map { it.text }
            allForms.any { form ->
                form == surface || form == base
            }
        }

        return filteredByForm
    }

    /**
     * Helper function to provide the mapping from Kuromoji POS to JMdict POS tags.
     */
    private fun getPosMapping(): Map<String, List<String>> {
        return posMapping
    }
}


data class WordGlossEntry(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "kanji_text") val kanjiText: String?,
    @ColumnInfo(name = "kana_text") val kanaText: String?,
    @ColumnInfo(name = "sense_id") val senseId: Long,
    @ColumnInfo(name = "lang") val lang: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    @ColumnInfo(name = "gloss_text") val glossText: String
)

@Dao
interface WordGlossDao {

    @Query(
        """
        SELECT 
            w.id AS word_id,
            k.text AS kanji_text,
            a.text AS kana_text,
            s.sense_id,
            g.lang,
            s.part_of_speech,
            g.text AS gloss_text
        FROM kana a
        LEFT JOIN kanji k ON k.text = :surface AND k.word_id = a.word_id
        JOIN words w ON w.id = COALESCE(k.word_id, a.word_id)
        JOIN sense s ON s.word_id = w.id
        JOIN gloss g ON g.sense_id = s.sense_id
        WHERE a.text = :surface
          AND g.lang = 'eng'
          AND s.part_of_speech LIKE '%' || :jmdictPos || '%'
        ORDER BY w.id, s.sense_id, g.lang
        """
    )
    suspend fun lookupWordGlossBySurfaceAndPos(
        surface: String,
        jmdictPos: String
    ): List<WordGlossEntry>
}


