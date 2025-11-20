package presentation.cli

import domain.entity.HashAlgorithm
import domain.usecase.BruteForceUseCase
import domain.usecase.DetectAlgorithmUseCase
import domain.usecase.GetAlphabetUseCase
import data.factory.HashCheckerFactory
import data.gpu.GPUAccelerator
import kotlinx.coroutines.runBlocking

class PasswordHackApp {
    private val detectAlgorithmUseCase = DetectAlgorithmUseCase()
    private val getAlphabetUseCase = GetAlphabetUseCase()
    private val bruteForceUseCase = BruteForceUseCase()
    
    fun run(args: Array<String>) = runBlocking {
        val parsedArgs = ArgumentParser.parse(args)
        
        if (parsedArgs.containsKey("help") || args.isEmpty()) {
            HelpPrinter.print()
            return@runBlocking
        }

        val hash = parsedArgs["hash"] ?: run {
            println("Ошибка: необходимо указать хэш через -hash=\"<hash>\"")
            HelpPrinter.print()
            return@runBlocking
        }

        if (hash.isEmpty()) {
            println("Ошибка: пустой хэш")
            return@runBlocking
        }

        val algorithm = try {
            detectAlgorithmUseCase.execute(hash)
        } catch (e: Exception) {
            println("Ошибка: ${e.message}")
            return@runBlocking
        }

        val threadCount = parsedArgs["threads"]?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors()
        val maxLength = parsedArgs["maxLength"]?.toIntOrNull() ?: 8
        val minLength = parsedArgs["minLength"]?.toIntOrNull() ?: 1
        val useGPU = parsedArgs.containsKey("gpu")
        
        val startTime = System.currentTimeMillis()

        println("Detected algorithm: $algorithm")
        println("Using $threadCount threads")
        if (useGPU) {
            if (GPUAccelerator.isAvailable() && (algorithm == HashAlgorithm.MD5 || algorithm == HashAlgorithm.SHA1)) {
                println("GPU acceleration: Available and will be used")
            } else {
                println("GPU acceleration: Not available or not supported for this algorithm")
            }
        }
        println("Password length range: $minLength-$maxLength")

        val checker = HashCheckerFactory.create(algorithm, hash)
        
        // Прогрессивный поиск: сначала с меньшим алфавитом для ускорения
        // Если не найдено, переходим к большему алфавиту
        val alphabets = when {
            maxLength <= 6 -> listOf("easy")
            maxLength <= 8 -> listOf("easy", "medium")
            else -> listOf("easy", "medium", "hard")
        }
        
        var password: String? = null
        
        try {
            for (complexity in alphabets) {
                if (password != null) break
                
                val alphabet = getAlphabetUseCase.execute(algorithm, complexity)
                
                if (alphabets.size > 1) {
                    println("Trying alphabet: $complexity (${alphabet.length} characters)")
                } else {
                    println("Alphabet size: ${alphabet.length} characters")
                }
                
                password = bruteForceUseCase.execute(checker, alphabet, minLength, maxLength, threadCount, useGPU)
            }
            
            if (alphabets.size > 1 && password == null) {
                println("Alphabet size: ${getAlphabetUseCase.execute(algorithm, alphabets.last()).length} characters (final attempt)")
            }
        } finally {
            if (useGPU) {
                GPUAccelerator.cleanup()
            }
        }
        
        println("Search space: [$minLength-$maxLength] chars")
        val duration = (System.currentTimeMillis() - startTime) / 1000.0

        if (password != null) {
            println("\n✅ Password found: $password")
        } else {
            println("\n❌ Password not found within search parameters")
            println("   Try increasing -length parameter or check if the hash is correct")
        }

        println("⏱️  Execution time: ${"%.2f".format(duration)} seconds")
    }
}

