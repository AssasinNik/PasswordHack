package domain.usecase

import domain.entity.HashAlgorithm

class GetAlphabetUseCase {
    fun execute(algorithm: HashAlgorithm, complexity: String = "medium"): String {
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
}

