package ru.cherenkov.data.checker

import org.mindrot.jbcrypt.BCrypt
import ru.cherenkov.domain.repository.HashChecker
import ru.cherenkov.util.HashUtils

class BcryptHashChecker(private val hash: String) : HashChecker {
    override fun check(candidate: String): Boolean {
        return BCrypt.checkpw(candidate, hash)
    }
    
    companion object {
        fun create(hash: String): BcryptHashChecker {
            return BcryptHashChecker(HashUtils.normalizeHash(hash))
        }
    }
}

