package domain.repository

interface HashChecker {
    fun check(candidate: String): Boolean
    fun checkBytes(candidate: ByteArray): Boolean = check(String(candidate))
}

