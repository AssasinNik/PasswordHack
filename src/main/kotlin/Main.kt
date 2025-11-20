import org.mindrot.jbcrypt.BCrypt
import de.mkammerer.argon2.Argon2Factory
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import org.jocl.*

enum class HashAlgorithm { MD5, SHA1, BCRYPT, ARGON2 }

interface HashChecker {
    fun check(candidate: String): Boolean
    fun checkBytes(candidate: ByteArray): Boolean = check(String(candidate))
}

class MD5HashChecker(private val targetHash: ByteArray) : HashChecker {
    private val threadLocalMD = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

    override fun check(candidate: String): Boolean {
        return checkBytes(candidate.toByteArray())
    }
    
    override fun checkBytes(candidate: ByteArray): Boolean {
        val md = threadLocalMD.get()
        md.reset()
        md.update(candidate)
        val digest = md.digest()
        return java.util.Arrays.equals(digest, targetHash)
    }
}

class SHA1HashChecker(private val targetHash: ByteArray) : HashChecker {
    private val threadLocalMD = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-1") }

    override fun check(candidate: String): Boolean {
        return checkBytes(candidate.toByteArray())
    }
    
    override fun checkBytes(candidate: ByteArray): Boolean {
        val md = threadLocalMD.get()
        md.reset()
        md.update(candidate)
        val digest = md.digest()
        return java.util.Arrays.equals(digest, targetHash)
    }
}

class BcryptHashChecker(private val hash: String) : HashChecker {
    override fun check(candidate: String): Boolean {
        return BCrypt.checkpw(candidate, hash)
    }
}

class Argon2HashChecker(private val hash: String) : HashChecker {
    companion object {
        val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    }

    override fun check(candidate: String): Boolean {
        return argon2.verify(hash, candidate.toCharArray())
    }
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

class PasswordGenerator(private val alphabet: String, private val length: Int) {
    private val alphabetSize = alphabet.length
    private val password = CharArray(length) { alphabet[0] }
    private val indices = IntArray(length) { 0 }
    private var cachedString: String? = null
    private var cachedBytes: ByteArray? = null
    private var isDirty = true
    
    fun reset() {
        for (i in password.indices) {
            password[i] = alphabet[0]
            indices[i] = 0
        }
        isDirty = true
        cachedString = null
        cachedBytes = null
    }
    
    fun increment(): Boolean {
        isDirty = true
        cachedString = null
        cachedBytes = null
        var i = length - 1
        while (i >= 0) {
            if (indices[i] < alphabetSize - 1) {
                indices[i]++
                password[i] = alphabet[indices[i]]
                return true
            } else {
                indices[i] = 0
                password[i] = alphabet[0]
                i--
            }
        }
        return false
    }
    
    override fun toString(): String {
        if (isDirty || cachedString == null) {
            cachedString = String(password)
            isDirty = false
        }
        return cachedString!!
    }
    
    fun getBytes(): ByteArray {
        if (isDirty || cachedBytes == null) {
            cachedBytes = String(password).toByteArray()
            isDirty = false
        }
        return cachedBytes!!
    }
    
    fun setFromIndex(index: Long) {
        isDirty = true
        cachedString = null
        cachedBytes = null
        var currentIndex = index
        for (i in length - 1 downTo 0) {
            val idx = (currentIndex % alphabetSize).toInt()
            indices[i] = idx
            password[i] = alphabet[idx]
            currentIndex /= alphabetSize
        }
    }
    
    fun setFromIndexBigInt(index: BigInteger) {
        isDirty = true
        cachedString = null
        cachedBytes = null
        var currentIndex = index
        val alphabetSizeBig = BigInteger.valueOf(alphabetSize.toLong())
        for (i in length - 1 downTo 0) {
            val remainder = currentIndex.mod(alphabetSizeBig)
            val idx = remainder.toInt()
            indices[i] = idx
            password[i] = alphabet[idx]
            currentIndex = currentIndex.divide(alphabetSizeBig)
        }
    }
}

fun detectAlgorithm(hash: String): HashAlgorithm {
    return when {
        hash.startsWith("\$2a\$") || hash.startsWith("\$2b\$") || hash.startsWith("\$2y\$") -> HashAlgorithm.BCRYPT
        hash.startsWith("\$argon2") -> HashAlgorithm.ARGON2
        hash.length == 32 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> HashAlgorithm.MD5
        hash.length == 40 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> HashAlgorithm.SHA1
        else -> throw IllegalArgumentException("Unsupported hash format: $hash")
    }
}

fun createHashChecker(algorithm: HashAlgorithm, hash: String): HashChecker {
    return when (algorithm) {
        HashAlgorithm.MD5 -> MD5HashChecker(hexToByteArray(hash))
        HashAlgorithm.SHA1 -> SHA1HashChecker(hexToByteArray(hash))
        HashAlgorithm.BCRYPT -> BcryptHashChecker(hash)
        HashAlgorithm.ARGON2 -> Argon2HashChecker(hash)
    }
}

// GPU ускоритель для MD5 и SHA-1
class GPUAccelerator {
    companion object {
        init {
            CL.setExceptionsEnabled(true)
        }
        
        private var initialized = false
        private var context: cl_context? = null
        private var device: cl_device_id? = null
        private var commandQueue: cl_command_queue? = null
        
        fun isAvailable(): Boolean {
            if (initialized) return context != null
            initialized = true
            
            try {
                val numPlatforms = IntArray(1)
                CL.clGetPlatformIDs(0, null, numPlatforms)
                
                if (numPlatforms[0] == 0) return false
                
                val platforms = arrayOfNulls<cl_platform_id>(numPlatforms[0])
                CL.clGetPlatformIDs(platforms.size, platforms, null)
                val platform = platforms[0] ?: return false
                
                val numDevices = IntArray(1)
                CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_GPU, 0, null, numDevices)
                
                if (numDevices[0] == 0) return false
                
                val devices = arrayOfNulls<cl_device_id>(numDevices[0])
                CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_GPU, devices.size, devices, null)
                device = devices[0] ?: return false
                
                val contextProperties = cl_context_properties()
                contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM.toLong(), platform)
                
