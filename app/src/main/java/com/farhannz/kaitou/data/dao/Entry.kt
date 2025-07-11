//package com.farhannz.kaitou.data.dao
//
//import androidx.room.*
//import com.farhannz.kaitou.data.models.*
//
//@Dao
//interface EntryDao {
//    @Insert fun insert(entry: Entry)
//    @Insert fun insertAll(kanji: List<Kanji>)
//    @Insert fun insertAll(kana: List<Kana>)
//    @Insert fun insertAll(glosses: List<Gloss>)
//
//    @Transaction
//    fun insertFull(entry: Entry, kanji: List<Kanji>, kana: List<Kana>, glosses: List<Gloss>) {
//        insert(entry)
//        insertAll(kanji); insertAll(kana); insertAll(glosses)
//    }
//
//    @Transaction
//    @Query("""
//    SELECT * FROM entries
//    WHERE id = :entryId
//  """)
//    fun getById(entryId: Int): EntryWithRelations?
//}