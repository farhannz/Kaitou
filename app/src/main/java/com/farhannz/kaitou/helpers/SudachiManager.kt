package com.farhannz.kaitou.helpers
//import android.content.Context
//import android.util.Log
//import com.worksap.nlp.sudachi.Config
//import com.worksap.nlp.sudachi.Dictionary
//import com.worksap.nlp.sudachi.DictionaryFactory
//import com.worksap.nlp.sudachi.Tokenizer
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import java.io.File
//import java.io.FileOutputStream
//
//object SudachiManager {
//
//    private var tokenizer: Tokenizer? = null
//    private const val SUDACHI_DIR = "sudachi"
//    private const val DICT_FILE = "system_core.dic"
//
//    private const val CONFIG_FILE = "sudachi.json"
//
//    suspend fun copySudachiAssets(context: Context) {
//        val assetManager = context.assets
//        val targetDir = File(context.filesDir, "sudachi")
//        targetDir.mkdirs()
//
//        val files = assetManager.list("sudachi") ?: return
//        for (filename in files) {
//            val outFile = File(targetDir, filename)
//            if (outFile.exists()) continue
//
//            assetManager.open("sudachi/$filename").use { input ->
//                FileOutputStream(outFile).use { output ->
//                    input.copyTo(output)
//                }
//            }
//        }
//    }
//
//    suspend fun getTokenizer(context: Context): Tokenizer {
//        tokenizer?.let { return it }
//
//        return withContext(Dispatchers.IO) {
//            copySudachiAssets(context)
//
//            val configFile = File(context.filesDir, "sudachi/sudachi.json").toPath()
//            Log.d("Sudachi", "Config path: ${configFile}")
//            // ✅ Use the JSON file directly
//            val config = Config.fromFile(configFile)
//            val dictionary = DictionaryFactory().create(config)
//            val newTokenizer = dictionary.create()
//            tokenizer = newTokenizer
//            newTokenizer
//        }
//    }
//
//    private fun copyAsset(context: Context, assetPath: String, destFile: File) {
//        context.assets.open(assetPath).use { inputStream ->
//            FileOutputStream(destFile).use { outputStream ->
//                inputStream.copyTo(outputStream)
//            }
//        }
//    }
//}