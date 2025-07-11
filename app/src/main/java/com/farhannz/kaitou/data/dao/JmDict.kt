package com.farhannz.kaitou.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.farhannz.kaitou.data.models.*

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

    @Transaction
    @Query("""
        SELECT * FROM words 
        WHERE id IN (
            SELECT word_id FROM kanji WHERE text = :text
            UNION
            SELECT word_id FROM kana WHERE text = :text
        )
    """)
    suspend fun lookupWordsByText(text: String): List<WordFull>

    @Transaction
    @Query("SELECT text FROM kanji UNION SELECT text FROM kana")
    suspend fun getAllDictionaryWords(): List<String>



    @Transaction
    @Query("""
    SELECT * FROM words 
    WHERE id IN (
        SELECT word_id FROM kanji WHERE text IN (:terms)
        UNION
        SELECT word_id FROM kana WHERE text IN (:terms)
    )
""")
    suspend fun lookupWordsByTerms(terms: List<String>): List<WordFull>
    /**
     * Looks up a word using its token information. For ambiguous parts of speech like
     * particles, it strictly filters the results to match the token's role.
     *
     * @param token The token information from the analyzer.
     * @return A list of matching `WordFull` objects, correctly filtered.
     */
    suspend fun lookupWord(token: TokenInfo): List<WordFull> {
        // 1. Get all potential words from the database based on text.
        val terms = listOfNotNull(token.baseForm, token.surface).distinct()
        if (terms.isEmpty()) {
            return emptyList()
        }
        val potentialWords = lookupWordsByTerms(terms) // Existing DAO call

        // 2. Define ambiguous POS categories that require strict filtering.
        val ambiguousPosCategories = setOf("助詞", "助動詞") // "Particle", "Auxiliary Verb"
        val tokenPosCategory = token.partOfSpeech.substringBefore("-")

        // 3. If the token is an ambiguous type (like a particle), filter the results.
        if (tokenPosCategory in ambiguousPosCategories) {
            val posMapping = getPosMapping()
            val mappedJmdictPOS = posMapping[tokenPosCategory] ?: emptyList()

            if (mappedJmdictPOS.isEmpty()) {
                return potentialWords // Cannot filter, return all results
            }

            // Return only the words that contain a sense matching the token's POS.
            return potentialWords.filter { word ->
                word.senses.any { senseWithGlosses ->
                    val sensePosTags = senseWithGlosses.sense.partOfSpeech
                        .removeSurrounding("[", "]")
                        .split(",")
                        .map { it.trim().removeSurrounding("\"") }

                    sensePosTags.any { it in mappedJmdictPOS }
                }
            }
        }

        // 4. For non-ambiguous words (nouns, verbs, etc.), return all potential matches.
        return potentialWords
    }

    /**
     * Helper function to provide the mapping from Kuromoji POS to JMdict POS tags.
     */
    private fun getPosMapping(): Map<String, List<String>> {
        return mapOf(
            "名詞" to listOf("n", "pn", "vs", "adj-no"),
            "動詞" to listOf("v1", "v5", "v5u", "v5k", "v5g", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r", "vk", "vz", "vi", "vt", "vs"),
            "助動詞" to listOf("aux", "aux-v", "aux-adj"),
            "形容詞" to listOf("adj-i", "adj-na", "adj-no"),
            "副詞" to listOf("adv", "adv-to"),
            "助詞" to listOf("prt"),
            "連体詞" to listOf("adj-pn"),
            "接続詞" to listOf("conj"),
            "感動詞" to listOf("int"),
            "記号" to listOf("sym"),
            "接頭詞" to listOf("pref"),
            "接尾詞" to listOf("suf"),
            "連語" to listOf("phr")
        )
    }
}