package presentation.cli

object ArgumentParser {
    fun parse(args: Array<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        for (arg in args) {
            when {
                arg.startsWith("-hash=") -> {
                    result["hash"] = arg.substringAfter("-hash=").trim('"', '\'')
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
        }
        
        return result
    }
}

