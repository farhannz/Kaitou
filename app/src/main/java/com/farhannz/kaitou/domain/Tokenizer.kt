package com.farhannz.kaitou.domain

import ai.onnxruntime.OnnxTensor

// Abstract base classes
interface Tokenizer {
    fun tokenize(text: String): List<Long>
    fun decode(tokens: List<Long>): String
}
