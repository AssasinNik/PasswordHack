package ru.cherenkov.data.checker

import ru.cherenkov.domain.repository.HashChecker
import ru.cherenkov.util.HashUtils
import java.security.MessageDigest

class SHA1HashChecker(private val targetHash: ByteArray) : HashChecker {
    private val threadLocalMD = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-1") }

    override fun check(candidate: String): Boolean {
        return checkBytes(candidate.toByteArray())
    }
    
    override fun checkBytes(candidate: ByteArray): Boolean {
        val md = threadLocalMD.get()
        md.reset()
        md.update(candidate)
        val digest = md.digest()
        return java.util.Arrays.equals(digest, targetHash)
    }
    
    companion object {
        fun create(hash: String): SHA1HashChecker {
            return SHA1HashChecker(HashUtils.hexToByteArray(hash))
        }
    }
}

