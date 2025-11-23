package ru.cherenkov.data.checker

import ru.cherenkov.domain.repository.HashChecker
import ru.cherenkov.util.HashUtils
import java.security.MessageDigest

class MD5HashChecker(private val targetHash: ByteArray) : HashChecker {
    private val threadLocalMD = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

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
        fun create(hash: String): MD5HashChecker {
            return MD5HashChecker(HashUtils.hexToByteArray(hash))
        }
    }
}

