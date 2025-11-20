package domain.usecase

import domain.repository.HashChecker
import data.util.PasswordGenerator
import data.gpu.GPUAccelerator
import data.repository.MD5HashChecker
import data.repository.SHA1HashChecker
import kotlinx.coroutines.*
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference

class BruteForceUseCase {
    suspend fun execute(
        checker: HashChecker,
        alphabet: String,
        minLength: Int,
        maxLength: Int,
        threadCount: Int,
        useGPU: Boolean = false
    ): String? = coroutineScope {
        val foundPassword = AtomicReference<String?>()
        val shouldStop = AtomicReference(false)
        
        // Определяем оптимальный размер батча в зависимости от алгоритма
        val isFastAlgorithm = checker is MD5HashChecker || checker is SHA1HashChecker
        
        // Проверяем доступность GPU и используем его если возможно
        val gpuAvailable = useGPU && isFastAlgorithm && GPUAccelerator.isAvailable()
        if (gpuAvailable) {
            println("🚀 GPU acceleration enabled")
        }
        
        val batchSize = if (isFastAlgorithm) {
            if (gpuAvailable) 200000L else 100000L
        } else {
            10000L
        }
        
        // Улучшенная оптимизация: проверяем shouldStop реже (идея из Python кода)
        // Локальный счетчик накапливается, проверка флага выполняется редко
        val stopCheckInterval = if (isFastAlgorithm) {
            batchSize * 2  // Проверяем реже для быстрых алгоритмов
        } else {
            batchSize / 2  // Для медленных алгоритмов проверяем чаще
        }
        val localCounterResetInterval = stopCheckInterval / 10  // Батчинг локального счетчика

        for (length in minLength..maxLength) {
            if (shouldStop.get()) break

            val alphabetSize = alphabet.length.toLong()
            val total = BigInteger.valueOf(alphabetSize).pow(length)
            
            val useLong = total <= BigInteger.valueOf(Long.MAX_VALUE)
            val totalLong = if (useLong) total.toLong() else Long.MAX_VALUE
            
            // Увеличиваем размер чанка для лучшего распределения работы
            val chunkSize = if (useLong) {
                (totalLong / threadCount + 1).coerceAtLeast(50000L)
            } else {
                total.divide(BigInteger.valueOf(threadCount.toLong())).toLong().coerceAtLeast(50000L)
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
                                    if (checker.checkBytes(candidateBytes)) {
                                        foundPassword.set(generator.toString())
                                        shouldStop.set(true)
                                        break
                                    }
                                    
                                    if (!generator.increment()) break
                                    count++
                                    localCounter++
                                    
                                    // Батчинг: проверяем shouldStop только периодически
                                    if (localCounter >= localCounterResetInterval) {
                                        if (shouldStop.get()) break
                                        localCounter = 0L
                                    }
                                }
                            } else {
                                while (count < (end - start)) {
                                    val candidate = generator.toString()
                                    if (checker.check(candidate)) {
                                        foundPassword.set(candidate)
                                        shouldStop.set(true)
                                        break
                                    }
                                    
                                    if (!generator.increment()) break
                                    count++
                                    localCounter++
                                    
                                    // Батчинг: проверяем shouldStop только периодически
                                    if (localCounter >= localCounterResetInterval) {
                                        if (shouldStop.get()) break
                                        localCounter = 0L
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
                                    if (checker.checkBytes(candidateBytes)) {
                                        foundPassword.set(generator.toString())
                                        shouldStop.set(true)
                                        break
                                    }
                                    
                                    if (!generator.increment()) break
                                    count = count.add(BigInteger.ONE)
                                    localCounterBig = localCounterBig.add(BigInteger.ONE)
                                    
                                    // Батчинг: проверяем shouldStop только периодически
                                    if (localCounterBig >= localCounterResetIntervalBig) {
                                        if (shouldStop.get()) break
                                        localCounterBig = BigInteger.ZERO
                                    }
                                }
                            } else {
                                while (count < range) {
                                    val candidate = generator.toString()
                                    if (checker.check(candidate)) {
                                        foundPassword.set(candidate)
                                        shouldStop.set(true)
                                        break
                                    }
                                    
                                    if (!generator.increment()) break
                                    count = count.add(BigInteger.ONE)
                                    localCounterBig = localCounterBig.add(BigInteger.ONE)
                                    
                                    // Батчинг: проверяем shouldStop только периодически
                                    if (localCounterBig >= localCounterResetIntervalBig) {
                                        if (shouldStop.get()) break
                                        localCounterBig = BigInteger.ZERO
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
            if (foundPassword.get() != null) break
        }

        foundPassword.get()
    }
}

