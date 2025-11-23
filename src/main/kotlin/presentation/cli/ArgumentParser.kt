package presentation.cli

object ArgumentParser {
    fun parse(args: Array<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg.startsWith("-hash=") -> {
                    var hashValue = arg.substringAfter("-hash=")
                    // Если значение начинается с кавычки, но не заканчивается, возможно оно разбито на несколько аргументов
                    // Это может произойти в PowerShell из-за обработки специальных символов
                    if ((hashValue.startsWith("\"") && !hashValue.endsWith("\"")) || 
                        (hashValue.startsWith("'") && !hashValue.endsWith("'"))) {
                        // Собираем хэш из нескольких аргументов
                        val parts = mutableListOf<String>()
                        parts.add(hashValue)
                        i++
                        while (i < args.size && !args[i].startsWith("-")) {
                            parts.add(args[i])
                            i++
                        }
                        hashValue = parts.joinToString(" ")
                        i-- // Откатываем, так как следующий цикл увеличит i
                    }
                    // Убираем кавычки и восстанавливаем экранированные символы
                    hashValue = hashValue.trim('"', '\'')
                    // Восстанавливаем $ из экранированных версий (для PowerShell)
                    hashValue = hashValue.replace("\\$", "$")
                    result["hash"] = hashValue
                }
                arg.startsWith("-length=") || arg.startsWith("-maxLength=") -> {
                    result["maxLength"] = arg.substringAfter("=").toIntOrNull()?.toString() ?: "8"
                }
                arg.startsWith("-minLength=") -> {
                    result["minLength"] = arg.substringAfter("=").toIntOrNull()?.toString() ?: "1"
                }
                arg.startsWith("-threads=") || arg.startsWith("-t=") -> {
                    result["threads"] = arg.substringAfter("=").toIntOrNull()?.toString() 
                        ?: Runtime.getRuntime().availableProcessors().toString()
                }
                arg == "-gpu" || arg == "--gpu" -> {
                    result["gpu"] = "true"
                }
                arg == "-help" || arg == "--help" || arg == "-h" -> {
                    result["help"] = "true"
                }
            }
            i++
        }
        
        return result
    }
}

