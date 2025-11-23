package ru.cherenkov.data.checker

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.cherenkov.domain.repository.HashChecker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Обертка для асинхронной проверки медленных алгоритмов (bcrypt, Argon2)
 * Использует батчинг и параллельную обработку для ускорения
 * 
 * Оптимизации:
 * - Использует выделенный thread pool для CPU-интенсивных операций
 * - Улучшенный батчинг с предварительным распределением работы
 * - Более эффективное управление параллелизмом
 */
class AsyncHashChecker(
    private val checker: HashChecker,
    private val batchSize: Int = 10,
    private val parallelism: Int = Runtime.getRuntime().availableProcessors()
) {
    // Выделенный thread pool для CPU-интенсивных операций хеширования
    // Используем фиксированный пул для лучшей производительности
    private val cpuDispatcher: CoroutineContext = Executors.newFixedThreadPool(parallelism)
        .asCoroutineDispatcher()
    
    /**
     * Асинхронная проверка одного кандидата
     */
    suspend fun checkAsync(candidate: String): Boolean = withContext(cpuDispatcher) {
        checker.check(candidate)
    }
    
    /**
     * Параллельная проверка батча кандидатов
     * Оптимизировано для лучшей утилизации CPU
     */
    suspend fun checkBatch(candidates: List<String>): List<Pair<String, Boolean>> = coroutineScope {
        candidates.map { candidate ->
            async(cpuDispatcher) {
                Pair(candidate, checker.check(candidate))
            }
        }.awaitAll()
    }
    
    /**
     * Параллельная проверка с ограничением параллелизма
     * Использует Semaphore для контроля количества одновременных проверок
     * 
     * Оптимизация: использует выделенный dispatcher для лучшей производительности
     */
    suspend fun checkBatchWithLimit(candidates: List<String>): List<Pair<String, Boolean>> = coroutineScope {
        val semaphore = Semaphore(parallelism)
        
        candidates.map { candidate ->
            async(cpuDispatcher) {
                semaphore.withPermit {
                    Pair(candidate, checker.check(candidate))
                }
            }
        }.awaitAll()
    }
    
    /**
     * Оптимизированная проверка батча с ранним выходом при нахождении совпадения
     * Позволяет прервать проверку остальных кандидатов, если пароль найден
     */
    suspend fun checkBatchWithEarlyExit(candidates: List<String>): Pair<String?, List<Pair<String, Boolean>>> = coroutineScope {
        val semaphore = Semaphore(parallelism)
        val foundPassword = java.util.concurrent.atomic.AtomicReference<String?>(null)
        
        val results = candidates.map { candidate ->
            async(cpuDispatcher) {
                // Проверяем, не найден ли уже пароль
                if (foundPassword.get() != null) {
                    return@async Pair(candidate, false)
                }
                
                semaphore.withPermit {
                    // Двойная проверка после получения разрешения
                    if (foundPassword.get() != null) {
                        return@withPermit Pair(candidate, false)
                    }
                    
                    val isMatch = checker.check(candidate)
                    if (isMatch) {
                        foundPassword.compareAndSet(null, candidate)
                    }
                    Pair(candidate, isMatch)
                }
            }
        }.awaitAll()
        
        Pair(foundPassword.get(), results)
    }
    
    /**
     * Освобождение ресурсов
     */
    fun cleanup() {
        try {
            (cpuDispatcher as? ExecutorCoroutineDispatcher)?.close()
        } catch (e: Exception) {
            // Игнорируем ошибки при закрытии
        }
    }
}

