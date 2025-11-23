package presentation.cli

import kotlinx.coroutines.*
import data.gpu.GPUAccelerator
import data.checker.HashCheckerFactory
import domain.entity.HashAlgorithm
import domain.service.AlphabetService
import domain.service.BruteForceService
import domain.service.HashDetectionService

fun main(args: Array<String>) = runBlocking {
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

    if (hash.isBlank()) {
        println("Ошибка: пустой хэш")
        return@runBlocking
    }

    val hashDetectionService = HashDetectionService()
    val algorithm = try {
        hashDetectionService.detectAlgorithm(hash)
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
    
    // Предупреждение для медленных алгоритмов
    if (algorithm == HashAlgorithm.BCRYPT || algorithm == HashAlgorithm.ARGON2) {
        println("⚠️  WARNING: Bcrypt/Argon2 are very slow algorithms. This may take a long time.")
        println("   Starting with digits only (0-9) for faster search...")
        println("   🚀 Using async batch processing with ${threadCount} parallel workers for acceleration...")
    }

    val checker = HashCheckerFactory.create(algorithm, hash)
    val alphabetService = AlphabetService()
    val bruteForceService = BruteForceService()
    
    // Для bcrypt и Argon2 используем более агрессивную стратегию - начинаем с очень маленького алфавита
    val alphabets = if (algorithm == HashAlgorithm.BCRYPT || algorithm == HashAlgorithm.ARGON2) {
        // Для медленных алгоритмов начинаем только с цифр
        listOf("easy")
    } else {
        alphabetService.getAlphabetsForMaxLength(maxLength)
    }
    
    var password: String? = null
    
    // Запускаем корутину для вывода прогресса
    // Для медленных алгоритмов обновляем чаще
    val progressInterval = if (algorithm == HashAlgorithm.BCRYPT || algorithm == HashAlgorithm.ARGON2) {
        2000L // Каждые 2 секунды для bcrypt/Argon2
    } else {
        5000L // Каждые 5 секунд для быстрых алгоритмов
    }
    
    val progressJob = launch {
        var lastUpdate = System.currentTimeMillis()
        var lastChecked = 0L
        var firstUpdate = true
        
        while (isActive) {
            delay(progressInterval)
            val currentTime = System.currentTimeMillis()
            val elapsed = (currentTime - lastUpdate) / 1000.0
            val currentChecked = bruteForceService.getTotalChecked()
            val checked = currentChecked - lastChecked
            val speed = if (elapsed > 0) checked / elapsed else 0.0
            val totalElapsed = (currentTime - startTime) / 1000.0
            
            // Выводим прогресс даже если скорость низкая (для медленных алгоритмов)
            if (firstUpdate || checked > 0 || currentChecked > 0) {
                val speedStr = if (speed > 0) "${"%.2f".format(speed)} pwd/s" else "calculating..."
                print("\r⏳ Checked: $currentChecked passwords | Speed: $speedStr | Elapsed: ${"%.1f".format(totalElapsed)}s")
                System.out.flush()
                firstUpdate = false
            }
            lastUpdate = currentTime
            lastChecked = currentChecked
        }
    }
    
    try {
        for (complexity in alphabets) {
            if (password != null) break
            
            val alphabet = alphabetService.getAlphabetForAlgorithm(algorithm, complexity)
            
            println("\n🔍 Starting search with alphabet: $complexity (${alphabet.length} characters)")
            println("   Length range: $minLength-$maxLength")
            println("   Searching... (progress will be shown every ${progressInterval / 1000} seconds)")
            
            password = bruteForceService.bruteForce(checker, alphabet, minLength, maxLength, threadCount, useGPU)
        }
        
        if (alphabets.size > 1 && password == null) {
            println("Alphabet size: ${alphabetService.getAlphabetForAlgorithm(algorithm, alphabets.last()).length} characters (final attempt)")
        }
    } finally {
        progressJob.cancel() // Останавливаем вывод прогресса
        println() // Новая строка после прогресса
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

