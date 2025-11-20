package domain.usecase

import domain.entity.HashAlgorithm

class DetectAlgorithmUseCase {
    fun execute(hash: String): HashAlgorithm {
        return when {
            hash.startsWith("\$2a\$") || hash.startsWith("\$2b\$") || hash.startsWith("\$2y\$") -> HashAlgorithm.BCRYPT
            hash.startsWith("\$argon2") -> HashAlgorithm.ARGON2
            hash.length == 32 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> HashAlgorithm.MD5
            hash.length == 40 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> HashAlgorithm.SHA1
            else -> throw IllegalArgumentException("Unsupported hash format: $hash")
        }
    }
}

