package ru.cherenkov.data.checker

import de.mkammerer.argon2.Argon2Factory
import ru.cherenkov.domain.repository.HashChecker
import ru.cherenkov.util.HashUtils

class Argon2HashChecker(private val hash: String) : HashChecker {
    companion object {
        val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
        
        fun create(hash: String): Argon2HashChecker {
            return Argon2HashChecker(HashUtils.normalizeHash(hash))
        }
    }

    override fun check(candidate: String): Boolean {
        return argon2.verify(hash, candidate.toCharArray())
    }
}

