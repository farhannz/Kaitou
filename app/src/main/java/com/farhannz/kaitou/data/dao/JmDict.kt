package com.farhannz.kaitou.data.dao

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.TokenHelper
import com.farhannz.kaitou.helpers.katakanaToHiragana
import com.farhannz.kaitou.helpers.mapPosToJmdict
import com.farhannz.kaitou.helpers.posMapping
import kotlinx.serialization.json.Json

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

    suspend fun lookupWordRework(token: TokenInfo, selectedEmbedding: FloatArray): List<WordFull> {
        val json = Json { ignoreUnknownKeys = true }
        val surface = token.surface
        val base = token.baseForm.orEmpty()
        val reading = token.reading
        val pos = token.partOfSpeech
        val inflectionType = token.inflectionType
        val inflectionForm = token.inflectionForm

        // Collect all possible lookup terms
        val lookupTerms = buildSet {
            add(surface)
            if (base.isNotEmpty() && base != surface) add(base)
        }.filter { it.isNotEmpty() }

        if (lookupTerms.isEmpty()) return emptyList()
        // Get potential matches from dictionary
        val potentialWords = lookupWordsByTerms(lookupTerms)
        if (potentialWords.isEmpty()) return emptyList()


        val targetPos = mapPosToJmdict(pos, inflectionType)
        val mappedPotentialWords = potentialWords.mapNotNull { word ->
            val matchingSenses = word.senses.filter { sense ->
                val tokenPos = json.decodeFromString<List<String>>(sense.sense.partOfSpeech)
                tokenPos.any { targetPos.contains(it) }
            }
            if (matchingSenses.isNotEmpty()) {
                word.copy(senses = matchingSenses)
            } else {
                null
            }
        }
        Log.d("LookupWordRework", token.toString())
        TokenHelper.rankDictionaryEntries(mappedPotentialWords, selectedEmbedding, targetPos)

        return mappedPotentialWords
    }

    /**
     * Looks up a word using its token information. For ambiguous parts of speech like
     * particles, it strictly filters the results to match the token's role.
     *
     * @param token The token information from the analyzer.
     * @return A list of matching `WordFull` objects, correctly filtered.
     */
    suspend fun lookupWord(token: TokenInfo): List<WordFull> {
        val surface = token.surface
        val base = token.baseForm.orEmpty()
        val reading = token.reading
        val inflectionType = token.inflectionType
        val inflectionForm = token.inflectionForm

        // Collect all possible lookup terms
        val lookupTerms = buildSet {
            add(surface)
            if (base.isNotEmpty() && base != surface) add(base)
            if (reading.isNotEmpty()) add(reading)
            if (reading.isNotEmpty() && katakanaToHiragana(reading) != reading) add(katakanaToHiragana(reading))
        }.filter { it.isNotEmpty() }

        if (lookupTerms.isEmpty()) return emptyList()
        // Get potential matches from dictionary
        val potentialWords = lookupWordsByTerms(lookupTerms)
        if (potentialWords.isEmpty()) return emptyList()

        // Enhanced POS mapping with verb type specificity
        val tokenPosCategory = token.partOfSpeech.substringBefore("-")
        val mappedPOS = when {
            // Special handling for verbs based on inflection type
            tokenPosCategory == "動詞" && inflectionType.isNotEmpty() -> {
                when (inflectionType) {
                    "サ変・スル" -> listOf("vs", "vs-i", "vs-s")
                    "カ変・クル" -> listOf("vk")
                    "一段" -> listOf("v1", "v1-s", "vz")
                    "五段" -> listOf("v5", "v5u", "v5k", "v5g", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r", "v5k-s")
                    else -> posMapping[token.partOfSpeech] ?: posMapping[tokenPosCategory] ?: emptyList()
                }
            }

            else -> posMapping[token.partOfSpeech] ?: posMapping[tokenPosCategory] ?: emptyList()
        }

        // Filter by matching forms (surface/base/reading)
        val scoredMatches = potentialWords.map { word ->
            val forms = (word.kanji.map { it.text } + word.kana.map { it.text }).toSet()

            val formScore = when {
                forms.contains(base) -> 3
                forms.contains(surface) -> 2
                forms.contains(reading) -> 1
                else -> 0
            }

            val posMatch = word.senses.any { sense ->
                val posTags = sense.sense.partOfSpeech
                    .removeSurrounding("[", "]")
                    .splitToSequence(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .toList()

                val grammarTypes = listOf("prt", "aux-v", "aux-adj", "conj", "int", "exp")

                when {
                    // Special case: if token is particle or auxiliary, only accept grammar types
                    tokenPosCategory.startsWith("助詞") || tokenPosCategory.startsWith("助動詞") ->
                        posTags.any { it in grammarTypes }

                    token.partOfSpeech.startsWith("接頭詞") ->
                        "pref" in posTags

                    mappedPOS.isNotEmpty() -> posTags.any { it in mappedPOS }


                    else -> true
                }
            }

            Triple(word, formScore, posMatch)
        }

        // Enhanced sense filtering with domain awareness
        return scoredMatches
            .filter { it.third } // POS match
            .sortedByDescending { it.second } // Higher form score first
            .map { it.first }
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


