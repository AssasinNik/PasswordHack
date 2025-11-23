package ru.cherenkov.presentation.cli

import kotlinx.coroutines.*
import ru.cherenkov.data.accelerator.GPUAccelerator
import ru.cherenkov.data.checker.HashCheckerFactory
import ru.cherenkov.domain.model.HashAlgorithm
import ru.cherenkov.domain.service.AlphabetService
import ru.cherenkov.domain.service.BruteForceService
import ru.cherenkov.domain.service.HashDetectionService

/**
 * Рассчитывает оптимальное количество корутин для брутфорса
 * на основе типа алгоритма и количества CPU ядер
 */
object CoroutineOptimizer {
    /**
     * Определяет оптимальное количество корутин для алгоритма
     * @param algorithm Тип алгоритма хэширования
     * @param requestedCount Запрошенное пользователем количество (null = авто)
     * @return Оптимальное количество корутин
     */
    fun calculateOptimalCoroutines(algorithm: HashAlgorithm, requestedCount: Int?): Int {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val isFastAlgorithm = algorithm == HashAlgorithm.MD5 || algorithm == HashAlgorithm.SHA1
        
        // Если пользователь явно указал количество, используем его
        if (requestedCount != null) {
            val maxRecommended = if (isFastAlgorithm) {
                cpuCores * 16 // Для быстрых алгоритмов максимум ядра * 16
            } else {
                cpuCores * 4  // Для медленных алгоритмов максимум ядра * 4
            }
            
            // Если запрошено больше оптимального, предупреждаем
            if (requestedCount > maxRecommended) {
                println("⚠️  Note: Requested $requestedCount coroutines may be excessive.")
                println("   Recommended maximum: $maxRecommended for ${algorithm.name}")
                println("   Using requested value anyway...")
            }
            return requestedCount
        }
        
        // Автоматический расчет оптимального количества
        return when {
            isFastAlgorithm -> {
                // Для быстрых алгоритмов (MD5, SHA-1) используем больше корутин
                // Каждая проверка быстрая, поэтому можем обрабатывать больше параллельно
                // Формула: ядра * 12-16 для максимальной производительности
                (cpuCores * 12).coerceAtMost(256).coerceAtLeast(16)
            }
            algorithm == HashAlgorithm.BCRYPT -> {
                // Для bcrypt используем меньше корутин, так как каждая проверка медленная
                // Формула: ядра * 2-3
                (cpuCores * 3).coerceAtMost(64).coerceAtLeast(8)
            }
            algorithm == HashAlgorithm.ARGON2 -> {
                // Для Argon2 используем еще меньше, так как он самый медленный
                // Формула: ядра * 2-2.5
                (cpuCores * 2).coerceAtMost(32).coerceAtLeast(4)
            }
            else -> {
                // По умолчанию
                cpuCores * 4
            }
        }
    }
    
    /**
     * Получает информацию о рекомендуемом количестве корутин
     */
    fun getRecommendationInfo(algorithm: HashAlgorithm): String {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val isFastAlgorithm = algorithm == HashAlgorithm.MD5 || algorithm == HashAlgorithm.SHA1
        
        val optimal = calculateOptimalCoroutines(algorithm, null)
        val maxRecommended = if (isFastAlgorithm) {
            cpuCores * 16
        } else {
            cpuCores * 4
        }
        
        return "Optimal: $optimal coroutines (max recommended: $maxRecommended for ${algorithm.name})"
    }
}

fun main(args: Array<String>) = runBlocking {
    val parsedArgs = ArgumentParser.parseArguments(args)
    
    if (parsedArgs.containsKey("help") || args.isEmpty()) {
        HelpPrinter.printHelp()
        return@runBlocking
    }

    val hash = parsedArgs["hash"] ?: run {
        println("Ошибка: необходимо указать хэш через -hash=\"<hash>\"")
        HelpPrinter.printHelp()
        return@runBlocking
    }

    if (hash.isEmpty()) {
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

    val requestedThreads = parsedArgs["threads"]?.toIntOrNull()
    val threadCount = CoroutineOptimizer.calculateOptimalCoroutines(algorithm, requestedThreads)
    val maxLength = parsedArgs["maxLength"]?.toIntOrNull() ?: 8
    val minLength = parsedArgs["minLength"]?.toIntOrNull() ?: 1
    val useGPU = parsedArgs.containsKey("gpu")
    
    val startTime = System.currentTimeMillis()

    val cpuCores = Runtime.getRuntime().availableProcessors()
    println("Detected algorithm: $algorithm")
    if (requestedThreads == null) {
        println("🚀 Auto-optimized coroutines: $threadCount (CPU cores: $cpuCores)")
        println("   ${CoroutineOptimizer.getRecommendationInfo(algorithm)}")
        println("   💡 Tip: Use -threads=N to override (max recommended: ${if (algorithm == HashAlgorithm.MD5 || algorithm == HashAlgorithm.SHA1) cpuCores * 16 else cpuCores * 4})")
    } else {
        println("Using $threadCount coroutines (requested: $requestedThreads)")
    }
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

