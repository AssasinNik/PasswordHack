package data.repository

import domain.repository.HashChecker
import org.mindrot.jbcrypt.BCrypt

class BcryptHashChecker(private val hash: String) : HashChecker {
    override fun check(candidate: String): Boolean {
        return BCrypt.checkpw(candidate, hash)
    }
}

