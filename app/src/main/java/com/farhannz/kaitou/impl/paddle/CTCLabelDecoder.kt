package com.farhannz.kaitou.impl.paddle

import kotlin.text.iterator

abstract class BaseRecLabelDecoder(
    characterList: List<String>? = null,
    useSpaceChar: Boolean = true
) {
    val character: List<String>
    val dict: Map<String, Int>
    var reverse: Boolean = false

    init {
        var baseCharacters = characterList ?: "0123456789abcdefghijklmnopqrstuvwxyz".map { it.toString() }

        if (useSpaceChar) {
            baseCharacters = baseCharacters + " "
        }

        character = addSpecialChar(baseCharacters)

        // Build dictionary mapping characters to indices
        dict = character.withIndex().associate { (index, char) -> char to index }
    }

    open fun addSpecialChar(characterList: List<String>): List<String> = characterList

    open fun getIgnoredTokens(): List<Int> = listOf(0)

    fun predReverse(pred: String): String {
        val result = mutableListOf<String>()
        var current = ""
        for (char in pred) {
            if (!char.toString().matches(Regex("[a-zA-Z0-9 :*./%+-]"))) {
                if (current.isNotEmpty()) result.add(current)
                result.add(char.toString())
                current = ""
            } else {
                current += char
            }
        }
        if (current.isNotEmpty()) result.add(current)
        return result.reversed().joinToString("")
    }

    fun decode(
        textIndex: List<IntArray>,
        textProb: List<FloatArray>? = null,
        isRemoveDuplicate: Boolean = false
    ): List<Pair<String, Float>> {
        val ignoredTokens = getIgnoredTokens().toSet()
        val resultList = mutableListOf<Pair<String, Float>>()

        for ((batchIdx, indices) in textIndex.withIndex()) {
            val selection = BooleanArray(indices.size) { true }

            // Handle duplicate removal
            if (isRemoveDuplicate) {
                for (i in 1 until indices.size) {
                    selection[i] = indices[i] != indices[i - 1]
                }
            }

            // Handle ignored tokens
            for ((i, token) in indices.withIndex()) {
                if (token in ignoredTokens) {
                    selection[i] = false
                }
            }

            val charList = mutableListOf<String>()
            val confList = mutableListOf<Float>()

            for ((i, token) in indices.withIndex()) {
                if (selection[i] && token < character.size) {
                    charList.add(character[token])
                    confList.add(textProb?.getOrNull(batchIdx)?.getOrNull(i) ?: 1f)
                }
            }

            val text = charList.joinToString("").let { if (reverse) predReverse(it) else it }
            val meanConf = if (confList.isNotEmpty()) confList.average().toFloat() else 0f
            resultList.add(text to meanConf)
        }
        return resultList
    }

    open operator fun invoke(pred: Array<FloatArray>): Pair<List<String>, List<Float>> {
        val predsIdx = pred.map { row ->
            row.withIndex().maxByOrNull { it.value }?.index ?: 0
        }.toIntArray()

        val predsProb = pred.map { row ->
            row.maxOrNull() ?: 0f
        }.toFloatArray()

        val decoded = decode(listOf(predsIdx), listOf(predsProb), isRemoveDuplicate = true)
        val texts = decoded.map { it.first }
        val scores = decoded.map { it.second }
        return texts to scores
    }
}

class CTCLabelDecoder(
    characterList: List<String>? = null,
    useSpaceChar: Boolean = true
) : BaseRecLabelDecoder(characterList, useSpaceChar) {

    override fun addSpecialChar(characterList: List<String>): List<String> {
        return listOf("blank") + characterList
    }

    override operator fun invoke(pred: Array<FloatArray>): Pair<List<String>, List<Float>> {
        val predsIdx = pred.map { row ->
            row.withIndex().maxByOrNull { it.value }?.index ?: 0
        }.toIntArray()

        val predsProb = pred.map { row ->
            row.maxOrNull() ?: 0f
        }.toFloatArray()

        val decoded = decode(listOf(predsIdx), listOf(predsProb), isRemoveDuplicate = true)
        val texts = decoded.map { it.first }
        val scores = decoded.map { it.second }
        return texts to scores
    }
}