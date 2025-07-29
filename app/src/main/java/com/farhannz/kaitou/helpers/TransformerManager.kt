package com.farhannz.kaitou.helpers

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.huggingface.tokenizers.jni.TokenizersLibrary
import ai.djl.util.Utils
import android.content.Context
import android.util.Log
import com.farhannz.kaitou.domain.ModelInput
import com.farhannz.kaitou.impl.OnnxModel
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import kotlin.io.path.Path

object TransformerManager {
    lateinit var tokenizer: HuggingFaceTokenizer
    lateinit var model: OnnxModel

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

        copyAssetFolderToCache(context, "onnx_export", File(context.cacheDir, "onnx_export"))
        val modelPath = File(context.cacheDir, "onnx_export/onnx/quantized.onnx").absolutePath
        val tokenizerPath = File(context.cacheDir, "onnx_export/tokenizer.json").absolutePath
        model = OnnxModel(modelPath)
        tokenizer = HuggingFaceTokenizer.newInstance(Path(tokenizerPath))
    }
}