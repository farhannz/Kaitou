package com.farhannz.kaitou

fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
    require(a.size == b.size) { "Vectors must have the same length" }

    var dot = 0.0
    var normA = 0.0
    var normB = 0.0

    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).toFloat()
}