package com.farhannz.kaitou.helpers

import android.content.Context
import com.farhannz.kaitou.data.models.DictionaryInfo
import com.farhannz.kaitou.data.models.Word
import com.farhannz.kaitou.external.jmdict_simplified.jmdict.JMdictJsonElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedReader
import java.io.InputStream



// JSON Loader
class DictionaryJsonLoader(private val context: Context) {
//    private val database = DictionaryDatabase.getDatabase(context)
    private val LOG_TAG = DictionaryJsonLoader::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun processMetadata(jsonChunk: String) {
        try {
            // Extract and parse metadata
            val cleanJson = "{$jsonChunk}"
            val metadata = json.decodeFromString<DictionaryInfo>(cleanJson)

            val info = DictionaryInfo(
                version = metadata.version,
                languages = metadata.languages,
                commonOnly = metadata.commonOnly,
                dictDate = metadata.dictDate,
                dictRevisions = metadata.dictRevisions
            )
            logger.DEBUG("$info")
//            database.dictionaryInfoDao().insert(info)
//
//            // Insert tags
//            metadata.tags?.let { tags ->
//                val tagList = tags.map { (key, description) -> Tag(key, description) }
//                database.tagDao().insertAll(tagList)
//            }
        } catch (e: Exception) {
            println("Error processing metadata: ${e.message}")
        }
    }

    private suspend fun processWordChunk(jsonChunk: String) {
        try {
            // Extract word objects from chunk
            val wordPattern = "\\{\"id\".*?\\}\\]\\}".toRegex()
            val words = mutableListOf<Word>()

            wordPattern.findAll(jsonChunk).forEach { match ->
                try {
                    val wordJson = match.value
                    val word = json.decodeFromString<Word>(wordJson)
                    words.add(word)
                } catch (e: Exception) {
                    println("Error parsing word: ${e.message}")
                }
            }

//            if (words.isNotEmpty()) {
//                database.wordDao().insertAll(words)
//            }
        } catch (e: Exception) {
            println("Error processing word chunk: ${e.message}")
        }
    }

    suspend fun loadDictionaryFromJson(reader: BufferedReader) {
        try {
            var line: String?
            val buffer = StringBuilder()
            var inWordsArray = false
            var braceCount = 0

            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()

                // Parse metadata first
                if (trimmed.contains("\"version\"") || trimmed.contains("\"languages\"") ||
                    trimmed.contains("\"dictDate\"") || trimmed.contains("\"tags\"")) {
                    buffer.append(line).append("\n")
                    continue
                }

                // Detect start of words array
                if (trimmed.contains("\"words\"")) {
                    inWordsArray = true
                    // Process metadata collected so far
                    if (buffer.isNotEmpty()) {
                        processMetadata(buffer.toString())
                        buffer.clear()
                    }
                    continue
                }

                // Process words in chunks
                if (inWordsArray) {
                    buffer.append(line).append("\n")

                    // Count braces to detect complete word objects
                    braceCount += trimmed.count { it == '{' }
                    braceCount -= trimmed.count { it == '}' }

                    // Process when we have complete word objects (every 100 words)
                    if (buffer.length > 500 || (braceCount == 0 && buffer.contains("}"))) {
                        processWordChunk(buffer.toString())
                        buffer.clear()

                        // Force garbage collection
                        System.gc()
                    }
                }
            }

            // Process remaining buffer
            if (buffer.isNotEmpty()) {
                processWordChunk(buffer.toString())
            }

        } catch (e: Exception) {
            throw Exception("Failed to load dictionary: ${e.message}")
        }
    }

    suspend fun loadFromAssets(fileName: String) {
        val reader = context.assets.open(fileName).bufferedReader()
        loadDictionaryFromJson(reader)
    }


}




@Serializable
data class JMDictData(
    @SerialName("words") val words : List<JMdictJsonElement.Word>
)

object JMdictManager {
    private var jmdictData: JMDictData? = null

    /**
     * Loads the JMdict data into memory from an input stream.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun initialize(inputStream: InputStream) {
        val jsonIgnoreUnknown = Json { ignoreUnknownKeys = true }
        jmdictData = jsonIgnoreUnknown.decodeFromStream<JMDictData>(inputStream)
    }

    /**
     * Returns the loaded JMdict data.
     */
    fun getJMdictData(): JMDictData {
        return jmdictData ?: throw IllegalStateException("JMdict data has not been initialized. Call initialize() first.")
    }

    /**
     * Clears the JMdict data from memory.
     */
    fun clear() {
        jmdictData = null
    }
}