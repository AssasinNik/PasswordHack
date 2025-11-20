package ru.cherenkov.domain.service

import ru.cherenkov.domain.model.HashAlgorithm

class AlphabetService {
    fun getAlphabetForAlgorithm(algorithm: HashAlgorithm, complexity: String = "medium"): String {
        return when (algorithm) {
            HashAlgorithm.MD5, HashAlgorithm.SHA1 -> {
                when (complexity) {
                    "easy" -> "0123456789abcdefghijklmnopqrstuvwxyz"
                    "medium" -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    "hard", "very_hard" -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#\$%^&*()_+-=[]{}|;:,.<>?"
                    else -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                }
            }
            HashAlgorithm.BCRYPT, HashAlgorithm.ARGON2 -> {
                when (complexity) {
                    "easy" -> "0123456789"
                    "medium" -> "0123456789abcdefghijklmnopqrstuvwxyz"
                    "hard", "very_hard" -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    else -> "0123456789"
                }
            }
        }
    }
    
    fun getAlphabetsForMaxLength(maxLength: Int): List<String> {
        return when {
            maxLength <= 6 -> listOf("easy")
            maxLength <= 8 -> listOf("easy", "medium")
            else -> listOf("easy", "medium", "hard")
        }
    }
}