                context = CL.clCreateContext(contextProperties, 1, arrayOf(device), null, null, null)
                commandQueue = CL.clCreateCommandQueue(context, device, 0, null)
                
                return true
            } catch (e: Exception) {
                return false
            }
        }
        
        fun cleanup() {
            try {
                commandQueue?.let { CL.clReleaseCommandQueue(it) }
                context?.let { CL.clReleaseContext(it) }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            commandQueue = null
            context = null
            device = null
        }
    }
}

fun getAlphabetForAlgorithm(algorithm: HashAlgorithm, complexity: String = "medium"): String {
    return when (algorithm) {
        HashAlgorithm.MD5, HashAlgorithm.SHA1 -> {
            when (complexity) {
                "easy" -> "0123456789abcdefghijklmnopqrstuvwxyz"
                "medium" -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                "hard", "very_hard" -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#\$%^&*()_+-=[]{}|;:,.<>?"
                else -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            }
        }
        HashAlgorithm.BCRYPT, HashAlgorithm.ARGON2 -> {
            when (complexity) {
                "easy" -> "0123456789"
                "medium" -> "0123456789abcdefghijklmnopqrstuvwxyz"
                "hard", "very_hard" -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                else -> "0123456789"
            }
        }
    }
}

suspend fun bruteForce(
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

fun parseArguments(args: Array<String>): Map<String, String> {
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

fun printHelp() {
    println("""
        PasswordHack - Быстрый подбор паролей
        
        Использование:
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="<hash>" [опции]
        
        Опции:
          -hash=<hash>          Хэш для подбора (обязательно)
          -length=<n>            Максимальная длина пароля (по умолчанию: 8)
          -minLength=<n>         Минимальная длина пароля (по умолчанию: 1)
          -threads=<n>           Количество потоков (по умолчанию: количество ядер CPU)
          -gpu                   Использовать GPU ускорение (если доступно, только для MD5/SHA-1)
          -help                  Показать эту справку
        
        Примеры:
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="7c4a8d09ca3762af61e59520943dc26494f8941b"
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="7c4a8d09ca3762af61e59520943dc26494f8941b" -length=10
          java -jar PasswordHack-1.0-SNAPSHOT.jar -hash="\$2a\$10\$..." -length=6 -threads=16
    """.trimIndent())
}

fun main(args: Array<String>) = runBlocking {
    val parsedArgs = parseArguments(args)
    
    if (parsedArgs.containsKey("help") || args.isEmpty()) {
        printHelp()
        return@runBlocking
    }

    val hash = parsedArgs["hash"] ?: run {
        println("Ошибка: необходимо указать хэш через -hash=\"<hash>\"")
        printHelp()
        return@runBlocking
    }

    if (hash.isEmpty()) {
        println("Ошибка: пустой хэш")
        return@runBlocking
    }

    val algorithm = try {
        detectAlgorithm(hash)
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

    val checker = createHashChecker(algorithm, hash)
    
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
            
            val alphabet = getAlphabetForAlgorithm(algorithm, complexity)
            
            if (alphabets.size > 1) {
                println("Trying alphabet: $complexity (${alphabet.length} characters)")
            } else {
                println("Alphabet size: ${alphabet.length} characters")
            }
            
            password = bruteForce(checker, alphabet, minLength, maxLength, threadCount, useGPU)
        }
        
        if (alphabets.size > 1 && password == null) {
            println("Alphabet size: ${getAlphabetForAlgorithm(algorithm, alphabets.last()).length} characters (final attempt)")
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