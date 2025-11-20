package data.util

object HexConverter {
    fun hexToByteArray(hexString: String): ByteArray {
        val cleanHex = StringBuilder()
        for (c in hexString) {
            if (c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F') {
                cleanHex.append(c)
            }
        }
        val hex = cleanHex.toString()
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val charIndex = i * 2
            bytes[i] = ((hex[charIndex].digitToInt(16) shl 4) or hex[charIndex + 1].digitToInt(16)).toByte()
        }
        return bytes
    }
}

