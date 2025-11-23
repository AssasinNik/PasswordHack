package ru.cherenkov.data.checker

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.cherenkov.domain.repository.HashChecker

/**
 * Обертка для асинхронной проверки медленных алгоритмов (bcrypt, Argon2)
 * Использует батчинг и параллельную обработку для ускорения
 */
class AsyncHashChecker(
    private val checker: HashChecker,
    private val batchSize: Int = 10,
    private val parallelism: Int = Runtime.getRuntime().availableProcessors()
) {
    /**
     * Асинхронная проверка одного кандидата
     */
    suspend fun checkAsync(candidate: String): Boolean = withContext(Dispatchers.Default) {
        checker.check(candidate)
    }
    
    /**
     * Параллельная проверка батча кандидатов
     */
    suspend fun checkBatch(candidates: List<String>): List<Pair<String, Boolean>> = coroutineScope {
        candidates.map { candidate ->
            async(Dispatchers.Default) {
                Pair(candidate, checker.check(candidate))
            }
        }.awaitAll()
    }
    
    /**
     * Параллельная проверка с ограничением параллелизма
     * Использует Semaphore для контроля количества одновременных проверок
     */
    suspend fun checkBatchWithLimit(candidates: List<String>): List<Pair<String, Boolean>> = coroutineScope {
        val semaphore = Semaphore(parallelism)
        
        candidates.map { candidate ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    Pair(candidate, checker.check(candidate))
                }
            }
        }.awaitAll()
    }
}

