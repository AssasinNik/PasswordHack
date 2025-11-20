package data.factory

import domain.entity.HashAlgorithm
import domain.repository.HashChecker
import data.repository.MD5HashChecker
import data.repository.SHA1HashChecker
import data.repository.BcryptHashChecker
import data.repository.Argon2HashChecker

object HashCheckerFactory {
    fun create(algorithm: HashAlgorithm, hash: String): HashChecker {
        return when (algorithm) {
            HashAlgorithm.MD5 -> MD5HashChecker.create(hash)
            HashAlgorithm.SHA1 -> SHA1HashChecker.create(hash)
            HashAlgorithm.BCRYPT -> BcryptHashChecker(hash)
            HashAlgorithm.ARGON2 -> Argon2HashChecker(hash)
        }
    }
}

