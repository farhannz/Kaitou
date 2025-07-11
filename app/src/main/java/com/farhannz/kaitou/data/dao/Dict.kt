package com.farhannz.kaitou.data.dao

import android.content.Context
import androidx.room.*
import com.farhannz.kaitou.data.models.*


@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWord(id: String): Word?
}



//@Dao
//interface WordDao {
//    @Query("SELECT * FROM words WHERE id = :id")
//    suspend fun getWord(id: String): Word?
//
//    @Query("SELECT * FROM words WHERE id IN (SELECT wordId FROM kanji WHERE text = :kanji)")
//    suspend fun getWordsByKanji(kanji: String): List<Word>
//
//    @Query("SELECT * FROM words WHERE id IN (SELECT wordId FROM kana WHERE text = :kana)")
//    suspend fun getWordsByKana(kana: String): List<Word>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertWord(word: Word)
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertWords(words: List<Word>)
//}

//@Dao
//interface KanjiDao {
//    @Query("SELECT * FROM kanji WHERE wordId = :wordId")
//    suspend fun getKanjiByWordId(wordId: String): List<Kanji>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertKanji(kanji: List<Kanji>)
//}
//
//@Dao
//interface KanaDao {
//    @Query("SELECT * FROM kana WHERE wordId = :wordId")
//    suspend fun getKanaByWordId(wordId: String): List<Kana>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertKana(kana: List<Kana>)
//}
//
//@Dao
//interface SenseDao {
//    @Query("SELECT * FROM senses WHERE wordId = :wordId")
//    suspend fun getSensesByWordId(wordId: String): List<Sense>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertSenses(senses: List<Sense>)
//}
//
//@Dao
//interface GlossDao {
//    @Query("SELECT * FROM glosses WHERE senseId = :senseId")
//    suspend fun getGlossesBySenseId(senseId: Long): List<Gloss>
//
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertGlosses(glosses: List<Gloss>)
//}

