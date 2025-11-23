package ru.cherenkov.data.checker

import ru.cherenkov.domain.model.HashAlgorithm
import ru.cherenkov.domain.repository.HashChecker
import ru.cherenkov.util.HashUtils

object HashCheckerFactory {
    fun create(algorithm: HashAlgorithm, hash: String): HashChecker {
        return when (algorithm) {
            HashAlgorithm.MD5 -> MD5HashChecker.create(hash)
            HashAlgorithm.SHA1 -> SHA1HashChecker.create(hash)
            HashAlgorithm.BCRYPT -> BcryptHashChecker.create(hash)
            HashAlgorithm.ARGON2 -> Argon2HashChecker.create(hash)
        }
    }
}

