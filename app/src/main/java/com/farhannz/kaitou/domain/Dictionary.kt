package com.farhannz.kaitou.domain

interface Dictionary {
    suspend fun lookup(word: String)
}