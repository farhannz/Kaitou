package com.farhannz.kaitou.impl.onnxruntime

import ai.onnxruntime.OrtSession

/**
 * qnn flavor: prefer Hexagon HTP (NPU) via the QNN execution provider,
 * falling back to XNNPACK then CPU threads on unsupported devices.
 */
fun applyHardwareAcceleration(options: OrtSession.SessionOptions) {
    try {
        val providerOptions = mapOf(Pair("backend_type", "htp"))
        options.addQnn(providerOptions)
        println("QNN is added")
    } catch (e: Exception) {
        println("QNN is not available, ${e.message}")
        try {
            val providerOptions = mapOf(Pair("intra_op_num_threads", "1"))
            options.addXnnpack(providerOptions)
            options.setIntraOpNumThreads(1)
            println("addXnnpack is added")
        } catch (e: Exception) {
            println("addXnnpack is not available, ${e.message}")
            try {
                options.setIntraOpNumThreads(4)
                println("CPU with 4 intra-op threads")
            } catch (e: Exception) {
                println("Thread config failed, ${e.message}")
            }
        }
    }
}
