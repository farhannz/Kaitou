package com.farhannz.kaitou.helpers

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.huggingface.tokenizers.jni.TokenizersLibrary
import ai.djl.util.Utils
import android.content.Context
import android.util.Log
import com.farhannz.kaitou.domain.ModelInput
import com.farhannz.kaitou.impl.onnxruntime.EmbeddingModel
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import kotlin.io.path.Path

object TransformerManager {
    private val LOG_TAG = this::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private lateinit var tokenizer: HuggingFaceTokenizer
    private lateinit var model: EmbeddingModel
    private val json = Json { ignoreUnknownKeys = true }
    fun copyAssetToCache(context: Context, assetFolder: String = "", assetName: String): String {
        val assetSubPath = "${assetFolder}/$assetName" // use relative asset path
        val outDir = File(context.cacheDir, "paddle")
        if (!outDir.exists()) outDir.mkdirs()

        val outFile = File(outDir, assetName)
        if (!outFile.exists()) {
            context.assets.open(assetSubPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }
    

    fun tokenizePair(first: String, second: String): Encoding {
        val encoded = tokenizer.encode(first, second)
        return encoded
    }

    fun rankPairs(idsList: LongArray, attnMaskList: LongArray, batchSize: Long): FloatArray {
        logger.DEBUG("BEFORE RUN")
        val result = model.predict(ModelInput(idsList, attnMaskList), batchSize)
        return result.embedding
    }

    fun getEmbeddings(text: String): FloatArray {
        val encoded = tokenizer.encode(text)
        val ids = encoded.ids
        val masks = encoded.attentionMask
        logger.DEBUG("ids: ${ids.joinToString(",")}")
        logger.DEBUG("masks: ${masks.joinToString(",")}")
        val embeddings = model.predict(ModelInput(ids, masks))
        return embeddings.embedding
    }

    fun copyAssetFolderToCache(
        context: Context,
        assetFolder: String = "",
        targetFolder: File = File(context.cacheDir, "")
    ) {
        val assetManager = context.assets
        val assets = assetManager.list(assetFolder) ?: return

        if (!targetFolder.exists()) targetFolder.mkdirs()

        for (assetName in assets) {
            val assetPath = if (assetFolder.isEmpty()) assetName else "$assetFolder/$assetName"
            val outFile = File(targetFolder, assetName)

            // Check if it's a directory
            val subAssets = assetManager.list(assetPath)
            if (!subAssets.isNullOrEmpty()) {
                // Recursively copy subfolder
                copyAssetFolderToCache(context, assetPath, outFile)
            } else {
                // It's a file, copy it
                if (!outFile.exists()) {
                    assetManager.open(assetPath).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }


    fun initialize(context: Context) {
        System.setProperty("ai.djl.offline", "true")

        copyAssetFolderToCache(
            context,
            "hotchpotch",
            File(context.cacheDir, "hotchpotch")
        )
        val modelName = "japanese-reranker-xsmall-v2"
        val modelPath =
            File(context.cacheDir, "hotchpotch/$modelName/int8.onnx").absolutePath
        val tokenizerPath =
            File(context.cacheDir, "hotchpotch/$modelName/tokenizer.json").absolutePath
        model = EmbeddingModel(modelPath, useSentenceEmbedding = false)
        tokenizer = HuggingFaceTokenizer.newInstance(Path(tokenizerPath))
    }
}