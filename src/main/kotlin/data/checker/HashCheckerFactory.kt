package data.checker

import domain.entity.HashAlgorithm
import domain.repository.HashChecker

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

