package data.generator

import java.math.BigInteger

class PasswordGenerator(private val alphabet: String, private val length: Int) {
    private val alphabetSize = alphabet.length
    private val password = CharArray(length) { alphabet[0] }
    private val indices = IntArray(length) { 0 }
    private var cachedString: String? = null
    private var cachedBytes: ByteArray? = null
    private var isDirty = true
    
    fun reset() {
        for (i in password.indices) {
            password[i] = alphabet[0]
            indices[i] = 0
        }
        isDirty = true
        cachedString = null
        cachedBytes = null
    }
    
    fun increment(): Boolean {
        isDirty = true
        cachedString = null
        cachedBytes = null
        var i = length - 1
        while (i >= 0) {
            if (indices[i] < alphabetSize - 1) {
                indices[i]++
                password[i] = alphabet[indices[i]]
                return true
            } else {
                indices[i] = 0
                password[i] = alphabet[0]
                i--
            }
        }
        return false
    }
    
    override fun toString(): String {
        if (isDirty || cachedString == null) {
            cachedString = String(password)
            isDirty = false
        }
        return cachedString!!
    }
    
    fun getBytes(): ByteArray {
        if (isDirty || cachedBytes == null) {
            cachedBytes = String(password).toByteArray()
            isDirty = false
        }
        return cachedBytes!!
    }
    
    fun setFromIndex(index: Long) {
        isDirty = true
        cachedString = null
        cachedBytes = null
        var currentIndex = index
        for (i in length - 1 downTo 0) {
            val idx = (currentIndex % alphabetSize).toInt()
            indices[i] = idx
            password[i] = alphabet[idx]
            currentIndex /= alphabetSize
        }
    }
    
    fun setFromIndexBigInt(index: BigInteger) {
        isDirty = true
        cachedString = null
        cachedBytes = null
        var currentIndex = index
        val alphabetSizeBig = BigInteger.valueOf(alphabetSize.toLong())
        for (i in length - 1 downTo 0) {
            val remainder = currentIndex.mod(alphabetSizeBig)
            val idx = remainder.toInt()
            indices[i] = idx
            password[i] = alphabet[idx]
            currentIndex = currentIndex.divide(alphabetSizeBig)
        }
    }
}

