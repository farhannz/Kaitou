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
    suspend fun lookupWordsByText(text: String): List<Word>
}