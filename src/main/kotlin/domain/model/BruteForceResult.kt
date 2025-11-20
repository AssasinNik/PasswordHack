package ru.cherenkov.domain.model

sealed class BruteForceResult {
    data class Found(val password: String) : BruteForceResult()
    data class Progress(val checked: Long, val total: Long) : BruteForceResult()
    object Completed : BruteForceResult()
    data class Error(val message: String) : BruteForceResult()
}

