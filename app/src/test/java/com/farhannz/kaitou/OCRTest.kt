package com.farhannz.kaitou

import com.farhannz.kaitou.data.models.TextLabel
import com.farhannz.kaitou.paddle.CTCLabelDecoder
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

class OCRCharacterValidationTest {

    val inputStream = this::class.java.classLoader
        ?.getResourceAsStream("dict.txt")
        ?: throw IllegalArgumentException("dict.txt not found in test resources")
    private val characters = BufferedReader(InputStreamReader(inputStream)).readLines()  // Your hardcoded + "blank"-prepended list

//    @Test
//    fun characterList_hasExpectedSizeAndBlankAtStart() {
//        assertEquals("Character list must have 18385 entries", 18385, characters.size)
//        assertEquals("First character must be 'blank'", "blank", characters.first())
//    }

    @Test
    fun characterList_containsEssentialCharacters() {
        val expectedChars = listOf("A", "Z", "a", "z", "0", "9", "あ", "ア", "日", "の", "。", "！")
        expectedChars.forEach {
            assertTrue("Missing expected character: '$it'", characters.contains(it))
        }
    }

    @Test
    fun decoder_decodesSimpleFakePredictionCorrectly() {
        val decoder = CTCLabelDecoder(characters)
        // Add 1 to account for the "blank" token at index 0
        val watashi = characters.indexOf("私") + 1
        val ha = characters.indexOf("は") + 1
        val ni = characters.indexOf("日") + 1
        val hon = characters.indexOf("本") + 1
        val jin = characters.indexOf("人") + 1

        val predIndices = listOf(intArrayOf(watashi, ha, ni, hon, jin))
        val predProbs = listOf(floatArrayOf(0.99f, 0.98f, 0.97f, 0.99f, 0.99f))

        val result = decoder.decode(predIndices, predProbs).firstOrNull()
        val (text, confidence) = result!!
        assertEquals("私は日本人", text)
    }
}
