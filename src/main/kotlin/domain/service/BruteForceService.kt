package ru.cherenkov.domain.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import ru.cherenkov.data.accelerator.GPUAccelerator
import ru.cherenkov.data.checker.AsyncHashChecker
import ru.cherenkov.data.checker.BcryptHashChecker
import ru.cherenkov.data.checker.Argon2HashChecker
import ru.cherenkov.data.checker.MD5HashChecker
import ru.cherenkov.data.checker.SHA1HashChecker
import ru.cherenkov.data.generator.PasswordGenerator
import ru.cherenkov.domain.model.BruteForceResult
import ru.cherenkov.domain.repository.HashChecker
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class BruteForceService {
    private val _totalChecked = AtomicLong(0)
    
    fun getTotalChecked(): Long = _totalChecked.get()
    
    suspend fun bruteForce(
        checker: HashChecker,
        alphabet: String,
        minLength: Int,
        maxLength: Int,
        threadCount: Int,
        useGPU: Boolean = false
    ): String? = coroutineScope {
        // Канал для передачи результатов поиска
        val resultChannel = Channel<BruteForceResult>(Channel.UNLIMITED)
        // Канал для передачи статистики прогресса
        val progressChannel = Channel<BruteForceResult.Progress>(Channel.UNLIMITED)
        
        val foundPassword = AtomicReference<String?>()
        val shouldStop = AtomicReference(false)
        val totalChecked = AtomicLong(0)
        
        // Сбрасываем счетчик для нового поиска
        _totalChecked.set(0)
        
        // Определяем оптимальный размер батча в зависимости от алгоритма
        val isFastAlgorithm = checker is MD5HashChecker || checker is SHA1HashChecker
        
        // Проверяем доступность GPU и используем его если возможно
        val gpuAvailable = useGPU && isFastAlgorithm && GPUAccelerator.isAvailable()
        if (gpuAvailable) {
            println("🚀 GPU acceleration enabled")
        }
        
        val isSlowAlgorithm = checker !is MD5HashChecker && checker !is SHA1HashChecker
        val isBcryptOrArgon2 = checker is BcryptHashChecker || checker is Argon2HashChecker
        
        // Для медленных алгоритмов создаем асинхронный checker с батчингом
        val asyncChecker = if (isBcryptOrArgon2) {
            // Для bcrypt/Argon2 используем батчинг: собираем несколько кандидатов и проверяем параллельно
            // Оптимизация: увеличиваем параллелизм для лучшей утилизации CPU
            // Используем больше параллелизма, чем количество корутин, так как каждая корутина может обрабатывать батчи
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val parallelismForSlow = if (checker is BcryptHashChecker) {
                // Для bcrypt используем больше параллелизма (он немного быстрее Argon2)
                (cpuCores * 2).coerceAtMost(32).coerceAtLeast(8)
            } else {
                // Для Argon2 используем меньше параллелизма (он самый медленный)
                (cpuCores * 1.5).toInt().coerceAtMost(24).coerceAtLeast(6)
            }
            // Увеличиваем размер батча для лучшей утилизации CPU
            val optimalBatchSize = if (checker is BcryptHashChecker) {
                30 // Для bcrypt можно использовать больший батч
            } else {
                20 // Для Argon2 используем меньший батч
            }
            AsyncHashChecker(checker, batchSize = optimalBatchSize, parallelism = parallelismForSlow)
        } else {
            null
        }
        
        val batchSize = if (isFastAlgorithm) {
            if (gpuAvailable) 200000L else 100000L
        } else {
            // Для медленных алгоритмов (bcrypt, Argon2) используем меньший батч
            1000L
        }
        
        // Улучшенная оптимизация: проверяем shouldStop реже (идея из Python кода)
        // Локальный счетчик накапливается, проверка флага выполняется редко
        val stopCheckInterval = if (isFastAlgorithm) {
            batchSize * 2  // Проверяем реже для быстрых алгоритмов
        } else {
            batchSize / 2  // Для медленных алгоритмов проверяем чаще
        }
        // Для медленных алгоритмов отправляем прогресс чаще, чтобы видеть работу
        val localCounterResetInterval = if (isSlowAlgorithm) {
            (stopCheckInterval / 5).coerceAtLeast(100L)  // Минимум каждые 100 проверок для медленных
        } else {
            stopCheckInterval / 10  // Батчинг локального счетчика для быстрых
        }

        // Асинхронная обработка результатов из канала
        val resultProcessor = async(Dispatchers.Default) {
            var result: String? = null
            try {
                // Обрабатываем результаты асинхронно
                for (resultValue in resultChannel) {
                    when (resultValue) {
                        is BruteForceResult.Found -> {
                            result = resultValue.password
                            foundPassword.set(result)
                            shouldStop.set(true)
                            break
                        }
                        is BruteForceResult.Completed -> {
                            break
                        }
                        is BruteForceResult.Error -> {
                            // Логируем ошибку, но продолжаем работу
                            println("⚠️ Error in worker: ${resultValue.message}")
                        }
                        is BruteForceResult.Progress -> {
                            // Прогресс обрабатывается отдельно
                        }
                    }
                    if (shouldStop.get()) break
                }
            } catch (e: Exception) {
                // Канал закрыт или произошла ошибка
            }
            result
        }
        
        // Асинхронная обработка статистики прогресса
        val progressProcessor = async(Dispatchers.Default) {
            try {
                for (progress in progressChannel) {
                    val added = progress.checked
                    totalChecked.addAndGet(added)
                    _totalChecked.addAndGet(added) // Обновляем глобальный счетчик
                    if (shouldStop.get()) break
                }
            } catch (e: Exception) {
                // Канал закрыт
            }
        }

        for (length in minLength..maxLength) {
            if (shouldStop.get()) break

            val alphabetSize = alphabet.length.toLong()
            val total = BigInteger.valueOf(alphabetSize).pow(length)
            
            val useLong = total <= BigInteger.valueOf(Long.MAX_VALUE)
            val totalLong = if (useLong) total.toLong() else Long.MAX_VALUE
            
            // Оптимизированный размер чанка для лучшего распределения работы
            // Для медленных алгоритмов используем меньшие чанки для лучшего баланса нагрузки
            val chunkSize = if (useLong) {
                if (isBcryptOrArgon2) {
                    // Для медленных алгоритмов используем меньшие чанки для лучшего баланса
                    (totalLong / threadCount + 1).coerceAtLeast(10000L).coerceAtMost(100000L)
                } else {
                    (totalLong / threadCount + 1).coerceAtLeast(50000L)
                }
            } else {
                if (isBcryptOrArgon2) {
                    val chunkSizeBig = total.divide(BigInteger.valueOf(threadCount.toLong()))
                    chunkSizeBig.min(BigInteger.valueOf(100000L)).toLong().coerceAtLeast(10000L)
                } else {
                    total.divide(BigInteger.valueOf(threadCount.toLong())).toLong().coerceAtLeast(50000L)
                }
            }

            val jobs = if (useLong) {
                List(threadCount) { workerId ->
                    launch(Dispatchers.Default) {
                        val generator = PasswordGenerator(alphabet, length)
                        try {
                            val start = workerId.toLong() * chunkSize
                            val end = if (workerId == threadCount - 1) {
                                totalLong
                            } else {
                                (start + chunkSize).coerceAtMost(totalLong)
                            }

                            generator.setFromIndex(start)
                            var count = 0L
                            var localCounter = 0L  // Локальный счетчик для батчинга (идея из Python)
                            
                            // Для быстрых алгоритмов используем ByteArray напрямую
                            if (isFastAlgorithm) {
                                while (count < (end - start)) {
                                    val candidateBytes = generator.getBytes()
                                    // Асинхронная проверка через канал
                                    if (checker.checkBytes(candidateBytes)) {
                                        val password = generator.toString()
                                        resultChannel.send(BruteForceResult.Found(password))
                                        shouldStop.set(true)
                                        break
                                    }
                                    
                                    if (!generator.increment()) break
                                    count++
                                    localCounter++
                                    
                                    // Отправляем статистику прогресса асинхронно
                                    if (localCounter >= localCounterResetInterval) {
                                        if (shouldStop.get()) break
                                        progressChannel.trySend(BruteForceResult.Progress(localCounter, end - start))
                                        localCounter = 0L
                                    }
                                }
                            } else {
                                // Для медленных алгоритмов используем батчинг
                                if (isBcryptOrArgon2 && asyncChecker != null) {
                                    // Оптимизированный размер батча в зависимости от алгоритма
                                    // Увеличиваем размер батча для лучшей утилизации CPU
                                    val batchSizeForSlow = if (checker is BcryptHashChecker) {
                                        30 // Для bcrypt используем больший батч
                                    } else {
                                        25 // Для Argon2 используем средний батч
                                    }
                                    // Предварительное выделение памяти для батча (оптимизация)
                                    val batch = ArrayList<String>(batchSizeForSlow)
                                    
                                    while (count < (end - start)) {
                                        val candidate = generator.toString()
                                        batch.add(candidate)
                                        
                                        if (!generator.increment()) break
                                        count++
                                        localCounter++
                                        
                                        // Когда батч заполнен или достигнут интервал, проверяем параллельно
                                        if (batch.size >= batchSizeForSlow || localCounter >= localCounterResetInterval) {
                                            if (shouldStop.get()) break
                                            
                                            // Параллельная проверка батча с оптимизацией
                                            val results = asyncChecker.checkBatchWithLimit(batch)
                                            
                                            // Проверяем результаты (оптимизация: прерываем при первом совпадении)
                                            var found = false
                                            for ((cand, isMatch) in results) {
                                                if (isMatch) {
                                                    resultChannel.send(BruteForceResult.Found(cand))
                                                    shouldStop.set(true)
                                                    found = true
                                                    break
                                                }
                                            }
                                            
                                            if (found || shouldStop.get()) break
                                            
                                            // Отправляем статистику прогресса
                                            progressChannel.trySend(BruteForceResult.Progress(batch.size.toLong(), end - start))
                                            batch.clear()
                                            localCounter = 0L
                                        }
                                    }
                                    
                                    // Проверяем оставшиеся кандидаты в батче
                                    if (batch.isNotEmpty() && !shouldStop.get()) {
                                        val results = asyncChecker.checkBatchWithLimit(batch)
                                        for ((cand, isMatch) in results) {
                                            if (isMatch) {
                                                resultChannel.send(BruteForceResult.Found(cand))
                                                shouldStop.set(true)
                                                break
                                            }
                                        }
                                    }
                                } else {
                                    // Для других алгоритмов используем обычную проверку
                                    while (count < (end - start)) {
                                        val candidate = generator.toString()
                                        // Асинхронная проверка через канал
                                        if (checker.check(candidate)) {
                                            resultChannel.send(BruteForceResult.Found(candidate))
                                            shouldStop.set(true)
                                            break
                                        }
                                        
                                        if (!generator.increment()) break
                                        count++
                                        localCounter++
                                        
                                        // Отправляем статистику прогресса асинхронно
                                        if (localCounter >= localCounterResetInterval) {
                                            if (shouldStop.get()) break
                                            progressChannel.trySend(BruteForceResult.Progress(localCounter, end - start))
                                            localCounter = 0L
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore exceptions, continue with other workers
                        }
                    }
                }
            } else {
                List(threadCount) { workerId ->
                    launch(Dispatchers.Default) {
                        val generator = PasswordGenerator(alphabet, length)
                        try {
                            val chunkSizeBig = BigInteger.valueOf(chunkSize)
                            val start = BigInteger.valueOf(workerId.toLong()).multiply(chunkSizeBig)
                            val end = if (workerId == threadCount - 1) {
                                total
                            } else {
                                start.add(chunkSizeBig).min(total)
                            }

                            generator.setFromIndexBigInt(start)
                            var count = BigInteger.ZERO
                            val range = end.subtract(start)
                            
                            val localCounterResetIntervalBig = BigInteger.valueOf(localCounterResetInterval)
                            var localCounterBig = BigInteger.ZERO
                            
                            if (isFastAlgorithm) {
                                while (count < range) {
                                    val candidateBytes = generator.getBytes()
                                    // Асинхронная проверка через канал
                                    if (checker.checkBytes(candidateBytes)) {
                                        val password = generator.toString()
                                        resultChannel.send(BruteForceResult.Found(password))
                                        shouldStop.set(true)
                                        break
                                    }
                                    
                                    if (!generator.increment()) break
                                    count = count.add(BigInteger.ONE)
                                    localCounterBig = localCounterBig.add(BigInteger.ONE)
                                    
                                    // Отправляем статистику прогресса асинхронно
                                    if (localCounterBig >= localCounterResetIntervalBig) {
                                        if (shouldStop.get()) break
                                        progressChannel.trySend(
                                            BruteForceResult.Progress(
                                                localCounterBig.toLong(),
                                                range.toLong()
                                            )
                                        )
                                        localCounterBig = BigInteger.ZERO
                                    }
                                }
                            } else {
                                // Для медленных алгоритмов используем батчинг
                                if (isBcryptOrArgon2 && asyncChecker != null) {
                                    // Оптимизированный размер батча в зависимости от алгоритма
                                    val batchSizeForSlow = if (checker is BcryptHashChecker) {
                                        30 // Для bcrypt используем больший батч
                                    } else {
                                        25 // Для Argon2 используем средний батч
                                    }
                                    val batchSizeBig = BigInteger.valueOf(batchSizeForSlow.toLong())
                                    // Предварительное выделение памяти для батча (оптимизация)
                                    val batch = ArrayList<String>(batchSizeForSlow)
                                    
                                    while (count < range) {
                                        val candidate = generator.toString()
                                        batch.add(candidate)
                                        
                                        if (!generator.increment()) break
                                        count = count.add(BigInteger.ONE)
                                        localCounterBig = localCounterBig.add(BigInteger.ONE)
                                        
                                        // Когда батч заполнен или достигнут интервал, проверяем параллельно
                                        if (batch.size >= batchSizeForSlow || localCounterBig >= localCounterResetIntervalBig) {
                                            if (shouldStop.get()) break
                                            
                                            // Параллельная проверка батча с оптимизацией
                                            val results = asyncChecker.checkBatchWithLimit(batch)
                                            
                                            // Проверяем результаты (оптимизация: прерываем при первом совпадении)
                                            var found = false
                                            for ((cand, isMatch) in results) {
                                                if (isMatch) {
                                                    resultChannel.send(BruteForceResult.Found(cand))
                                                    shouldStop.set(true)
                                                    found = true
                                                    break
                                                }
                                            }
                                            
                                            if (found || shouldStop.get()) break
                                            
                                            // Отправляем статистику прогресса
                                            progressChannel.trySend(
                                                BruteForceResult.Progress(
                                                    batch.size.toLong(),
                                                    range.toLong()
                                                )
                                            )
                                            batch.clear()
                                            localCounterBig = BigInteger.ZERO
                                        }
                                    }
                                    
                                    // Проверяем оставшиеся кандидаты в батче
                                    if (batch.isNotEmpty() && !shouldStop.get()) {
                                        val results = asyncChecker.checkBatchWithLimit(batch)
                                        for ((cand, isMatch) in results) {
                                            if (isMatch) {
                                                resultChannel.send(BruteForceResult.Found(cand))
                                                shouldStop.set(true)
                                                break
                                            }
                                        }
                                    }
                                } else {
                                    // Для других алгоритмов используем обычную проверку
                                    while (count < range) {
                                        val candidate = generator.toString()
                                        // Асинхронная проверка через канал
                                        if (checker.check(candidate)) {
                                            resultChannel.send(BruteForceResult.Found(candidate))
                                            shouldStop.set(true)
                                            break
                                        }
                                        
                                        if (!generator.increment()) break
                                        count = count.add(BigInteger.ONE)
                                        localCounterBig = localCounterBig.add(BigInteger.ONE)
                                        
                                        // Отправляем статистику прогресса асинхронно
                                        if (localCounterBig >= localCounterResetIntervalBig) {
                                            if (shouldStop.get()) break
                                            progressChannel.trySend(
                                                BruteForceResult.Progress(
                                                    localCounterBig.toLong(),
                                                    range.toLong()
                                                )
                                            )
                                            localCounterBig = BigInteger.ZERO
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore exceptions, continue with other workers
                        }
                    }
                }
            }

            jobs.joinAll()
            
            // Проверяем, найден ли пароль
            if (foundPassword.get() != null || shouldStop.get()) {
                break
            }
        }

        // Отправляем сигнал о завершении
        resultChannel.send(BruteForceResult.Completed)
        
        // Закрываем каналы
        resultChannel.close()
        progressChannel.close()
        
        // Ждем завершения обработки результатов и прогресса
        val result = resultProcessor.await()
        progressProcessor.cancel() // Отменяем обработку прогресса, если еще не завершена
        
        // Очищаем ресурсы асинхронного checker
        asyncChecker?.cleanup()
        
        result ?: foundPassword.get()
    }
}

