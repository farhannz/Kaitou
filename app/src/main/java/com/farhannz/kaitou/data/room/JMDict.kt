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




//@Database(entities = [Word::class], version = 1)
//abstract class DictionaryDatabase : RoomDatabase() {
//    abstract fun wordDao(): WordDao
//    companion object {
//        fun getDatabase(context: Context): DictionaryDatabase {
//            return Room.databaseBuilder(
//                context.applicationContext,
//                DictionaryDatabase::class.java,
//                "jmdict"
//            ).createFromAsset("jmdict/jmdict.db")
//                .fallbackToDestructiveMigration(false)
//                .build()
//        }
//
//    }
//}

//@Database(
//    entities = [Word::class, Kanji::class, Kana::class, Sense::class, Gloss::class],
//    version = 1,
//    exportSchema = false
//)
//@TypeConverters(Converters::class)
//abstract class JMdictDatabase : RoomDatabase() {
//    abstract fun wordDao(): WordDao
//    abstract fun kanjiDao(): KanjiDao
//    abstract fun kanaDao(): KanaDao
//    abstract fun senseDao(): SenseDao
//    abstract fun glossDao(): GlossDao
//
//    companion object {
//        @Volatile
//        private var INSTANCE: JMdictDatabase? = null
//
//        fun getDatabase(context: Context): JMdictDatabase {
//            return INSTANCE ?: synchronized(this) {
//                val instance = Room.databaseBuilder(
//                    context.applicationContext,
//                    JMdictDatabase::class.java,
//                    "jmdict_database"
//                ).build()
//                INSTANCE = instance
//                instance
//            }
//        }
//    }
//}

//// Type converters for JSON strings
//class Converters {
//    @TypeConverter
//    fun fromStringList(value: List<String>): String {
//        return Json.encodeToString(value)
//    }
//
//    @TypeConverter
//    fun toStringList(value: String): List<String> {
//        return Json.decodeFromString(value)
//    }
//}