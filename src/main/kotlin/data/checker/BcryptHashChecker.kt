package data.checker

import org.mindrot.jbcrypt.BCrypt
import domain.repository.HashChecker
import data.util.HexConverter

class BcryptHashChecker(private val hash: String) : HashChecker {
    override fun check(candidate: String): Boolean {
        return BCrypt.checkpw(candidate, hash)
    }
    
    companion object {
        fun create(hash: String): BcryptHashChecker {
            return BcryptHashChecker(HexConverter.normalizeHash(hash))
        }
    }
}

