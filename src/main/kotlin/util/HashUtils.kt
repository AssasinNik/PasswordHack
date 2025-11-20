package ru.cherenkov.util

object HashUtils {
    fun normalizeHash(hash: String): String {
        // Нормализуем хэш: восстанавливаем $ если они были повреждены PowerShell
        var normalized = hash.replace("\\\\", "\\").replace("\\$", "$")
        // Если хэш bcrypt начинается без $, добавляем его
        if (normalized.matches(Regex("^2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$"))) {
            normalized = "\$" + normalized
        }
        // Если хэш argon2 начинается без $, добавляем его
        if (normalized.startsWith("argon2") && !normalized.startsWith("\$argon2")) {
            normalized = "\$" + normalized
        }
        return normalized
    }
    
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

