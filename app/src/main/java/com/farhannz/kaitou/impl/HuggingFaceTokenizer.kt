package com.farhannz.kaitou.impl

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import com.farhannz.kaitou.domain.Tokenizer
import java.nio.file.Path

class HFTokenizer(
    private val tokenizer: HuggingFaceTokenizer
) : Tokenizer {

    override fun tokenize(text: String): List<Long> {
        return tokenizer.encode(text).ids.toList()
    }

    override fun decode(tokens: List<Long>): String {
        return tokenizer.decode(tokens.toLongArray())
    }

    companion object {
        fun fromPretrained(modelName: String): HFTokenizer {
            return HFTokenizer(HuggingFaceTokenizer.newInstance(modelName))
        }

        fun fromLocal(path: Path): HFTokenizer {
            return HFTokenizer(HuggingFaceTokenizer.newInstance(path))
        }
    }
}