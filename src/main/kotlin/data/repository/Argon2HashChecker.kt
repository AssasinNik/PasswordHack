package data.repository

import domain.repository.HashChecker
import de.mkammerer.argon2.Argon2Factory

class Argon2HashChecker(private val hash: String) : HashChecker {
    companion object {
        val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    }

    override fun check(candidate: String): Boolean {
        return argon2.verify(hash, candidate.toCharArray())
    }
}

