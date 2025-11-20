package ru.cherenkov.domain.service

import ru.cherenkov.domain.model.HashAlgorithm
import ru.cherenkov.util.HashUtils

class HashDetectionService {
    fun detectAlgorithm(hash: String): HashAlgorithm {
        val normalizedHash = HashUtils.normalizeHash(hash)
        
        return when {
            // Проверяем bcrypt: должен начинаться с $2a$, $2b$, $2y$
            normalizedHash.startsWith("\$2a\$") || 
            normalizedHash.startsWith("\$2b\$") || 
            normalizedHash.startsWith("\$2y\$") -> HashAlgorithm.BCRYPT
            normalizedHash.startsWith("\$argon2") -> HashAlgorithm.ARGON2
            hash.length == 32 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> HashAlgorithm.MD5
            hash.length == 40 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> HashAlgorithm.SHA1
            else -> throw IllegalArgumentException("Unsupported hash format: ${hash.take(20)}...")
        }
    }
}

