package com.farhannz.kaitou.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.data.dao.*


@Database(
    entities = [
        DictionaryInfo::class,
        Tag::class,
        Word::class,
        Kanji::class,
        Kana::class,
        Sense::class,
        Gloss::class
    ],
    exportSchema = false,
    version = 1
)
abstract class JmdictDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
        companion object {
        fun getDatabase(context: Context): JmdictDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                JmdictDatabase::class.java,
                "jmdict"
            ).createFromAsset("jmdict/jmdict_normalized.db")
                .fallbackToDestructiveMigration(false)
                .build()
        }

    }
}
