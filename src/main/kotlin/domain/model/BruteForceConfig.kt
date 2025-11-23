package ru.cherenkov.domain.model

data class BruteForceConfig(
    val hash: String,
    val algorithm: HashAlgorithm,
    val minLength: Int,
    val maxLength: Int,
    val threadCount: Int,
    val useGPU: Boolean = false
)

