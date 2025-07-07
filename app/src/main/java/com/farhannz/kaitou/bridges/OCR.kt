package com.farhannz.kaitou.bridges

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object OCRBridge {
    init {
        System.loadLibrary("native-lib")
    }

    external fun runOCR(): String
    external fun initPaddle()
    external fun initMecab(mecabDict: String)


//    fun copyAssets(context: Context, assetName: String) : String {
//        val assetManager = context.assets
//        val targetDir = File(context.filesDir, assetName)
//        targetDir.mkdirs()
//
//        val files = assetManager.list(assetName) ?: return ""
//        for (filename in files) {
//            val outFile = File(targetDir, filename)
//            if (outFile.exists()) continue
//
//            assetManager.open("$assetName/$filename").use { input ->
//                FileOutputStream(outFile).use { output ->
//                    input.copyTo(output)
//                }
//            }
//        }
//        return context.filesDir.absolutePath
//    }

    fun prepareInitModel(context: Context) {
//        val mecabDict = copyAssets(context, "neologd")
//        initMecab(mecabDict)
    }
}
