package com.farhannz.kaitou.impl.onnxruntime

import ai.onnxruntime.OrtSession

/**
 * standard flavor: plain onnxruntime-android has no QNN, so accelerate via
 * NNAPI (system drivers, zero extra APK size). Falls back to CPU threads.
 */
fun applyHardwareAcceleration(options: OrtSession.SessionOptions) {
    try {
        options.addNnapi()
        println("NNAPI is added")
    } catch (e: Exception) {
        println("NNAPI is not available, ${e.message}")
        try {
            options.setIntraOpNumThreads(4)
            println("CPU with 4 intra-op threads")
        } catch (e: Exception) {
            println("Thread config failed, ${e.message}")
        }
    }
}
